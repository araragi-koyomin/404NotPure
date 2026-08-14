package com.example.tomatomall.service.cache;

import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class ProductDetailDatabaseLoader {

    private final ProductRepository productRepository;
    private final ProductDetailCache productDetailCache;

    public ProductDetailDatabaseLoader(
            ProductRepository productRepository,
            ProductDetailCache productDetailCache
    ) {
        this.productRepository = productRepository;
        this.productDetailCache = productDetailCache;
    }

    @Transactional
    public LoadResult loadAndCache(int productId) {
        Product product = productRepository.findByIdForUpdate(productId).orElse(null);
        if (product == null) {
            productDetailCache.putMissing(productId);
            return LoadResult.missing();
        }

        ProductDTO productDTO = ProductDTO.fromProduct(product);
        productDetailCache.putProduct(productId, productDTO);
        return LoadResult.product(productDTO);
    }

    public static final class LoadResult {
        private static final LoadResult MISSING = new LoadResult(null);

        private final ProductDTO product;

        private LoadResult(ProductDTO product) {
            this.product = product;
        }

        public static LoadResult product(ProductDTO product) {
            return new LoadResult(Objects.requireNonNull(product));
        }

        public static LoadResult missing() {
            return MISSING;
        }

        public boolean isMissing() {
            return product == null;
        }

        public ProductDTO getProduct() {
            return product;
        }
    }
}
