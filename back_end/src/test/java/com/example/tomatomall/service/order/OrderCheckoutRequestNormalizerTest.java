package com.example.tomatomall.service.order;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.InvalidCheckoutRequestException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCheckoutRequestNormalizerTest {

    private final OrderCheckoutRequestNormalizer normalizer =
            new OrderCheckoutRequestNormalizer(new PaymentMethodResolver());

    @Test
    void normalizesPaymentMethodAggregatesDuplicatesAndSortsProducts() {
        NormalizedCheckoutRequest normalized = normalizer.normalize(request(
                "  aLiPaY ", item(8, 1), item(2, 3), item(8, 4)));

        assertEquals("Alipay", normalized.getPaymentMethod());
        assertEquals("v1|paymentMethod=Alipay|items=2:3,8:5", normalized.getCanonicalValue());
        assertEquals(64, normalized.getFingerprint().length());
        assertEquals(Integer.valueOf(3), normalized.getQuantitiesByProduct().get(2));
        assertEquals(Integer.valueOf(5), normalized.getQuantitiesByProduct().get(8));
    }

    @Test
    void sameBusinessRequestHasSameFingerprintRegardlessOfItemOrder() {
        String first = normalizer.normalize(request("Alipay", item(2, 3), item(8, 5))).getFingerprint();
        String second = normalizer.normalize(request("alipay", item(8, 2), item(2, 3), item(8, 3)))
                .getFingerprint();

        assertEquals(first, second);
    }

    @Test
    void changedQuantityChangesFingerprint() {
        String first = normalizer.normalize(request("Alipay", item(2, 1))).getFingerprint();
        String second = normalizer.normalize(request("Alipay", item(2, 2))).getFingerprint();

        assertNotEquals(first, second);
    }

    @Test
    void rejectsUnknownPaymentMethod() {
        assertThrows(InvalidCheckoutRequestException.class,
                () -> normalizer.normalize(request("WechatPay", item(2, 1))));
    }

    @Test
    void rejectsInvalidProductIdAndAggregatedOverflow() {
        assertThrows(InvalidCheckoutRequestException.class,
                () -> normalizer.normalize(request("Alipay", item(0, 1))));
        assertThrows(InvalidCheckoutRequestException.class,
                () -> normalizer.normalize(request(
                        "Alipay", item(2, Integer.MAX_VALUE), item(2, 1))));
    }

    private CreateOrderDTO request(String paymentMethod, CreateOrderDTO.OrderItemDTO... items) {
        CreateOrderDTO request = new CreateOrderDTO();
        request.setPaymentMethod(paymentMethod);
        request.setItems(Arrays.asList(items));
        return request;
    }

    private CreateOrderDTO.OrderItemDTO item(int productId, int amount) {
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setProductId(productId);
        item.setAmount(amount);
        return item;
    }
}
