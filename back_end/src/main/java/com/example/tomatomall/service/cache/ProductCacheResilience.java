package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class ProductCacheResilience {

    public enum RedisAccess {
        NORMAL,
        BYPASS,
        RECOVERY_PROBE
    }

    private enum State {
        NORMAL,
        BYPASS,
        RECOVERY_PROBE
    }

    private final ProductCacheResilienceProperties properties;
    private final Clock clock;
    private final Semaphore databaseFallbackPermits;
    private final AtomicInteger activeDatabaseFallbacks = new AtomicInteger();
    private final AtomicLong bypassUntilMillis = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.NORMAL);
    private final Counter redisFailures;
    private final Counter redisBypassed;
    private final Counter databaseFallbackSuccess;
    private final Counter databaseFallbackRejected;
    private final Counter recoveryAttempts;
    private final Counter recoverySuccess;
    private final Counter recoveryCleanupFailures;

    @Autowired
    public ProductCacheResilience(
            ProductCacheResilienceProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(properties, meterRegistry, Clock.systemUTC());
    }

    ProductCacheResilience(
            ProductCacheResilienceProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        validate(properties);
        this.databaseFallbackPermits = new Semaphore(properties.getMaxConcurrentDatabaseFallbacks(), true);
        this.redisFailures = meterRegistry.counter("tomatomall.cache.product.redis.failures");
        this.redisBypassed = meterRegistry.counter("tomatomall.cache.product.redis.bypassed");
        this.databaseFallbackSuccess = meterRegistry.counter("tomatomall.cache.product.database.fallback.success");
        this.databaseFallbackRejected = meterRegistry.counter("tomatomall.cache.product.database.fallback.rejected");
        this.recoveryAttempts = meterRegistry.counter("tomatomall.cache.product.redis.recovery.attempts");
        this.recoverySuccess = meterRegistry.counter("tomatomall.cache.product.redis.recovery.success");
        this.recoveryCleanupFailures = meterRegistry.counter("tomatomall.cache.product.redis.recovery.cleanup.failures");
        Gauge.builder("tomatomall.cache.product.redis.circuit.state", state,
                        value -> stateValue(value.get()))
                .register(meterRegistry);
        Gauge.builder("tomatomall.cache.product.database.fallback.active", activeDatabaseFallbacks, AtomicInteger::get)
                .register(meterRegistry);
    }

    public RedisAccess beforeRedisAccess() {
        State current = state.get();
        if (current == State.NORMAL) {
            return RedisAccess.NORMAL;
        }
        if (current == State.BYPASS && clock.millis() >= bypassUntilMillis.get()
                && state.compareAndSet(State.BYPASS, State.RECOVERY_PROBE)) {
            recoveryAttempts.increment();
            return RedisAccess.RECOVERY_PROBE;
        }
        redisBypassed.increment();
        return RedisAccess.BYPASS;
    }

    public void recordRedisInfrastructureFailure() {
        redisFailures.increment();
        reopenBypass();
    }

    public void recordRecoveryFailure() {
        reopenBypass();
    }

    public void recordRecoveryCleanupFailure() {
        recoveryCleanupFailures.increment();
        reopenBypass();
    }

    public void recordRecoverySuccess() {
        state.set(State.NORMAL);
        bypassUntilMillis.set(0);
        recoverySuccess.increment();
    }

    public <T> T executeDatabaseFallback(Supplier<T> databaseWork) {
        boolean acquired;
        try {
            acquired = databaseFallbackPermits.tryAcquire(
                    properties.getDatabaseFallbackWait().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseFallbackRejected.increment();
            throw new ProductCacheFallbackRejectedException();
        }
        if (!acquired) {
            databaseFallbackRejected.increment();
            throw new ProductCacheFallbackRejectedException();
        }
        activeDatabaseFallbacks.incrementAndGet();
        try {
            T result = databaseWork.get();
            databaseFallbackSuccess.increment();
            return result;
        } finally {
            activeDatabaseFallbacks.decrementAndGet();
            databaseFallbackPermits.release();
        }
    }

    public int activeDatabaseFallbacks() {
        return activeDatabaseFallbacks.get();
    }

    static ProductCacheResilience unmetered() {
        return new ProductCacheResilience(
                new ProductCacheResilienceProperties(),
                new SimpleMeterRegistry(),
                Clock.systemUTC()
        );
    }

    private void reopenBypass() {
        bypassUntilMillis.set(clock.millis() + properties.getBypassDuration().toMillis());
        state.set(State.BYPASS);
    }

    private static double stateValue(State state) {
        if (state == State.BYPASS) {
            return 1.0;
        }
        if (state == State.RECOVERY_PROBE) {
            return 2.0;
        }
        return 0.0;
    }

    private static void validate(ProductCacheResilienceProperties properties) {
        if (properties.getBypassDuration() == null || properties.getBypassDuration().isNegative()
                || properties.getBypassDuration().isZero()) {
            throw new IllegalArgumentException("bypassDuration must be positive");
        }
        if (properties.getMaxConcurrentDatabaseFallbacks() <= 0) {
            throw new IllegalArgumentException("maxConcurrentDatabaseFallbacks must be positive");
        }
        if (properties.getDatabaseFallbackWait() == null || properties.getDatabaseFallbackWait().isNegative()) {
            throw new IllegalArgumentException("databaseFallbackWait must not be negative");
        }
        if (properties.getCleanupBatchSize() <= 0) {
            throw new IllegalArgumentException("cleanupBatchSize must be positive");
        }
    }
}
