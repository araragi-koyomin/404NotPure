package com.example.tomatomall.configure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveLogSanitizerTest {

    @Test
    void masksCommonSensitiveFieldsWithoutUsingRealCredentials() {
        assertAll(
                () -> assertEquals("Authorization: ***",
                        SensitiveLogSanitizer.sanitize("Authorization: Bearer test-only-token")),
                () -> assertEquals("Cookie=***",
                        SensitiveLogSanitizer.sanitize("Cookie=session=test-only-cookie")),
                () -> assertEquals("accessKeyId=***",
                        SensitiveLogSanitizer.sanitize("accessKeyId=test-only-access-id")),
                () -> assertEquals("accessKeySecret: ***",
                        SensitiveLogSanitizer.sanitize("accessKeySecret: test-only-secret")),
                () -> assertEquals("token=***",
                        SensitiveLogSanitizer.sanitize("token=test-only-token")),
                () -> assertEquals("signature: ***",
                        SensitiveLogSanitizer.sanitize("signature: test-only-signature")),
                () -> assertEquals("password=***",
                        SensitiveLogSanitizer.sanitize("password=test-only-password"))
        );
    }

    @Test
    void masksJsonAndQueryValuesButPreservesNonSensitiveContext() {
        String json = SensitiveLogSanitizer.sanitize(
                "{\"accessKeyId\":\"test-only-id\",\"status\":\"ok\"}"
        );
        String query = SensitiveLogSanitizer.sanitize(
                "request failed: https://example.invalid/upload?token=test-only-token&page=2"
        );

        assertAll(
                () -> assertEquals("{\"accessKeyId\":\"***\",\"status\":\"ok\"}", json),
                () -> assertEquals(
                        "request failed: https://example.invalid/upload?token=***&page=2",
                        query
                ),
                () -> assertFalse(json.contains("test-only-id")),
                () -> assertFalse(query.contains("test-only-token"))
        );
    }

    @Test
    void leavesOrdinaryBusinessLogUnchanged() {
        String message = "product 12 saved successfully, imageCount=3";

        assertEquals(message, SensitiveLogSanitizer.sanitize(message));
        assertEquals(null, SensitiveLogSanitizer.sanitize(null));
    }

    @Test
    void masksCloudDiagnosticIdentifiersIfTheyReachAnotherLogger() {
        String sanitized = SensitiveLogSanitizer.sanitize(
                "HostId=test-only-host RequestId=test-only-request "
                        + "EncodedDiagnosticMessage=test-only-diagnostic"
        );

        assertAll(
                () -> assertEquals(
                        "HostId=*** RequestId=*** EncodedDiagnosticMessage=***",
                        sanitized
                ),
                () -> assertFalse(sanitized.contains("test-only-host")),
                () -> assertFalse(sanitized.contains("test-only-request")),
                () -> assertFalse(sanitized.contains("test-only-diagnostic"))
        );
    }
}
