package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class ProductCacheSingleFlight {

    private final boolean enabled;
    private final Duration waitTimeout;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<Integer, Flight> flights = new ConcurrentHashMap<>();
    private final AtomicInteger activeWaiters = new AtomicInteger();
    private final Counter leaders;
    private final Counter followers;
    private final Counter waitSuccess;
    private final Counter waitTimeouts;
    private final Counter waitInterrupted;
    private final Counter leaderFailures;
    private final Timer waitDuration;

    @Autowired
    public ProductCacheSingleFlight(
            ProductCacheSingleFlightProperties properties,
            MeterRegistry meterRegistry,
            @Value("${tomatomall.cache.product-detail.enabled:true}") boolean productCacheEnabled
    ) {
        this(properties, meterRegistry, productCacheEnabled, System::nanoTime);
    }

    ProductCacheSingleFlight(
            ProductCacheSingleFlightProperties properties,
            MeterRegistry meterRegistry,
            boolean productCacheEnabled,
            LongSupplier nanoTime
    ) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        validate(properties);
        this.enabled = productCacheEnabled && properties.isEnabled();
        this.waitTimeout = properties.getWaitTimeout();
        this.leaders = meterRegistry.counter("tomatomall.cache.product.singleflight.leader");
        this.followers = meterRegistry.counter("tomatomall.cache.product.singleflight.follower");
        this.waitSuccess = meterRegistry.counter("tomatomall.cache.product.singleflight.wait.success");
        this.waitTimeouts = meterRegistry.counter("tomatomall.cache.product.singleflight.wait.timeout");
        this.waitInterrupted = meterRegistry.counter("tomatomall.cache.product.singleflight.wait.interrupted");
        this.leaderFailures = meterRegistry.counter("tomatomall.cache.product.singleflight.leader.failures");
        this.waitDuration = meterRegistry.timer("tomatomall.cache.product.singleflight.wait.duration");
        Gauge.builder("tomatomall.cache.product.singleflight.active", flights, ConcurrentHashMap::size)
                .register(meterRegistry);
        Gauge.builder("tomatomall.cache.product.singleflight.waiters.active", activeWaiters, AtomicInteger::get)
                .register(meterRegistry);
        Gauge.builder("tomatomall.cache.product.singleflight.enabled", this, value -> value.enabled ? 1.0 : 0.0)
                .register(meterRegistry);
    }

    ProductCacheSingleFlight(
            ProductCacheSingleFlightProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(properties, meterRegistry, true);
    }

    public long newDeadlineNanos() {
        return nanoTime.getAsLong() + waitTimeout.toNanos();
    }

    public <T> Outcome<T> execute(int productId, long deadlineNanos, Supplier<T> leaderWork) {
        Objects.requireNonNull(leaderWork, "leaderWork");
        if (!enabled) {
            return Outcome.leader(leaderWork.get());
        }
        if (deadlineNanos - nanoTime.getAsLong() <= 0) {
            waitTimeouts.increment();
            throw new ProductCacheSingleFlightTimeoutException();
        }

        Flight candidate = new Flight();
        Flight current = flights.putIfAbsent(productId, candidate);
        if (current == null) {
            if (deadlineNanos - nanoTime.getAsLong() <= 0) {
                ProductCacheSingleFlightTimeoutException timeout =
                        new ProductCacheSingleFlightTimeoutException();
                waitTimeouts.increment();
                candidate.completion.completeExceptionally(timeout);
                flights.remove(productId, candidate);
                throw timeout;
            }
            return lead(productId, candidate, leaderWork);
        }
        return follow(current, deadlineNanos);
    }

    public int activeFlights() {
        return flights.size();
    }

    public int activeWaiters() {
        return activeWaiters.get();
    }

    private <T> Outcome<T> lead(int productId, Flight flight, Supplier<T> leaderWork) {
        leaders.increment();
        try {
            T result = leaderWork.get();
            flight.completion.complete(null);
            return Outcome.leader(result);
        } catch (RuntimeException | Error exception) {
            leaderFailures.increment();
            flight.completion.completeExceptionally(exception);
            throw exception;
        } finally {
            flights.remove(productId, flight);
        }
    }

    private <T> Outcome<T> follow(Flight flight, long deadlineNanos) {
        followers.increment();
        activeWaiters.incrementAndGet();
        long startedNanos = nanoTime.getAsLong();
        try {
            long remainingNanos = deadlineNanos - startedNanos;
            if (remainingNanos <= 0) {
                waitTimeouts.increment();
                throw new ProductCacheSingleFlightTimeoutException();
            }
            flight.completion.get(remainingNanos, TimeUnit.NANOSECONDS);
            waitSuccess.increment();
            return Outcome.follower();
        } catch (TimeoutException exception) {
            waitTimeouts.increment();
            throw new ProductCacheSingleFlightTimeoutException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            waitInterrupted.increment();
            throw new ProductCacheSingleFlightInterruptedException();
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        } finally {
            activeWaiters.decrementAndGet();
            waitDuration.record(nanoTime.getAsLong() - startedNanos, TimeUnit.NANOSECONDS);
        }
    }

    private RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        return new IllegalStateException("product cache leader failed", throwable);
    }

    private static void validate(ProductCacheSingleFlightProperties properties) {
        Duration timeout = properties.getWaitTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("waitTimeout must be positive");
        }
    }

    private static final class Flight {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
    }

    public static final class Outcome<T> {
        private final boolean leader;
        private final T leaderResult;

        private Outcome(boolean leader, T leaderResult) {
            this.leader = leader;
            this.leaderResult = leaderResult;
        }

        public static <T> Outcome<T> leader(T result) {
            return new Outcome<>(true, result);
        }

        public static <T> Outcome<T> follower() {
            return new Outcome<>(false, null);
        }

        public boolean isLeader() {
            return leader;
        }

        public T getLeaderResult() {
            return leaderResult;
        }
    }
}
