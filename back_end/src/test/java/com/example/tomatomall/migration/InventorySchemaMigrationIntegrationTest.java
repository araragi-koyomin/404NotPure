package com.example.tomatomall.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
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
import java.util.Locale;
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

class InventorySchemaMigrationIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomato_inventory_migration_test_";

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
                    throw new IllegalStateException("拒绝删除不属于库存迁移测试的数据库: " + schema);
                }
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void migratesFreshDatabaseWithInventoryConstraintsAndIsRepeatable() throws SQLException {
        String schema = createTemporarySchema();

        MigrateResult first = flyway(schema, null).migrate();

        assertEquals(6, first.migrationsExecuted);
        assertTrue(uniqueIndexExists(schema, "uk_stockpile_product_id", "product_id"));
        assertTrue(foreignKeyHasRestrictRules(
                schema,
                "fk_stockpile_product",
                "product_id",
                "products",
                "product_id"
        ));
        assertEquals(0, flyway(schema, null).migrate().migrationsExecuted);
    }

    @Test
    void upgradesV2DataWithoutOverwritingExistingStockAndBackfillsMissingStock() throws SQLException {
        String schema = createTemporarySchema();
        migrateToV2(schema);
        insertProduct(schema, 8101, "existing-stock");
        insertProduct(schema, 8102, "missing-stock");
        execute(schema,
                "INSERT INTO stockpile (id, amount, frozen, product_id) VALUES (9101, 7, 2, 8101)");

        MigrateResult result = flyway(schema, null).migrate();

        assertEquals(4, result.migrationsExecuted);
        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM stockpile "
                        + "WHERE id=9101 AND product_id=8101 AND amount=7 AND frozen=2"));
        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM stockpile "
                        + "WHERE product_id=8102 AND amount=0 AND frozen=0"));
        assertEquals(2, count(schema, "SELECT COUNT(*) FROM stockpile"));
    }

    @Test
    void refusesDuplicateStockWithoutMergingDeletingOrBackfilling() throws SQLException {
        String schema = createTemporarySchema();
        migrateToV2(schema);
        insertProduct(schema, 8201, "duplicate-stock");
        insertProduct(schema, 8202, "must-not-be-backfilled");
        execute(schema,
                "INSERT INTO stockpile (id, amount, frozen, product_id) VALUES "
                        + "(9201, 3, 1, 8201), (9202, 5, 2, 8201)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals(2, count(schema, "SELECT COUNT(*) FROM stockpile WHERE product_id=8201"));
        assertEquals(8, count(schema, "SELECT SUM(amount) FROM stockpile WHERE product_id=8201"));
        assertEquals(3, count(schema, "SELECT SUM(frozen) FROM stockpile WHERE product_id=8201"));
        assertEquals(0, count(schema, "SELECT COUNT(*) FROM stockpile WHERE product_id=8202"));
        assertFalse(uniqueIndexExists(schema, "uk_stockpile_product_id", "product_id"));
        assertFalse(foreignKeyHasRestrictRules(
                schema,
                "fk_stockpile_product",
                "product_id",
                "products",
                "product_id"
        ));
    }

    @Test
    void refusesStockThatReferencesMissingProductWithoutChangingRows() throws SQLException {
        String schema = createTemporarySchema();
        migrateToV2(schema);
        execute(schema,
                "INSERT INTO stockpile (id, amount, frozen, product_id) VALUES (9301, 4, 0, 999001)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM stockpile "
                        + "WHERE id=9301 AND product_id=999001 AND amount=4 AND frozen=0"));
        assertFalse(uniqueIndexExists(schema, "uk_stockpile_product_id", "product_id"));
        assertFalse(foreignKeyHasRestrictRules(
                schema,
                "fk_stockpile_product",
                "product_id",
                "products",
                "product_id"
        ));
    }

    @Test
    void concurrentStockCreationKeepsExactlyOneRowPerProduct() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, null).migrate();
        insertProduct(schema, 8401, "concurrent-stock");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> insertStockConcurrently(schema, 9401, 8401, ready, start));
            Future<Boolean> second = executor.submit(() -> insertStockConcurrently(schema, 9402, 8401, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
            assertEquals(1, count(schema, "SELECT COUNT(*) FROM stockpile WHERE product_id=8401"));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean insertStockConcurrently(String schema,
                                            int stockId,
                                            int productId,
                                            CountDownLatch ready,
                                            CountDownLatch start) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password)) {
            connection.setAutoCommit(false);
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发库存测试没有收到开始信号");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO stockpile (id, amount, frozen, product_id) VALUES (?, 1, 0, ?)")) {
                statement.setInt(1, stockId);
                statement.setInt(2, productId);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                return false;
            }
        }
    }

    private void migrateToV2(String schema) {
        assertEquals(2, flyway(schema, MigrationVersion.fromVersion("2")).migrate().migrationsExecuted);
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

    private void insertProduct(String schema, int productId, String title) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO products (product_id, title) VALUES (?, ?)")) {
            statement.setInt(1, productId);
            statement.setString(2, title);
            statement.executeUpdate();
        }
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
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private boolean uniqueIndexExists(String schema, String indexName, String column) throws SQLException {
        return metadataCount(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema=? AND table_name='stockpile' AND index_name=? "
                        + "AND column_name=? AND non_unique=0",
                schema,
                indexName,
                column
        ) == 1;
    }

    private boolean foreignKeyHasRestrictRules(String schema,
                                               String constraintName,
                                               String column,
                                               String referencedTable,
                                               String referencedColumn) throws SQLException {
        return metadataCount(
                "SELECT COUNT(*) FROM information_schema.key_column_usage key_usage "
                        + "JOIN information_schema.referential_constraints reference_rule "
                        + "ON reference_rule.constraint_schema=key_usage.constraint_schema "
                        + "AND reference_rule.constraint_name=key_usage.constraint_name "
                        + "WHERE key_usage.table_schema=? AND key_usage.table_name='stockpile' "
                        + "AND key_usage.constraint_name=? AND key_usage.column_name=? "
                        + "AND key_usage.referenced_table_name=? AND key_usage.referenced_column_name=? "
                        + "AND reference_rule.update_rule='RESTRICT' "
                        + "AND reference_rule.delete_rule='RESTRICT'",
                schema,
                constraintName,
                column,
                referencedTable,
                referencedColumn
        ) == 1;
    }

    private int metadataCount(String sql, String... parameters) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String serverUrl() {
        return "jdbc:mysql://" + host + ":" + port
                + "/?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false";
    }

    private String databaseUrl(String schema) {
        return "jdbc:mysql://" + host + ":" + port + "/" + schema
                + "?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false";
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT).equals("null") ? defaultValue : value.trim();
    }
}
