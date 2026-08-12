package com.example.tomatomall.service.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "tomatomall.cache.product-detail.resilience")
public class ProductCacheResilienceProperties {

    private Duration bypassDuration = Duration.ofSeconds(5);
    private int maxConcurrentDatabaseFallbacks = 4;
    private Duration databaseFallbackWait = Duration.ofMillis(50);
    private int cleanupBatchSize = 100;

    public ProductCacheResilienceProperties() {
    }

    public ProductCacheResilienceProperties(
            Duration bypassDuration,
            int maxConcurrentDatabaseFallbacks,
            Duration databaseFallbackWait,
            int cleanupBatchSize
    ) {
        this.bypassDuration = bypassDuration;
        this.maxConcurrentDatabaseFallbacks = maxConcurrentDatabaseFallbacks;
        this.databaseFallbackWait = databaseFallbackWait;
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public Duration getBypassDuration() {
        return bypassDuration;
    }

    public void setBypassDuration(Duration bypassDuration) {
        this.bypassDuration = bypassDuration;
    }

    public int getMaxConcurrentDatabaseFallbacks() {
        return maxConcurrentDatabaseFallbacks;
    }

    public void setMaxConcurrentDatabaseFallbacks(int maxConcurrentDatabaseFallbacks) {
        this.maxConcurrentDatabaseFallbacks = maxConcurrentDatabaseFallbacks;
    }

    public Duration getDatabaseFallbackWait() {
        return databaseFallbackWait;
    }

    public void setDatabaseFallbackWait(Duration databaseFallbackWait) {
        this.databaseFallbackWait = databaseFallbackWait;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }
}
