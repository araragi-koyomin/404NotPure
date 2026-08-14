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
import com.example.tomatomall.service.cache.ProductCacheResilience;
import com.example.tomatomall.service.cache.ProductCacheSingleFlight;
import com.example.tomatomall.service.cache.ProductCacheSingleFlightInterruptedException;
import com.example.tomatomall.service.cache.ProductCacheSingleFlightTimeoutException;
import com.example.tomatomall.service.cache.ProductDetailDatabaseLoader;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.aop.support.AopUtils;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

@SpringBootTest(properties = "tomatomall.cache.product-detail.single-flight.wait-timeout=5s")
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

    @SpyBean
    private ProductDetailDatabaseLoader productDetailDatabaseLoader;

    @Autowired
    private ProductCacheSingleFlight productCacheSingleFlight;

    @SpyBean
    private ProductCacheResilience productCacheResilience;

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
    void databaseLoaderIsARealTransactionalProxyAndCacheHitSkipsIt() {
        assertTrue(AopUtils.isAopProxy(productDetailDatabaseLoader));
        Product product = createProduct("transaction-boundary");
        AtomicInteger transactionChecks = new AtomicInteger();
        doAnswer(invocation -> {
            assertTrue(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "数据库加载组件必须在真实 Spring 事务中执行"
            );
            transactionChecks.incrementAndGet();
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ProductVO first = productService.getProductById(product.getId());
        ProductVO second = productService.getProductById(product.getId());

        assertEquals(product.getTitle(), first.getTitle());
        assertEquals(product.getTitle(), second.getTitle());
        assertEquals(1, transactionChecks.get());
        verify(productDetailDatabaseLoader, times(1)).loadAndCache(product.getId());
    }

    @Test
    void simultaneousMissesForOneExistingProductUseOneDatabaseLoader() throws Exception {
        Product product = createProduct("single-flight-existing");
        int requestCount = 12;
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loaderCalls = new AtomicInteger();
        doAnswer(invocation -> {
            loaderCalls.incrementAndGet();
            loaderEntered.countDown();
            assertTrue(releaseLoader.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ProductVO>> requests = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return productService.getProductById(product.getId());
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            awaitCondition(
                    () -> productCacheSingleFlight.activeWaiters() == requestCount - 1,
                    5,
                    TimeUnit.SECONDS
            );
            assertEquals(1, productCacheSingleFlight.activeFlights());
            releaseLoader.countDown();

            for (Future<ProductVO> request : requests) {
                ProductVO result = request.get(5, TimeUnit.SECONDS);
                assertEquals(product.getId(), result.getId());
                assertEquals(product.getTitle(), result.getTitle());
            }
            assertEquals(1, loaderCalls.get());
            verify(productDetailDatabaseLoader, times(1)).loadAndCache(product.getId());
            ProductDTO cached = assertInstanceOf(
                    ProductDTO.class,
                    redisTemplate.opsForValue().get(cacheKey(product.getId()))
            );
            assertEquals(product.getTitle(), cached.getTitle());
            assertEquals(0, productCacheSingleFlight.activeFlights());
            assertEquals(0, productCacheSingleFlight.activeWaiters());
        } finally {
            start.countDown();
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousMissesForOneMissingProductUseOneDatabaseConfirmation() throws Exception {
        int missingProductId = 1_800_000_000 + Math.abs(marker.hashCode() % 100_000_000);
        cacheIds.add(missingProductId);
        int requestCount = 10;
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loaderCalls = new AtomicInteger();
        doAnswer(invocation -> {
            loaderCalls.incrementAndGet();
            loaderEntered.countDown();
            assertTrue(releaseLoader.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(missingProductId);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TomatoException>> requests = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return assertThrows(
                            TomatoException.class,
                            () -> productService.getProductById(missingProductId)
                    );
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            awaitCondition(
                    () -> productCacheSingleFlight.activeWaiters() == requestCount - 1,
                    5,
                    TimeUnit.SECONDS
            );
            releaseLoader.countDown();

            for (Future<TomatoException> request : requests) {
                assertEquals("404", request.get(5, TimeUnit.SECONDS).getCode());
            }
            assertEquals(1, loaderCalls.get());
            verify(productDetailDatabaseLoader, times(1)).loadAndCache(missingProductId);
            assertInstanceOf(
                    MissingProductCacheEntry.class,
                    redisTemplate.opsForValue().get(cacheKey(missingProductId))
            );
            assertEquals(0, productCacheSingleFlight.activeFlights());
            assertEquals(0, productCacheSingleFlight.activeWaiters());
        } finally {
            start.countDown();
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedLeaderReleasesFollowersLeavesNoCacheAndAllowsANewRequest() throws Exception {
        Product product = createProduct("single-flight-failure");
        IllegalStateException controlledFailure = new IllegalStateException("controlled loader failure");
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger failedCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (failedCalls.getAndIncrement() == 0) {
                leaderEntered.countDown();
                assertTrue(releaseLeader.await(5, TimeUnit.SECONDS));
                throw controlledFailure;
            }
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> leader = executor.submit(() -> assertThrows(
                    IllegalStateException.class,
                    () -> productService.getProductById(product.getId())
            ));
            assertTrue(leaderEntered.await(5, TimeUnit.SECONDS));
            Future<Throwable> follower = executor.submit(() -> assertThrows(
                    IllegalStateException.class,
                    () -> productService.getProductById(product.getId())
            ));
            awaitCondition(() -> productCacheSingleFlight.activeWaiters() == 1, 5, TimeUnit.SECONDS);
            releaseLeader.countDown();

            assertSame(controlledFailure, leader.get(5, TimeUnit.SECONDS));
            assertSame(controlledFailure, follower.get(5, TimeUnit.SECONDS));
            assertNull(redisTemplate.opsForValue().get(cacheKey(product.getId())));
            assertEquals(0, productCacheSingleFlight.activeFlights());
            assertEquals(0, productCacheSingleFlight.activeWaiters());

            ProductVO retry = productService.getProductById(product.getId());
            assertEquals(product.getTitle(), retry.getTitle());
            assertInstanceOf(
                    ProductDTO.class,
                    redisTemplate.opsForValue().get(cacheKey(product.getId()))
            );
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void missesForDifferentProductsEnterIndependentDatabaseLoads() throws Exception {
        Product first = createProduct("single-flight-different-a");
        Product second = createProduct("single-flight-different-b");
        CountDownLatch bothLoadersEntered = new CountDownLatch(2);
        CountDownLatch releaseLoaders = new CountDownLatch(1);
        doAnswer(invocation -> {
            bothLoadersEntered.countDown();
            assertTrue(releaseLoaders.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(first.getId());
        doAnswer(invocation -> {
            bothLoadersEntered.countDown();
            assertTrue(releaseLoaders.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(second.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductVO> firstRequest = executor.submit(() -> productService.getProductById(first.getId()));
            Future<ProductVO> secondRequest = executor.submit(() -> productService.getProductById(second.getId()));
            assertTrue(bothLoadersEntered.await(5, TimeUnit.SECONDS),
                    "不同商品必须能够同时进入各自的数据库加载事务");
            assertEquals(2, productCacheSingleFlight.activeFlights());
            releaseLoaders.countDown();
            assertEquals(first.getId(), firstRequest.get(5, TimeUnit.SECONDS).getId());
            assertEquals(second.getId(), secondRequest.get(5, TimeUnit.SECONDS).getId());
        } finally {
            releaseLoaders.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void realServiceFollowerTimesOutWithoutCancellingDatabaseLeader() throws Exception {
        Product product = createProduct("single-flight-timeout");
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        doAnswer(invocation -> {
            loaderEntered.countDown();
            assertTrue(releaseLoader.await(8, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductVO> leader = executor.submit(() -> productService.getProductById(product.getId()));
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            Future<Throwable> follower = executor.submit(() -> assertThrows(
                    ProductCacheSingleFlightTimeoutException.class,
                    () -> productService.getProductById(product.getId())
            ));
            assertInstanceOf(ProductCacheSingleFlightTimeoutException.class,
                    follower.get(7, TimeUnit.SECONDS));
            assertFalse(leader.isDone(), "等待者超时不能取消仍在数据库事务中的负责人");
            releaseLoader.countDown();
            assertEquals(product.getId(), leader.get(5, TimeUnit.SECONDS).getId());
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedRealServiceFollowerRestoresFlagAndKeepsLeaderRunning() throws Exception {
        Product product = createProduct("single-flight-interrupt");
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        doAnswer(invocation -> {
            loaderEntered.countDown();
            assertTrue(releaseLoader.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ExecutorService leaderExecutor = Executors.newSingleThreadExecutor();
        AtomicInteger interruptFlagObserved = new AtomicInteger();
        try {
            Future<ProductVO> leader = leaderExecutor.submit(() -> productService.getProductById(product.getId()));
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            Thread follower = new Thread(() -> {
                assertThrows(ProductCacheSingleFlightInterruptedException.class,
                        () -> productService.getProductById(product.getId()));
                if (Thread.currentThread().isInterrupted()) interruptFlagObserved.incrementAndGet();
            });
            follower.start();
            awaitCondition(() -> productCacheSingleFlight.activeWaiters() == 1, 5, TimeUnit.SECONDS);
            follower.interrupt();
            follower.join(2000);
            assertFalse(follower.isAlive());
            assertEquals(1, interruptFlagObserved.get());
            assertFalse(leader.isDone());
            releaseLoader.countDown();
            assertEquals(product.getId(), leader.get(5, TimeUnit.SECONDS).getId());
        } finally {
            releaseLoader.countDown();
            leaderExecutor.shutdownNow();
        }
    }

    @Test
    void followerRedisFailureResultUsesCache003LimiterInSpringContext() throws Exception {
        Product product = createProduct("single-flight-redis-failure-routing");
        AtomicInteger lookups = new AtomicInteger();
        doAnswer(invocation -> {
            int call = lookups.incrementAndGet();
            if (call <= 2) return ProductDetailCache.LookupResult.miss();
            if (call == 3) return ProductDetailCache.LookupResult.databaseFallback();
            return invocation.callRealMethod();
        }).when(productDetailCache).lookup(product.getId());
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        doAnswer(invocation -> {
            if (loads.getAndIncrement() == 0) {
                loaderEntered.countDown();
                assertTrue(releaseFirstLoader.await(5, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(productDetailDatabaseLoader).loadAndCache(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductVO> leader = executor.submit(() -> productService.getProductById(product.getId()));
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            Future<ProductVO> follower = executor.submit(() -> productService.getProductById(product.getId()));
            awaitCondition(() -> productCacheSingleFlight.activeWaiters() == 1, 5, TimeUnit.SECONDS);
            releaseFirstLoader.countDown();
            assertEquals(product.getId(), leader.get(5, TimeUnit.SECONDS).getId());
            assertEquals(product.getId(), follower.get(5, TimeUnit.SECONDS).getId());
            verify(productCacheResilience, atLeastOnce()).executeDatabaseFallback(any());
        } finally {
            releaseFirstLoader.countDown();
            executor.shutdownNow();
        }
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

    private void awaitCondition(Check check, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!check.satisfied() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(check.satisfied(), "condition was not satisfied before timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean satisfied();
    }
}
