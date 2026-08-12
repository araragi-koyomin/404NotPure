package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCacheResilienceTest {

    @Test
    void firstInfrastructureFailureImmediatelyBypassesRedisUntilRecoveryWindow() {
        MutableClock clock = new MutableClock();
        ProductCacheResilience resilience = resilience(clock, 4, Duration.ofMillis(50));

        assertEquals(ProductCacheResilience.RedisAccess.NORMAL, resilience.beforeRedisAccess());

        resilience.recordRedisInfrastructureFailure();

        assertEquals(ProductCacheResilience.RedisAccess.BYPASS, resilience.beforeRedisAccess());
        clock.advance(Duration.ofSeconds(5));
        assertEquals(ProductCacheResilience.RedisAccess.RECOVERY_PROBE, resilience.beforeRedisAccess());
        assertEquals(ProductCacheResilience.RedisAccess.BYPASS, resilience.beforeRedisAccess());
    }

    @Test
    void failedRecoveryReopensBypassAndSuccessfulRecoveryClosesIt() {
        MutableClock clock = new MutableClock();
        ProductCacheResilience resilience = resilience(clock, 4, Duration.ofMillis(50));
        resilience.recordRedisInfrastructureFailure();
        clock.advance(Duration.ofSeconds(5));
        assertEquals(ProductCacheResilience.RedisAccess.RECOVERY_PROBE, resilience.beforeRedisAccess());

        resilience.recordRecoveryFailure();
        assertEquals(ProductCacheResilience.RedisAccess.BYPASS, resilience.beforeRedisAccess());
        clock.advance(Duration.ofSeconds(5));
        assertEquals(ProductCacheResilience.RedisAccess.RECOVERY_PROBE, resilience.beforeRedisAccess());

        resilience.recordRecoverySuccess();
        assertEquals(ProductCacheResilience.RedisAccess.NORMAL, resilience.beforeRedisAccess());
    }

    @Test
    void onlyOneConcurrentRequestCanBecomeTheRecoveryProbe() throws Exception {
        MutableClock clock = new MutableClock();
        ProductCacheResilience resilience = resilience(clock, 4, Duration.ofMillis(50));
        resilience.recordRedisInfrastructureFailure();
        clock.advance(Duration.ofSeconds(5));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger probes = new AtomicInteger();
        try {
            Future<?>[] requests = new Future<?>[8];
            for (int index = 0; index < requests.length; index++) {
                requests[index] = executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    if (resilience.beforeRedisAccess() == ProductCacheResilience.RedisAccess.RECOVERY_PROBE) {
                        probes.incrementAndGet();
                    }
                });
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> request : requests) {
                request.get(1, TimeUnit.SECONDS);
            }
            assertEquals(1, probes.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void onlyConfiguredNumberOfDatabaseFallbacksCanRunAndRejectedRequestReturnsQuickly() throws Exception {
        ProductCacheResilience resilience = resilience(new MutableClock(), 4, Duration.ofMillis(50));
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?>[] holders = new Future<?>[4];
            for (int index = 0; index < holders.length; index++) {
                holders[index] = executor.submit(() -> resilience.executeDatabaseFallback(() -> {
                    entered.countDown();
                    await(release);
                    return "ok";
                }));
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(4, resilience.activeDatabaseFallbacks());

            long started = System.nanoTime();
            Future<?> rejected = executor.submit(() -> assertThrows(
                    ProductCacheFallbackRejectedException.class,
                    () -> resilience.executeDatabaseFallback(() -> "unexpected")
            ));
            rejected.get(1, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis >= 40 && elapsedMillis < 500, "elapsed=" + elapsedMillis);

            release.countDown();
            for (Future<?> holder : holders) {
                holder.get(1, TimeUnit.SECONDS);
            }
            assertEquals(0, resilience.activeDatabaseFallbacks());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void databaseFallbackPermitIsReleasedWhenDatabaseWorkFails() {
        ProductCacheResilience resilience = resilience(new MutableClock(), 1, Duration.ofMillis(10));

        assertThrows(IllegalStateException.class, () -> resilience.executeDatabaseFallback(() -> {
            throw new IllegalStateException("simulated database failure");
        }));

        assertEquals("recovered", resilience.executeDatabaseFallback(() -> "recovered"));
        assertEquals(0, resilience.activeDatabaseFallbacks());
    }

    @Test
    void resilienceMetricsDescribeFailureBypassFallbackRejectionAndRecovery() throws Exception {
        MutableClock clock = new MutableClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheResilience resilience = new ProductCacheResilience(
                new ProductCacheResilienceProperties(Duration.ofSeconds(5), 1, Duration.ofMillis(10), 100),
                registry,
                clock
        );
        resilience.recordRedisInfrastructureFailure();
        resilience.beforeRedisAccess();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> active = executor.submit(() -> resilience.executeDatabaseFallback(() -> {
                await(release);
                return "ok";
            }));
            while (resilience.activeDatabaseFallbacks() == 0) {
                Thread.yield();
            }
            assertThrows(ProductCacheFallbackRejectedException.class,
                    () -> resilience.executeDatabaseFallback(() -> "rejected"));
            release.countDown();
            active.get(1, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        clock.advance(Duration.ofSeconds(5));
        assertEquals(ProductCacheResilience.RedisAccess.RECOVERY_PROBE, resilience.beforeRedisAccess());
        resilience.recordRecoverySuccess();

        assertEquals(1.0, registry.counter("tomatomall.cache.product.redis.failures").count());
        assertEquals(1.0, registry.counter("tomatomall.cache.product.redis.bypassed").count());
        assertEquals(1.0, registry.counter("tomatomall.cache.product.database.fallback.success").count());
        assertEquals(1.0, registry.counter("tomatomall.cache.product.database.fallback.rejected").count());
        assertEquals(1.0, registry.counter("tomatomall.cache.product.redis.recovery.attempts").count());
        assertEquals(1.0, registry.counter("tomatomall.cache.product.redis.recovery.success").count());
    }

    private ProductCacheResilience resilience(Clock clock, int maxFallbacks, Duration wait) {
        return new ProductCacheResilience(
                new ProductCacheResilienceProperties(Duration.ofSeconds(5), maxFallbacks, wait, 100),
                new SimpleMeterRegistry(),
                clock
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-13T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
