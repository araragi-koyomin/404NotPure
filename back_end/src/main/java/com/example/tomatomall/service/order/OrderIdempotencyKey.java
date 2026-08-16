package com.example.tomatomall.service.order;

import com.example.tomatomall.exception.InvalidCheckoutRequestException;

import java.util.UUID;

public final class OrderIdempotencyKey {

    private OrderIdempotencyKey() {
    }

    public static String requireCanonical(String value) {
        if (value == null || value.length() != 36) {
            throw invalid();
        }
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) {
                throw invalid();
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static InvalidCheckoutRequestException invalid() {
        return new InvalidCheckoutRequestException("Idempotency-Key 必须是小写标准 UUID");
    }
}
