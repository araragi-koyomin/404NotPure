package com.example.tomatomall.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSchemaMigrationIntegrationTest {

    private static final String SCHEMA_PREFIX = "tomato_payment_migration_test_";

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
                    throw new IllegalStateException("拒绝删除不属于迁移测试的数据库: " + schema);
                }
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void applicationConfigurationKeepsAutomaticBaselineDisabledByDefault() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        PropertySource<?> application = loader.load(
                "application",
                new ClassPathResource("application.yml")
        ).get(0);

        assertEquals(
                "${FLYWAY_BASELINE_ON_MIGRATE:false}",
                application.getProperty("spring.flyway.baseline-on-migrate")
        );
    }

    @Test
    void refusesToModifyUnknownNonEmptySchemaWithoutExplicitBaseline() throws SQLException {
        String schema = createTemporarySchema();
        execute(schema, "CREATE TABLE unrelated_table (id INT NOT NULL PRIMARY KEY)");

        assertThrows(FlywayException.class, () -> flyway(schema, null, false).migrate());

        assertFalse(tableExists(schema, "orders"));
        assertFalse(columnExists(schema, "orders", "paid_time"));
    }

    @Test
    void migratesAnEmptyDatabaseAndIsRepeatable() throws SQLException {
        String schema = createTemporarySchema();
        Flyway flyway = flyway(schema, null, true);

        MigrateResult firstRun = flyway.migrate();

        assertEquals(2, firstRun.migrationsExecuted);
        assertTrue(tableExists(schema, "orders"));
        assertTrue(tableExists(schema, "stockpile"));
        assertTrue(columnExists(schema, "orders", "paid_time"));
        assertTrue(columnExists(schema, "orders", "alipay_trade_no"));
        assertTrue(uniqueIndexExists(schema, "orders", "alipay_trade_no"));
        validateJpaSchema(schema);

        MigrateResult secondRun = flyway.migrate();
        assertEquals(0, secondRun.migrationsExecuted);
    }

    @Test
    void baselinesAnExistingSchemaThenPreservesOrdersWhileAddingPaymentColumns() throws SQLException {
        String schema = createTemporarySchema();

        createJpaSchema(schema);
        execute(schema, "ALTER TABLE orders DROP COLUMN paid_time, DROP COLUMN alipay_trade_no");
        insertLegacyOrder(schema);

        MigrateResult upgrade = flyway(schema, null, true).migrate();

        assertEquals(1, upgrade.migrationsExecuted);
        assertEquals(1, count(schema, "SELECT COUNT(*) FROM orders WHERE order_id = 7001"));
        assertEquals(1, count(schema,
                "SELECT COUNT(*) FROM orders WHERE order_id = 7001 "
                        + "AND paid_time IS NULL AND alipay_trade_no IS NULL"));
        assertTrue(uniqueIndexExists(schema, "orders", "alipay_trade_no"));
        validateJpaSchema(schema);
    }

    private Flyway flyway(String schema, MigrationVersion target, boolean baselineOnMigrate) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(databaseUrl(schema), user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion("1"));
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void createJpaSchema(String schema) {
        initializeJpa(schema, "create-only");
    }

    private void validateJpaSchema(String schema) {
        initializeJpa(schema, "validate");
    }

    private void initializeJpa(String schema, String schemaAction) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(databaseUrl(schema));
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.MySQL8Dialect");

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.tomatomall.po", "com.example.tomatomall.dto");
        factory.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", schemaAction);
        properties.setProperty(
                "hibernate.implicit_naming_strategy",
                SpringImplicitNamingStrategy.class.getName()
        );
        properties.setProperty(
                "hibernate.physical_naming_strategy",
                "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy"
        );
        factory.setJpaProperties(properties);

        try {
            factory.afterPropertiesSet();
            EntityManagerFactory entityManagerFactory = factory.getObject();
            if (entityManagerFactory == null) {
                throw new IllegalStateException("JPA EntityManagerFactory initialization failed");
            }
        } finally {
            factory.destroy();
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

    private void insertLegacyOrder(String schema) throws SQLException {
        execute(schema,
                "INSERT INTO account "
                        + "(id, name, password, role, username) "
                        + "VALUES (7001, 'migration-test', 'not-a-real-password', 'USER', 'migration-test')");
        execute(schema,
                "INSERT INTO orders "
                        + "(order_id, payment_method, status, total_amount, user_id) "
                        + "VALUES (7001, 'ALIPAY', 'PENDING', 12.34, 7001)");
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

    private boolean tableExists(String schema, String table) throws SQLException {
        return metadataCount(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name = ?",
                schema,
                table
        ) == 1;
    }

    private boolean columnExists(String schema, String table, String column) throws SQLException {
        return metadataCount(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                schema,
                table,
                column
        ) == 1;
    }

    private boolean uniqueIndexExists(String schema, String table, String column) throws SQLException {
        return metadataCount(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ? AND non_unique = 0",
                schema,
                table,
                column
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
