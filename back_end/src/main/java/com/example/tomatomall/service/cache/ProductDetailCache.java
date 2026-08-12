package com.example.tomatomall.service.cache;

import com.example.tomatomall.dto.MissingProductCacheEntry;
import com.example.tomatomall.dto.ProductDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
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
    private final boolean enabled;
    private final ProductCacheResilience resilience;
    private final int cleanupBatchSize;
    private final Runnable resetRedisConnection;

    public ProductDetailCache(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, true, ProductCacheResilience.unmetered(), 100, () -> { });
    }

    public ProductDetailCache(RedisTemplate<String, Object> redisTemplate, boolean enabled) {
        this(redisTemplate, enabled, ProductCacheResilience.unmetered(), 100, () -> { });
    }

    @Autowired
    public ProductDetailCache(RedisTemplate<String, Object> redisTemplate,
                              @Value("${tomatomall.cache.product-detail.enabled:true}") boolean enabled,
                              ProductCacheResilience resilience,
                              ProductCacheResilienceProperties properties,
                              RedisConnectionFactory connectionFactory) {
        this(redisTemplate, enabled, resilience, properties.getCleanupBatchSize(),
                connectionReset(connectionFactory));
    }

    ProductDetailCache(RedisTemplate<String, Object> redisTemplate,
                       boolean enabled,
                       ProductCacheResilience resilience,
                       int cleanupBatchSize) {
        this(redisTemplate, enabled, resilience, cleanupBatchSize, () -> { });
    }

    ProductDetailCache(RedisTemplate<String, Object> redisTemplate,
                       boolean enabled,
                       ProductCacheResilience resilience,
                       int cleanupBatchSize,
                       Runnable resetRedisConnection) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.resilience = resilience;
        this.cleanupBatchSize = cleanupBatchSize;
        this.resetRedisConnection = resetRedisConnection;
    }

    public static String key(int productId) {
        return KEY_PREFIX + productId;
    }

    public LookupResult lookup(int productId) {
        if (!enabled) {
            return LookupResult.miss();
        }
        if (!prepareRedisAccess()) {
            return LookupResult.databaseFallback();
        }
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
            if (isRedisInfrastructureFailure(exception)) {
                resilience.recordRedisInfrastructureFailure();
                logRedisFailure("read", exception);
                return LookupResult.databaseFallback();
            }
            logRedisFailure("read", exception);
            return LookupResult.miss();
        }
    }

    public void putProduct(int productId, ProductDTO product) {
        if (!enabled || !prepareRedisAccess()) {
            return;
        }
        put(key(productId), product, randomTtl(PRODUCT_TTL_MIN_SECONDS, PRODUCT_TTL_MAX_SECONDS));
    }

    public void putMissing(int productId) {
        if (!enabled || !prepareRedisAccess()) {
            return;
        }
        put(key(productId), new MissingProductCacheEntry(),
                randomTtl(MISSING_TTL_MIN_SECONDS, MISSING_TTL_MAX_SECONDS));
    }

    public void evict(int productId) {
        if (!enabled || !prepareRedisAccess()) {
            return;
        }
        safeDelete(key(productId));
    }

    public void evictAfterCommit(int productId) {
        if (!enabled) {
            return;
        }
        afterCommit(() -> evict(productId));
    }

    public void runAfterCommit(Runnable action) {
        if (!enabled) {
            return;
        }
        afterCommit(() -> {
            if (prepareRedisAccess()) {
                action.run();
            }
        });
    }

    private void put(String cacheKey, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            recordInfrastructureFailureIfApplicable(exception);
            logRedisFailure("write", exception);
        }
    }

    private void safeDelete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            recordInfrastructureFailureIfApplicable(exception);
            logRedisFailure("delete", exception);
        }
    }

    private boolean prepareRedisAccess() {
        ProductCacheResilience.RedisAccess access = resilience.beforeRedisAccess();
        if (access == ProductCacheResilience.RedisAccess.NORMAL) {
            return true;
        }
        if (access == ProductCacheResilience.RedisAccess.BYPASS) {
            return false;
        }
        return recoverRedisAndClearProductCache();
    }

    private boolean recoverRedisAndClearProductCache() {
        try {
            resetRedisConnection.run();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(KEY_PREFIX + "*")
                    .count(cleanupBatchSize)
                    .build();
            List<String> batch = new ArrayList<>(cleanupBatchSize);
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() == cleanupBatchSize) {
                        deleteRecoveryBatch(batch);
                    }
                }
            }
            deleteRecoveryBatch(batch);
            resilience.recordRecoverySuccess();
            return true;
        } catch (RuntimeException exception) {
            if (isRedisInfrastructureFailure(exception)) {
                resilience.recordRecoveryFailure();
            } else {
                resilience.recordRecoveryCleanupFailure();
            }
            logRedisFailure("recovery-cleanup", exception);
            return false;
        }
    }

    private static Runnable connectionReset(RedisConnectionFactory connectionFactory) {
        if (connectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) connectionFactory;
            return lettuceConnectionFactory::resetConnection;
        }
        return () -> { };
    }

    private void deleteRecoveryBatch(List<String> batch) {
        if (batch.isEmpty()) {
            return;
        }
        redisTemplate.delete(new ArrayList<>(batch));
        batch.clear();
    }

    private void recordInfrastructureFailureIfApplicable(RuntimeException exception) {
        if (isRedisInfrastructureFailure(exception)) {
            resilience.recordRedisInfrastructureFailure();
        }
    }

    static boolean isRedisInfrastructureFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.equals("org.springframework.data.redis.RedisConnectionFailureException")
                    || className.equals("org.springframework.dao.QueryTimeoutException")
                    || className.equals("io.lettuce.core.RedisConnectionException")
                    || className.equals("io.lettuce.core.RedisCommandTimeoutException")
                    || isClosedRedisConnection(className, current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isClosedRedisConnection(String className, String message) {
        return className.equals("org.springframework.data.redis.RedisSystemException")
                && "Connection is closed".equals(message);
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
        private final boolean databaseFallback;

        private LookupResult(ProductDTO product, boolean missing, boolean databaseFallback) {
            this.product = product;
            this.missing = missing;
            this.databaseFallback = databaseFallback;
        }

        public static LookupResult miss() {
            return new LookupResult(null, false, false);
        }

        public static LookupResult missing() {
            return new LookupResult(null, true, false);
        }

        public static LookupResult product(ProductDTO product) {
            return new LookupResult(product, false, false);
        }

        public static LookupResult databaseFallback() {
            return new LookupResult(null, false, true);
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

        public boolean requiresDatabaseFallback() {
            return databaseFallback;
        }
    }
}
