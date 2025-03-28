package com.example.tomatomall.configure;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Configuration
public class LoginInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);
    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        // 排除注册和登录路径
        logger.info("Request URI: {}", request.getRequestURI());
        logger.info("Method: {}", request.getMethod());
        logger.info("Content-Type: {}", request.getContentType());
        String uri = request.getRequestURI();
        logger.info("Request URI: {}", uri);
        if (uri.equals("/api/accounts") || uri.equals("/api/accounts/login") || uri.equals("/api/images")) {
            return true;
        }

        // 从Cookie中获取Token
        String token =null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token == null || !tokenUtil.verifyToken(token)) {
            throw TomatoException.notLogin();
        }

        if (uri.startsWith("/api/accounts/")) {
            String[] parts = uri.split("/");
            String requestUsername = parts[parts.length - 1];
            logger.info("Request username/telephone: {}", requestUsername);

            Integer accountId = TokenUtil.getUserIdFromToken(token); // token 中是 id
            Account account = userRepository.findById(accountId).orElseThrow(TomatoException::notLogin);
            logger.info("Actual username: {}, telephone: {}", account.getUsername(), account.getTelephone());

            // 允许 requestUsername 是 username 或 telephone
            if (!account.getUsername().equals(requestUsername) &&
                    !account.getTelephone().equals(requestUsername)) {
                throw TomatoException.notLogin();
            }
        }
        return true;
    }
}
