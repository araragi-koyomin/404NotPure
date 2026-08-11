package com.example.tomatomall.demo;

import java.nio.file.Path;

public record DemoDataImportConfig(
        int bookCount,
        int bulkUserCount,
        long seed,
        String rawPassword,
        Path assetDirectory
) {
    public static final int DEFAULT_BOOK_COUNT = 300;
    public static final int DEFAULT_USER_COUNT = 500;
    public static final long DEFAULT_SEED = 404L;
    public static final int MIN_BOOK_COUNT = 20;
    public static final int MAX_BOOK_COUNT = 2000;
    public static final int MAX_USER_COUNT = 5000;

    public DemoDataImportConfig {
        if (bookCount < MIN_BOOK_COUNT || bookCount > MAX_BOOK_COUNT) {
            throw new IllegalArgumentException("演示书籍数量必须在 20～2000 之间");
        }
        if (bulkUserCount < 0 || bulkUserCount > MAX_USER_COUNT) {
            throw new IllegalArgumentException("规模用户数量必须在 0～5000 之间");
        }
        if (rawPassword == null || rawPassword.trim().length() < 12) {
            throw new IllegalArgumentException("本地演示密码必须至少 12 个字符");
        }
        if (assetDirectory == null) {
            throw new IllegalArgumentException("必须提供本地 SVG 输出目录");
        }
        rawPassword = rawPassword.trim();
        assetDirectory = assetDirectory.toAbsolutePath().normalize();
    }

    public static DemoDataImportConfig defaults(String rawPassword, Path assetDirectory) {
        return new DemoDataImportConfig(
                DEFAULT_BOOK_COUNT,
                DEFAULT_USER_COUNT,
                DEFAULT_SEED,
                rawPassword,
                assetDirectory
        );
    }
}
