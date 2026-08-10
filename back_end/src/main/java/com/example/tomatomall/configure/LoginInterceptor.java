package com.example.tomatomall.configure;

import com.example.tomatomall.util.TokenUtil;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final TokenUtil tokenUtil;

    public LoginInterceptor(TokenUtil tokenUtil) {
        this.tokenUtil = tokenUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isPublicRequest(request)) {
            return true;
        }
        tokenUtil.requireToken(request);
        return true;
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (HttpMethod.OPTIONS.matches(method)) return true;
        if (HttpMethod.POST.matches(method)
                && ("/api/accounts".equals(uri)
                || "/api/accounts/login".equals(uri)
                || "/api/accounts/logout".equals(uri))) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && isPathOrChild(uri, "/api/products")) return true;
        if (HttpMethod.POST.matches(method) && "/api/orders/notify".equals(uri)) return true;
        if (HttpMethod.GET.matches(method) && "/api/orders/returnUrl".equals(uri)) return true;

        // Deprecated assistant endpoint keeps only its existing POST compatibility boundary.
        return HttpMethod.POST.matches(method) && "/api/assistant/chat".equals(uri);
    }

    private boolean isPathOrChild(String uri, String path) {
        return path.equals(uri) || uri.startsWith(path + "/");
    }
}
