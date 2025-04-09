package com.example.tomatomall.vo;
import com.example.tomatomall.po.Carts;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartsListVO {
    private List<CartItemVO> items;
    private int total;
    private BigDecimal totalAmount;
}