package com.example.tomatomall.configure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssProbeLoggingTest {

    @Test
    void rawSurefireRuntimeSuppressesSensitiveHttpLoggersWithoutSpring() {
        Logger headers = (Logger) LoggerFactory.getLogger("org.apache.http.headers");
        Logger wire = (Logger) LoggerFactory.getLogger("org.apache.http.wire");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        headers.addAppender(appender);
        wire.addAppender(appender);

        headers.debug("Authorization: OSS probe-access-id:probe-signature");
        wire.debug("token=probe-token&signature=probe-signature");

        assertEquals(Level.OFF, headers.getEffectiveLevel());
        assertEquals(Level.OFF, wire.getEffectiveLevel());
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void testRuntimeSuppressesGeneratedDevelopmentPasswordLogger() {
        Logger generatedPasswordLogger = (Logger) LoggerFactory.getLogger(
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        generatedPasswordLogger.addAppender(appender);

        generatedPasswordLogger.warn("Using generated security password: test-only-placeholder");

        assertEquals(Level.OFF, generatedPasswordLogger.getEffectiveLevel());
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void testRuntimeSuppressesAliyunSdkDiagnosticResponseLogger() {
        Logger aliyunLogger = (Logger) LoggerFactory.getLogger("com.aliyun.oss");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        aliyunLogger.addAppender(appender);

        aliyunLogger.warn(
                "HostId: test-only-bucket.invalid RequestId: test-only-request "
                        + "EncodedDiagnosticMessage: test-only-diagnostic"
        );

        assertEquals(Level.OFF, aliyunLogger.getEffectiveLevel());
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void testRuntimeSuppressesAlipaySdkBusinessParameterLogger() {
        Logger alipayBusinessErrorLogger = (Logger) LoggerFactory.getLogger("sdk.biz.err");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        alipayBusinessErrorLogger.addAppender(appender);

        alipayBusinessErrorLogger.error(
                "app_id=test-only-app&out_trade_no=test-only-order&sign=test-only-signature"
        );

        assertEquals(Level.OFF, alipayBusinessErrorLogger.getEffectiveLevel());
        assertTrue(appender.list.isEmpty());
    }
}
