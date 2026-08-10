package com.example.tomatomall.controller;

import com.example.tomatomall.configure.SecurityConfig;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartsController.class)
@Import(SecurityConfig.class)
class CartsControllerOrderValidationTest {

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
        "{\"paymentMethod\":\"Alipay\",\"items\":[]}",
        "{\"paymentMethod\":\"Alipay\",\"items\":[null]}",
        "{\"paymentMethod\":\"Alipay\",\"items\":[{\"productId\":null,\"amount\":1}]}",
        "{\"paymentMethod\":\"Alipay\",\"items\":[{\"productId\":1,\"amount\":null}]}",
        "{\"paymentMethod\":\"Alipay\",\"items\":[{\"productId\":1,\"amount\":0}]}",
        "{\"paymentMethod\":\"Alipay\",\"items\":[{\"productId\":1,\"amount\":-1}]}",
        "{\"paymentMethod\":\"   \",\"items\":[{\"productId\":1,\"amount\":1}]}"
    })
    void checkoutRejectsInvalidOrderBeforeCallingService(String body) throws Exception {
        String token = tokenUtilForTest().generateToken(1);

        mockMvc.perform(post("/api/cart/checkout")
                .cookie(new Cookie("token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.msg").isNotEmpty())
            .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(orderService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "null",
        "{\"paymentMethod\":\"Alipay\""
    })
    void checkoutWrapsUnreadableRequestBodyAsStableBadRequest(String body) throws Exception {
        String token = tokenUtilForTest().generateToken(1);

        mockMvc.perform(post("/api/cart/checkout")
                .cookie(new Cookie("token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.msg").isNotEmpty())
            .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(orderService);
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
