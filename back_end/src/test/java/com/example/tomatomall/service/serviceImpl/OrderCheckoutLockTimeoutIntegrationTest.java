package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.OrderCheckoutUnavailableException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.order.NormalizedCheckoutRequest;
import com.example.tomatomall.service.order.OrderCheckoutRequestNormalizer;
import com.example.tomatomall.service.order.OrderCheckoutResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties =
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1")
class OrderCheckoutLockTimeoutIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCheckoutRequestNormalizer requestNormalizer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockPileRepository stockPileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private Integer accountId;
    private Integer productId;

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Account account = new Account();
        account.setUsername("order-lock-timeout-" + marker);
        account.setPassword("test-password");
        account.setName("Order lock timeout test");
        account.setRole("USER");
        account.setPoints(0);
        accountId = userRepository.saveAndFlush(account).getId();

        Product product = Product.builder()
                .title("order-lock-timeout-" + marker)
                .price(new BigDecimal("19.99"))
                .rate(5.0)
                .description("test")
                .detail("test")
                .cover("test")
                .category("literature")
                .build();
        productId = productRepository.saveAndFlush(product).getId();
        stockPileRepository.saveAndFlush(StockPile.builder()
                .productId(productId)
                .amount(2)
                .frozen(0)
                .build());
    }

    @AfterEach
    void cleanUp() {
        if (accountId != null) {
            jdbcTemplate.update(
                    "delete from order_item where order_id in (select order_id from orders where user_id=?)",
                    accountId);
            jdbcTemplate.update("delete from orders where user_id=?", accountId);
        }
        if (productId != null) {
            jdbcTemplate.update("delete from stockpile where product_id=?", productId);
            jdbcTemplate.update("delete from products where product_id=?", productId);
        }
        if (accountId != null) {
            jdbcTemplate.update("delete from account where id=?", accountId);
        }
    }

    @Test
    void actualMysqlLockTimeoutReturnsUnavailableWithoutFreezingStockAndAllowsRetry() throws Exception {
        String key = UUID.randomUUID().toString();
        CreateOrderDTO request = request(1);
        NormalizedCheckoutRequest normalized = requestNormalizer.normalize(request);

        try (Connection claimant = dataSource.getConnection()) {
            claimant.setAutoCommit(false);
            insertUncommittedOrder(claimant, key, normalized.getFingerprint());

            assertThrows(OrderCheckoutUnavailableException.class,
                    () -> orderService.addOrder(accountId, key, request));
            assertStock(2, 0);
            assertEquals(0, committedOrdersForKey(key));

            claimant.rollback();
        }

        OrderCheckoutResult retry = orderService.addOrder(accountId, key, request);
        assertEquals(false, retry.isReplayed());
        assertStock(1, 1);
        assertEquals(1, committedOrdersForKey(key));
    }

    private void insertUncommittedOrder(Connection connection,
                                        String key,
                                        String fingerprint) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into orders (payment_method,status,total_amount,user_id,idempotency_key,"
                        + "request_fingerprint,create_time) values ('Alipay','PENDING',?,?,?,?,"
                        + "CURRENT_TIMESTAMP)")) {
            statement.setBigDecimal(1, new BigDecimal("19.99"));
            statement.setInt(2, accountId);
            statement.setString(3, key);
            statement.setString(4, fingerprint);
            statement.executeUpdate();
        }
    }

    private CreateOrderDTO request(int amount) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(productId);
        item.setAmount(amount);
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod("Alipay");
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private void assertStock(int amount, int frozen) {
        StockPile stock = stockPileRepository.findByProductId(productId)
                .orElseThrow(AssertionError::new);
        assertEquals(amount, stock.getAmount());
        assertEquals(frozen, stock.getFrozen());
    }

    private int committedOrdersForKey(String key) {
        return jdbcTemplate.queryForObject(
                "select count(*) from orders where user_id=? and idempotency_key=?",
                Integer.class,
                accountId,
                key);
    }
}
