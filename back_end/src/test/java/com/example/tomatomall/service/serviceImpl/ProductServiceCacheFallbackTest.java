package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.cache.ProductCacheResilience;
import com.example.tomatomall.service.cache.ProductDetailCache;
import com.example.tomatomall.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceCacheFallbackTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDetailCache productDetailCache;

    @Mock
    private ProductCacheResilience productCacheResilience;

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
        when(productRepository.findByIdForUpdate(81)).thenReturn(Optional.of(product));

        ProductVO result = productService.getProductById(81);

        assertEquals("fallback-book", result.getTitle());
        verify(productCacheResilience, never()).executeDatabaseFallback(any());
        verify(productDetailCache).putProduct(any(Integer.class), any());
    }

    @Test
    void redisInfrastructureFailureUsesLimitedDatabaseFallbackAndReturnsDatabaseValue() {
        when(productDetailCache.lookup(81)).thenReturn(ProductDetailCache.LookupResult.databaseFallback());
        when(productCacheResilience.executeDatabaseFallback(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProductVO> work = invocation.getArgument(0);
            return work.get();
        });
        when(productRepository.findByIdForUpdate(81)).thenReturn(Optional.of(product));

        ProductVO result = productService.getProductById(81);

        assertEquals("database authoritative", result.getDescription());
        verify(productCacheResilience).executeDatabaseFallback(any());
        verify(productDetailCache).putProduct(any(Integer.class), any());
    }
}
