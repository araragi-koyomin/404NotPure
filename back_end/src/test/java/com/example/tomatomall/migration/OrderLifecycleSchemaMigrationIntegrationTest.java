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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleSchemaMigrationIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomatomall_order_lifecycle_";

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
                    throw new IllegalStateException("Refusing to drop unexpected schema " + schema);
                }
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void freshSchemaAddsLifecycleColumnsIndexAndStatusConstraint() throws Exception {
        String schema = createTemporarySchema();

        Flyway flyway = flyway(schema, null);
        flyway.migrate();

        assertEquals("6", flyway.info().current().getVersion().getVersion());
        assertTrue(columnExists(schema, "orders", "cancelled_time"));
        assertTrue(columnExists(schema, "orders", "closed_time"));
        assertTrue(indexExists(schema, "orders", "idx_orders_status_create_time_order_id"));
        assertTrue(checkConstraintExists(schema, "orders", "chk_orders_status"));

        insertAccount(schema, 8101);
        execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) VALUES "
                + "(8101, 'Alipay', 'CANCELLED', 12.34, 8101), "
                + "(8102, 'Alipay', 'CLOSED', 12.34, 8101)");
        assertEquals(2, count(schema, "SELECT COUNT(*) FROM orders WHERE order_id IN (8101, 8102)"));
        assertThrows(SQLException.class, () -> execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) "
                + "VALUES (8103, 'Alipay', 'UNKNOWN', 12.34, 8101)"));
        assertThrows(SQLException.class, () -> execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) "
                + "VALUES (8104, 'Alipay', 'pending', 12.34, 8101)"));
    }

    @Test
    void unknownLegacyStatusBlocksV6WithoutLeavingPartialStructure() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, MigrationVersion.fromVersion("5")).migrate();
        insertAccount(schema, 8201);
        execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) "
                + "VALUES (8201, 'Alipay', 'UNKNOWN', 12.34, 8201)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals("5", latestSuccessfulVersion(schema));
        assertFalse(columnExists(schema, "orders", "cancelled_time"));
        assertFalse(columnExists(schema, "orders", "closed_time"));
        assertFalse(indexExists(schema, "orders", "idx_orders_status_create_time_order_id"));
        assertFalse(checkConstraintExists(schema, "orders", "chk_orders_status"));
        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM orders WHERE order_id=8201 AND status='UNKNOWN'"));
    }

    @Test
    void caseVariantLegacyStatusBlocksV6WithoutLeavingPartialStructure() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, MigrationVersion.fromVersion("5")).migrate();
        insertAccount(schema, 8251);
        execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) "
                + "VALUES (8251, 'Alipay', 'Paid', 12.34, 8251)");

        assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

        assertEquals("5", latestSuccessfulVersion(schema));
        assertFalse(columnExists(schema, "orders", "cancelled_time"));
        assertFalse(columnExists(schema, "orders", "closed_time"));
        assertFalse(indexExists(schema, "orders", "idx_orders_status_create_time_order_id"));
        assertFalse(checkConstraintExists(schema, "orders", "chk_orders_status"));
        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM orders WHERE order_id=8251 AND BINARY status='Paid'"));
    }

    @Test
    void validLegacyStatusesMigrateWithoutRewritingOrders() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, MigrationVersion.fromVersion("5")).migrate();
        insertAccount(schema, 8301);
        execute(schema, "INSERT INTO orders "
                + "(order_id, payment_method, status, total_amount, user_id) VALUES "
                + "(8301, 'Alipay', 'PENDING', 12.34, 8301), "
                + "(8302, 'Alipay', 'PAID', 12.34, 8301), "
                + "(8303, 'Alipay', 'CANCELLED', 12.34, 8301), "
                + "(8304, 'Alipay', 'CLOSED', 12.34, 8301)");

        Flyway upgraded = flyway(schema, null);
        upgraded.migrate();

        assertEquals("6", upgraded.info().current().getVersion().getVersion());
        assertEquals(4, count(schema, "SELECT COUNT(*) FROM orders WHERE order_id BETWEEN 8301 AND 8304"));
        assertEquals(4, count(schema, "SELECT COUNT(*) FROM orders "
                + "WHERE order_id BETWEEN 8301 AND 8304 "
                + "AND cancelled_time IS NULL AND closed_time IS NULL"));
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(databaseUrl(schema), user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false);
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

    private void insertAccount(String schema, int id) throws SQLException {
        execute(schema, "INSERT INTO account (id, name, password, role, username) VALUES ("
                + id + ", 'order-lifecycle-test', 'not-a-real-password', 'USER', 'order-lifecycle-"
                + id + "')");
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

    private String latestSuccessfulVersion(String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM flyway_schema_history "
                     + "WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private boolean columnExists(String schema, String table, String column) throws SQLException {
        return metadataCount("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name=? AND column_name=?", schema, table, column) == 1;
    }

    private boolean indexExists(String schema, String table, String index) throws SQLException {
        return metadataCount("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema=? AND table_name=? AND index_name=?", schema, table, index) == 1;
    }

    private boolean checkConstraintExists(String schema, String table, String constraint) throws SQLException {
        return metadataCount("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema=? AND table_name=? AND constraint_name=? "
                + "AND constraint_type='CHECK'", schema, table, constraint) == 1;
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
