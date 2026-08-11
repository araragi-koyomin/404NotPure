package com.example.tomatomall.demo;

public final class DemoDatabaseGuard {

    public static final String DEMO_DATABASE_NAME = "tomatomall_demo";

    private DemoDatabaseGuard() {
    }

    public static void requireExactDemoDatabase(String databaseName) {
        requireExpectedDatabase(databaseName, DEMO_DATABASE_NAME);
    }

    static void requireExpectedDatabase(String databaseName, String expectedDatabaseName) {
        if (databaseName == null || !expectedDatabaseName.equals(databaseName)) {
            throw new IllegalStateException(
                    "DATA-001 只允许写入数据库 " + expectedDatabaseName
                            + "，当前数据库为 " + (databaseName == null ? "<未选择>" : databaseName)
            );
        }
    }
}
