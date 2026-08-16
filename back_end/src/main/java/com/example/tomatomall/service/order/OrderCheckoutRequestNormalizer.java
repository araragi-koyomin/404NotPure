package com.example.tomatomall.service.order;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.InvalidCheckoutRequestException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class OrderCheckoutRequestNormalizer {

    private final PaymentMethodResolver paymentMethodResolver;

    public OrderCheckoutRequestNormalizer(PaymentMethodResolver paymentMethodResolver) {
        this.paymentMethodResolver = paymentMethodResolver;
    }

    public NormalizedCheckoutRequest normalize(CreateOrderDTO request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidCheckoutRequestException("订单信息不完整或购买数量非法");
        }

        PaymentMethod paymentMethod = paymentMethodResolver.resolve(request.getPaymentMethod());
        Map<Integer, Integer> quantities = new TreeMap<>();
        try {
            for (CreateOrderDTO.OrderItemDTO item : request.getItems()) {
                if (item == null || item.getProductId() == null || item.getProductId() <= 0
                        || item.getAmount() == null || item.getAmount() <= 0) {
                    throw new InvalidCheckoutRequestException("订单信息不完整或购买数量非法");
                }
                quantities.merge(item.getProductId(), item.getAmount(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new InvalidCheckoutRequestException("订单信息不完整或购买数量非法");
        }

        String items = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        String canonical = "v1|paymentMethod=" + paymentMethod.getApiValue() + "|items=" + items;
        return new NormalizedCheckoutRequest(
                paymentMethod.getApiValue(),
                quantities,
                canonical,
                sha256(canonical)
        );
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}
