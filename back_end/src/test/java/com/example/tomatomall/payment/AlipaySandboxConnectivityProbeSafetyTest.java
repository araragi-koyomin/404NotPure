package com.example.tomatomall.payment;

import com.alipay.api.response.AlipayTradeQueryResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlipaySandboxConnectivityProbeSafetyTest {

    @Test
    void acceptsOfficialSandboxGatewayVariants() {
        assertDoesNotThrow(() -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                "https://openapi.alipaydev.com/gateway.do"
        ));
        assertDoesNotThrow(() -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
        ));
    }

    @Test
    void rejectsProductionThirdPartyAndAmbiguousGatewayUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                        "https://openapi.alipay.com/gateway.do"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                        "https://example.com/gateway.do"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                        "http://openapi.alipaydev.com/gateway.do"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                        "https://openapi.alipaydev.com/gateway.do?target=other"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> AlipaySandboxConnectivityProbeIT.validateSandboxGateway(
                        "https://user@openapi.alipaydev.com/gateway.do"
                ));
    }

    @Test
    void acceptsOnlyTheExpectedMissingTradeResult() {
        AlipayTradeQueryResponse missingTrade = response("40004", "ACQ.TRADE_NOT_EXIST");
        AlipayTradeQueryResponse genericSuccess = response("10000", null);
        AlipayTradeQueryResponse otherBusinessFailure = response("40004", "ACQ.INVALID_PARAMETER");

        assertTrue(AlipaySandboxConnectivityProbeIT.isExpectedMissingTradeResponse(missingTrade));
        assertFalse(AlipaySandboxConnectivityProbeIT.isExpectedMissingTradeResponse(genericSuccess));
        assertFalse(AlipaySandboxConnectivityProbeIT.isExpectedMissingTradeResponse(otherBusinessFailure));
        assertFalse(AlipaySandboxConnectivityProbeIT.isExpectedMissingTradeResponse(null));
    }

    private AlipayTradeQueryResponse response(String code, String subCode) {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode(code);
        response.setSubCode(subCode);
        return response;
    }
}
