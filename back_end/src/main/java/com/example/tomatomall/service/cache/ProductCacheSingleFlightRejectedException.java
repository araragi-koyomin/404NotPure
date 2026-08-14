package com.example.tomatomall.service.cache;

public abstract class ProductCacheSingleFlightRejectedException extends RuntimeException {

    protected ProductCacheSingleFlightRejectedException() {
        super("商品服务暂时繁忙，请稍后重试");
    }
}
