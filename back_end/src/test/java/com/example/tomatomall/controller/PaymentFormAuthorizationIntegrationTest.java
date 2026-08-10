package com.example.tomatomall.controller;

import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.OrderItem;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.PaymentFormGateway;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.Cookie;
import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class PaymentFormAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenUtil tokenUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private OrdersRepository ordersRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockPileRepository stockPileRepository;
    @Autowired private EntityManager entityManager;

    @MockBean private PaymentFormGateway paymentFormGateway;

    private Account owner;
    private Account otherUser;
    private Account administrator;
    private Orders pendingOrder;
    private Orders paidOrder;
    private Product pendingProduct;

    @BeforeEach
    void setUp() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        owner = createAccount("pay-owner-" + marker, "USER", "12");
        otherUser = createAccount("pay-other-" + marker, "USER", "13");
        administrator = createAccount("pay-admin-" + marker, "ADMIN", "14");
        pendingOrder = createOrder(owner, OrderStatus.PENDING.name());
        paidOrder = createOrder(owner, OrderStatus.PAID.name());
        pendingProduct = createPendingOrderItem(pendingOrder, marker);
        when(paymentFormGateway.createPaymentForm(any(Orders.class))).thenReturn("<form></form>");
    }

    @Test
    void anonymousRequestIsRejectedBeforePaymentGateway() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", pendingOrder.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));

        verifyNoInteractions(paymentFormGateway);
        assertPendingOrderUnchanged();
    }

    @Test
    void ownerCanCreatePaymentFormWithMatchingCookieAndHeader() throws Exception {
        String token = tokenUtil.generateToken(owner.getId());

        mockMvc.perform(post("/api/orders/{id}/pay", pendingOrder.getOrderId())
                        .cookie(new Cookie("token", token))
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.orderId").value(pendingOrder.getOrderId().toString()))
                .andExpect(jsonPath("$.data.totalAmount").value("39.98"))
                .andExpect(jsonPath("$.data.paymentMethod").value("Alipay"))
                .andExpect(jsonPath("$.data.paymentForm").value("<form></form>"));

        verify(paymentFormGateway).createPaymentForm(any(Orders.class));
        assertPendingOrderUnchanged();
    }

    @Test
    void otherUserCannotCreateOwnersPaymentForm() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", pendingOrder.getOrderId())
                        .header("token", tokenUtil.generateToken(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(paymentFormGateway);
        assertPendingOrderUnchanged();
    }

    @Test
    void administratorCannotCreateAnotherUsersPaymentForm() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", pendingOrder.getOrderId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(administrator.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(paymentFormGateway);
        assertPendingOrderUnchanged();
    }

    @Test
    void nonPendingOrderIsRejectedBeforePaymentGateway() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", paidOrder.getOrderId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("409"));

        verifyNoInteractions(paymentFormGateway);
    }

    @Test
    void unknownOrderIsRejectedBeforePaymentGateway() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", Integer.MAX_VALUE)
                        .header("token", tokenUtil.generateToken(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(paymentFormGateway);
    }

    @Test
    void conflictingAuthenticatedIdentitiesAreRejectedBeforePaymentGateway() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/pay", pendingOrder.getOrderId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId())))
                        .header("token", tokenUtil.generateToken(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));

        verifyNoInteractions(paymentFormGateway);
        assertPendingOrderUnchanged();
    }

    private Account createAccount(String username, String role, String phonePrefix) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("test-password");
        account.setName(username);
        account.setRole(role);
        account.setPoints(0);
        account.setTelephone(phonePrefix + String.format("%09d", Math.abs(username.hashCode()) % 1_000_000_000));
        return userRepository.saveAndFlush(account);
    }

    private Orders createOrder(Account account, String status) {
        Orders order = new Orders();
        order.setAccount(account);
        order.setTotalAmount(new BigDecimal("39.98"));
        order.setPaymentMethod("Alipay");
        order.setStatus(status);
        order.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return ordersRepository.saveAndFlush(order);
    }

    private Product createPendingOrderItem(Orders order, String marker) {
        Product product = Product.builder()
                .title("payment-form-" + marker)
                .price(new BigDecimal("19.99"))
                .rate(5.0)
                .description("test")
                .detail("test")
                .cover("test")
                .category("test")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
        product = productRepository.saveAndFlush(product);
        stockPileRepository.saveAndFlush(StockPile.builder()
                .productId(product.getId())
                .amount(6)
                .frozen(2)
                .build());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(2);
        entityManager.persist(orderItem);
        entityManager.flush();
        return product;
    }

    private void assertPendingOrderUnchanged() {
        Orders unchanged = ordersRepository.findById(pendingOrder.getOrderId()).orElseThrow(AssertionError::new);
        assertEquals(OrderStatus.PENDING.name(), unchanged.getStatus());
        assertNull(unchanged.getPaidTime());
        assertNull(unchanged.getAlipayTradeNo());
        StockPile stock = stockPileRepository.findByProductId(pendingProduct.getId())
                .orElseThrow(AssertionError::new);
        assertEquals(6, stock.getAmount());
        assertEquals(2, stock.getFrozen());
    }
}
