package com.example.tomatomall.service;

import com.alipay.api.AlipayApiException;
import com.example.tomatomall.po.Orders;

import java.time.Instant;

public interface PaymentFormGateway {
    String createPaymentForm(Orders order, Instant expiresAt) throws AlipayApiException;
}
