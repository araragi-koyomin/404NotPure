package com.example.tomatomall.service.cache;

public class ProductCacheFallbackRejectedException extends RuntimeException {

    public ProductCacheFallbackRejectedException() {
        super("商品服务暂时繁忙，请稍后重试");
    }
}
