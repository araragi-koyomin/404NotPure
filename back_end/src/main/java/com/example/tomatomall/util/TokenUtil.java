package com.example.tomatomall.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class TokenUtil {
    public static final String TOKEN_NAME = "token";

    private final UserRepository userRepository;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationSeconds;
    private final boolean secureCookie;

    @Autowired
    public TokenUtil(
            UserRepository userRepository,
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expiration-seconds:86400}") long expirationSeconds,
            @Value("${auth.cookie.secure:false}") boolean secureCookie
    ) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }
        if (expirationSeconds <= 0 || expirationSeconds > Integer.MAX_VALUE) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be a positive integer");
        }
        this.userRepository = userRepository;
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expirationSeconds = expirationSeconds;
        this.secureCookie = secureCookie;
    }

    public boolean verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        try {
            verifier.verify(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public String generateToken(Integer userId) {
        Date expireDate = new Date(System.currentTimeMillis() + Duration.ofSeconds(expirationSeconds).toMillis());
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withExpiresAt(expireDate)
                .sign(algorithm);
    }

    public Integer getUserIdFromToken(String token) {
        try {
            DecodedJWT decodedJWT = verifier.verify(token);
            return Integer.parseInt(decodedJWT.getSubject());
        } catch (Exception exception) {
            throw TomatoException.notLogin();
        }
    }

    public String getUserRoleFromToken(String token) {
        Integer userId = getUserIdFromToken(token);
        Account account = userRepository.findById(userId).orElseThrow(TomatoException::notLogin);
        return account.getRole();
    }

    public static String extractTokenFromRequest(HttpServletRequest request) {
        Set<String> distinctTokens = new LinkedHashSet<>();
        distinctTokens.addAll(headerTokens(request));
        distinctTokens.addAll(cookieTokens(request));

        if (distinctTokens.size() > 1) {
            throw TomatoException.notLogin();
        }
        return distinctTokens.stream().findFirst().orElse(null);
    }

    public String requireToken(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (!verifyToken(token)) {
            throw TomatoException.notLogin();
        }
        return token;
    }

    public void setTokenToCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(TOKEN_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(expirationSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(TOKEN_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public int getUserIdFromRequest(HttpServletRequest request) {
        return getUserIdFromToken(requireToken(request));
    }

    public void validateAdminRole(HttpServletRequest request) {
        String token = requireToken(request);
        String role = getUserRoleFromToken(token);
        if (!"admin".equalsIgnoreCase(role)) {
            throw TomatoException.noPermission();
        }
    }

    private static List<String> headerTokens(HttpServletRequest request) {
        List<String> tokens = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders(TOKEN_NAME);
        if (headers == null) return tokens;
        while (headers.hasMoreElements()) {
            addNonBlank(tokens, headers.nextElement());
        }
        return tokens;
    }

    private static List<String> cookieTokens(HttpServletRequest request) {
        List<String> tokens = new ArrayList<>();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return tokens;
        for (Cookie cookie : cookies) {
            if (TOKEN_NAME.equals(cookie.getName())) {
                addNonBlank(tokens, cookie.getValue());
            }
        }
        return tokens;
    }

    private static void addNonBlank(List<String> tokens, String value) {
        if (value != null && !value.trim().isEmpty()) {
            tokens.add(value.trim());
        }
    }
}
