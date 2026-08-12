package com.example.tomatomall.service.cache;

import com.example.tomatomall.dto.ProductDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDetailCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void redisReadFailureBecomesCacheMissInsteadOfBreakingProductRead() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(42)))
                .thenThrow(new RedisConnectionFailureException("simulated unavailable Redis"));
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        ProductDetailCache.LookupResult result = cache.lookup(42);

        assertTrue(result.isMiss());
    }

    @Test
    void invalidCachedTypeIsRemovedAndTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(43))).thenReturn("wrong-type");
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        ProductDetailCache.LookupResult result = cache.lookup(43);

        assertTrue(result.isMiss());
        verify(redisTemplate).delete(ProductDetailCache.key(43));
    }

    @Test
    void unreadableCachedValueIsRemovedAndTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductDetailCache.key(49)))
                .thenThrow(new SerializationException("simulated corrupted cached value"));
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        ProductDetailCache.LookupResult result = cache.lookup(49);

        assertTrue(result.isMiss());
        verify(redisTemplate).delete(ProductDetailCache.key(49));
    }

    @Test
    void redisWriteFailureDoesNotFailDatabaseBackedRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("simulated unavailable Redis"))
                .when(valueOperations)
                .set(eq(ProductDetailCache.key(44)), any(ProductDTO.class), anyLong(), eq(TimeUnit.SECONDS));
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        assertDoesNotThrow(() -> cache.putProduct(44, new ProductDTO()));
    }

    @Test
    void productTtlStaysInsideRandomizedRange() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        cache.putProduct(45, new ProductDTO());

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(
                eq(ProductDetailCache.key(45)),
                any(ProductDTO.class),
                ttl.capture(),
                eq(TimeUnit.SECONDS)
        );
        assertTrue(ttl.getValue() >= 1800);
        assertTrue(ttl.getValue() <= 3599);
    }

    @Test
    void missingProductTtlStaysInsideShortProtectionRange() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);

        cache.putMissing(47);

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(
                eq(ProductDetailCache.key(47)),
                any(),
                ttl.capture(),
                eq(TimeUnit.SECONDS)
        );
        assertTrue(ttl.getValue() >= 60);
        assertTrue(ttl.getValue() <= 119);
    }

    @Test
    void evictionWaitsUntilDatabaseTransactionHasCommitted() {
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        cache.evictAfterCommit(46);

        verify(redisTemplate, never()).delete(ProductDetailCache.key(46));
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(redisTemplate).delete(ProductDetailCache.key(46));
    }

    @Test
    void rolledBackDatabaseTransactionDoesNotEvictCache() {
        ProductDetailCache cache = new ProductDetailCache(redisTemplate);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        cache.evictAfterCommit(48);

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(redisTemplate, never()).delete(ProductDetailCache.key(48));
    }

    @Test
    void disabledCacheNeverReadsWritesDeletesOrSchedulesCacheWork() {
        ProductDetailCache cache = new ProductDetailCache(redisTemplate, false);
        Runnable afterCommitAction = org.mockito.Mockito.mock(Runnable.class);

        ProductDetailCache.LookupResult result = cache.lookup(50);
        cache.putProduct(50, new ProductDTO());
        cache.putMissing(50);
        cache.evict(50);
        cache.evictAfterCommit(50);
        cache.runAfterCommit(afterCommitAction);

        assertTrue(result.isMiss());
        verifyNoInteractions(redisTemplate, afterCommitAction);
    }
}
