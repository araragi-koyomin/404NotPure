package com.example.tomatomall.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductPageVO {
    private final List<ProductSummaryVO> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
}
