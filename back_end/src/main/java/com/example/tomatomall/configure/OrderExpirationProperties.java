package com.example.tomatomall.configure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "tomatomall.order.expiration")
public class OrderExpirationProperties {
    private boolean enabled = true;
    private Duration pendingTimeout = Duration.ofMinutes(30);
    private Duration scanInterval = Duration.ofSeconds(60);
    private int batchSize = 100;

    @PostConstruct
    void validate() {
        if (pendingTimeout == null || pendingTimeout.isZero() || pendingTimeout.isNegative()) {
            throw new IllegalArgumentException("Order pending timeout must be positive");
        }
        if (scanInterval == null || scanInterval.isZero() || scanInterval.isNegative()) {
            throw new IllegalArgumentException("Order expiration scan interval must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Order expiration batch size must be positive");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPendingTimeout() { return pendingTimeout; }
    public void setPendingTimeout(Duration pendingTimeout) { this.pendingTimeout = pendingTimeout; }
    public Duration getScanInterval() { return scanInterval; }
    public void setScanInterval(Duration scanInterval) { this.scanInterval = scanInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
