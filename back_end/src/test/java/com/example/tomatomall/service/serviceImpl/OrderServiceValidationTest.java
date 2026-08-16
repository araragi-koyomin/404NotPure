package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.InvalidCheckoutRequestException;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.order.OrderCheckoutRequestNormalizer;
import com.example.tomatomall.service.order.OrderCheckoutTransactionService;
import com.example.tomatomall.service.order.PaymentMethodResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceValidationTest {

    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    private OrdersRepository ordersRepository;
    private OrderCheckoutTransactionService transactionService;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        ordersRepository = mock(OrdersRepository.class);
        transactionService = mock(OrderCheckoutTransactionService.class);
        orderService = new OrderServiceImpl(
                ordersRepository,
                new OrderCheckoutRequestNormalizer(new PaymentMethodResolver()),
                transactionService,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void rejectsNullRequestBeforeReadingDatabase() {
        assertInvalidOrder(null);
    }

    @Test
    void rejectsBlankPaymentMethodBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(1);
        request.setPaymentMethod("   ");
        assertInvalidOrder(request);
    }

    @Test
    void rejectsEmptyItemsBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(1);
        request.setItems(Collections.emptyList());
        assertInvalidOrder(request);
    }

    @Test
    void rejectsNullItemBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(1);
        request.setItems(Collections.singletonList(null));
        assertInvalidOrder(request);
    }

    @Test
    void rejectsNullProductIdBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(1);
        request.getItems().get(0).setProductId(null);
        assertInvalidOrder(request);
    }

    @Test
    void rejectsNullQuantityBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(1);
        request.getItems().get(0).setAmount(null);
        assertInvalidOrder(request);
    }

    @Test
    void rejectsZeroAndNegativeQuantityBeforeReadingDatabase() {
        assertInvalidOrder(validRequest(0));
        assertInvalidOrder(validRequest(-1));
    }

    @Test
    void rejectsAggregatedQuantityOverflowBeforeReadingDatabase() {
        CreateOrderDTO request = validRequest(Integer.MAX_VALUE);
        CreateOrderDTO.OrderItemDTO additionalItem = new CreateOrderDTO.OrderItemDTO();
        additionalItem.setProductId(1);
        additionalItem.setAmount(1);
        request.setItems(Arrays.asList(request.getItems().get(0), additionalItem));
        assertInvalidOrder(request);
    }

    @Test
    void rejectsInvalidIdempotencyKeyBeforeReadingDatabase() {
        assertThrows(InvalidCheckoutRequestException.class,
                () -> orderService.addOrder(1, "NOT-A-UUID", validRequest(1)));
        verifyNoInteractions(ordersRepository, transactionService);
    }

    private void assertInvalidOrder(CreateOrderDTO request) {
        assertThrows(InvalidCheckoutRequestException.class,
                () -> orderService.addOrder(1, KEY, request));
        verifyNoInteractions(ordersRepository, transactionService);
    }

    private CreateOrderDTO validRequest(Integer quantity) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(1);
        item.setAmount(quantity);
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod("Alipay");
        request.setItems(Collections.singletonList(item));
        return request;
    }
}
