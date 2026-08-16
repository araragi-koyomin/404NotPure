package com.example.tomatomall.service.order;

public enum PaymentMethod {
    ALIPAY("Alipay");

    private final String apiValue;

    PaymentMethod(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }
}
