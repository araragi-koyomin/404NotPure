package com.example.tomatomall.dto;

import lombok.Data;

@Data
public class AddProductToCartDTO {
    private int productId;
    private int quantity;
}