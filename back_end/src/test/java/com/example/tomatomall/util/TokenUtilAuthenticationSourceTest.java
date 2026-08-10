package com.example.tomatomall.util;

import com.auth0.jwt.JWT;
import com.example.tomatomall.exception.TomatoException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenUtilAuthenticationSourceTest {

    @Test
    void cookieAndHeaderWithSameValueResolveToOneIdentity() {
        MockHttpServletRequest request = requestWithTokens("same-token", "same-token");

        assertEquals("same-token", TokenUtil.extractTokenFromRequest(request));
    }

    @Test
    void conflictingCookieAndHeaderAreRejected() {
        MockHttpServletRequest request = requestWithTokens("cookie-token", "header-token");

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> TokenUtil.extractTokenFromRequest(request)
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void authenticationCookieUsesJwtLifetimeAndSameSiteLax() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        TokenUtil tokenUtil = new TokenUtil(
                org.mockito.Mockito.mock(com.example.tomatomall.repository.UserRepository.class),
                "unit-test-only-jwt-secret-32-characters-minimum",
                24 * 60 * 60,
                false
        );
        tokenUtil.setTokenToCookie(response, "synthetic-token");

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("Max-Age=86400"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void logoutCookieExpiresImmediatelyWithMatchingSecurityAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenUtil tokenUtil = new TokenUtil(
                org.mockito.Mockito.mock(com.example.tomatomall.repository.UserRepository.class),
                "unit-test-only-jwt-secret-32-characters-minimum",
                24 * 60 * 60,
                false
        );

        tokenUtil.clearTokenCookie(response);

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("token="));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Path=/"));
    }

    @Test
    void tokensAreBoundToConfiguredSecretAndConfiguredLifetime() {
        TokenUtil issuer = tokenUtil("first-unit-test-jwt-secret-at-least-32-characters", 90);
        TokenUtil otherEnvironment = tokenUtil("second-unit-test-jwt-secret-at-least-32-characters", 90);

        long beforeIssue = System.currentTimeMillis();
        String token = issuer.generateToken(42);
        long lifetimeMillis = JWT.decode(token).getExpiresAt().getTime() - beforeIssue;

        assertTrue(issuer.verifyToken(token));
        assertFalse(otherEnvironment.verifyToken(token));
        assertTrue(lifetimeMillis >= 89_000 && lifetimeMillis <= 91_000);
    }

    @Test
    void shortJwtSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> tokenUtil("too-short", 90));
    }

    private TokenUtil tokenUtil(String secret, long expirationSeconds) {
        return new TokenUtil(
                org.mockito.Mockito.mock(com.example.tomatomall.repository.UserRepository.class),
                secret,
                expirationSeconds,
                false
        );
    }

    private MockHttpServletRequest requestWithTokens(String cookieToken, String headerToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.setCookies(new Cookie("token", cookieToken));
        request.addHeader("token", headerToken);
        return request;
    }
}
