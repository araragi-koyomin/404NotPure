package com.example.tomatomall.service.order;

import com.example.tomatomall.configure.OrderExpirationProperties;
import com.example.tomatomall.po.Orders;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Component
public class OrderExpirationPolicy {
    private final OrderExpirationProperties properties;
    private final Clock clock;

    public OrderExpirationPolicy(OrderExpirationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public Instant expiresAt(Orders order) {
        Timestamp createTime = order.getCreateTime();
        if (createTime == null) {
            throw new IllegalStateException("Order create time is missing");
        }
        return createTime.toInstant().plus(properties.getPendingTimeout());
    }

    public boolean isExpired(Orders order) {
        return !now().isBefore(expiresAt(order));
    }

    public Instant expirationCutoff() {
        return now().minus(properties.getPendingTimeout());
    }
}
