package com.example.tomatomall.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.service.PaymentFormGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AlipayPaymentFormGateway implements PaymentFormGateway {
    private final String serverUrl;
    private final String appId;
    private final String privateKey;
    private final String alipayPublicKey;
    private final String charset;
    private final String signType;
    private final String notifyUrl;
    private final String returnUrl;

    public AlipayPaymentFormGateway(
            @Value("${alipay.server-url}") String serverUrl,
            @Value("${alipay.app-id}") String appId,
            @Value("${alipay.private-key}") String privateKey,
            @Value("${alipay.alipay-public-key}") String alipayPublicKey,
            @Value("${alipay.charset}") String charset,
            @Value("${alipay.sign-type}") String signType,
            @Value("${alipay.notify-url}") String notifyUrl,
            @Value("${alipay.return-url}") String returnUrl
    ) {
        this.serverUrl = serverUrl;
        this.appId = appId;
        this.privateKey = privateKey;
        this.alipayPublicKey = alipayPublicKey;
        this.charset = charset;
        this.signType = signType;
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
    }

    @Override
    public String createPaymentForm(Orders order) throws AlipayApiException {
        AlipayClient alipayClient = new DefaultAlipayClient(
                serverUrl, appId, privateKey, "json", charset, alipayPublicKey, signType
        );
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", order.getOrderId().toString());
        bizContent.put("total_amount", order.getTotalAmount().toPlainString());
        bizContent.put("subject", "订单支付 - " + order.getOrderId());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toJSONString());
        return alipayClient.pageExecute(request).getBody();
    }
}
