package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.configure.OrderExpirationProperties;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.OrderItem;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.OrderLifecycleService;
import com.example.tomatomall.service.order.OrderExpirationBatchProcessor;
import com.example.tomatomall.service.order.OrderExpirationPolicy;
import com.example.tomatomall.vo.OrdersVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.example.tomatomall.util.TokenUtil;

import javax.servlet.http.Cookie;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "tomatomall.order.expiration.pending-timeout=30m",
        "tomatomall.order.expiration.enabled=false"
})
@AutoConfigureMockMvc
class OrderLifecycleServiceIntegrationTest {

    @Autowired private OrderLifecycleService orderLifecycleService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrdersRepository ordersRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockPileRepository stockPileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private TokenUtil tokenUtil;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderExpirationPolicy expirationPolicy;

    private final List<Integer> productIds = new ArrayList<>();
    private Account owner;
    private Account otherUser;
    private Account administrator;
    private String marker;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        owner = createAccount("lifecycle-owner-" + marker);
        otherUser = createAccount("lifecycle-other-" + marker);
        administrator = createAccount("lifecycle-admin-" + marker, "ADMIN");
    }

    @AfterEach
    void cleanUp() {
        for (Account account : new Account[]{owner, otherUser, administrator}) {
            if (account != null && account.getId() != null) {
                List<Integer> orderIds = jdbcTemplate.queryForList(
                        "select order_id from orders where user_id=?", Integer.class, account.getId());
                for (Integer orderId : orderIds) {
                    jdbcTemplate.update("delete from order_item where order_id=?", orderId);
                    jdbcTemplate.update("delete from orders where order_id=?", orderId);
                }
            }
        }
        for (Integer productId : productIds) {
            jdbcTemplate.update("delete from stockpile where product_id=?", productId);
            jdbcTemplate.update("delete from products where product_id=?", productId);
        }
        for (Account account : new Account[]{owner, otherUser, administrator}) {
            if (account != null && account.getId() != null) {
                jdbcTemplate.update("delete from account where id=?", account.getId());
            }
        }
    }

    @Test
    void ownerCancelsPendingOrderAndRestoresFrozenStock() {
        Product product = createProduct("cancel", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING, Instant.now().minusSeconds(60), product, 2);

        OrdersVO result = orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId());

        assertEquals(OrderStatus.CANCELLED.name(), result.getStatus());
        assertNotNull(result.getCancelledTime());
        assertNull(result.getClosedTime());
        assertOrderState(order.getOrderId(), OrderStatus.CANCELLED, true, false);
        assertStock(product.getId(), 6, 0);
    }

    @Test
    void cancellationAtOrAfterDeadlineClosesOrderInstead() {
        Product product = createProduct("expired", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING,
                Instant.now().minus(Duration.ofMinutes(31)), product, 2);

        OrdersVO result = orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId());

        assertEquals(OrderStatus.CLOSED.name(), result.getStatus());
        assertNull(result.getCancelledTime());
        assertNotNull(result.getClosedTime());
        assertStock(product.getId(), 6, 0);
    }

    @Test
    void repeatedCancellationIsIdempotentAndDoesNotRestoreStockTwice() {
        Product product = createProduct("repeat", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING, Instant.now().minusSeconds(60), product, 2);

        OrdersVO first = orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId());
        Timestamp persistedCancellationTime = ordersRepository.findById(order.getOrderId())
                .orElseThrow(AssertionError::new).getCancelledTime();
        OrdersVO second = orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId());

        assertEquals(OrderStatus.CANCELLED.name(), first.getStatus());
        assertEquals(OrderStatus.CANCELLED.name(), second.getStatus());
        assertNotNull(first.getCancelledTime());
        assertEquals(persistedCancellationTime, second.getCancelledTime());
        assertStock(product.getId(), 6, 0);
    }

    @Test
    void paidOrderAndOtherUsersOrderCannotBeCancelled() {
        Product product = createProduct("reject", 4, 2);
        Orders paid = createOrder(owner, OrderStatus.PAID, Instant.now().minusSeconds(60), product, 2);

        TomatoException paidFailure = assertThrows(TomatoException.class,
                () -> orderLifecycleService.cancelOrder(owner.getId(), paid.getOrderId()));
        TomatoException ownerFailure = assertThrows(TomatoException.class,
                () -> orderLifecycleService.cancelOrder(otherUser.getId(), paid.getOrderId()));

        assertEquals("409", paidFailure.getCode());
        assertEquals("403", ownerFailure.getCode());
        assertStock(product.getId(), 4, 2);
    }

    @Test
    void restoreFailureRollsBackStatusAndEarlierStockUpdates() {
        Product first = createProduct("rollback-first", 4, 2);
        Product second = createProduct("rollback-second", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING, Instant.now().minusSeconds(60), first, 2);
        addOrderItem(order, second, 2);
        jdbcTemplate.update("delete from stockpile where product_id=?", second.getId());

        TomatoException failure = assertThrows(TomatoException.class,
                () -> orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId()));

        assertEquals("500", failure.getCode());
        assertOrderState(order.getOrderId(), OrderStatus.PENDING, false, false);
        assertStock(first.getId(), 4, 2);
    }

    @Test
    void cancellationEndpointRequiresIdentityAndUsesAuthenticatedOwner() throws Exception {
        Product product = createProduct("endpoint", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING, Instant.now().minusSeconds(60), product, 2);

        mockMvc.perform(post("/api/orders/{id}/cancel", order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getOrderId())
                        .header("token", tokenUtil.generateToken(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getOrderId())
                        .header("token", tokenUtil.generateToken(administrator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getOrderId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertStock(product.getId(), 6, 0);
    }

    @Test
    void concurrentCancellationAndPaymentHaveExactlyOneWinner() throws Exception {
        Product product = createProduct("cancel-pay-race", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING, Instant.now().minusSeconds(60), product, 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancellation = executor.submit(() -> {
                start.await();
                try {
                    orderLifecycleService.cancelOrder(owner.getId(), order.getOrderId());
                    successes.incrementAndGet();
                } catch (TomatoException exception) {
                    if ("409".equals(exception.getCode())) conflicts.incrementAndGet(); else throw exception;
                }
                return null;
            });
            Future<?> payment = executor.submit(() -> {
                start.await();
                try {
                    paymentService.updateOrderStatus(String.valueOf(order.getOrderId()),
                            "race-" + marker, "39.98");
                    successes.incrementAndGet();
                } catch (TomatoException exception) {
                    if ("409".equals(exception.getCode())) conflicts.incrementAndGet(); else throw exception;
                }
                return null;
            });
            start.countDown();
            cancellation.get(20, TimeUnit.SECONDS);
            payment.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
        Orders current = ordersRepository.findById(order.getOrderId()).orElseThrow(AssertionError::new);
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(0, stock.getFrozen());
        if (OrderStatus.CANCELLED.name().equals(current.getStatus())) {
            assertEquals(6, stock.getAmount());
        } else {
            assertEquals(OrderStatus.PAID.name(), current.getStatus());
            assertEquals(4, stock.getAmount());
        }
    }

    @Test
    void twoExpirationWorkersRestoreStockOnlyOnce() throws Exception {
        Product product = createProduct("two-expiration-workers", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING,
                Instant.now().minus(Duration.ofMinutes(31)), product, 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger changed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 2; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    if (orderLifecycleService.closeExpiredOrder(order.getOrderId())) {
                        changed.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, changed.get());
        assertEquals(1, skipped.get());
        assertOrderState(order.getOrderId(), OrderStatus.CLOSED, false, true);
        assertStock(product.getId(), 6, 0);
    }

    @Test
    void concurrentPaymentAndAutomaticCloseHaveExactlyOneWinner() throws Exception {
        Product product = createProduct("close-pay-race", 4, 2);
        Orders order = createOrder(owner, OrderStatus.PENDING,
                Instant.now().minus(Duration.ofMinutes(31)), product, 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger paymentWins = new AtomicInteger();
        AtomicInteger closeWins = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> close = executor.submit(() -> {
                start.await();
                if (orderLifecycleService.closeExpiredOrder(order.getOrderId())) {
                    closeWins.incrementAndGet();
                }
                return null;
            });
            Future<?> payment = executor.submit(() -> {
                start.await();
                try {
                    paymentService.updateOrderStatus(String.valueOf(order.getOrderId()),
                            "close-pay-" + marker, "39.98");
                    paymentWins.incrementAndGet();
                } catch (TomatoException exception) {
                    if (!"409".equals(exception.getCode())) throw exception;
                }
                return null;
            });
            start.countDown();
            close.get(20, TimeUnit.SECONDS);
            payment.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, paymentWins.get() + closeWins.get());
        Orders current = ordersRepository.findById(order.getOrderId()).orElseThrow(AssertionError::new);
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(0, stock.getFrozen());
        if (paymentWins.get() == 1) {
            assertEquals(OrderStatus.PAID.name(), current.getStatus());
            assertEquals(4, stock.getAmount());
        } else {
            assertEquals(OrderStatus.CLOSED.name(), current.getStatus());
            assertEquals(6, stock.getAmount());
        }
    }

    @Test
    void batchCommitsSuccessfulOrdersAndContinuesAfterOneOrderRollsBack() {
        Instant oldest = Instant.parse("2001-01-01T00:00:00Z");
        Product first = createProduct("batch-first", 4, 2);
        Product broken = createProduct("batch-broken", 4, 1);
        Product third = createProduct("batch-third", 4, 2);
        Orders firstOrder = createOrder(owner, OrderStatus.PENDING, oldest, first, 2);
        Orders brokenOrder = createOrder(owner, OrderStatus.PENDING, oldest.plusSeconds(1), broken, 2);
        Orders thirdOrder = createOrder(owner, OrderStatus.PENDING, oldest.plusSeconds(2), third, 2);

        OrdersRepository batchQuery = mock(OrdersRepository.class);
        when(batchQuery.findExpiredPendingOrderIds(
                eq(OrderStatus.PENDING.name()), any(Timestamp.class), any(Pageable.class)))
                .thenReturn(Arrays.asList(firstOrder.getOrderId(), brokenOrder.getOrderId(), thirdOrder.getOrderId()));
        OrderExpirationProperties batchProperties = new OrderExpirationProperties();
        batchProperties.setBatchSize(3);
        OrderExpirationBatchProcessor isolatedProcessor = new OrderExpirationBatchProcessor(
                batchQuery, orderLifecycleService, expirationPolicy, batchProperties, new SimpleMeterRegistry());

        isolatedProcessor.runOnce();

        assertOrderState(firstOrder.getOrderId(), OrderStatus.CLOSED, false, true);
        assertOrderState(brokenOrder.getOrderId(), OrderStatus.PENDING, false, false);
        assertOrderState(thirdOrder.getOrderId(), OrderStatus.CLOSED, false, true);
        assertStock(first.getId(), 6, 0);
        assertStock(broken.getId(), 4, 1);
        assertStock(third.getId(), 6, 0);
    }

    private Account createAccount(String username) {
        return createAccount(username, "USER");
    }

    private Account createAccount(String username, String role) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("test-password");
        account.setName(username);
        account.setRole(role);
        account.setPoints(0);
        account.setTelephone("18" + String.format("%09d", Math.abs(username.hashCode()) % 1_000_000_000));
        return userRepository.saveAndFlush(account);
    }

    private Product createProduct(String purpose, int amount, int frozen) {
        Product product = Product.builder()
                .title("lifecycle-" + purpose + "-" + marker)
                .price(new BigDecimal("19.99"))
                .rate(5.0).description("test").detail("test").cover("test").category("test")
                .specifications(new ArrayList<>()).contentImages(new ArrayList<>()).build();
        product = productRepository.saveAndFlush(product);
        productIds.add(product.getId());
        stockPileRepository.saveAndFlush(StockPile.builder()
                .productId(product.getId()).amount(amount).frozen(frozen).build());
        return product;
    }

    private Orders createOrder(Account account, OrderStatus status, Instant createdAt,
                               Product product, int quantity) {
        Orders order = new Orders();
        order.setAccount(account);
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setPaymentMethod("Alipay");
        order.setStatus(status.name());
        order.setCreateTime(Timestamp.from(createdAt));
        order = ordersRepository.saveAndFlush(order);
        addOrderItem(order, product, quantity);
        return order;
    }

    private void addOrderItem(Orders order, Product product, int quantity) {
        jdbcTemplate.update("insert into order_item (order_id, product_id, quantity) values (?, ?, ?)",
                order.getOrderId(), product.getId(), quantity);
    }

    private void assertOrderState(int orderId, OrderStatus status,
                                  boolean hasCancelledTime, boolean hasClosedTime) {
        Orders current = ordersRepository.findById(orderId).orElseThrow(AssertionError::new);
        assertEquals(status.name(), current.getStatus());
        assertEquals(hasCancelledTime, current.getCancelledTime() != null);
        assertEquals(hasClosedTime, current.getClosedTime() != null);
    }

    private void assertStock(int productId, int amount, int frozen) {
        StockPile stock = stockPileRepository.findByProductId(productId).orElseThrow(AssertionError::new);
        assertEquals(amount, stock.getAmount());
        assertEquals(frozen, stock.getFrozen());
    }
}
