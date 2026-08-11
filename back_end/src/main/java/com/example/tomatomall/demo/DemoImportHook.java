package com.example.tomatomall.demo;

import java.sql.Connection;

@FunctionalInterface
public interface DemoImportHook {
    void afterProductsInserted(Connection connection) throws Exception;

    static DemoImportHook none() {
        return connection -> {
        };
    }
}
