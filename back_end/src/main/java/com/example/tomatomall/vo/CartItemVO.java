package com.example.tomatomall.vo;

import com.example.tomatomall.po.Carts;
import com.example.tomatomall.po.Product;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVO {
    private Integer cartItemId;
    private Integer productId;
    private String title;
    private BigDecimal price;
    private String description;
    private String cover;
    private String detail;
    private Integer quantity;

    public CartItemVO(Carts cart) {
        Product product = cart.getProduct();
        this.cartItemId = cart.getCartItemId();
        this.productId = product.getId();
        this.title = product.getTitle();
        this.price = product.getPrice();
        this.description = product.getDescription();
        this.cover = product.getCover();
        this.detail = product.getDetail();
        this.quantity = cart.getQuantity();
    }
}