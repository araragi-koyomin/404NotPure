package com.example.tomatomall.configure;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class LoginInterceptorImageUploadTest {

    private LoginInterceptor interceptor;
    private TokenUtil tokenUtil;

    @BeforeEach
    void setUp() {
        tokenUtil = mock(TokenUtil.class);
        interceptor = new LoginInterceptor(tokenUtil);
        doThrow(TomatoException.notLogin()).when(tokenUtil).requireToken(any());
    }

    @Test
    void anonymousImageUploadIsRejected() {
        MockHttpServletRequest request = post("/api/images");

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void imageUploadWithInvalidTokenIsRejected() {
        MockHttpServletRequest request = post("/api/images");
        request.setCookies(new Cookie("token", "invalid-token"));
        when(tokenUtil.verifyToken("invalid-token")).thenReturn(false);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void registrationWithoutAuthenticationRemainsAllowed() {
        assertTrue(interceptor.preHandle(
                post("/api/accounts"),
                new MockHttpServletResponse(),
                new Object()
        ));
    }

    @Test
    void logoutWithoutAuthenticationRemainsAllowedSoExpiredCookiesCanBeCleared() {
        assertTrue(interceptor.preHandle(
                post("/api/accounts/logout"),
                new MockHttpServletResponse(),
                new Object()
        ));
    }

    @Test
    void anonymousCartRequestIsRejected() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(
                        new MockHttpServletRequest("GET", "/api/cart"),
                        new MockHttpServletResponse(),
                        new Object()
                )
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void anonymousPaymentFormRequestIsRejected() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(
                        post("/api/orders/123/pay"),
                        new MockHttpServletResponse(),
                        new Object()
                )
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void productReadRemainsPublicButProductWriteRequiresAuthentication() {
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/products/123"),
                new MockHttpServletResponse(),
                new Object()
        ));

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(
                        post("/api/products"),
                        new MockHttpServletResponse(),
                        new Object()
                )
        );
        assertEquals("401", exception.getCode());
    }

    @Test
    void adjacentProductPrefixIsNotTreatedAsPublicProductRoute() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(
                        new MockHttpServletRequest("GET", "/api/products-admin"),
                        new MockHttpServletResponse(),
                        new Object()
                )
        );

        assertEquals("401", exception.getCode());
    }

    @Test
    void alipayNotifyRemainsPublic() {
        assertTrue(interceptor.preHandle(
                post("/api/orders/notify"),
                new MockHttpServletResponse(),
                new Object()
        ));
    }

    @Test
    void alipayReturnUrlRemainsPublic() {
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/orders/returnUrl"),
                new MockHttpServletResponse(),
                new Object()
        ));
    }

    @Test
    void validTokenHeaderAuthenticatesApiClient() {
        MockHttpServletRequest request = post("/api/images");
        request.addHeader("token", "header-token");
        doReturn("header-token").when(tokenUtil).requireToken(request);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void conflictingCookieAndHeaderAreRejected() {
        MockHttpServletRequest request = post("/api/images");
        request.setCookies(new Cookie("token", "cookie-token"));
        request.addHeader("token", "different-header-token");
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())
        );

        assertEquals("401", exception.getCode());
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("multipart/form-data");
        return request;
    }
}
