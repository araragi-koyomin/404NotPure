package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.InvalidCheckoutRequestException;
import com.example.tomatomall.exception.OrderCheckoutConflictException;
import com.example.tomatomall.exception.OrderCheckoutUnavailableException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.order.NormalizedCheckoutRequest;
import com.example.tomatomall.service.order.OrderCheckoutRequestNormalizer;
import com.example.tomatomall.service.order.OrderCheckoutTransactionService;
import com.example.tomatomall.service.order.PaymentMethodResolver;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.CannotAcquireLockException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCheckoutMetricsTest {

    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";
    private static final String METRIC = "tomatomall.order.checkout.requests";

    private OrdersRepository ordersRepository;
    private OrderCheckoutTransactionService transactionService;
    private OrderCheckoutRequestNormalizer normalizer;
    private SimpleMeterRegistry meterRegistry;
    private OrderServiceImpl orderService;
    private CreateOrderDTO request;
    private NormalizedCheckoutRequest normalized;

    @BeforeEach
    void setUp() {
        ordersRepository = mock(OrdersRepository.class);
        transactionService = mock(OrderCheckoutTransactionService.class);
        normalizer = new OrderCheckoutRequestNormalizer(new PaymentMethodResolver());
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderServiceImpl(
                ordersRepository, normalizer, transactionService, meterRegistry);
        request = request(1);
        normalized = normalizer.normalize(request);
    }

    @Test
    void recordsCreatedAndReplayWithOnlyTheFixedOutcomeTag() {
        when(ordersRepository.findByAccountIdAndIdempotencyKey(1, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(order(normalized.getFingerprint())));
        when(transactionService.create(eq(1), eq(KEY), any()))
                .thenReturn(order(normalized.getFingerprint()));

        orderService.addOrder(1, KEY, request);
        orderService.addOrder(1, KEY, request);

        assertCounter("created", 1);
        assertCounter("replayed", 1);
        for (Meter meter : meterRegistry.getMeters()) {
            assertEquals(1, meter.getId().getTags().size());
            assertEquals("outcome", meter.getId().getTags().get(0).getKey());
        }
    }

    @Test
    void recordsConflictTimeoutAndValidationFailureExactlyOnce() {
        when(ordersRepository.findByAccountIdAndIdempotencyKey(1, KEY))
                .thenReturn(Optional.of(order("different-fingerprint")));
        assertThrows(OrderCheckoutConflictException.class,
                () -> orderService.addOrder(1, KEY, request));

        when(ordersRepository.findByAccountIdAndIdempotencyKey(1, KEY))
                .thenReturn(Optional.empty());
        when(transactionService.create(eq(1), eq(KEY), any()))
                .thenThrow(new CannotAcquireLockException("expected lock timeout"));
        assertThrows(OrderCheckoutUnavailableException.class,
                () -> orderService.addOrder(1, KEY, request));

        assertThrows(InvalidCheckoutRequestException.class,
                () -> orderService.addOrder(1, "invalid", request));

        assertCounter("conflict", 1);
        assertCounter("timeout", 1);
        assertCounter("failed", 1);
    }

    @ParameterizedTest
    @CsvSource({
            "1213, HY000",
            "0, 40001"
    })
    void deadlockClassificationRecognizesEitherMysqlErrorCodeOrSqlState(
            int errorCode,
            String sqlState
    ) {
        Orders winner = order(normalized.getFingerprint());
        when(ordersRepository.findByAccountIdAndIdempotencyKey(1, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        SQLException deadlock = new SQLException("expected deadlock", sqlState, errorCode);
        when(transactionService.create(eq(1), eq(KEY), any()))
                .thenThrow(new CannotAcquireLockException("expected deadlock", deadlock));

        assertEquals(true, orderService.addOrder(1, KEY, request).isReplayed());
        assertCounter("replayed", 1);
    }

    private void assertCounter(String outcome, double expected) {
        assertEquals(expected, meterRegistry.get(METRIC).tag("outcome", outcome).counter().count());
    }

    private Orders order(String fingerprint) {
        Account account = new Account();
        account.setId(1);
        Orders order = new Orders();
        order.setOrderId(10);
        order.setAccount(account);
        order.setTotalAmount(BigDecimal.ONE);
        order.setPaymentMethod("Alipay");
        order.setStatus("PENDING");
        order.setIdempotencyKey(KEY);
        order.setRequestFingerprint(fingerprint);
        return order;
    }

    private CreateOrderDTO request(int amount) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(1);
        item.setAmount(amount);
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod("Alipay");
        request.setItems(Collections.singletonList(item));
        return request;
    }
}
