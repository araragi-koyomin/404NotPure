package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.MissingProductCacheEntry;
import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.AdvertisementsRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.AdvertisementsService;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.cache.ProductDetailCache;
import com.example.tomatomall.vo.AdvertisementsVO;
import com.example.tomatomall.vo.ProductVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class ProductCacheIntegrationTest {

    private static final String KEY_PREFIX = "product:detail:v1:";
    private static final String LEGACY_KEY_PREFIX = "advertisement:product:";

    @Autowired
    private ProductService productService;

    @Autowired
    private AdvertisementsService advertisementsService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AdvertisementsRepository advertisementsRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Environment environment;

    @SpyBean
    private ProductDetailCache productDetailCache;

    private final List<Integer> productIds = new ArrayList<>();
    private final List<Integer> advertisementIds = new ArrayList<>();
    private final List<Integer> cacheIds = new ArrayList<>();
    private String marker;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @AfterEach
    void cleanUp() {
        for (Integer advertisementId : advertisementIds) {
            jdbcTemplate.update("delete from advertisements where id = ?", advertisementId);
        }
        for (Integer productId : productIds) {
            jdbcTemplate.update("delete from product_content_images where product_id = ?", productId);
            jdbcTemplate.update("delete from product_specifications where product_id = ?", productId);
            jdbcTemplate.update("delete from stockpile where product_id = ?", productId);
            jdbcTemplate.update("delete from products where product_id = ?", productId);
        }
        for (Integer cacheId : cacheIds) {
            redisTemplate.delete(cacheKey(cacheId));
            redisTemplate.delete(LEGACY_KEY_PREFIX + cacheId);
        }
    }

    @Test
    void cacheMissReadsMysqlAndBackfillsRealRedisWithRandomizedTtl() {
        Product product = createProduct("miss");

        ProductVO result = productService.getProductById(product.getId());

        assertEquals(product.getId(), result.getId());
        Object cached = redisTemplate.opsForValue().get(cacheKey(product.getId()));
        ProductDTO cachedProduct = assertInstanceOf(ProductDTO.class, cached);
        assertEquals(product.getTitle(), cachedProduct.getTitle());
        assertTtlBetween(cacheKey(product.getId()), 1800, 3599);
    }

    @Test
    void cachedProductCanBeReadAfterDatabaseRowIsGone() {
        Product product = createProduct("hit");
        productService.getProductById(product.getId());
        jdbcTemplate.update("delete from products where product_id = ?", product.getId());

        ProductVO cached = productService.getProductById(product.getId());

        assertEquals(product.getTitle(), cached.getTitle());
    }

    @Test
    void missingProductUsesShortLivedMarkerAndReturnsDomainError() {
        int missingProductId = 2_000_000_000 + Math.abs(marker.hashCode() % 100_000_000);
        cacheIds.add(missingProductId);

        TomatoException first = assertThrows(
                TomatoException.class,
                () -> productService.getProductById(missingProductId)
        );
        assertEquals("404", first.getCode());

        Object markerValue = redisTemplate.opsForValue().get(cacheKey(missingProductId));
        assertInstanceOf(MissingProductCacheEntry.class, markerValue);
        assertTtlBetween(cacheKey(missingProductId), 60, 119);

        TomatoException second = assertThrows(
                TomatoException.class,
                () -> productService.getProductById(missingProductId)
        );
        assertEquals("404", second.getCode());
    }

    @Test
    void missingMarkerPreventsDatabaseRequeryUntilMarkerIsRemoved() {
        Product product = createProduct("missing-marker-hit");
        jdbcTemplate.update("delete from products where product_id = ?", product.getId());
        assertThrows(TomatoException.class, () -> productService.getProductById(product.getId()));
        assertInstanceOf(
                MissingProductCacheEntry.class,
                redisTemplate.opsForValue().get(cacheKey(product.getId()))
        );

        jdbcTemplate.update(
                "insert into products (product_id, title, price, rate, description, detail, cover, category) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                product.getId(),
                "reappeared-" + marker,
                new BigDecimal("39.90"),
                4.6,
                "reappeared product",
                "reappeared product detail",
                "/demo/reappeared.png",
                "test"
        );

        assertThrows(TomatoException.class, () -> productService.getProductById(product.getId()));
        redisTemplate.delete(cacheKey(product.getId()));
        assertEquals("reappeared-" + marker, productService.getProductById(product.getId()).getTitle());
    }

    @Test
    void productCreationClearsPreexistingMissingMarkerAfterCommit() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Product created = transactionTemplate.execute(status -> {
            ProductVO request = productRequest("created-after-missing");
            Product saved = productService.createProduct(request);
            productDetailCache.putMissing(saved.getId());
            return saved;
        });
        assertNotNull(created);
        productIds.add(created.getId());
        cacheIds.add(created.getId());

        assertNull(redisTemplate.opsForValue().get(cacheKey(created.getId())));
        assertEquals(created.getTitle(), productService.getProductById(created.getId()).getTitle());
    }

    @Test
    void missingLookupAndConcurrentCreationDoNotLeaveAStaleMissingMarker() throws Exception {
        Product reserved = createProduct("reserved-missing-id");
        jdbcTemplate.update("delete from products where product_id = ?", reserved.getId());
        CountDownLatch missingWriteStarted = new CountDownLatch(1);
        CountDownLatch allowMissingWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            missingWriteStarted.countDown();
            assertTrue(allowMissingWrite.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailCache).putMissing(reserved.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TomatoException> reader = executor.submit(() -> assertThrows(
                    TomatoException.class,
                    () -> productService.getProductById(reserved.getId())
            ));
            assertTrue(missingWriteStarted.await(5, TimeUnit.SECONDS));

            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            CountDownLatch creatorStarted = new CountDownLatch(1);
            Future<?> creator = executor.submit(() -> {
                creatorStarted.countDown();
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update(
                            "insert into products (product_id, title, price, rate, description, detail, cover, category) "
                                    + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                            reserved.getId(),
                            "concurrently-created-" + marker,
                            new BigDecimal("49.90"),
                            4.7,
                            "concurrently created product",
                            "concurrently created product detail",
                            "/demo/concurrently-created.png",
                            "test"
                    );
                    productDetailCache.evictAfterCommit(reserved.getId());
                });
            });
            assertTrue(creatorStarted.await(5, TimeUnit.SECONDS));
            boolean creatorWaitedForMissingLookup;
            try {
                creator.get(500, TimeUnit.MILLISECONDS);
                creatorWaitedForMissingLookup = false;
            } catch (TimeoutException expected) {
                creatorWaitedForMissingLookup = true;
            } finally {
                allowMissingWrite.countDown();
            }

            reader.get(5, TimeUnit.SECONDS);
            creator.get(5, TimeUnit.SECONDS);
            assertTrue(creatorWaitedForMissingLookup, "创建应等待缺失查询完成并随后清除旧标记");
            assertNull(redisTemplate.opsForValue().get(cacheKey(reserved.getId())));
            assertEquals(
                    "concurrently-created-" + marker,
                    productService.getProductById(reserved.getId()).getTitle()
            );
        } finally {
            allowMissingWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void wrongCacheTypeIsRemovedAndRebuiltFromMysql() {
        Product product = createProduct("wrong-type");
        redisTemplate.opsForValue().set(cacheKey(product.getId()), "not-a-product", 30, TimeUnit.MINUTES);

        ProductVO result = productService.getProductById(product.getId());

        assertEquals(product.getTitle(), result.getTitle());
        assertInstanceOf(ProductDTO.class, redisTemplate.opsForValue().get(cacheKey(product.getId())));
    }

    @Test
    void productUpdateInvalidatesCachedDetailAfterTransactionCommit() {
        Product product = createProduct("update");
        productService.getProductById(product.getId());
        assertNotNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));

        ProductVO update = new ProductVO();
        update.setId(product.getId());
        update.setTitle("updated-" + marker);
        productService.update(update);

        assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
        assertEquals("updated-" + marker, productService.getProductById(product.getId()).getTitle());
    }

    @Test
    void rolledBackProductUpdateKeepsDatabaseAndCacheAtPreviousValue() {
        Product product = createProduct("update-rollback");
        ProductVO original = productService.getProductById(product.getId());
        assertNotNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            ProductVO update = new ProductVO();
            update.setId(product.getId());
            update.setTitle("must-rollback-" + marker);
            productService.update(update);
            assertNotNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
            status.setRollbackOnly();
        });

        String persistedTitle = jdbcTemplate.queryForObject(
                "select title from products where product_id = ?",
                String.class,
                product.getId()
        );
        assertEquals(original.getTitle(), persistedTitle);
        ProductDTO cached = assertInstanceOf(
                ProductDTO.class,
                redisTemplate.opsForValue().get(cacheKey(product.getId()))
        );
        assertEquals(original.getTitle(), cached.getTitle());
    }

    @Test
    void cacheMissFillCannotOverwriteAConcurrentProductUpdate() throws Exception {
        Product product = createProduct("concurrent-fill");
        CountDownLatch cacheWriteStarted = new CountDownLatch(1);
        CountDownLatch allowCacheWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProductDTO value = invocation.getArgument(1);
            if (value.getId() == product.getId()) {
                cacheWriteStarted.countDown();
                assertTrue(allowCacheWrite.await(5, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(productDetailCache).putProduct(eq(product.getId()), any(ProductDTO.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductVO> reader = executor.submit(() -> productService.getProductById(product.getId()));
            assertTrue(cacheWriteStarted.await(5, TimeUnit.SECONDS));

            ProductVO update = new ProductVO();
            update.setId(product.getId());
            update.setTitle("concurrent-updated-" + marker);
            CountDownLatch writerStarted = new CountDownLatch(1);
            Future<String> writer = executor.submit(() -> {
                writerStarted.countDown();
                return productService.update(update);
            });
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
            boolean writerWaitedForReader;
            try {
                writer.get(500, TimeUnit.MILLISECONDS);
                writerWaitedForReader = false;
            } catch (TimeoutException expected) {
                writerWaitedForReader = true;
            } finally {
                allowCacheWrite.countDown();
            }

            reader.get(5, TimeUnit.SECONDS);
            writer.get(5, TimeUnit.SECONDS);
            assertTrue(writerWaitedForReader, "商品更新应等待正在回填旧快照的事务完成");
            assertEquals(
                    "concurrent-updated-" + marker,
                    jdbcTemplate.queryForObject(
                            "select title from products where product_id = ?",
                            String.class,
                            product.getId()
                    )
            );
            assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
        } finally {
            allowCacheWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void productDeleteInvalidatesCachedDetailAfterTransactionCommit() {
        Product product = createProduct("delete");
        productService.getProductById(product.getId());
        assertNotNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));

        productService.delete(product.getId());

        assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
        assertThrows(TomatoException.class, () -> productService.getProductById(product.getId()));
    }

    @Test
    void advertisementCreationPrewarmsTheGeneralProductDetailCache() {
        Product product = createProduct("advertisement-create");
        AdvertisementsVO request = advertisementFor(product.getId(), "create");

        AdvertisementsVO created = advertisementsService.createAdvertisement(request);
        advertisementIds.add(created.getId());

        ProductDTO cached = assertInstanceOf(
                ProductDTO.class,
                redisTemplate.opsForValue().get(cacheKey(product.getId()))
        );
        assertEquals(product.getTitle(), cached.getTitle());
        assertTtlBetween(cacheKey(product.getId()), 1800, 3599);
    }

    @Test
    void advertisementPrewarmCannotOverwriteAConcurrentProductUpdate() throws Exception {
        Product product = createProduct("concurrent-advertisement-prewarm");
        CountDownLatch cacheWriteStarted = new CountDownLatch(1);
        CountDownLatch allowCacheWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProductDTO value = invocation.getArgument(1);
            if (value.getId() == product.getId()) {
                cacheWriteStarted.countDown();
                assertTrue(allowCacheWrite.await(5, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(productDetailCache).putProduct(eq(product.getId()), any(ProductDTO.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdvertisementsVO> advertisement = executor.submit(
                    () -> advertisementsService.createAdvertisement(
                            advertisementFor(product.getId(), "concurrent-prewarm")
                    )
            );
            assertTrue(cacheWriteStarted.await(5, TimeUnit.SECONDS));

            ProductVO update = new ProductVO();
            update.setId(product.getId());
            update.setTitle("advertisement-race-updated-" + marker);
            CountDownLatch writerStarted = new CountDownLatch(1);
            Future<String> writer = executor.submit(() -> {
                writerStarted.countDown();
                return productService.update(update);
            });
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
            boolean writerWaitedForPrewarm;
            try {
                writer.get(500, TimeUnit.MILLISECONDS);
                writerWaitedForPrewarm = false;
            } catch (TimeoutException expected) {
                writerWaitedForPrewarm = true;
            } finally {
                allowCacheWrite.countDown();
            }

            AdvertisementsVO created = advertisement.get(5, TimeUnit.SECONDS);
            advertisementIds.add(created.getId());
            writer.get(5, TimeUnit.SECONDS);
            assertTrue(writerWaitedForPrewarm, "商品更新应等待广告预热读取事务完成");
            assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
        } finally {
            allowCacheWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void advertisementProductChangeInvalidatesOldCacheAndPrewarmsNewProduct() {
        Product oldProduct = createProduct("advertisement-old");
        Product newProduct = createProduct("advertisement-new");
        AdvertisementsVO created = advertisementsService.createAdvertisement(
                advertisementFor(oldProduct.getId(), "update")
        );
        advertisementIds.add(created.getId());
        assertNotNull(redisTemplate.opsForValue().get(cacheKey(oldProduct.getId())));

        AdvertisementsVO update = new AdvertisementsVO();
        update.setId(created.getId());
        update.setProductId(newProduct.getId());
        advertisementsService.updateAdvertisement(update);

        assertNull(redisTemplate.opsForValue().get(cacheKey(oldProduct.getId())));
        ProductDTO cachedNewProduct = assertInstanceOf(
                ProductDTO.class,
                redisTemplate.opsForValue().get(cacheKey(newProduct.getId()))
        );
        assertEquals(newProduct.getTitle(), cachedNewProduct.getTitle());
    }

    @Test
    void advertisementDeleteInvalidatesItsProductDetailCache() {
        Product product = createProduct("advertisement-delete");
        AdvertisementsVO created = advertisementsService.createAdvertisement(
                advertisementFor(product.getId(), "delete")
        );
        advertisementIds.add(created.getId());
        assertNotNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));

        advertisementsService.deleteAdvertisement(created.getId());

        assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
    }

    @Test
    void rolledBackAdvertisementProductChangeKeepsOldCacheAndDoesNotPrewarmNewProduct() {
        Product oldProduct = createProduct("advertisement-rollback-old");
        Product newProduct = createProduct("advertisement-rollback-new");
        AdvertisementsVO created = advertisementsService.createAdvertisement(
                advertisementFor(oldProduct.getId(), "rollback")
        );
        advertisementIds.add(created.getId());
        assertInstanceOf(
                ProductDTO.class,
                redisTemplate.opsForValue().get(cacheKey(oldProduct.getId()))
        );
        redisTemplate.delete(cacheKey(newProduct.getId()));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            AdvertisementsVO update = new AdvertisementsVO();
            update.setId(created.getId());
            update.setProductId(newProduct.getId());
            advertisementsService.updateAdvertisement(update);
            status.setRollbackOnly();
        });

        assertEquals(
                oldProduct.getId(),
                jdbcTemplate.queryForObject(
                        "select product_id from advertisements where id = ?",
                        Integer.class,
                        created.getId()
                )
        );
        assertInstanceOf(
                ProductDTO.class,
                redisTemplate.opsForValue().get(cacheKey(oldProduct.getId()))
        );
        assertNull(redisTemplate.opsForValue().get(cacheKey(newProduct.getId())));
    }

    @Test
    void unusedRedisRepositoryScanningIsDisabled() {
        assertEquals("false", environment.getProperty("spring.data.redis.repositories.enabled"));
    }

    private Product createProduct(String purpose) {
        Product product = Product.builder()
                .title("cache-" + purpose + "-" + marker)
                .price(new BigDecimal("29.90"))
                .rate(4.8)
                .description("cache integration test")
                .detail("cache integration test detail")
                .cover("/demo/cache-test.png")
                .category("test")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
        product = productRepository.saveAndFlush(product);
        productIds.add(product.getId());
        cacheIds.add(product.getId());
        redisTemplate.delete(cacheKey(product.getId()));
        redisTemplate.delete(LEGACY_KEY_PREFIX + product.getId());
        return product;
    }

    private AdvertisementsVO advertisementFor(int productId, String purpose) {
        AdvertisementsVO advertisement = new AdvertisementsVO();
        advertisement.setTitle("cache-ad-" + purpose + "-" + marker);
        advertisement.setContent("cache integration advertisement");
        advertisement.setImgUrl("/demo/cache-ad-test.png");
        advertisement.setProductId(productId);
        return advertisement;
    }

    private ProductVO productRequest(String purpose) {
        ProductVO product = new ProductVO();
        product.setTitle("cache-" + purpose + "-" + marker);
        product.setPrice(new BigDecimal("29.90"));
        product.setRate(4.8);
        product.setDescription("cache integration test");
        product.setDetail("cache integration test detail");
        product.setCover("/demo/cache-test.png");
        product.setCategory("test");
        product.setSpecifications(new ArrayList<>());
        product.setContentImages(new ArrayList<>());
        return product;
    }

    private void assertTtlBetween(String key, long minimum, long maximum) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl >= minimum - 1, "TTL should be at least " + (minimum - 1) + " seconds but was " + ttl);
        assertTrue(ttl <= maximum, "TTL should be at most " + maximum + " seconds but was " + ttl);
    }

    private String cacheKey(int productId) {
        return KEY_PREFIX + productId;
    }
}
