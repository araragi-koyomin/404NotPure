package com.example.tomatomall.migration;

import org.flywaydb.core.Flyway;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdempotencySchemaMigrationIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomatomall_order_idempotency_";
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";
    private static final String FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
    void freshSchemaAddsBinaryAsciiColumnsPairCheckAndUserScopedUniqueKey() throws Exception {
        String schema = createTemporarySchema();
        Flyway flyway = flyway(schema, null);

        assertEquals(7, flyway.migrate().migrationsExecuted);
        assertEquals("7", flyway.info().current().getVersion().getVersion());
        assertColumn(schema, "idempotency_key", "char", "ascii", "ascii_bin", 36);
        assertColumn(schema, "request_fingerprint", "char", "ascii", "ascii_bin", 64);
        assertTrue(constraintExists(schema, "uk_orders_user_idempotency_key", "UNIQUE"));
        assertTrue(constraintExists(schema, "chk_orders_idempotency_pair", "CHECK"));

        insertAccount(schema, 9101);
        insertAccount(schema, 9102);
        insertOrder(schema, 9101, 9101, null, null);
        insertOrder(schema, 9102, 9101, KEY, FINGERPRINT);
        assertThrows(SQLException.class,
                () -> insertOrder(schema, 9103, 9101, KEY, FINGERPRINT));
        insertOrder(schema, 9104, 9102, KEY, FINGERPRINT);
        assertThrows(SQLException.class,
                () -> insertOrder(schema, 9105, 9101, UUID.randomUUID().toString(), null));
        assertThrows(SQLException.class,
                () -> insertOrder(schema, 9106, 9101, null, FINGERPRINT));

        assertEquals(3, count(schema, "SELECT COUNT(*) FROM orders"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void upgradingV6PreservesHistoricalOrderAndLeavesItsIdempotencyFieldsNull() throws Exception {
        String schema = createTemporarySchema();
        flyway(schema, MigrationVersion.fromVersion("6")).migrate();
        insertAccount(schema, 9201);
        insertOrderBeforeV7(schema, 9201, 9201);

        Flyway upgraded = flyway(schema, null);
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        assertEquals("7", upgraded.info().current().getVersion().getVersion());
        assertEquals(1, count(schema, "SELECT COUNT(*) FROM orders WHERE order_id=9201 "
                + "AND idempotency_key IS NULL AND request_fingerprint IS NULL"));
    }

    private void assertColumn(String schema,
                              String column,
                              String dataType,
                              String characterSet,
                              String collation,
                              int length) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT data_type, character_set_name, collation_name, character_maximum_length "
                             + "FROM information_schema.columns WHERE table_schema=? "
                             + "AND table_name='orders' AND column_name=?")) {
            statement.setString(1, schema);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(dataType, result.getString(1));
                assertEquals(characterSet, result.getString(2));
                assertEquals(collation, result.getString(3));
                assertEquals(length, result.getInt(4));
            }
        }
    }

    private boolean constraintExists(String schema, String constraint, String type) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.table_constraints "
                             + "WHERE constraint_schema=? AND table_name='orders' "
                             + "AND constraint_name=? AND constraint_type=?")) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            statement.setString(3, type);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
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
                + id + ", 'idempotency-test', 'not-a-real-password', 'USER', 'idempotency-"
                + id + "')");
    }

    private void insertOrder(String schema,
                             int orderId,
                             int userId,
                             String key,
                             String fingerprint) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO orders (order_id, payment_method, status, total_amount, user_id, "
                             + "idempotency_key, request_fingerprint) VALUES (?, 'Alipay', "
                             + "'PENDING', 12.34, ?, ?, ?)")) {
            statement.setInt(1, orderId);
            statement.setInt(2, userId);
            statement.setString(3, key);
            statement.setString(4, fingerprint);
            statement.executeUpdate();
        }
    }

    private void insertOrderBeforeV7(String schema, int orderId, int userId) throws SQLException {
        execute(schema, "INSERT INTO orders (order_id, payment_method, status, total_amount, user_id) "
                + "VALUES (" + orderId + ", 'Alipay', 'PENDING', 12.34, " + userId + ")");
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
