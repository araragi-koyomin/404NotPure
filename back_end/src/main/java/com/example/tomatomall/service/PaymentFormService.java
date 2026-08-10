package com.example.tomatomall.service;

import com.alipay.api.AlipayApiException;
import com.example.tomatomall.dto.PaymentData;

public interface PaymentFormService {
    PaymentData createPaymentForm(int userId, int orderId) throws AlipayApiException;
}
