package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.service.cache.ProductCacheResilience;
import com.example.tomatomall.service.cache.ProductCacheSingleFlight;
import com.example.tomatomall.service.cache.ProductDetailCache;
import com.example.tomatomall.service.cache.ProductDetailDatabaseLoader;
import com.example.tomatomall.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ProductServiceCacheFallbackTest {

    @Mock
    private ProductDetailCache productDetailCache;

    @Mock
    private ProductCacheResilience productCacheResilience;

    @Mock
    private ProductCacheSingleFlight productCacheSingleFlight;

    @Mock
    private ProductDetailDatabaseLoader productDetailDatabaseLoader;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void createProduct() {
        product = Product.builder()
                .id(81)
                .title("fallback-book")
                .price(new BigDecimal("39.90"))
                .rate(4.5)
                .description("database authoritative")
                .detail("detail")
                .cover("/demo/fallback.svg")
                .category("literature")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
    }

    @Test
    void ordinaryCacheMissUsesExistingDatabasePathWithoutFailureLimiter() {
        when(productDetailCache.lookup(81)).thenReturn(ProductDetailCache.LookupResult.miss());
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(Long.MAX_VALUE);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProductDetailDatabaseLoader.LoadResult> work = invocation.getArgument(2);
            return ProductCacheSingleFlight.Outcome.leader(work.get());
        });
        when(productDetailDatabaseLoader.loadAndCache(81)).thenReturn(
                ProductDetailDatabaseLoader.LoadResult.product(ProductDTO.fromProduct(product))
        );

        ProductVO result = productService.getProductById(81);

        assertEquals("fallback-book", result.getTitle());
        verify(productCacheResilience, never()).executeDatabaseFallback(any());
        verify(productCacheSingleFlight).execute(anyInt(), anyLong(), any());
        verify(productDetailDatabaseLoader).loadAndCache(81);
    }

    @Test
    void redisInfrastructureFailureUsesLimitedDatabaseFallbackAndReturnsDatabaseValue() {
        when(productDetailCache.lookup(81)).thenReturn(ProductDetailCache.LookupResult.databaseFallback());
        when(productCacheResilience.executeDatabaseFallback(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProductDetailDatabaseLoader.LoadResult> work = invocation.getArgument(0);
            return work.get();
        });
        when(productDetailDatabaseLoader.loadAndCache(81)).thenReturn(
                ProductDetailDatabaseLoader.LoadResult.product(ProductDTO.fromProduct(product))
        );

        ProductVO result = productService.getProductById(81);

        assertEquals("database authoritative", result.getDescription());
        verify(productCacheResilience).executeDatabaseFallback(any());
        verify(productCacheSingleFlight, never()).execute(anyInt(), anyLong(), any());
        verify(productDetailDatabaseLoader).loadAndCache(81);
    }

    @Test
    void followerRereadsRedisInsteadOfUsingTheLeadersInMemoryResult() {
        ProductDTO current = ProductDTO.fromProduct(product);
        current.setTitle("value-from-redis-after-wait");
        when(productDetailCache.lookup(81))
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.product(current));
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(Long.MAX_VALUE);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any()))
                .thenReturn(ProductCacheSingleFlight.Outcome.follower());

        ProductVO result = productService.getProductById(81);

        assertEquals("value-from-redis-after-wait", result.getTitle());
        verify(productDetailCache, org.mockito.Mockito.times(2)).lookup(81);
        verify(productDetailDatabaseLoader, never()).loadAndCache(81);
    }

    @Test
    void leaderConfirmedMissingProductKeepsTheExistingDomain404() {
        when(productDetailCache.lookup(81)).thenReturn(ProductDetailCache.LookupResult.miss());
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(Long.MAX_VALUE);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any()))
                .thenReturn(ProductCacheSingleFlight.Outcome.leader(
                        ProductDetailDatabaseLoader.LoadResult.missing()
                ));

        com.example.tomatomall.exception.TomatoException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.example.tomatomall.exception.TomatoException.class,
                        () -> productService.getProductById(81)
                );

        assertEquals("404", exception.getCode());
        assertEquals("商品不存在！", exception.getMessage());
    }

    @Test
    void followerWhoseRedisRereadDetectsInfrastructureFailureUsesCache003Limiter() {
        when(productDetailCache.lookup(81))
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.databaseFallback());
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(9_000L);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any()))
                .thenReturn(ProductCacheSingleFlight.Outcome.follower());
        when(productCacheResilience.executeDatabaseFallback(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProductDetailDatabaseLoader.LoadResult> work = invocation.getArgument(0);
            return work.get();
        });
        when(productDetailDatabaseLoader.loadAndCache(81)).thenReturn(
                ProductDetailDatabaseLoader.LoadResult.product(ProductDTO.fromProduct(product))
        );

        ProductVO result = productService.getProductById(81);

        assertEquals("fallback-book", result.getTitle());
        verify(productDetailCache, times(2)).lookup(81);
        verify(productCacheResilience).executeDatabaseFallback(any());
        verify(productDetailDatabaseLoader).loadAndCache(81);
    }

    @Test
    void repeatedMissAfterFollowerWakeupReusesTheOriginalDeadline() {
        ProductDTO current = ProductDTO.fromProduct(product);
        when(productDetailCache.lookup(81))
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.product(current));
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(12_345L);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any()))
                .thenReturn(ProductCacheSingleFlight.Outcome.follower());

        ProductVO result = productService.getProductById(81);

        assertEquals("fallback-book", result.getTitle());
        verify(productCacheSingleFlight, times(1)).newDeadlineNanos();
        ArgumentCaptor<Long> deadlines = ArgumentCaptor.forClass(Long.class);
        verify(productCacheSingleFlight, times(2)).execute(
                org.mockito.ArgumentMatchers.eq(81),
                deadlines.capture(),
                any()
        );
        assertEquals(java.util.List.of(12_345L, 12_345L), deadlines.getAllValues());
        verify(productDetailDatabaseLoader, never()).loadAndCache(81);
    }

    @Test
    void zeroValuedDeadlineIsInitializedOnlyOnce() {
        ProductDTO current = ProductDTO.fromProduct(product);
        when(productDetailCache.lookup(81))
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.miss())
                .thenReturn(ProductDetailCache.LookupResult.product(current));
        when(productCacheSingleFlight.newDeadlineNanos()).thenReturn(0L);
        when(productCacheSingleFlight.execute(anyInt(), anyLong(), any()))
                .thenReturn(ProductCacheSingleFlight.Outcome.follower());

        ProductVO result = productService.getProductById(81);

        assertEquals("fallback-book", result.getTitle());
        verify(productCacheSingleFlight, times(1)).newDeadlineNanos();
        verify(productCacheSingleFlight, times(2)).execute(eq(81), eq(0L), any());
    }
}
