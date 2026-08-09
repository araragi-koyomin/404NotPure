package com.example.tomatomall.configure;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class SensitiveLogMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveLogSanitizer.sanitize(event.getFormattedMessage());
    }
}
