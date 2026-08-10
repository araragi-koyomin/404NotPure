package com.example.tomatomall.service;

import com.alipay.api.AlipayApiException;
import com.example.tomatomall.po.Orders;

public interface PaymentFormGateway {
    String createPaymentForm(Orders order) throws AlipayApiException;
}
