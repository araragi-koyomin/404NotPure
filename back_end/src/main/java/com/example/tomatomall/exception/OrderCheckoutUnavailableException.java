package com.example.tomatomall.exception;

public class OrderCheckoutUnavailableException extends RuntimeException {
    public OrderCheckoutUnavailableException() {
        super("订单正在处理中，请稍后使用相同幂等键重试");
    }

    public OrderCheckoutUnavailableException(Throwable cause) {
        super("订单正在处理中，请稍后使用相同幂等键重试", cause);
    }
}
