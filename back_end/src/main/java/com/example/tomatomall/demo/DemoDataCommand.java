package com.example.tomatomall.demo;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Path;

public final class DemoDataCommand {

    private DemoDataCommand() {
    }

    public static void main(String[] args) throws Exception {
        String databaseName = requiredEnvironment("DB_NAME");
        DemoDatabaseGuard.requireExactDemoDatabase(databaseName);

        String host = requiredEnvironment("DB_HOST");
        String port = requiredEnvironment("DB_PORT");
        String user = requiredEnvironment("DB_USER");
        String password = requiredEnvironment("DB_PASSWORD");
        String demoPassword = requiredEnvironment("TOMATOMALL_DEMO_PASSWORD");
        int bookCount = integerEnvironment(
                "DEMO_DATA_BOOK_COUNT",
                DemoDataImportConfig.DEFAULT_BOOK_COUNT
        );
        int userCount = integerEnvironment(
                "DEMO_DATA_USER_COUNT",
                DemoDataImportConfig.DEFAULT_USER_COUNT
        );
        long seed = longEnvironment("DEMO_DATA_SEED", DemoDataImportConfig.DEFAULT_SEED);
        Path assetDirectory = Path.of(requiredEnvironment("TOMATOMALL_DEMO_ASSET_DIR"));
        DemoDataImportConfig config = new DemoDataImportConfig(
                bookCount,
                userCount,
                seed,
                demoPassword,
                assetDirectory
        );

        String databaseUrl = databaseUrl(host, port, databaseName);
        Flyway.configure()
                .dataSource(databaseUrl, user, password)
                .defaultSchema(databaseName)
                .schemas(databaseName)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();

        DemoDataset dataset = new DemoDataGenerator().generate(config);
        DemoAssetSummary assetSummary = new DemoAssetWriter().write(dataset, assetDirectory);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(databaseUrl);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        DemoDataImporter importer = new DemoDataImporter(
                dataSource,
                new BCryptPasswordEncoder(),
                DemoImportHook.none()
        );
        DemoImportSummary importSummary = importer.importData(config);

        System.out.printf(
                "DATA-001 完成：新增商品 %d、库存 %d、规格 %d、详情图 %d、用户 %d、广告 %d；"
                        + "本地 SVG 封面 %d、详情图 %d、广告图 %d。%n",
                importSummary.createdProducts(),
                importSummary.createdStockpiles(),
                importSummary.createdSpecifications(),
                importSummary.createdContentImages(),
                importSummary.createdUsers(),
                importSummary.createdAdvertisements(),
                assetSummary.coverCount(),
                assetSummary.detailImageCount(),
                assetSummary.advertisementCount()
        );
        System.out.println("数据库已有的 DATA-001 记录、密码、资料和库存没有被覆盖。");
    }

    private static String databaseUrl(String host, String port, String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false";
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少必需的环境变量 " + name);
        }
        return value.trim();
    }

    private static int integerEnvironment(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : Integer.parseInt(value.trim());
    }

    private static long longEnvironment(String name, long fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : Long.parseLong(value.trim());
    }
}
