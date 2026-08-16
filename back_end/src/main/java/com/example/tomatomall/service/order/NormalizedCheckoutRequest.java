package com.example.tomatomall.service.order;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class NormalizedCheckoutRequest {
    private final String paymentMethod;
    private final Map<Integer, Integer> quantitiesByProduct;
    private final String canonicalValue;
    private final String fingerprint;

    public NormalizedCheckoutRequest(String paymentMethod,
                                     Map<Integer, Integer> quantitiesByProduct,
                                     String canonicalValue,
                                     String fingerprint) {
        this.paymentMethod = paymentMethod;
        this.quantitiesByProduct = Collections.unmodifiableMap(new TreeMap<>(quantitiesByProduct));
        this.canonicalValue = canonicalValue;
        this.fingerprint = fingerprint;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Map<Integer, Integer> getQuantitiesByProduct() {
        return quantitiesByProduct;
    }

    public String getCanonicalValue() {
        return canonicalValue;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}
