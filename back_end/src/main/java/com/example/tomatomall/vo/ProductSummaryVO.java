package com.example.tomatomall.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProductSummaryVO {
    private final Integer id;
    private final String title;
    private final BigDecimal price;
    private final Double rate;
    private final String cover;
    private final String category;
    private final String author;
}
