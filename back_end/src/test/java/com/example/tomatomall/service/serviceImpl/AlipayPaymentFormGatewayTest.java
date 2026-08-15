package com.example.tomatomall.service.serviceImpl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.tomatomall.po.Orders;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlipayPaymentFormGatewayTest {
    @Test
    void paymentFormUsesTheOrdersAbsoluteExpirationTime() {
        AlipayPaymentFormGateway gateway = new AlipayPaymentFormGateway(
                "https://example.invalid", "app", "private", "public",
                "UTF-8", "RSA2", "https://notify.invalid", "https://return.invalid");
        Orders order = new Orders();
        order.setOrderId(123);
        order.setTotalAmount(new BigDecimal("39.98"));

        JSONObject content = JSON.parseObject(gateway.buildBizContent(
                order, Instant.parse("2026-08-16T01:02:03Z")));

        assertEquals("123", content.getString("out_trade_no"));
        assertEquals("39.98", content.getString("total_amount"));
        assertEquals("2026-08-16 09:02:03", content.getString("time_expire"));
    }
}
