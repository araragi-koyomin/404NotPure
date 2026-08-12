package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ProductDetailCacheResilienceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private Cursor<String> cursor;

    @Test
    void connectionFailureMarksLookupUnavailableAndNextLookupBypassesRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(61)))
                .thenThrow(new RedisConnectionFailureException("simulated unavailable Redis"));
        ProductDetailCache cache = cache();

        ProductDetailCache.LookupResult first = cache.lookup(61);
        ProductDetailCache.LookupResult second = cache.lookup(61);

        assertTrue(first.requiresDatabaseFallback());
        assertTrue(second.requiresDatabaseFallback());
        verify(valueOperations, times(1)).get(ProductDetailCache.key(61));
    }

    @Test
    void closedLettuceConnectionMarksLookupUnavailableAndNextLookupBypassesRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(68)))
                .thenThrow(new RedisSystemException("Connection is closed", null));
        ProductDetailCache cache = cache();

        ProductDetailCache.LookupResult first = cache.lookup(68);
        ProductDetailCache.LookupResult second = cache.lookup(68);

        assertTrue(first.requiresDatabaseFallback());
        assertTrue(second.requiresDatabaseFallback());
        verify(valueOperations, times(1)).get(ProductDetailCache.key(68));
    }

    @Test
    void corruptedSingleValueDoesNotBypassOtherRedisReads() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(62)))
                .thenThrow(new SerializationException("simulated corrupted value"));
        when(valueOperations.get(ProductDetailCache.key(63))).thenReturn(null);
        ProductDetailCache cache = cache();

        ProductDetailCache.LookupResult corrupted = cache.lookup(62);
        ProductDetailCache.LookupResult nextProduct = cache.lookup(63);

        assertTrue(corrupted.isMiss());
        assertFalse(corrupted.requiresDatabaseFallback());
        assertTrue(nextProduct.isMiss());
        assertFalse(nextProduct.requiresDatabaseFallback());
        verify(valueOperations).get(ProductDetailCache.key(63));
    }

    @Test
    void writesAndDeletesAreSkippedWhileRedisIsBypassed() {
        ProductCacheResilience resilience = resilience();
        resilience.recordRedisInfrastructureFailure();
        ProductDetailCache cache = new ProductDetailCache(redisTemplate, true, resilience, 100);

        cache.putProduct(64, new com.example.tomatomall.dto.ProductDTO());
        cache.putMissing(64);
        cache.evict(64);

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(any(String.class));
        verify(redisTemplate, never()).scan(any());
    }

    @Test
    void advertisementWarmupIsSkippedBeforeItReadsDatabaseWhileRedisIsBypassed() {
        ProductCacheResilience resilience = resilience();
        resilience.recordRedisInfrastructureFailure();
        ProductDetailCache cache = new ProductDetailCache(redisTemplate, true, resilience, 100);
        Runnable warmup = mock(Runnable.class);

        cache.runAfterCommit(warmup);

        verify(warmup, never()).run();
        verify(redisTemplate, never()).scan(any());
    }

    @Test
    void successfulRecoveryClearsOnlyScannedProductKeysBeforeReadingRedisAgain() {
        MutableClock clock = new MutableClock();
        ProductCacheResilience resilience = resilience(clock);
        resilience.recordRedisInfrastructureFailure();
        clock.advance(Duration.ofSeconds(5));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(65))).thenReturn(null);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(ProductDetailCache.key(65), ProductDetailCache.key(66));
        when(redisTemplate.scan(any())).thenReturn(cursor);
        Runnable connectionReset = mock(Runnable.class);
        ProductDetailCache cache = new ProductDetailCache(redisTemplate, true, resilience, 100, connectionReset);

        ProductDetailCache.LookupResult result = cache.lookup(65);

        assertTrue(result.isMiss());
        assertFalse(result.requiresDatabaseFallback());
        verify(redisTemplate).delete(List.of(ProductDetailCache.key(65), ProductDetailCache.key(66)));
        verify(valueOperations).get(ProductDetailCache.key(65));
        verify(connectionReset).run();
    }

    @Test
    void failedRecoveryCleanupKeepsRedisBypassed() {
        MutableClock clock = new MutableClock();
        ProductCacheResilience resilience = resilience(clock);
        resilience.recordRedisInfrastructureFailure();
        clock.advance(Duration.ofSeconds(5));
        doThrow(new RedisConnectionFailureException("still unavailable"))
                .when(redisTemplate).scan(any());
        ProductDetailCache cache = new ProductDetailCache(redisTemplate, true, resilience, 100);

        ProductDetailCache.LookupResult recovery = cache.lookup(67);
        ProductDetailCache.LookupResult next = cache.lookup(67);

        assertTrue(recovery.requiresDatabaseFallback());
        assertTrue(next.requiresDatabaseFallback());
        verify(redisTemplate, times(1)).scan(any());
        verify(redisTemplate, never()).opsForValue();
    }

    private ProductDetailCache cache() {
        return new ProductDetailCache(redisTemplate, true, resilience(), 100);
    }

    private ProductCacheResilience resilience() {
        return resilience(Clock.systemUTC());
    }

    private ProductCacheResilience resilience(Clock clock) {
        return new ProductCacheResilience(
                new ProductCacheResilienceProperties(Duration.ofSeconds(5), 4, Duration.ofMillis(50), 100),
                new SimpleMeterRegistry(),
                clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-13T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
