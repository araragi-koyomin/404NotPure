package com.example.tomatomall.service.order;

import com.example.tomatomall.configure.OrderExpirationProperties;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.OrderLifecycleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderExpirationBatchProcessorTest {
    @Test
    void oneOrderFailureDoesNotStopRemainingOrdersAndOutcomesAreCounted() {
        OrdersRepository ordersRepository = mock(OrdersRepository.class);
        OrderLifecycleService lifecycleService = mock(OrderLifecycleService.class);
        OrderExpirationPolicy policy = mock(OrderExpirationPolicy.class);
        OrderExpirationProperties properties = new OrderExpirationProperties();
        properties.setBatchSize(3);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Instant cutoff = Instant.parse("2026-08-16T00:00:00Z");
        when(policy.expirationCutoff()).thenReturn(cutoff);
        when(ordersRepository.findExpiredPendingOrderIds(
                eq("PENDING"), eq(Timestamp.from(cutoff)), any(Pageable.class)))
                .thenReturn(Arrays.asList(11, 12, 13));
        when(lifecycleService.closeExpiredOrder(11)).thenReturn(true);
        when(lifecycleService.closeExpiredOrder(12)).thenThrow(new RuntimeException("expected test failure"));
        when(lifecycleService.closeExpiredOrder(13)).thenReturn(false);

        OrderExpirationBatchProcessor processor = new OrderExpirationBatchProcessor(
                ordersRepository, lifecycleService, policy, properties, meterRegistry);

        processor.runOnce();

        verify(lifecycleService).closeExpiredOrder(13);
        assertEquals(3.0, meterRegistry.counter("tomatomall.order.expiration.scanned").count());
        assertEquals(1.0, meterRegistry.counter("tomatomall.order.expiration.closed").count());
        assertEquals(1.0, meterRegistry.counter("tomatomall.order.expiration.skipped").count());
        assertEquals(1.0, meterRegistry.counter("tomatomall.order.expiration.failed").count());
    }
}
