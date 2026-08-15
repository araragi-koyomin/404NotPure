package com.example.tomatomall.controller;

import com.example.tomatomall.configure.SecurityConfig;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartsController.class)
@Import(SecurityConfig.class)
class CartsControllerQuantityValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartsService cartsService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private TokenUtil tokenUtil;

    @MockBean
    private UserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"productId\":1}",
            "{\"productId\":1,\"quantity\":null}",
            "{\"productId\":1,\"quantity\":0}",
            "{\"productId\":1,\"quantity\":-1}",
            "{\"productId\":1,\"quantity\":1.5}",
            "{\"productId\":1,\"quantity\":\"2\"}",
            "{\"productId\":1,\"quantity\":true}",
            "{\"quantity\":1}",
            "{\"productId\":null,\"quantity\":1}",
            "{\"productId\":0,\"quantity\":1}",
            "{\"productId\":-1,\"quantity\":1}",
            "{\"productId\":1.5,\"quantity\":1}",
            "{\"productId\":\"1\",\"quantity\":1}",
            "{\"productId\":true,\"quantity\":1}"
    })
    void addRejectsAnythingOtherThanStrictPositiveIntegerFieldsBeforeCallingService(String body)
            throws Exception {
        mockMvc.perform(post("/api/cart")
                        .cookie(authenticatedCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(cartsService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"quantity\":null}",
            "{\"quantity\":0}",
            "{\"quantity\":-1}",
            "{\"quantity\":1.5}",
            "{\"quantity\":\"2\"}",
            "{\"quantity\":true}"
    })
    void updateRejectsAnythingOtherThanStrictPositiveIntegerQuantityBeforeCallingService(String body)
            throws Exception {
        mockMvc.perform(patch("/api/cart/{id}", 1)
                        .cookie(authenticatedCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(cartsService);
    }

    private Cookie authenticatedCookie() {
        return new Cookie("token", tokenUtilForTest().generateToken(1));
    }

    private TokenUtil tokenUtilForTest() {
        return new TokenUtil(
                userRepository,
                "unit-test-only-jwt-secret-32-characters-minimum",
                86400,
                false
        );
    }
}
