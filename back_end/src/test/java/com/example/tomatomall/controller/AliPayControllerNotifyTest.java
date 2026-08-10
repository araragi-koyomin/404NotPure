package com.example.tomatomall.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.example.tomatomall.service.serviceImpl.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AliPayControllerNotifyTest {

    private static final String APP_ID = "test-app-id";
    private static final String SELLER_ID = "test-seller-id";

    private AliPayController controller;
    private PaymentService paymentService;
    private String privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        controller = new AliPayController();
        paymentService = mock(PaymentService.class);
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        ReflectionTestUtils.setField(controller, "alipayPublicKey", publicKey);
        ReflectionTestUtils.setField(controller, "charset", "UTF-8");
        ReflectionTestUtils.setField(controller, "signType", "RSA2");
        ReflectionTestUtils.setField(controller, "appId", APP_ID);
        ReflectionTestUtils.setField(controller, "sellerId", SELLER_ID);
    }

    @Test
    void invalidSignatureReturnsFailWithoutCallingPaymentService() throws Exception {
        Map<String, String> params = notification("TRADE_SUCCESS");
        params.put("sign", "invalid-signature");

        MockHttpServletResponse response = notifyController(params);

        assertEquals("fail", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService, never()).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    @Test
    void unsupportedTradeStatusReturnsFailEvenWithValidSignature() throws Exception {
        Map<String, String> params = signed(notification("WAIT_BUYER_PAY"));

        MockHttpServletResponse response = notifyController(params);

        assertEquals("fail", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService, never()).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    @Test
    void notificationForDifferentApplicationReturnsFail() throws Exception {
        Map<String, String> params = notification("TRADE_SUCCESS");
        params.put("app_id", "another-app-id");

        MockHttpServletResponse response = notifyController(signed(params));

        assertEquals("fail", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService, never()).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    @Test
    void notificationForDifferentSellerReturnsFail() throws Exception {
        Map<String, String> params = notification("TRADE_SUCCESS");
        params.put("seller_id", "another-seller-id");

        MockHttpServletResponse response = notifyController(signed(params));

        assertEquals("fail", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService, never()).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    @Test
    void tradeFinishedNotificationIsAcceptedAsSuccessfulPayment() throws Exception {
        Map<String, String> params = signed(notification("TRADE_FINISHED"));

        MockHttpServletResponse response = notifyController(params);

        assertEquals("success", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    @Test
    void validSuccessfulNotificationDelegatesOnlyAfterSignatureVerification() throws Exception {
        Map<String, String> params = signed(notification("TRADE_SUCCESS"));

        MockHttpServletResponse response = notifyController(params);

        assertEquals("success", response.getContentAsString(StandardCharsets.UTF_8));
        verify(paymentService).updateOrderStatus("1001", "trade-1001", "19.99");
    }

    private MockHttpServletResponse notifyController(Map<String, String> params) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            request.addParameter(entry.getKey(), entry.getValue());
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.handleAlipayNotify(request, response);
        return response;
    }

    private Map<String, String> notification(String tradeStatus) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("out_trade_no", "1001");
        params.put("trade_no", "trade-1001");
        params.put("total_amount", "19.99");
        params.put("trade_status", tradeStatus);
        params.put("app_id", APP_ID);
        params.put("seller_id", SELLER_ID);
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        return params;
    }

    private Map<String, String> signed(Map<String, String> params) throws Exception {
        String content = AlipaySignature.getSignCheckContentV1(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2"));
        return params;
    }
}
