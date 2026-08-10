package com.example.tomatomall.payment;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AlipaySandboxConnectivityProbeIT {

    @Test
    void sandboxAcceptsConfiguredApplicationSignature() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_REAL_ALIPAY_PROBE")));

        String serverUrl = required("ALIPAY_SERVER_URL");
        validateSandboxGateway(serverUrl);
        String appId = required("ALIPAY_APP_ID");
        String privateKey = required("ALIPAY_APP_PRIVATE_KEY");
        String alipayPublicKey = required("ALIPAY_ALIPAY_PUBLIC_KEY");

        AlipayClient client = new DefaultAlipayClient(
                serverUrl,
                appId,
                privateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
        );
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject content = new JSONObject();
        content.put("out_trade_no", "tomatomall-probe-" + UUID.randomUUID());
        request.setBizContent(content.toJSONString());

        AlipayTradeQueryResponse response = client.execute(request);

        assertNotNull(response, "Sandbox returned no response");
        assertTrue(
                isExpectedMissingTradeResponse(response),
                () -> "Sandbox rejected the configured application identity or signature; "
                        + "check the current sandbox APPID, gateway, application private key "
                        + "and Alipay public key"
        );
    }

    static void validateSandboxGateway(String serverUrl) {
        try {
            URI gateway = new URI(serverUrl);
            String host = gateway.getHost();
            boolean officialSandboxHost = host != null
                    && ("alipaydev.com".equals(host.toLowerCase(Locale.ROOT))
                    || host.toLowerCase(Locale.ROOT).endsWith(".alipaydev.com"));
            boolean defaultHttpsPort = gateway.getPort() == -1 || gateway.getPort() == 443;

            if (!"https".equalsIgnoreCase(gateway.getScheme())
                    || !officialSandboxHost
                    || !defaultHttpsPort
                    || gateway.getRawUserInfo() != null
                    || !"/gateway.do".equals(gateway.getPath())
                    || gateway.getRawQuery() != null
                    || gateway.getRawFragment() != null) {
                throw new IllegalArgumentException("ALIPAY_SERVER_URL is not an official sandbox gateway");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("ALIPAY_SERVER_URL is not a valid sandbox gateway", exception);
        }
    }

    static boolean isExpectedMissingTradeResponse(AlipayTradeQueryResponse response) {
        return response != null
                && "40004".equals(response.getCode())
                && "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode());
    }

    private String required(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.trim().isEmpty(), () -> name + " is not configured");
        return value.trim();
    }
}
