package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCacheSingleFlightTest {

    @Test
    void sameProductHasOneLeaderAndReleasesEveryFollower() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheSingleFlight singleFlight = singleFlight(Duration.ofMillis(500), registry);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger databaseLoads = new AtomicInteger();
        long deadline = singleFlight.newDeadlineNanos();

        try {
            List<Future<ProductCacheSingleFlight.Outcome<String>>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> singleFlight.execute(41, deadline, () -> {
                    databaseLoads.incrementAndGet();
                    leaderEntered.countDown();
                    await(releaseLeader);
                    return "loaded";
                })));
            }

            assertTrue(leaderEntered.await(1, TimeUnit.SECONDS));
            awaitCondition(() -> singleFlight.activeWaiters() == 7, Duration.ofSeconds(1));
            releaseLeader.countDown();

            int leaders = 0;
            int followers = 0;
            for (Future<ProductCacheSingleFlight.Outcome<String>> result : results) {
                ProductCacheSingleFlight.Outcome<String> outcome = result.get(1, TimeUnit.SECONDS);
                if (outcome.isLeader()) {
                    leaders++;
                    assertEquals("loaded", outcome.getLeaderResult());
                } else {
                    followers++;
                }
            }

            assertEquals(1, leaders);
            assertEquals(7, followers);
            assertEquals(1, databaseLoads.get());
            assertEquals(0, singleFlight.activeFlights());
            assertEquals(0, singleFlight.activeWaiters());
            assertEquals(1.0, registry.counter("tomatomall.cache.product.singleflight.leader").count());
            assertEquals(7.0, registry.counter("tomatomall.cache.product.singleflight.follower").count());
            assertEquals(7.0, registry.counter("tomatomall.cache.product.singleflight.wait.success").count());
            assertEquals(7, registry.timer("tomatomall.cache.product.singleflight.wait.duration").count());
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentProductsCanLoadAtTheSameTime() throws Exception {
        ProductCacheSingleFlight singleFlight = singleFlight(Duration.ofMillis(500), new SimpleMeterRegistry());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<ProductCacheSingleFlight.Outcome<Integer>> first = executor.submit(
                    () -> singleFlight.execute(51, singleFlight.newDeadlineNanos(), () -> {
                        bothEntered.countDown();
                        await(release);
                        return 51;
                    })
            );
            Future<ProductCacheSingleFlight.Outcome<Integer>> second = executor.submit(
                    () -> singleFlight.execute(52, singleFlight.newDeadlineNanos(), () -> {
                        bothEntered.countDown();
                        await(release);
                        return 52;
                    })
            );

            assertTrue(bothEntered.await(1, TimeUnit.SECONDS), "不同商品不应共用同一把协调锁");
            assertEquals(2, singleFlight.activeFlights());
            release.countDown();
            assertTrue(first.get(1, TimeUnit.SECONDS).isLeader());
            assertTrue(second.get(1, TimeUnit.SECONDS).isLeader());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void leaderFailureReachesCurrentFollowersAndNextRequestCanRetry() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheSingleFlight singleFlight = singleFlight(Duration.ofMillis(500), registry);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        IllegalStateException databaseFailure = new IllegalStateException("controlled database failure");
        long deadline = singleFlight.newDeadlineNanos();

        try {
            Future<?> leader = executor.submit(() -> singleFlight.execute(61, deadline, () -> {
                leaderEntered.countDown();
                await(releaseLeader);
                throw databaseFailure;
            }));
            assertTrue(leaderEntered.await(1, TimeUnit.SECONDS));
            Future<?> follower = executor.submit(() -> singleFlight.execute(61, deadline, () -> "must-not-run"));
            awaitCondition(() -> singleFlight.activeWaiters() == 1, Duration.ofSeconds(1));
            releaseLeader.countDown();

            assertSame(databaseFailure, executionCause(leader));
            assertSame(databaseFailure, executionCause(follower));
            assertEquals(0, singleFlight.activeFlights());
            assertEquals(0, singleFlight.activeWaiters());
            assertEquals(1.0, registry.counter("tomatomall.cache.product.singleflight.leader.failures").count());

            ProductCacheSingleFlight.Outcome<String> retry = singleFlight.execute(
                    61,
                    singleFlight.newDeadlineNanos(),
                    () -> "retry-success"
            );
            assertTrue(retry.isLeader());
            assertEquals("retry-success", retry.getLeaderResult());
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void followerTimeoutDoesNotCancelOrRemoveTheRunningLeader() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheSingleFlight singleFlight = singleFlight(Duration.ofMillis(80), registry);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);

        try {
            Future<ProductCacheSingleFlight.Outcome<String>> leader = executor.submit(
                    () -> singleFlight.execute(71, singleFlight.newDeadlineNanos(), () -> {
                        leaderEntered.countDown();
                        await(releaseLeader);
                        return "leader-result";
                    })
            );
            assertTrue(leaderEntered.await(1, TimeUnit.SECONDS));
            long followerDeadline = singleFlight.newDeadlineNanos();

            ProductCacheSingleFlightTimeoutException timeout = assertThrows(
                    ProductCacheSingleFlightTimeoutException.class,
                    () -> singleFlight.execute(71, followerDeadline, () -> "must-not-run")
            );

            assertEquals("商品服务暂时繁忙，请稍后重试", timeout.getMessage());
            assertEquals(1, singleFlight.activeFlights(), "等待者超时不能删除负责人仍持有的记录");
            assertFalse(leader.isDone(), "等待者超时不能取消负责人");
            assertEquals(1.0, registry.counter("tomatomall.cache.product.singleflight.wait.timeout").count());

            releaseLeader.countDown();
            assertEquals("leader-result", leader.get(1, TimeUnit.SECONDS).getLeaderResult());
            assertEquals(0, singleFlight.activeFlights());
            assertEquals(0, singleFlight.activeWaiters());
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void nonPositiveWaitTimeoutIsRejected() {
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setWaitTimeout(Duration.ZERO);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProductCacheSingleFlight(properties, new SimpleMeterRegistry())
        );
    }

    @Test
    void deadlineKeepsConfiguredWaitWhenMonotonicClockValueIsNegative() {
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setEnabled(true);
        properties.setWaitTimeout(Duration.ofMillis(500));
        LongSupplier negativeClock = () -> -1_000_000_000L;
        ProductCacheSingleFlight singleFlight = new ProductCacheSingleFlight(
                properties,
                new SimpleMeterRegistry(),
                true,
                negativeClock
        );

        assertEquals(-500_000_000L, singleFlight.newDeadlineNanos());
    }

    @Test
    void requestExpiringBetweenInitialCheckAndFlightRegistrationCannotBecomeLeader() {
        AtomicInteger clockReads = new AtomicInteger();
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setEnabled(true);
        properties.setWaitTimeout(Duration.ofMillis(500));
        ProductCacheSingleFlight singleFlight = new ProductCacheSingleFlight(
                properties,
                new SimpleMeterRegistry(),
                true,
                () -> clockReads.getAndIncrement() == 0 ? 1_000_000_000L : 1_500_000_000L
        );
        AtomicInteger leaderExecutions = new AtomicInteger();

        assertThrows(
                ProductCacheSingleFlightTimeoutException.class,
                () -> singleFlight.execute(82, 1_500_000_000L, () -> {
                    leaderExecutions.incrementAndGet();
                    return "must-not-run";
                })
        );
        assertEquals(0, leaderExecutions.get());
        assertEquals(0, singleFlight.activeFlights());
    }

    @Test
    void deadlineComparisonWorksAcrossNanoTimeWraparound() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 10L);
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setEnabled(true);
        properties.setWaitTimeout(Duration.ofNanos(20));
        ProductCacheSingleFlight singleFlight = new ProductCacheSingleFlight(
                properties,
                new SimpleMeterRegistry(),
                true,
                clock::get
        );
        long wrappedDeadline = singleFlight.newDeadlineNanos();
        assertEquals(Long.MIN_VALUE + 9L, wrappedDeadline);

        clock.set(Long.MIN_VALUE + 8L);
        assertEquals("loaded", singleFlight.execute(83, wrappedDeadline, () -> "loaded").getLeaderResult());
        clock.set(Long.MIN_VALUE + 9L);
        assertThrows(
                ProductCacheSingleFlightTimeoutException.class,
                () -> singleFlight.execute(84, wrappedDeadline, () -> "must-not-run")
        );
    }

    @Test
    void disabledSingleFlightRunsEveryCallerWithoutCreatingCoordinationState() throws Exception {
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setEnabled(false);
        properties.setWaitTimeout(Duration.ofMillis(500));
        ProductCacheSingleFlight singleFlight = new ProductCacheSingleFlight(
                properties,
                new SimpleMeterRegistry()
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        try {
            Future<?> first = executor.submit(() -> singleFlight.execute(81, Long.MAX_VALUE, () -> {
                executions.incrementAndGet();
                bothEntered.countDown();
                await(release);
                return "first";
            }));
            Future<?> second = executor.submit(() -> singleFlight.execute(81, Long.MAX_VALUE, () -> {
                executions.incrementAndGet();
                bothEntered.countDown();
                await(release);
                return "second";
            }));

            assertTrue(bothEntered.await(1, TimeUnit.SECONDS));
            assertEquals(2, executions.get());
            assertEquals(0, singleFlight.activeFlights());
            release.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedFollowerRestoresInterruptStatusAndDoesNotRemoveLeader() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheSingleFlight singleFlight = singleFlight(Duration.ofSeconds(1), registry);
        ExecutorService leaderExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger followerInterrupted = new AtomicInteger();

        try {
            Future<?> leader = leaderExecutor.submit(() -> singleFlight.execute(
                    91,
                    singleFlight.newDeadlineNanos(),
                    () -> {
                        leaderEntered.countDown();
                        await(releaseLeader);
                        return "leader";
                    }
            ));
            assertTrue(leaderEntered.await(1, TimeUnit.SECONDS));

            Thread follower = new Thread(() -> {
                assertThrows(
                        ProductCacheSingleFlightInterruptedException.class,
                        () -> singleFlight.execute(91, singleFlight.newDeadlineNanos(), () -> "must-not-run")
                );
                if (Thread.currentThread().isInterrupted()) {
                    followerInterrupted.incrementAndGet();
                }
            });
            follower.start();
            awaitCondition(() -> singleFlight.activeWaiters() == 1, Duration.ofSeconds(1));
            follower.interrupt();
            follower.join(1000);

            assertFalse(follower.isAlive());
            assertEquals(1, followerInterrupted.get());
            assertEquals(1, singleFlight.activeFlights());
            assertEquals(0, singleFlight.activeWaiters());
            assertEquals(1.0, registry.counter(
                    "tomatomall.cache.product.singleflight.wait.interrupted"
            ).count());

            releaseLeader.countDown();
            leader.get(1, TimeUnit.SECONDS);
            assertEquals(0, singleFlight.activeFlights());
        } finally {
            releaseLeader.countDown();
            leaderExecutor.shutdownNow();
        }
    }

    private ProductCacheSingleFlight singleFlight(Duration timeout, SimpleMeterRegistry registry) {
        ProductCacheSingleFlightProperties properties = new ProductCacheSingleFlightProperties();
        properties.setEnabled(true);
        properties.setWaitTimeout(timeout);
        return new ProductCacheSingleFlight(properties, registry);
    }

    private Throwable executionCause(Future<?> future) throws Exception {
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS));
        return exception.getCause();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test thread interrupted", exception);
        }
    }

    private static void awaitCondition(Check check, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!check.satisfied() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(check.satisfied(), "condition was not satisfied before timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean satisfied();
    }
}
