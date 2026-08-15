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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

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

        OrdersVO created = orderService.addOrder(accountId, request(item(product.getId(), 2)));

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

        OrdersVO created = orderService.addOrder(
            accountId,
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
            () -> orderService.addOrder(
                accountId,
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
            () -> orderService.addOrder(accountId, request(item(product.getId(), 1)))
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
        doThrow(new RuntimeException("expected order persistence failure"))
            .when(ordersRepository)
            .save(Mockito.<Orders>any());

        assertThrows(
            RuntimeException.class,
            () -> orderService.addOrder(accountId, request(item(product.getId(), 1)))
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
                        orderService.addOrder(accountId, request(item(product.getId(), 1)));
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
