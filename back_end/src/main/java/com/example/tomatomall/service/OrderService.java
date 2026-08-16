package com.example.tomatomall.service;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.service.order.OrderCheckoutResult;

public interface OrderService {
  OrderCheckoutResult addOrder(Integer userId, String idempotencyKey, CreateOrderDTO dto);
}
