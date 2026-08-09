package com.example.tomatomall.configure;

import java.util.regex.Pattern;

/**
 * Removes common credential values before a log line is written.
 */
public final class SensitiveLogSanitizer {

    private static final String SENSITIVE_KEY =
            "(?:authorization|proxy[_-]?authorization|cookie|set[_-]?cookie|"
                    + "access[_-]?key(?:[_-]?(?:id|secret))?|api[_-]?key|private[_-]?key|"
                    + "client[_-]?secret|(?:refresh[_-]?)?token|signature|sign|password|"
                    + "host[_-]?id|request[_-]?id|encoded[_-]?diagnostic[_-]?message|"
                    + "auth[_-]?principal(?:[_-]?(?:owner[_-]?id|display[_-]?name))?)";

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "(?im)(\\b(?:authorization|proxy-authorization|cookie|set-cookie)\\b\\s*[:=]\\s*)([^\\r\\n]*)"
    );

    private static final Pattern QUOTED_JSON_PATTERN = Pattern.compile(
            "(?i)([\\\"']" + SENSITIVE_KEY + "[\\\"']\\s*:\\s*[\\\"'])(.*?)([\\\"'])"
    );

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\b" + SENSITIVE_KEY + "\\b\\s*[:=]\\s*)([\\\"'][^\\\"']*[\\\"']|[^\\s&,;\\]}]+)"
    );

    private SensitiveLogSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String sanitized = HEADER_PATTERN.matcher(message).replaceAll("$1***");
        sanitized = QUOTED_JSON_PATTERN.matcher(sanitized).replaceAll("$1***$3");
        return KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1***");
    }
}
