package com.example.tomatomall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CartsVO implements Serializable {
    private Integer cartItemId;
    private Integer productId;
    private String title;
    private BigDecimal price;
    private String description;
    private String detail;
    private String cover;
    private Integer quantity;
}