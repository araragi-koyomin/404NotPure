package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.PaymentServiceImpl;
import com.example.tomatomall.vo.OrdersVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PaymentServiceIntegrationTest {

    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("19.99");

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockPileRepository stockPileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Integer> productIds = new ArrayList<>();
    private Integer accountId;
    private String marker;
    private String triggerName;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Account account = new Account();
        account.setUsername("payment-test-" + marker);
        account.setPassword("test-password");
        account.setName("Payment Test");
        account.setRole("USER");
        account.setPoints(0);
        account.setTelephone("18" + String.format("%09d", Math.abs(marker.hashCode()) % 1_000_000_000));
        accountId = userRepository.saveAndFlush(account).getId();
    }

    @AfterEach
    void cleanUp() {
        if (triggerName != null && triggerName.startsWith("payment_release_failure_")) {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName);
        }

        if (accountId != null) {
            List<Integer> orderIds = jdbcTemplate.queryForList(
                    "select order_id from orders where user_id = ?",
                    Integer.class,
                    accountId
            );
            for (Integer orderId : orderIds) {
                jdbcTemplate.update("delete from order_item where order_id = ?", orderId);
                jdbcTemplate.update("delete from orders where order_id = ?", orderId);
            }
        }

        for (Integer productId : productIds) {
            jdbcTemplate.update("delete from stockpile where product_id = ?", productId);
            jdbcTemplate.update("delete from products where product_id = ?", productId);
        }

        if (accountId != null) {
            jdbcTemplate.update("delete from account where id = ?", accountId);
        }
    }

    @Test
    void successfulPaymentStoresResultAndReleasesFrozenStockOnce() {
        Product product = createProduct("success", 5);
        OrdersVO created = createOrder(product.getId(), 2);
        Timestamp originalCreateTime = findOrder(created.getOrderId()).getCreateTime();
        String tradeNo = tradeNo("success");

        paymentService.updateOrderStatus(
                created.getOrderId().toString(),
                tradeNo,
                "39.980"
        );

        Orders paid = findOrder(created.getOrderId());
        assertEquals("PAID", paid.getStatus());
        assertEquals(originalCreateTime, paid.getCreateTime());
        assertNotNull(paid.getPaidTime());
        assertEquals(tradeNo, paid.getAlipayTradeNo());
        assertStock(product.getId(), 3, 0);
    }

    @Test
    void amountMismatchLeavesOrderPendingAndStockFrozen() {
        Product product = createProduct("amount", 5);
        OrdersVO created = createOrder(product.getId(), 2);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus(
                        created.getOrderId().toString(),
                        tradeNo("amount"),
                        "39.99"
                )
        );

        assertEquals("400", exception.getCode());
        assertPendingWithoutPaymentResult(created.getOrderId());
        assertStock(product.getId(), 3, 2);
    }

    @Test
    void malformedOrderNumberIsRejectedAsInvalidNotification() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus("not-an-order", tradeNo("bad-order"), "19.99")
        );

        assertEquals("400", exception.getCode());
        assertEquals(0, countOrders());
    }

    @Test
    void wellFormedButUnknownOrderNumberIsRejected() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus(
                        String.valueOf(Integer.MAX_VALUE),
                        tradeNo("unknown-order"),
                        "19.99"
                )
        );

        assertEquals("400", exception.getCode());
        assertEquals(0, countOrders());
    }

    @Test
    void illegalSourceStatusCannotBecomePaid() {
        Product product = createProduct("illegal-state", 5);
        OrdersVO created = createOrder(product.getId(), 1);
        Orders order = findOrder(created.getOrderId());
        order.setStatus("CANCELLED");
        ordersRepository.saveAndFlush(order);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus(
                        created.getOrderId().toString(),
                        tradeNo("illegal-state"),
                        "19.99"
                )
        );

        assertEquals("409", exception.getCode());
        Orders unchanged = findOrder(created.getOrderId());
        assertEquals("CANCELLED", unchanged.getStatus());
        assertNull(unchanged.getPaidTime());
        assertNull(unchanged.getAlipayTradeNo());
        assertStock(product.getId(), 4, 1);
    }

    @Test
    void serialDuplicateNotificationIsIdempotent() {
        Product product = createProduct("serial-duplicate", 5);
        OrdersVO created = createOrder(product.getId(), 2);
        String tradeNo = tradeNo("serial-duplicate");

        paymentService.updateOrderStatus(created.getOrderId().toString(), tradeNo, "39.98");
        Orders firstResult = findOrder(created.getOrderId());
        Timestamp firstPaidTime = firstResult.getPaidTime();

        paymentService.updateOrderStatus(created.getOrderId().toString(), tradeNo, "39.980");

        Orders duplicateResult = findOrder(created.getOrderId());
        assertEquals("PAID", duplicateResult.getStatus());
        assertEquals(firstPaidTime, duplicateResult.getPaidTime());
        assertEquals(tradeNo, duplicateResult.getAlipayTradeNo());
        assertStock(product.getId(), 3, 0);
    }

    @Test
    void paidOrderRejectsDifferentTradeNumber() {
        Product product = createProduct("different-trade", 5);
        OrdersVO created = createOrder(product.getId(), 1);

        paymentService.updateOrderStatus(
                created.getOrderId().toString(),
                tradeNo("original"),
                "19.99"
        );

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus(
                        created.getOrderId().toString(),
                        tradeNo("different"),
                        "19.99"
                )
        );

        assertEquals("409", exception.getCode());
        assertStock(product.getId(), 4, 0);
    }

    @Test
    void sameAlipayTradeNumberCannotPayTwoOrders() {
        Product firstProduct = createProduct("trade-unique-first", 2);
        Product secondProduct = createProduct("trade-unique-second", 2);
        OrdersVO firstOrder = createOrder(firstProduct.getId(), 1);
        OrdersVO secondOrder = createOrder(secondProduct.getId(), 1);
        String tradeNo = tradeNo("unique");

        paymentService.updateOrderStatus(firstOrder.getOrderId().toString(), tradeNo, "19.99");

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> paymentService.updateOrderStatus(secondOrder.getOrderId().toString(), tradeNo, "19.99")
        );

        assertEquals("409", exception.getCode());
        assertEquals("PAID", findOrder(firstOrder.getOrderId()).getStatus());
        assertPendingWithoutPaymentResult(secondOrder.getOrderId());
        assertStock(firstProduct.getId(), 1, 0);
        assertStock(secondProduct.getId(), 1, 1);
    }

    @Test
    void concurrentDuplicateNotificationsReleaseFrozenStockOnlyOnce() throws Exception {
        Product product = createProduct("concurrent", 5);
        OrdersVO created = createOrder(product.getId(), 2);
        String tradeNo = tradeNo("concurrent");
        int workers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch snapshotsRead = new CountDownLatch(workers);
        CountDownLatch beginPayment = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.executeWithoutResult(status -> {
                        Orders snapshot = findOrder(created.getOrderId());
                        assertEquals("PENDING", snapshot.getStatus());
                        snapshotsRead.countDown();
                        try {
                            if (!beginPayment.await(20, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting to begin concurrent payment");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("Interrupted while waiting to begin concurrent payment", exception);
                        }
                        paymentService.updateOrderStatus(
                                created.getOrderId().toString(),
                                tradeNo,
                                "39.98"
                        );
                    });
                    return null;
                }));
            }

            boolean everyTransactionReadPending = snapshotsRead.await(20, TimeUnit.SECONDS);
            beginPayment.countDown();
            assertTrue(everyTransactionReadPending);
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            beginPayment.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Orders paid = findOrder(created.getOrderId());
        assertEquals("PAID", paid.getStatus());
        assertNotNull(paid.getPaidTime());
        assertEquals(tradeNo, paid.getAlipayTradeNo());
        assertStock(product.getId(), 3, 0);
    }

    @Test
    void stockReleaseFailureRollsBackPaymentResult() {
        Product product = createProduct("rollback", 5);
        OrdersVO created = createOrder(product.getId(), 2);
        triggerName = "payment_release_failure_" + marker;
        jdbcTemplate.execute(
                "CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON stockpile FOR EACH ROW "
                        + "BEGIN IF NEW.product_id = " + product.getId() + " THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expected payment stock failure'; "
                        + "END IF; END"
        );

        assertThrows(
                RuntimeException.class,
                () -> paymentService.updateOrderStatus(
                        created.getOrderId().toString(),
                        tradeNo("rollback"),
                        "39.98"
                )
        );

        assertPendingWithoutPaymentResult(created.getOrderId());
        assertStock(product.getId(), 3, 2);
    }

    @Test
    void laterStockReleaseFailureRollsBackEarlierProductRelease() {
        Product firstProduct = createProduct("rollback-first", 5);
        Product secondProduct = createProduct("rollback-second", 5);
        OrdersVO created = createOrder(
                Arrays.asList(
                        orderItem(firstProduct.getId(), 1),
                        orderItem(secondProduct.getId(), 2)
                )
        );
        triggerName = "payment_release_failure_" + marker;
        jdbcTemplate.execute(
                "CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON stockpile FOR EACH ROW "
                        + "BEGIN IF NEW.product_id = " + secondProduct.getId() + " THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expected later payment stock failure'; "
                        + "END IF; END"
        );

        assertThrows(
                RuntimeException.class,
                () -> paymentService.updateOrderStatus(
                        created.getOrderId().toString(),
                        tradeNo("rollback-later"),
                        "59.97"
                )
        );

        assertPendingWithoutPaymentResult(created.getOrderId());
        assertStock(firstProduct.getId(), 4, 1);
        assertStock(secondProduct.getId(), 3, 2);
    }

    private Product createProduct(String purpose, int amount) {
        Product product = Product.builder()
                .title("payment-" + purpose + "-" + marker)
                .price(PRODUCT_PRICE)
                .rate(5.0)
                .description("test")
                .detail("test")
                .cover("test")
                .category("test")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
        product = productRepository.saveAndFlush(product);
        productIds.add(product.getId());

        StockPile stock = StockPile.builder()
                .productId(product.getId())
                .amount(amount)
                .frozen(0)
                .build();
        stockPileRepository.saveAndFlush(stock);
        return product;
    }

    private OrdersVO createOrder(int productId, int quantity) {
        return createOrder(Collections.singletonList(orderItem(productId, quantity)));
    }

    private OrdersVO createOrder(List<CreateOrderDTO.OrderItemDTO> items) {
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod("Alipay");
        request.setItems(items);
        return orderService.addOrder(accountId, request);
    }

    private CreateOrderDTO.OrderItemDTO orderItem(int productId, int quantity) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(productId);
        item.setAmount(quantity);
        return item;
    }

    private Orders findOrder(int orderId) {
        return ordersRepository.findById(orderId).orElseThrow(AssertionError::new);
    }

    private void assertPendingWithoutPaymentResult(int orderId) {
        Orders order = findOrder(orderId);
        assertEquals("PENDING", order.getStatus());
        assertNull(order.getPaidTime());
        assertNull(order.getAlipayTradeNo());
    }

    private void assertStock(int productId, int expectedAmount, int expectedFrozen) {
        StockPile stock = stockPileRepository.findByProductId(productId).orElseThrow(AssertionError::new);
        assertEquals(expectedAmount, stock.getAmount());
        assertEquals(expectedFrozen, stock.getFrozen());
        assertTrue(stock.getFrozen() >= 0);
    }

    private int countOrders() {
        return jdbcTemplate.queryForObject(
                "select count(*) from orders where user_id = ?",
                Integer.class,
                accountId
        );
    }

    private String tradeNo(String purpose) {
        return ("20260810" + Math.abs((purpose + marker).hashCode()) + marker)
                .replace("-", "")
                .substring(0, 24);
    }
}
