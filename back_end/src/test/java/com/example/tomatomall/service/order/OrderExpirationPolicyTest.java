package com.example.tomatomall.service.order;

import com.example.tomatomall.configure.OrderExpirationProperties;
import com.example.tomatomall.po.Orders;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderExpirationPolicyTest {
    @Test
    void exactDeadlineIsExpiredButOneNanosecondBeforeIsNot() {
        Instant createdAt = Instant.parse("2026-08-16T00:00:00Z");
        OrderExpirationProperties properties = new OrderExpirationProperties();
        properties.setPendingTimeout(Duration.ofMinutes(30));
        Orders order = new Orders();
        order.setCreateTime(Timestamp.from(createdAt));

        OrderExpirationPolicy beforeDeadline = new OrderExpirationPolicy(properties,
                Clock.fixed(createdAt.plus(Duration.ofMinutes(30)).minusNanos(1), ZoneOffset.UTC));
        OrderExpirationPolicy atDeadline = new OrderExpirationPolicy(properties,
                Clock.fixed(createdAt.plus(Duration.ofMinutes(30)), ZoneOffset.UTC));

        assertFalse(beforeDeadline.isExpired(order));
        assertTrue(atDeadline.isExpired(order));
    }
}
