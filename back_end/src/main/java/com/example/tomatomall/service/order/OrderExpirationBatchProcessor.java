package com.example.tomatomall.service.order;

import com.example.tomatomall.configure.OrderExpirationProperties;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.OrderLifecycleService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

@Component
public class OrderExpirationBatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(OrderExpirationBatchProcessor.class);

    private final OrdersRepository ordersRepository;
    private final OrderLifecycleService lifecycleService;
    private final OrderExpirationPolicy expirationPolicy;
    private final OrderExpirationProperties properties;
    private final Counter scanned;
    private final Counter closed;
    private final Counter skipped;
    private final Counter failed;

    public OrderExpirationBatchProcessor(OrdersRepository ordersRepository,
                                         OrderLifecycleService lifecycleService,
                                         OrderExpirationPolicy expirationPolicy,
                                         OrderExpirationProperties properties,
                                         MeterRegistry meterRegistry) {
        this.ordersRepository = ordersRepository;
        this.lifecycleService = lifecycleService;
        this.expirationPolicy = expirationPolicy;
        this.properties = properties;
        this.scanned = meterRegistry.counter("tomatomall.order.expiration.scanned");
        this.closed = meterRegistry.counter("tomatomall.order.expiration.closed");
        this.skipped = meterRegistry.counter("tomatomall.order.expiration.skipped");
        this.failed = meterRegistry.counter("tomatomall.order.expiration.failed");
    }

    public void runOnce() {
        List<Integer> orderIds = ordersRepository.findExpiredPendingOrderIds(
                OrderStatus.PENDING.name(),
                Timestamp.from(expirationPolicy.expirationCutoff()),
                PageRequest.of(0, properties.getBatchSize()));
        scanned.increment(orderIds.size());
        for (Integer orderId : orderIds) {
            try {
                if (lifecycleService.closeExpiredOrder(orderId)) {
                    closed.increment();
                } else {
                    skipped.increment();
                }
            } catch (RuntimeException exception) {
                failed.increment();
                log.warn("Failed to close expired order id={}", orderId, exception);
            }
        }
    }
}
