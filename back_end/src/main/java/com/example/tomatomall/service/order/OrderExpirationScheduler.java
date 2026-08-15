package com.example.tomatomall.service.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "tomatomall.order.expiration.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderExpirationScheduler {
    private final OrderExpirationBatchProcessor batchProcessor;

    public OrderExpirationScheduler(OrderExpirationBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    @Scheduled(fixedDelayString = "#{@orderExpirationProperties.scanInterval.toMillis()}")
    public void closeExpiredOrders() {
        batchProcessor.runOnce();
    }
}
