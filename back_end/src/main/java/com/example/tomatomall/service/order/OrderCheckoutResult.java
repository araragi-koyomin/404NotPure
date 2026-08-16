package com.example.tomatomall.service.order;

import com.example.tomatomall.vo.OrdersVO;

public final class OrderCheckoutResult {
    private final OrdersVO order;
    private final boolean replayed;

    private OrderCheckoutResult(OrdersVO order, boolean replayed) {
        this.order = order;
        this.replayed = replayed;
    }

    public static OrderCheckoutResult created(OrdersVO order) {
        return new OrderCheckoutResult(order, false);
    }

    public static OrderCheckoutResult replayed(OrdersVO order) {
        return new OrderCheckoutResult(order, true);
    }

    public OrdersVO getOrder() {
        return order;
    }

    public boolean isReplayed() {
        return replayed;
    }
}
