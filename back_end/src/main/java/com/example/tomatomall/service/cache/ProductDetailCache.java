package com.example.tomatomall.service.cache;

import com.example.tomatomall.dto.MissingProductCacheEntry;
import com.example.tomatomall.dto.ProductDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ProductDetailCache {

    private static final String KEY_PREFIX = "product:detail:v1:";
    private static final long PRODUCT_TTL_MIN_SECONDS = 1800;
    private static final long PRODUCT_TTL_MAX_SECONDS = 3599;
    private static final long MISSING_TTL_MIN_SECONDS = 60;
    private static final long MISSING_TTL_MAX_SECONDS = 119;

    private final RedisTemplate<String, Object> redisTemplate;

    public ProductDetailCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public static String key(int productId) {
        return KEY_PREFIX + productId;
    }

    public LookupResult lookup(int productId) {
        String cacheKey = key(productId);
        try {
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue == null) {
                return LookupResult.miss();
            }
            if (cachedValue instanceof ProductDTO) {
                return LookupResult.product((ProductDTO) cachedValue);
            }
            if (cachedValue instanceof MissingProductCacheEntry) {
                return LookupResult.missing();
            }
            safeDelete(cacheKey);
            return LookupResult.miss();
        } catch (SerializationException exception) {
            safeDelete(cacheKey);
            logRedisFailure("deserialize", exception);
            return LookupResult.miss();
        } catch (RuntimeException exception) {
            logRedisFailure("read", exception);
            return LookupResult.miss();
        }
    }

    public void putProduct(int productId, ProductDTO product) {
        put(key(productId), product, randomTtl(PRODUCT_TTL_MIN_SECONDS, PRODUCT_TTL_MAX_SECONDS));
    }

    public void putMissing(int productId) {
        put(key(productId), new MissingProductCacheEntry(),
                randomTtl(MISSING_TTL_MIN_SECONDS, MISSING_TTL_MAX_SECONDS));
    }

    public void evict(int productId) {
        safeDelete(key(productId));
    }

    public void evictAfterCommit(int productId) {
        afterCommit(() -> evict(productId));
    }

    public void runAfterCommit(Runnable action) {
        afterCommit(action);
    }

    private void put(String cacheKey, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            logRedisFailure("write", exception);
        }
    }

    private void safeDelete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            logRedisFailure("delete", exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safelyRunAfterCommit(action);
                }
            });
            return;
        }
        safelyRunAfterCommit(action);
    }

    private void safelyRunAfterCommit(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            logRedisFailure("after-commit", exception);
        }
    }

    private long randomTtl(long minimum, long maximum) {
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
    }

    private void logRedisFailure(String operation, RuntimeException exception) {
        log.warn("Redis product cache {} failed; database result remains authoritative. errorType={}",
                operation, exception.getClass().getSimpleName());
    }

    public static final class LookupResult {
        private final ProductDTO product;
        private final boolean missing;

        private LookupResult(ProductDTO product, boolean missing) {
            this.product = product;
            this.missing = missing;
        }

        public static LookupResult miss() {
            return new LookupResult(null, false);
        }

        public static LookupResult missing() {
            return new LookupResult(null, true);
        }

        public static LookupResult product(ProductDTO product) {
            return new LookupResult(product, false);
        }

        public boolean isMiss() {
            return product == null && !missing;
        }

        public boolean isMissing() {
            return missing;
        }

        public ProductDTO getProduct() {
            return product;
        }
    }
}
