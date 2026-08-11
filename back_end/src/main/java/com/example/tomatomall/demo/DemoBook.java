package com.example.tomatomall.demo;

import java.math.BigDecimal;
import java.util.List;

public record DemoBook(
        int ordinal,
        boolean publicDomain,
        String title,
        BigDecimal price,
        double rate,
        String description,
        String detail,
        String coverUrl,
        String category,
        List<DemoSpecification> specifications,
        List<String> contentImageUrls,
        int stockAmount
) {
    public DemoBook {
        specifications = List.copyOf(specifications);
        contentImageUrls = List.copyOf(contentImageUrls);
    }
}
