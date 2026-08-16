package com.example.tomatomall.service.order;

import com.example.tomatomall.exception.InvalidCheckoutRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PaymentMethodResolver {

    public PaymentMethod resolve(String rawValue) {
        if (rawValue == null) {
            throw new InvalidCheckoutRequestException("支付方式不能为空");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if ("alipay".equals(normalized)) {
            return PaymentMethod.ALIPAY;
        }
        throw new InvalidCheckoutRequestException("暂不支持该支付方式");
    }
}
