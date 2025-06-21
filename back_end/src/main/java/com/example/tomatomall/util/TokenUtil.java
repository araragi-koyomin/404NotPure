package com.example.tomatomall.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

@Component
public class TokenUtil {
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;
    private static final String SECRET = "404NotPure";

    @Autowired
    UserRepository userRepository;

    public boolean verifyToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token); // 校验成功说明 token 有效
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateToken(Integer userId) {
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRE_TIME);
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        return JWT.create()
                .withSubject(String.valueOf(userId)) // 将 id 转为字符串作为 subject
                .withExpiresAt(expireDate)
                .sign(algorithm);
    }

    public static Integer getUserIdFromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            return Integer.parseInt(decodedJWT.getSubject()); // 解析出的是 id
        } catch (Exception e) {
            throw TomatoException.notLogin(); // 或自定义异常
        }
    }

    public String getUserRoleFromToken(String token) {
        Integer userId = getUserIdFromToken(token);
        Account account = userRepository.findById(userId).orElseThrow(TomatoException::notLogin);
        return account.getRole();
    }

    /**
     * 从HTTP请求中提取token
     * @param request HTTP请求对象
     * @return token字符串
     */
    public static String extractTokenFromRequest(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return token;
    }

    /**
     * 设置token到Cookie
     * @param response HTTP响应对象
     * @param token 认证token
     */
    public static void setTokenToCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7天有效期
        response.addCookie(cookie);
    }

    /**
     * 从请求中获取用户ID（包含登录验证）
     * @param request HTTP请求对象
     * @return 用户ID
     * @throws TomatoException 未登录时抛出
     */
    public static int getUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            throw TomatoException.notLogin();
        }
        try {
            return getUserIdFromToken(token);
        } catch (Exception e) {
            throw TomatoException.notLogin();
        }
    }

    /**
     * 验证管理员权限
     * @param request – HTTP请求对象
     * @throws TomatoException 无权限时抛出
     */
    public void validateAdminRole(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        String role = getUserRoleFromToken(token);
        if (!"admin".equalsIgnoreCase(role)) {
            throw TomatoException.noPermission();
    public Integer getUserIdfromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            return Integer.parseInt(decodedJWT.getSubject()); // 解析出的是 id
        } catch (Exception e) {
            throw TomatoException.notLogin(); // 或自定义异常
        }
    }
}
