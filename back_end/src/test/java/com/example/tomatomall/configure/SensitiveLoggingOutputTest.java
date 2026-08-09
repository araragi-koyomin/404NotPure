package com.example.tomatomall.configure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLoggingOutputTest {

    @Test
    void masksMessageAndExceptionAtTheLogOutputBoundary() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%maskedMsg%n%maskedThrowable");
        encoder.start();

        OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        appender.start();

        Logger logger = context.getLogger("sensitive-output-test");
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            logger.info("token=test-only-message status=ok");
            logger.error(
                    "request failed for product 12",
                    new IllegalStateException("accessKeySecret=test-only-exception-secret")
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
        }

        String logged = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(logged.contains("token=*** status=ok")),
                () -> assertTrue(logged.contains("request failed for product 12")),
                () -> assertTrue(logged.contains("accessKeySecret=***")),
                () -> assertFalse(logged.contains("test-only-message")),
                () -> assertFalse(logged.contains("test-only-exception-secret"))
        );
    }
}
