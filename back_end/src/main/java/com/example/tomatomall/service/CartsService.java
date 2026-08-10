package com.example.tomatomall.service;

import com.example.tomatomall.vo.CartsListVO;
import com.example.tomatomall.vo.CartsVO;

public interface CartsService {
    CartsVO addProductToCart(int userId, int productId, int quantity);
    String deleteCartItem(int userId, int cartItemId);
    String updateCartItemQuantity(int userId, int cartItemId, int quantity);
    CartsListVO getCartItems(int userId);
}
