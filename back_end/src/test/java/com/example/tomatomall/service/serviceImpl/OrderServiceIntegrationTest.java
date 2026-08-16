package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.exception.OrderCheckoutConflictException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.order.OrderCheckoutRequestNormalizer;
import com.example.tomatomall.service.order.NormalizedCheckoutRequest;
import com.example.tomatomall.vo.OrdersVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class OrderServiceIntegrationTest {

    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("19.99");

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private OrdersRepository ordersRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockPileRepository stockPileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrderCheckoutRequestNormalizer requestNormalizer;

    private final List<Integer> productIds = new ArrayList<>();
    private Integer accountId;
    private String marker;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Account account = new Account();
        account.setUsername("order-test-" + marker);
        account.setPassword("test-password");
        account.setName("Order Test");
        account.setRole("USER");
        account.setPoints(0);
        account.setTelephone("19" + String.format("%09d", Math.abs(marker.hashCode()) % 1_000_000_000));
        accountId = userRepository.saveAndFlush(account).getId();
    }

    @AfterEach
    void cleanUp() {
        Mockito.reset(ordersRepository);

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
    void createsPendingOrderAndFreezesStock() {
        Product product = createProduct("normal", 5);

        OrdersVO created = checkout(request(item(product.getId(), 2)));

        assertNotNull(created.getOrderId());
        assertEquals(accountId, created.getUserId());
        assertEquals(0, new BigDecimal("39.98").compareTo(created.getTotalAmount()));
        assertEquals("PENDING", created.getStatus());
        assertEquals("Alipay", created.getPaymentMethod());

        assertStock(product.getId(), 3, 2);
        assertEquals(1, countOrders());
        assertEquals(1, countOrderItems(created.getOrderId()));
        assertEquals(2, orderItemQuantity(created.getOrderId(), product.getId()));
    }

    @Test
    void aggregatesDuplicateProductEntriesBeforeCreatingOrderItems() {
        Product product = createProduct("duplicate", 5);

        OrdersVO created = checkout(
            request(item(product.getId(), 2), item(product.getId(), 3))
        );

        assertEquals(0, PRODUCT_PRICE.multiply(new BigDecimal("5")).compareTo(created.getTotalAmount()));
        assertStock(product.getId(), 0, 5);
        assertEquals(1, countOrderItems(created.getOrderId()));
        assertEquals(5, orderItemQuantity(created.getOrderId(), product.getId()));
    }

    @Test
    void insufficientLaterItemRollsBackEarlierStockFreezeAndOrder() {
        Product available = createProduct("available", 5);
        Product unavailable = createProduct("unavailable", 0);

        TomatoException exception = assertThrows(
            TomatoException.class,
            () -> checkout(
                request(item(available.getId(), 1), item(unavailable.getId(), 1))
            )
        );

        assertEquals("404", exception.getCode());
        assertStock(available.getId(), 5, 0);
        assertStock(unavailable.getId(), 0, 0);
        assertEquals(0, countOrders());
    }

    @Test
    void missingStockRecordDoesNotCreateOrder() {
        Product product = createProductWithoutStock("missing-stock");

        TomatoException exception = assertThrows(
            TomatoException.class,
            () -> checkout(request(item(product.getId(), 1)))
        );

        assertEquals("404", exception.getCode());
        assertEquals(0, countOrders());
        assertEquals(0, countStockRows(product.getId()));
    }

    @Test
    void databaseConstraintRejectsDuplicateStockWithoutChangingOriginalRow() {
        Product product = createProduct("duplicate-stock", 3);
        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "insert into stockpile (amount, frozen, product_id) values (?, ?, ?)",
                4,
                0,
                product.getId()
        ));

        assertEquals(0, countOrders());
        assertEquals(1, countStockRows(product.getId()));
        assertEquals(3, totalAvailableStock(product.getId()));
        assertEquals(0, totalFrozenStock(product.getId()));
    }

    @Test
    void orderPersistenceFailureRollsBackStockFreeze() {
        Product product = createProduct("rollback", 2);
        AtomicInteger flushes = new AtomicInteger();
        doAnswer(invocation -> {
            Orders saved = (Orders) invocation.callRealMethod();
            if (flushes.incrementAndGet() == 2) {
                throw new RuntimeException("expected order persistence failure");
            }
            return saved;
        }).when(ordersRepository).saveAndFlush(any(Orders.class));

        assertThrows(
            RuntimeException.class,
            () -> checkout(request(item(product.getId(), 1)))
        );

        Mockito.reset(ordersRepository);
        assertStock(product.getId(), 2, 0);
        assertEquals(0, countOrders());
    }

    @Test
    void concurrentOrdersCannotFreezeMoreThanAvailableStock() throws Exception {
        Product product = createProduct("concurrent", 1);
        int workers = 16;

        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger stockFailures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        checkout(request(item(product.getId(), 1)));
                        successes.incrementAndGet();
                    } catch (TomatoException exception) {
                        if ("404".equals(exception.getCode())) {
                            stockFailures.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, successes.get());
        assertEquals(workers - 1, stockFailures.get());
        assertStock(product.getId(), 0, 1);
        assertEquals(1, countOrders());
    }

    @Test
    void serialReplayReturnsSameOrderWithoutFreezingStockAgain() {
        Product product = createProduct("serial-replay", 5);
        String key = UUID.randomUUID().toString();
        CreateOrderDTO request = request(item(product.getId(), 2));

        com.example.tomatomall.service.order.OrderCheckoutResult first =
                orderService.addOrder(accountId, key, request);
        com.example.tomatomall.service.order.OrderCheckoutResult second =
                orderService.addOrder(accountId, key, request);

        assertEquals(first.getOrder().getOrderId(), second.getOrder().getOrderId());
        assertEquals(false, first.isReplayed());
        assertEquals(true, second.isReplayed());
        assertStock(product.getId(), 3, 2);
        assertEquals(1, countOrders());
    }

    @Test
    void sameKeyWithDifferentRequestConflictsWithoutChangingStock() {
        Product product = createProduct("request-conflict", 5);
        String key = UUID.randomUUID().toString();

        orderService.addOrder(accountId, key, request(item(product.getId(), 1)));
        assertThrows(OrderCheckoutConflictException.class,
                () -> orderService.addOrder(accountId, key, request(item(product.getId(), 2))));

        assertStock(product.getId(), 4, 1);
        assertEquals(1, countOrders());
    }

    @Test
    void sixteenConcurrentSameKeyRequestsCreateOneOrderAndFreezeOnce() throws Exception {
        Product product = createProduct("same-key-concurrent", 20);
        String key = UUID.randomUUID().toString();
        int workers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return orderService.addOrder(
                            accountId,
                            key,
                            request(item(product.getId(), 2))
                    ).getOrder().getOrderId();
                }));
            }
            start.countDown();
            Integer expectedOrderId = null;
            for (Future<Integer> future : futures) {
                Integer actualOrderId = future.get(30, TimeUnit.SECONDS);
                if (expectedOrderId == null) {
                    expectedOrderId = actualOrderId;
                }
                assertEquals(expectedOrderId, actualOrderId);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertStock(product.getId(), 18, 2);
        assertEquals(1, countOrders());
        assertEquals(1, countOrderItems(jdbcTemplate.queryForObject(
                "select order_id from orders where user_id=? and idempotency_key=?",
                Integer.class,
                accountId,
                key
        )));
    }

    @Test
    void concurrentDifferentPayloadsOnSameKeyProduceOneWinnerAndOneConflict() throws Exception {
        Product product = createProduct("concurrent-conflict", 5);
        String key = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> checkoutOutcome(
                    start, key, request(item(product.getId(), 1))));
            Future<Object> second = executor.submit(() -> checkoutOutcome(
                    start, key, request(item(product.getId(), 2))));
            start.countDown();

            Object firstOutcome = first.get(20, TimeUnit.SECONDS);
            Object secondOutcome = second.get(20, TimeUnit.SECONDS);
            int conflicts = (firstOutcome instanceof OrderCheckoutConflictException ? 1 : 0)
                    + (secondOutcome instanceof OrderCheckoutConflictException ? 1 : 0);
            int successes = (firstOutcome instanceof OrdersVO ? 1 : 0)
                    + (secondOutcome instanceof OrdersVO ? 1 : 0);
            assertEquals(1, conflicts);
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, countOrders());
        int frozen = totalFrozenStock(product.getId());
        assertEquals(true, frozen == 1 || frozen == 2);
        assertStock(product.getId(), 5 - frozen, frozen);
    }

    private Object checkoutOutcome(CountDownLatch start,
                                   String key,
                                   CreateOrderDTO request) throws InterruptedException {
        start.await();
        try {
            return orderService.addOrder(accountId, key, request).getOrder();
        } catch (OrderCheckoutConflictException exception) {
            return exception;
        }
    }

    @Test
    void differentKeysForSameRequestRemainIndependentOrders() {
        Product product = createProduct("different-keys", 4);
        CreateOrderDTO request = request(item(product.getId(), 2));

        Integer first = orderService.addOrder(accountId, UUID.randomUUID().toString(), request)
                .getOrder().getOrderId();
        Integer second = orderService.addOrder(accountId, UUID.randomUUID().toString(), request)
                .getOrder().getOrderId();

        assertEquals(false, first.equals(second));
        assertStock(product.getId(), 0, 4);
        assertEquals(2, countOrders());
    }

    @Test
    void differentUsersMayUseTheSameUuidWithoutSharingOrders() {
        Product product = createProduct("different-users", 4);
        Account secondAccount = new Account();
        secondAccount.setUsername("order-second-" + marker);
        secondAccount.setPassword("test-password");
        secondAccount.setName("Second Order Test");
        secondAccount.setRole("USER");
        secondAccount.setPoints(0);
        secondAccount = userRepository.saveAndFlush(secondAccount);
        String key = UUID.randomUUID().toString();
        try {
            Integer first = orderService.addOrder(
                    accountId, key, request(item(product.getId(), 2))).getOrder().getOrderId();
            Integer second = orderService.addOrder(
                    secondAccount.getId(), key, request(item(product.getId(), 2))).getOrder().getOrderId();

            assertEquals(false, first.equals(second));
            assertStock(product.getId(), 0, 4);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from orders where user_id=? and idempotency_key=?",
                    Integer.class,
                    secondAccount.getId(),
                    key
            ));
        } finally {
            List<Integer> secondOrderIds = jdbcTemplate.queryForList(
                    "select order_id from orders where user_id=?", Integer.class, secondAccount.getId());
            for (Integer orderId : secondOrderIds) {
                jdbcTemplate.update("delete from order_item where order_id=?", orderId);
                jdbcTemplate.update("delete from orders where order_id=?", orderId);
            }
            jdbcTemplate.update("delete from account where id=?", secondAccount.getId());
        }
    }

    @Test
    void replayReturnsExistingOrderRegardlessOfItsCurrentStatus() {
        Product product = createProduct("status-replay", 8);
        String[] statuses = {"PENDING", "PAID", "CANCELLED", "CLOSED"};

        for (String status : statuses) {
            String key = UUID.randomUUID().toString();
            CreateOrderDTO request = request(item(product.getId(), 1));
            Integer orderId = orderService.addOrder(accountId, key, request).getOrder().getOrderId();
            jdbcTemplate.update("update orders set status=? where order_id=?", status, orderId);

            com.example.tomatomall.service.order.OrderCheckoutResult replay =
                    orderService.addOrder(accountId, key, request);
            assertEquals(true, replay.isReplayed());
            assertEquals(orderId, replay.getOrder().getOrderId());
            assertEquals(status, replay.getOrder().getStatus());
        }

        assertStock(product.getId(), 4, 4);
        assertEquals(4, countOrders());
    }

    @Test
    void followerReturnsCommittedClaimInsteadOfCreatingAnotherOrder() throws Exception {
        assertFollowerBehaviorAfterClaimResolution(true);
    }

    @Test
    void followerTakesOverWhenTheFirstClaimRollsBack() throws Exception {
        assertFollowerBehaviorAfterClaimResolution(false);
    }

    private void assertFollowerBehaviorAfterClaimResolution(boolean commitFirst) throws Exception {
        Product product = createProduct(commitFirst ? "commit-follower" : "rollback-follower", 5);
        String key = UUID.randomUUID().toString();
        CreateOrderDTO request = request(item(product.getId(), 2));
        NormalizedCheckoutRequest normalized = requestNormalizer.normalize(request);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            int claimedOrderId = insertUncommittedClaim(
                    first, product.getId(), key, normalized.getFingerprint(), 2);
            Future<com.example.tomatomall.service.order.OrderCheckoutResult> follower =
                    executor.submit(() -> orderService.addOrder(accountId, key, request));

            assertThrows(TimeoutException.class, () -> follower.get(300, TimeUnit.MILLISECONDS));
            if (commitFirst) {
                first.commit();
            } else {
                first.rollback();
            }

            com.example.tomatomall.service.order.OrderCheckoutResult result =
                    follower.get(20, TimeUnit.SECONDS);
            if (commitFirst) {
                assertEquals(true, result.isReplayed());
                assertEquals(claimedOrderId, result.getOrder().getOrderId());
            } else {
                assertEquals(false, result.isReplayed());
                assertEquals(false, Integer.valueOf(claimedOrderId).equals(result.getOrder().getOrderId()));
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertStock(product.getId(), 3, 2);
        assertEquals(1, countOrders());
    }

    private int insertUncommittedClaim(Connection connection,
                                       int productId,
                                       String key,
                                       String fingerprint,
                                       int quantity) throws Exception {
        int orderId;
        try (PreparedStatement insertOrder = connection.prepareStatement(
                "insert into orders (payment_method,status,total_amount,user_id,idempotency_key,"
                        + "request_fingerprint,create_time) values ('Alipay','PENDING',?,?,?,?,CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS)) {
            insertOrder.setBigDecimal(1, PRODUCT_PRICE.multiply(BigDecimal.valueOf(quantity)));
            insertOrder.setInt(2, accountId);
            insertOrder.setString(3, key);
            insertOrder.setString(4, fingerprint);
            insertOrder.executeUpdate();
            try (ResultSet keys = insertOrder.getGeneratedKeys()) {
                keys.next();
                orderId = keys.getInt(1);
            }
        }
        try (PreparedStatement freeze = connection.prepareStatement(
                "update stockpile set amount=amount-?, frozen=frozen+? "
                        + "where product_id=? and amount>=?")) {
            freeze.setInt(1, quantity);
            freeze.setInt(2, quantity);
            freeze.setInt(3, productId);
            freeze.setInt(4, quantity);
            assertEquals(1, freeze.executeUpdate());
        }
        try (PreparedStatement item = connection.prepareStatement(
                "insert into order_item (quantity,order_id,product_id) values (?,?,?)")) {
            item.setInt(1, quantity);
            item.setInt(2, orderId);
            item.setInt(3, productId);
            item.executeUpdate();
        }
        return orderId;
    }

    private Product createProduct(String purpose, int amount) {
        Product product = createProductWithoutStock(purpose);

        StockPile stock = StockPile.builder()
            .productId(product.getId())
            .amount(amount)
            .frozen(0)
            .build();
        stockPileRepository.saveAndFlush(stock);
        return product;
    }

    private Product createProductWithoutStock(String purpose) {
        Product product = Product.builder()
            .title("order-" + purpose + "-" + marker)
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
        return product;
    }

    private CreateOrderDTO request(CreateOrderDTO.OrderItemDTO... items) {
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod("Alipay");
        request.setItems(Arrays.asList(items));
        return request;
    }

    private OrdersVO checkout(CreateOrderDTO request) {
        return orderService.addOrder(accountId, UUID.randomUUID().toString(), request).getOrder();
    }

    private CreateOrderDTO.OrderItemDTO item(int productId, int amount) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(productId);
        item.setAmount(amount);
        return item;
    }

    private int countOrders() {
        return jdbcTemplate.queryForObject(
            "select count(*) from orders where user_id = ?",
            Integer.class,
            accountId
        );
    }

    private int countOrderItems(int orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from order_item where order_id = ?",
            Integer.class,
            orderId
        );
    }

    private int orderItemQuantity(int orderId, int productId) {
        return jdbcTemplate.queryForObject(
            "select quantity from order_item where order_id = ? and product_id = ?",
            Integer.class,
            orderId,
            productId
        );
    }

    private int countStockRows(int productId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from stockpile where product_id = ?",
            Integer.class,
            productId
        );
    }

    private int totalAvailableStock(int productId) {
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(amount), 0) from stockpile where product_id = ?",
            Integer.class,
            productId
        );
    }

    private int totalFrozenStock(int productId) {
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(frozen), 0) from stockpile where product_id = ?",
            Integer.class,
            productId
        );
    }

    private void assertStock(int productId, int expectedAmount, int expectedFrozen) {
        StockPile stock = stockPileRepository.findByProductId(productId).orElseThrow(AssertionError::new);
        assertEquals(expectedAmount, stock.getAmount());
        assertEquals(expectedFrozen, stock.getFrozen());
    }
}
