package com.example.tomatomall.demo;

public record DemoAdvertisement(
        int ordinal,
        String title,
        String content,
        String imageUrl,
        int bookOrdinal
) {
}
