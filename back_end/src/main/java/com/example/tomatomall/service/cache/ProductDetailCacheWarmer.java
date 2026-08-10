package com.example.tomatomall.service.cache;

import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductDetailCacheWarmer {

    private final ProductRepository productRepository;
    private final ProductDetailCache productDetailCache;

    public ProductDetailCacheWarmer(
            ProductRepository productRepository,
            ProductDetailCache productDetailCache
    ) {
        this.productRepository = productRepository;
        this.productDetailCache = productDetailCache;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void warmLatestProduct(int productId) {
        productRepository.findByIdForUpdate(productId).ifPresentOrElse(
                product -> productDetailCache.putProduct(productId, ProductDTO.fromProduct(product)),
                () -> productDetailCache.evict(productId)
        );
    }
}
