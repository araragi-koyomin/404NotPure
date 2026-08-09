package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceValidationTest {

    private UserRepository userRepository;
    private OrdersRepository ordersRepository;
    private ProductRepository productRepository;
    private StockPileRepository stockPileRepository;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        ordersRepository = mock(OrdersRepository.class);
        productRepository = mock(ProductRepository.class);
        stockPileRepository = mock(StockPileRepository.class);

        orderService = new OrderServiceImpl();
        orderService.userRepository = userRepository;
        orderService.ordersRepository = ordersRepository;
        orderService.productRepository = productRepository;
        orderService.stockPileRepository = stockPileRepository;
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
    void rejectsZeroQuantityBeforeReadingDatabase() {
        assertInvalidOrder(validRequest(0));
    }

    @Test
    void rejectsNegativeQuantityBeforeReadingDatabase() {
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

    private void assertInvalidOrder(CreateOrderDTO request) {
        TomatoException exception = assertThrows(
            TomatoException.class,
            () -> orderService.addOrder(1, request)
        );

        assertEquals("400", exception.getCode());
        verifyNoInteractions(userRepository, ordersRepository, productRepository, stockPileRepository);
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
