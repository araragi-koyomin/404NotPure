package com.example.tomatomall.service;

import com.example.tomatomall.vo.OrdersVO;

public interface OrderLifecycleService {
    OrdersVO cancelOrder(int userId, int orderId);

    boolean closeExpiredOrder(int orderId);

    boolean closeIfExpiredForPayment(int userId, int orderId);
}
