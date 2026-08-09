package com.example.tomatomall.configure;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginInterceptorImageUploadTest {

    private LoginInterceptor interceptor;
    private TokenUtil tokenUtil;

    @BeforeEach
    void setUp() {
        interceptor = new LoginInterceptor();
        tokenUtil = mock(TokenUtil.class);
        ReflectionTestUtils.setField(interceptor, "tokenUtil", tokenUtil);
        ReflectionTestUtils.setField(interceptor, "userRepository", mock(UserRepository.class));
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

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("multipart/form-data");
        return request;
    }
}
