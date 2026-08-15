package com.example.tomatomall.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartSchemaMigrationIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomato_cart_migration_test_";
    private final String host = environment("DB_HOST", "127.0.0.1");
    private final String port = environment("DB_PORT", "3307");
    private final String user = environment("DB_USER", "root");
    private final String password = environment("DB_PASSWORD", "");
    private final List<String> createdSchemas = new ArrayList<>();

    @AfterEach
    void dropTemporarySchemas() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             Statement statement = connection.createStatement()) {
            for (String schema : createdSchemas) {
                if (!schema.startsWith(SCHEMA_PREFIX)) {
                    throw new IllegalStateException("拒绝删除不属于购物车迁移测试的数据库: " + schema);
                }
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void migratesFreshDatabaseWithPositiveQuantityAndUniqueUserProductConstraints() throws Exception {
        String schema = createTemporarySchema();

        assertEquals(6, flyway(schema, null).migrate().migrationsExecuted);
        assertTrue(uniqueConstraintExists(schema));
        assertTrue(positiveQuantityCheckExists(schema));
        insertRequiredParentRows(schema, 7010, 8010);
        assertThrows(SQLException.class, () -> execute(schema,
                "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) "
                        + "VALUES (9020, 0, 7010, 8010)"));
        assertThrows(SQLException.class, () -> execute(schema,
                "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) "
                        + "VALUES (9021, -1, 7010, 8010)"));
        assertEquals(0, count(schema, "SELECT COUNT(*) FROM carts WHERE user_id=7010"));
        assertEquals(0, flyway(schema, null).migrate().migrationsExecuted);
    }

    @Test
    void upgradesValidV4CartWithoutChangingItsIdentityOrQuantity() throws Exception {
        String schema = createTemporarySchema();
        migrateToV4(schema);
        insertRequiredParentRows(schema, 7001, 8001);
        execute(schema, "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) "
                + "VALUES (9001, 3, 7001, 8001)");

        assertEquals(2, flyway(schema, null).migrate().migrationsExecuted);

        assertEquals(1, count(schema, "SELECT COUNT(*) FROM carts "
                + "WHERE cart_item_id=9001 AND quantity=3 AND user_id=7001 AND product_id=8001"));
        assertTrue(uniqueConstraintExists(schema));
        assertTrue(positiveQuantityCheckExists(schema));
    }

    @Test
    void refusesNonPositiveHistoricalQuantityWithoutChangingAnyCartRow() throws Exception {
        String schema = createTemporarySchema();
        migrateToV4(schema);
        insertRequiredParentRows(schema, 7002, 8002);
        insertRequiredParentRows(schema, 7006, 8006);
        execute(schema, "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) VALUES "
                + "(9002, 0, 7002, 8002), (9003, -1, 7006, 8006)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals(2, count(schema, "SELECT COUNT(*) FROM carts WHERE cart_item_id IN (9002, 9003)"));
        assertEquals(-1, count(schema, "SELECT SUM(quantity) FROM carts WHERE cart_item_id IN (9002, 9003)"));
        assertFalse(uniqueConstraintExists(schema));
        assertFalse(positiveQuantityCheckExists(schema));
    }

    @Test
    void refusesHistoricalDuplicateUserProductWithoutDeletingOrMergingRows() throws Exception {
        String schema = createTemporarySchema();
        migrateToV4(schema);
        insertRequiredParentRows(schema, 7003, 8003);
        execute(schema, "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) VALUES "
                + "(9004, 2, 7003, 8003), (9005, 4, 7003, 8003)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals(2, count(schema, "SELECT COUNT(*) FROM carts WHERE user_id=7003 AND product_id=8003"));
        assertEquals(6, count(schema, "SELECT SUM(quantity) FROM carts WHERE user_id=7003 AND product_id=8003"));
        assertFalse(uniqueConstraintExists(schema));
        assertFalse(positiveQuantityCheckExists(schema));
    }

    @Test
    void concurrentInsertKeepsExactlyOneCartRowForTheSameUserAndProduct() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, null).migrate();
        insertRequiredParentRows(schema, 7004, 8004);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> insertCart(schema, 9010, ready, start));
            Future<Boolean> second = executor.submit(() -> insertCart(schema, 9011, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, count(schema, "SELECT COUNT(*) FROM carts WHERE user_id=7004 AND product_id=8004"));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean insertCart(String schema, int cartId, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password)) {
            connection.setAutoCommit(false);
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO carts (cart_item_id, quantity, user_id, product_id) VALUES (?, 1, 7004, 8004)")) {
                statement.setInt(1, cartId);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                return false;
            }
        }
    }

    private void migrateToV4(String schema) {
        assertEquals(4, flyway(schema, MigrationVersion.fromVersion("4")).migrate().migrationsExecuted);
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(databaseUrl(schema), user, password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private String createTemporarySchema() throws SQLException {
        String schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        createdSchemas.add(schema);
        return schema;
    }

    private void insertRequiredParentRows(String schema, int accountId, int productId) throws SQLException {
        execute(schema, "INSERT INTO account (id, name, password, role, username) VALUES ("
                + accountId + ", 'cart-test', 'password', 'USER', 'cart-user-" + accountId + "')");
        execute(schema, "INSERT INTO products (product_id, title) VALUES ("
                + productId + ", 'cart-product-" + productId + "')");
    }

    private void execute(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private boolean uniqueConstraintExists(String schema) throws SQLException {
        return metadataCount("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=? AND table_name='carts' AND index_name='uk_carts_user_product' "
                + "AND non_unique=0", schema) == 2;
    }

    private boolean positiveQuantityCheckExists(String schema) throws SQLException {
        return metadataCount("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema=? AND table_name='carts' "
                + "AND constraint_name='chk_carts_quantity_positive' AND constraint_type='CHECK'", schema) == 1;
    }

    private int metadataCount(String sql, String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String serverUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private String databaseUrl(String schema) {
        return "jdbc:mysql://" + host + ":" + port + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
