package com.example.tomatomall.configure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {
    static final String DEFAULT_ALLOWED_ORIGINS = "http://127.0.0.1:5173,http://localhost:5173";

    private final Set<String> allowedOrigins;

    CorsFilter() {
        this(DEFAULT_ALLOWED_ORIGINS);
    }

    @Autowired
    public CorsFilter(@Value("${app.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String origins) {
        if (origins == null || origins.trim().isEmpty()) {
            this.allowedOrigins = Collections.emptySet();
        } else {
            this.allowedOrigins = Arrays.stream(origins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String origin = request.getHeader("Origin");
        boolean trustedOrigin = origin != null && allowedOrigins.contains(origin);

        if (trustedOrigin) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.addHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, PATCH, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, token, X-Requested-With, Idempotency-Key");
            response.setHeader("Access-Control-Expose-Headers", "Idempotent-Replay");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if (HttpMethodSupport.isOptions(request)) {
            response.setStatus(origin == null || trustedOrigin
                    ? HttpServletResponse.SC_OK
                    : HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(req, res);
    }

    private static final class HttpMethodSupport {
        private static boolean isOptions(HttpServletRequest request) {
            return "OPTIONS".equalsIgnoreCase(request.getMethod());
        }
    }
}
