package com.example.tomatomall.service;

import java.util.Map;

public interface PaymentServiceImpl {
  public void updateOrderStatus(String orderId, String alipayTradeNo, String amount);
}
