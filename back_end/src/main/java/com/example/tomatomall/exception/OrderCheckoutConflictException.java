package com.example.tomatomall.exception;

public class OrderCheckoutConflictException extends RuntimeException {
    public OrderCheckoutConflictException() {
        super("该幂等键已经用于不同的结算内容，请重新确认订单");
    }
}
