package com.example.tomatomall.demo;

import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DemoDataImporter {

    private static final String IMPORT_LOCK_NAME = "tomatomall:data-001";
    private static final int LOCK_TIMEOUT_SECONDS = 10;
    private static final int QUERY_CHUNK_SIZE = 400;

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final String expectedDatabaseName;
    private final DemoImportHook importHook;
    private final DemoDataGenerator generator = new DemoDataGenerator();

    public DemoDataImporter(DataSource dataSource,
                            PasswordEncoder passwordEncoder,
                            DemoImportHook importHook) {
        this(dataSource, passwordEncoder, DemoDatabaseGuard.DEMO_DATABASE_NAME, importHook);
    }

    private DemoDataImporter(DataSource dataSource,
                             PasswordEncoder passwordEncoder,
                             String expectedDatabaseName,
                             DemoImportHook importHook) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.expectedDatabaseName = expectedDatabaseName;
        this.importHook = importHook;
    }

    static DemoDataImporter forExpectedDatabase(DataSource dataSource,
                                                 PasswordEncoder passwordEncoder,
                                                 String expectedDatabaseName,
                                                 DemoImportHook importHook) {
        return new DemoDataImporter(dataSource, passwordEncoder, expectedDatabaseName, importHook);
    }

    public DemoImportSummary importData(DemoDataImportConfig config) {
        DemoDataset dataset = generator.generate(config);
        try (Connection connection = dataSource.getConnection()) {
            DemoDatabaseGuard.requireExpectedDatabase(currentDatabase(connection), expectedDatabaseName);
            acquireImportLock(connection);
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean committed = false;
            Throwable failure = null;
            try {
                connection.setAutoCommit(false);
                ProductImportResult products = importProducts(connection, dataset.books());
                importHook.afterProductsInserted(connection);
                int stockpiles = importStockpiles(connection, dataset.books(), products.idsByOrdinal());
                int specifications = importSpecifications(connection, dataset.books(), products.idsByOrdinal());
                int images = importContentImages(connection, dataset.books(), products.idsByOrdinal());
                int users = importUsers(connection, dataset.users(), config.rawPassword());
                int advertisements = importAdvertisements(
                        connection,
                        dataset.advertisements(),
                        products.idsByOrdinal()
                );
                connection.commit();
                committed = true;
                return new DemoImportSummary(
                        products.createdCount(),
                        stockpiles,
                        specifications,
                        images,
                        users,
                        advertisements
                );
            } catch (Throwable throwable) {
                failure = throwable;
                if (throwable instanceof Error error) {
                    throw error;
                }
                if (throwable instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }
                throw new DemoDataImportException("DATA-001 导入失败，数据库事务已回滚", throwable);
            } finally {
                if (!committed) {
                    rollbackSafely(connection, failure);
                }
                releaseImportLock(connection);
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DemoDataImportException("无法连接或操作 DATA-001 演示数据库", exception);
        }
    }

    private ProductImportResult importProducts(Connection connection, List<DemoBook> books)
            throws SQLException {
        List<String> covers = books.stream().map(DemoBook::coverUrl).toList();
        Map<String, Integer> existingByCover = queryUniqueValues(
                connection,
                "products",
                "cover",
                "product_id",
                covers
        );
        Map<Integer, Integer> idsByOrdinal = new LinkedHashMap<>();
        int created = 0;
        String sql = "INSERT INTO products "
                + "(title, price, rate, description, detail, cover, category) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (DemoBook book : books) {
                Integer existingId = existingByCover.get(databaseIdentity(book.coverUrl()));
                if (existingId != null) {
                    idsByOrdinal.put(book.ordinal(), existingId);
                    continue;
                }
                statement.setString(1, book.title());
                statement.setBigDecimal(2, book.price());
                statement.setDouble(3, book.rate());
                statement.setString(4, book.description());
                statement.setString(5, book.detail());
                statement.setString(6, book.coverUrl());
                statement.setString(7, book.category());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("创建演示商品时影响行数不是 1: " + book.ordinal());
                }
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new SQLException("创建演示商品后没有返回主键: " + book.ordinal());
                    }
                    idsByOrdinal.put(book.ordinal(), generatedKeys.getInt(1));
                }
                created++;
            }
        }
        return new ProductImportResult(idsByOrdinal, created);
    }

    private int importStockpiles(Connection connection,
                                 List<DemoBook> books,
                                 Map<Integer, Integer> productIds) throws SQLException {
        List<Integer> ids = new ArrayList<>(productIds.values());
        Set<Integer> existing = queryUniqueProductIds(connection, "stockpile", ids);
        int created = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO stockpile (amount, frozen, product_id) VALUES (?, 0, ?)")) {
            for (DemoBook book : books) {
                int productId = productIds.get(book.ordinal());
                if (existing.contains(productId)) {
                    continue;
                }
                statement.setInt(1, book.stockAmount());
                statement.setInt(2, productId);
                statement.addBatch();
                created++;
            }
            statement.executeBatch();
        }
        return created;
    }

    private int importSpecifications(Connection connection,
                                     List<DemoBook> books,
                                     Map<Integer, Integer> productIds) throws SQLException {
        Map<Integer, Set<String>> existing = queryExistingChildValues(
                connection,
                "product_specifications",
                "item",
                new ArrayList<>(productIds.values())
        );
        int created = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO product_specifications (item, value, product_id) VALUES (?, ?, ?)")) {
            for (DemoBook book : books) {
                int productId = productIds.get(book.ordinal());
                Set<String> existingItems = existing.getOrDefault(productId, Set.of());
                for (DemoSpecification specification : book.specifications()) {
                    if (existingItems.contains(databaseIdentity(specification.item()))) {
                        continue;
                    }
                    statement.setString(1, specification.item());
                    statement.setString(2, specification.value());
                    statement.setInt(3, productId);
                    statement.addBatch();
                    created++;
                }
            }
            statement.executeBatch();
        }
        return created;
    }

    private int importContentImages(Connection connection,
                                    List<DemoBook> books,
                                    Map<Integer, Integer> productIds) throws SQLException {
        Map<Integer, Set<String>> existing = queryExistingChildValues(
                connection,
                "product_content_images",
                "image_url",
                new ArrayList<>(productIds.values())
        );
        int created = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO product_content_images (image_url, product_id) VALUES (?, ?)")) {
            for (DemoBook book : books) {
                int productId = productIds.get(book.ordinal());
                Set<String> existingUrls = existing.getOrDefault(productId, Set.of());
                for (String imageUrl : book.contentImageUrls()) {
                    if (existingUrls.contains(databaseIdentity(imageUrl))) {
                        continue;
                    }
                    statement.setString(1, imageUrl);
                    statement.setInt(2, productId);
                    statement.addBatch();
                    created++;
                }
            }
            statement.executeBatch();
        }
        return created;
    }

    private int importUsers(Connection connection, List<DemoUser> users, String rawPassword)
            throws SQLException {
        List<String> usernames = users.stream().map(DemoUser::username).toList();
        Map<String, Integer> existing = queryUniqueValues(
                connection,
                "account",
                "username",
                "id",
                usernames
        );
        String passwordHash = passwordEncoder.encode(rawPassword);
        int created = 0;
        String sql = "INSERT INTO account "
                + "(username, password, name, role, avatar, telephone, email, location, points) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DemoUser demoUser : users) {
                if (existing.containsKey(databaseIdentity(demoUser.username()))) {
                    continue;
                }
                statement.setString(1, demoUser.username());
                statement.setString(2, passwordHash);
                statement.setString(3, demoUser.name());
                statement.setString(4, demoUser.role());
                statement.setNull(5, Types.VARCHAR);
                statement.setNull(6, Types.VARCHAR);
                statement.setString(7, demoUser.email());
                statement.setString(8, demoUser.location());
                statement.setInt(9, demoUser.points());
                statement.addBatch();
                created++;
            }
            statement.executeBatch();
        }
        return created;
    }

    private int importAdvertisements(Connection connection,
                                     List<DemoAdvertisement> advertisements,
                                     Map<Integer, Integer> productIds) throws SQLException {
        List<String> urls = advertisements.stream().map(DemoAdvertisement::imageUrl).toList();
        Map<String, Integer> existing = queryUniqueValues(
                connection,
                "advertisements",
                "image_url",
                "id",
                urls
        );
        int created = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO advertisements (title, content, image_url, product_id) "
                        + "VALUES (?, ?, ?, ?)")) {
            for (DemoAdvertisement advertisement : advertisements) {
                if (existing.containsKey(databaseIdentity(advertisement.imageUrl()))) {
                    continue;
                }
                statement.setString(1, advertisement.title());
                statement.setString(2, advertisement.content());
                statement.setString(3, advertisement.imageUrl());
                statement.setInt(4, productIds.get(advertisement.bookOrdinal()));
                statement.addBatch();
                created++;
            }
            statement.executeBatch();
        }
        return created;
    }

    private Map<String, Integer> queryUniqueValues(Connection connection,
                                                    String table,
                                                    String valueColumn,
                                                    String idColumn,
                                                    List<String> values) throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        List<String> identities = values.stream().map(DemoDataImporter::databaseIdentity).toList();
        Set<String> requestedIdentities = new HashSet<>(identities);
        for (List<String> chunk : chunks(identities)) {
            String sql = "SELECT " + idColumn + ", " + valueColumn + " FROM " + table
                    + " WHERE LOWER(RTRIM(" + valueColumn + ")) IN ("
                    + placeholders(chunk.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setParameters(statement, chunk);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String value = resultSet.getString(valueColumn);
                        String identity = databaseIdentity(value);
                        if (!requestedIdentities.contains(identity)) {
                            throw new IllegalStateException(
                                    "数据库存在排序规则等价但标识不同的记录，拒绝继续: "
                                            + table + "." + valueColumn
                            );
                        }
                        Integer previous = result.put(
                                identity,
                                resultSet.getInt(idColumn)
                        );
                        if (previous != null) {
                            throw new IllegalStateException(
                                    "DATA-001 标识存在重复记录，拒绝继续: " + table + "." + valueColumn
                            );
                        }
                    }
                }
            }
        }
        return result;
    }

    private Set<Integer> queryUniqueProductIds(Connection connection,
                                               String table,
                                               List<Integer> productIds) throws SQLException {
        Set<Integer> result = new HashSet<>();
        for (List<Integer> chunk : chunks(productIds)) {
            String sql = "SELECT product_id, COUNT(*) AS row_count FROM " + table
                    + " WHERE product_id IN (" + placeholders(chunk.size()) + ") GROUP BY product_id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setParameters(statement, chunk);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        if (resultSet.getInt("row_count") != 1) {
                            throw new IllegalStateException(
                                    "一个演示商品存在多条库存记录，拒绝继续: "
                                            + resultSet.getInt("product_id")
                            );
                        }
                        result.add(resultSet.getInt("product_id"));
                    }
                }
            }
        }
        return result;
    }

    private Map<Integer, Set<String>> queryExistingChildValues(Connection connection,
                                                               String table,
                                                               String valueColumn,
                                                               List<Integer> productIds)
            throws SQLException {
        Map<Integer, Set<String>> result = new HashMap<>();
        for (List<Integer> chunk : chunks(productIds)) {
            String sql = "SELECT product_id, " + valueColumn + ", COUNT(*) AS row_count FROM " + table
                    + " WHERE product_id IN (" + placeholders(chunk.size()) + ") "
                    + "GROUP BY product_id, " + valueColumn;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setParameters(statement, chunk);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        if (resultSet.getInt("row_count") != 1) {
                            throw new IllegalStateException(
                                    "演示商品子记录存在重复值，拒绝继续: " + table
                            );
                        }
                        Set<String> values = result.computeIfAbsent(
                                resultSet.getInt("product_id"),
                                ignored -> new HashSet<>()
                        );
                        if (!values.add(databaseIdentity(resultSet.getString(valueColumn)))) {
                            throw new IllegalStateException(
                                    "演示商品子记录存在等价重复值，拒绝继续: " + table
                            );
                        }
                    }
                }
            }
        }
        return result;
    }

    private String currentDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void acquireImportLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, IMPORT_LOCK_NAME);
            statement.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException("另一个 DATA-001 导入仍在运行，请稍后重试");
                }
            }
        }
    }

    private void releaseImportLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, IMPORT_LOCK_NAME);
            statement.executeQuery();
        } catch (SQLException ignored) {
            // Connection close will release the named lock. Do not hide the original import result.
        }
    }

    private void rollbackSafely(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            if (original != null) {
                original.addSuppressed(rollbackFailure);
            } else {
                throw new DemoDataImportException("DATA-001 回滚失败", rollbackFailure);
            }
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
            // The connection is closed immediately afterwards.
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static String databaseIdentity(String value) {
        return value.stripTrailing().toLowerCase(Locale.ROOT);
    }

    private static void setParameters(PreparedStatement statement, List<?> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setObject(index + 1, values.get(index));
        }
    }

    private static <T> List<List<T>> chunks(List<T> values) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += QUERY_CHUNK_SIZE) {
            chunks.add(values.subList(start, Math.min(values.size(), start + QUERY_CHUNK_SIZE)));
        }
        return chunks;
    }

    private record ProductImportResult(Map<Integer, Integer> idsByOrdinal, int createdCount) {
    }
}
