package com.example.tomatomall.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataImporterIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomato_demo_import_test_";

    private final String host = environment("DB_HOST", "127.0.0.1");
    private final String port = environment("DB_PORT", "3307");
    private final String user = environment("DB_USER", "root");
    private final String password = environment("DB_PASSWORD", "");
    private final List<String> createdSchemas = new ArrayList<>();

    @TempDir
    Path tempDirectory;

    @AfterEach
    void dropTemporarySchemas() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             Statement statement = connection.createStatement()) {
            for (String schema : createdSchemas) {
                if (!schema.startsWith(SCHEMA_PREFIX)) {
                    throw new IllegalStateException("拒绝删除非 DATA-001 测试数据库: " + schema);
                }
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void importsCompleteDatasetAndSecondRunDoesNotDuplicateOrOverwriteBusinessChanges() throws Exception {
        String schema = createMigratedSchema();
        DemoDataImporter importer = importer(schema, DemoImportHook.none());
        DemoDataImportConfig config = config(300, 500, 404L);

        DemoImportSummary first = importer.importData(config);
        assertEquals(300, first.createdProducts());
        assertEquals(502, first.createdUsers());
        assertEquals(6, first.createdAdvertisements());
        assertEquals(300, count(schema, "products"));
        assertEquals(300, count(schema, "stockpile"));
        assertEquals(1200, count(schema, "product_specifications"));
        assertEquals(300, count(schema, "product_content_images"));
        assertEquals(502, count(schema, "account"));

        String originalPasswordHash = scalarString(schema,
                "SELECT password FROM account WHERE username = 'demo_user'");
        execute(schema, "UPDATE account SET name = '面试现场修改', password = 'changed-hash' "
                + "WHERE username = 'demo_user'");
        execute(schema, "UPDATE stockpile SET amount = 7 WHERE product_id = "
                + "(SELECT product_id FROM products WHERE cover = "
                + "'/demo-data/generated/seed-404/books/book-0001-cover.svg')");
        execute(schema, "INSERT INTO products (title, cover, price) "
                + "VALUES ('用户自己创建的书', '/user-content/cover.svg', 9.90)");
        execute(schema, "INSERT INTO stockpile (amount, frozen, product_id) "
                + "SELECT 13, 2, product_id FROM products WHERE cover = '/user-content/cover.svg'");
        execute(schema, "INSERT INTO account "
                + "(username, password, name, role, email, location, points) VALUES "
                + "('user_owned_account', 'user-owned-hash', '用户自己的账户', 'USER', "
                + "'user-owned@example.invalid', '用户数据', 19)");

        DemoImportSummary repeated = importer.importData(config);

        assertEquals(0, repeated.createdProducts());
        assertEquals(0, repeated.createdUsers());
        assertEquals(0, repeated.createdAdvertisements());
        assertEquals(301, count(schema, "products"));
        assertEquals(301, count(schema, "stockpile"));
        assertEquals(503, count(schema, "account"));
        assertEquals("面试现场修改", scalarString(schema,
                "SELECT name FROM account WHERE username = 'demo_user'"));
        assertEquals("changed-hash", scalarString(schema,
                "SELECT password FROM account WHERE username = 'demo_user'"));
        assertEquals(7, scalarInt(schema,
                "SELECT amount FROM stockpile WHERE product_id = "
                        + "(SELECT product_id FROM products WHERE cover = "
                        + "'/demo-data/generated/seed-404/books/book-0001-cover.svg')"));
        assertEquals(13, scalarInt(schema,
                "SELECT amount FROM stockpile WHERE product_id = "
                        + "(SELECT product_id FROM products WHERE cover = '/user-content/cover.svg')"));
        assertEquals("user-owned-hash", scalarString(schema,
                "SELECT password FROM account WHERE username = 'user_owned_account'"));
        assertTrue(!"changed-hash".equals(originalPasswordHash));
        assertTrue(new BCryptPasswordEncoder().matches("local-demo-password", originalPasswordHash));
    }

    @Test
    void failureAfterProductWritesRollsBackEveryDatabaseRecord() throws Exception {
        String schema = createMigratedSchema();
        DemoDataImporter importer = importer(schema,
                connection -> {
                    throw new SQLException("intentional DATA-001 rollback test");
                });

        assertThrows(DemoDataImportException.class,
                () -> importer.importData(config(24, 8, 404L)));

        assertEquals(0, count(schema, "products"));
        assertEquals(0, count(schema, "stockpile"));
        assertEquals(0, count(schema, "product_specifications"));
        assertEquals(0, count(schema, "product_content_images"));
        assertEquals(0, count(schema, "advertisements"));
        assertEquals(0, count(schema, "account"));
    }

    @Test
    void databaseNameMismatchIsRejectedBeforeAnyWrite() throws Exception {
        String schema = createMigratedSchema();
        DemoDataImporter importer = new DemoDataImporter(
                dataSource(schema),
                new BCryptPasswordEncoder(),
                DemoImportHook.none()
        );

        assertThrows(IllegalStateException.class,
                () -> importer.importData(config(24, 8, 404L)));
        assertEquals(0, count(schema, "products"));
        assertEquals(0, count(schema, "account"));
    }

    @Test
    void mysqlCaseInsensitiveMarkersAreRecognizedWithoutCreatingDuplicates() throws Exception {
        String schema = createMigratedSchema();
        execute(schema, "INSERT INTO account "
                + "(username, password, name, role, email, location, points) VALUES "
                + "('Demo_User  ', 'existing-hash', '已有演示用户', 'USER', "
                + "'existing@example.invalid', '保留数据', 7)");
        execute(schema, "INSERT INTO products (title, cover, price, category) VALUES "
                + "('已有演示商品', "
                + "'/DEMO-DATA/GENERATED/SEED-404/BOOKS/BOOK-0001-COVER.SVG  ', "
                + "9.90, 'literature')");
        execute(schema, "INSERT INTO product_content_images (image_url, product_id) "
                + "SELECT '/DEMO-DATA/GENERATED/SEED-404/BOOKS/BOOK-0001-DETAIL-01.SVG  ', "
                + "product_id FROM products WHERE title = '已有演示商品'");
        execute(schema, "INSERT INTO product_specifications (item, value, product_id) "
                + "SELECT '作者  ', '用户保留的作者值', product_id FROM products "
                + "WHERE title = '已有演示商品'");

        DemoImportSummary summary = importer(schema, DemoImportHook.none())
                .importData(config(24, 8, 404L));

        assertEquals(23, summary.createdProducts());
        assertEquals(9, summary.createdUsers());
        assertEquals(24, count(schema, "products"));
        assertEquals(10, count(schema, "account"));
        assertEquals(24, count(schema, "stockpile"));
        assertEquals(24, count(schema, "product_content_images"));
        assertEquals(96, count(schema, "product_specifications"));
        assertEquals(0, scalarInt(schema,
                "SELECT COUNT(*) FROM account WHERE BINARY username = 'demo_user'"));
        assertEquals(1, scalarInt(schema,
                "SELECT COUNT(*) FROM account WHERE BINARY username = 'Demo_User  '"));
    }

    @Test
    void errorAfterProductWritesStillRollsBackBeforeAutoCommitIsRestored() throws Exception {
        String schema = createMigratedSchema();
        DemoDataImporter importer = importer(schema, connection -> {
            throw new AssertionError("intentional DATA-001 severe failure test");
        });

        assertThrows(AssertionError.class,
                () -> importer.importData(config(24, 8, 404L)));

        assertEquals(0, count(schema, "products"));
        assertEquals(0, count(schema, "stockpile"));
        assertEquals(0, count(schema, "account"));
    }

    @Test
    void equivalentDuplicateMarkersAreRejectedAndEarlierWritesAreRolledBack() throws Exception {
        String schema = createMigratedSchema();
        execute(schema, "INSERT INTO account "
                + "(username, password, name, role) VALUES "
                + "('demo_user', 'first-hash', '第一条', 'USER'), "
                + "('Demo_User', 'second-hash', '第二条', 'USER')");

        assertThrows(IllegalStateException.class,
                () -> importer(schema, DemoImportHook.none())
                        .importData(config(24, 8, 404L)));

        assertEquals(0, count(schema, "products"));
        assertEquals(0, count(schema, "stockpile"));
        assertEquals(2, count(schema, "account"));
    }

    @Test
    void accentEquivalentMarkerIsRejectedInsteadOfCreatingAConflictingUsername() throws Exception {
        String schema = createMigratedSchema();
        execute(schema, "INSERT INTO account "
                + "(username, password, name, role) VALUES "
                + "('démó_user', 'existing-hash', '带重音字符的已有用户', 'USER')");

        assertThrows(IllegalStateException.class,
                () -> importer(schema, DemoImportHook.none())
                        .importData(config(24, 8, 404L)));

        assertEquals(0, count(schema, "products"));
        assertEquals(1, count(schema, "account"));
        assertEquals(0, scalarInt(schema,
                "SELECT COUNT(*) FROM account WHERE BINARY username = 'demo_user'"));
    }

    @Test
    void concurrentImportersAreSerializedAndDoNotDuplicateRecords() throws Exception {
        String schema = createMigratedSchema();
        CountDownLatch firstReachedHook = new CountDownLatch(1);
        CountDownLatch allowFirstToCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondReachedHook = new CountDownLatch(1);
        DemoDataImporter firstImporter = importer(schema, connection -> {
            firstReachedHook.countDown();
            try {
                if (!allowFirstToCommit.await(5, TimeUnit.SECONDS)) {
                    throw new SQLException("timed out waiting to finish first import");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("first import interrupted", exception);
            }
        });
        DemoDataImporter secondImporter = importer(schema,
                connection -> secondReachedHook.countDown());
        DemoDataImportConfig config = config(24, 8, 404L);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<DemoImportSummary> first = executor.submit(() -> firstImporter.importData(config));
            assertTrue(firstReachedHook.await(5, TimeUnit.SECONDS));
            Future<DemoImportSummary> second = executor.submit(() -> {
                secondStarted.countDown();
                return secondImporter.importData(config);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertTrue(waitForNamedLockWaiter(schema, 5, TimeUnit.SECONDS));
            assertEquals(1, secondReachedHook.getCount());
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));

            allowFirstToCommit.countDown();
            assertEquals(24, first.get(10, TimeUnit.SECONDS).createdProducts());
            assertEquals(0, second.get(10, TimeUnit.SECONDS).createdProducts());
            assertTrue(secondReachedHook.await(1, TimeUnit.SECONDS));
        } finally {
            allowFirstToCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(24, count(schema, "products"));
        assertEquals(24, count(schema, "stockpile"));
        assertEquals(10, count(schema, "account"));
        assertEquals(6, count(schema, "advertisements"));
    }

    private boolean waitForNamedLockWaiter(String schema, long timeout, TimeUnit unit)
            throws Exception {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            int waiters = scalarInt(schema,
                    "SELECT COUNT(*) FROM information_schema.PROCESSLIST "
                            + "WHERE INFO LIKE 'SELECT GET_LOCK%' "
                            + "AND INFO LIKE '%tomatomall:data-001%'");
            if (waiters > 0) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private DemoDataImporter importer(String schema, DemoImportHook hook) {
        return DemoDataImporter.forExpectedDatabase(
                dataSource(schema),
                new BCryptPasswordEncoder(),
                schema,
                hook
        );
    }

    private DemoDataImportConfig config(int books, int users, long seed) {
        return new DemoDataImportConfig(books, users, seed,
                "local-demo-password", tempDirectory);
    }

    private String createMigratedSchema() throws SQLException {
        String schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(serverUrl(), user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        createdSchemas.add(schema);
        Flyway.configure()
                .dataSource(databaseUrl(schema), user, password)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return schema;
    }

    private DataSource dataSource(String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(databaseUrl(schema));
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    private int count(String schema, String table) throws SQLException {
        return scalarInt(schema, "SELECT COUNT(*) FROM " + table);
    }

    private int scalarInt(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String scalarString(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void execute(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), user, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
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

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
