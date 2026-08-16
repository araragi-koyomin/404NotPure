package com.example.tomatomall.controller;

import com.example.tomatomall.configure.SecurityConfig;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.order.OrderCheckoutResult;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.OrdersVO;
import com.example.tomatomall.exception.OrderCheckoutConflictException;
import com.example.tomatomall.exception.OrderCheckoutUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartsController.class)
@Import(SecurityConfig.class)
class OrderCheckoutIdempotencyContractTest {

    private static final String VALID_KEY = "123e4567-e89b-12d3-a456-426614174000";
    private static final String VALID_BODY = "{\"paymentMethod\":\"Alipay\","
            + "\"items\":[{\"productId\":1,\"amount\":1}]}";

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
    @NullSource
    @ValueSource(strings = {
            "",
            "123E4567-E89B-12D3-A456-426614174000",
            "123e4567-e89b-12d3-a456-42661417400",
            "not-a-uuid"
    })
    void checkoutRejectsMissingOrNonCanonicalIdempotencyKey(String idempotencyKey) throws Exception {
        String token = tokenUtilForTest().generateToken(1);
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                post("/api/cart/checkout")
                        .cookie(new Cookie("token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(orderService);
    }

    @Test
    void replayKeepsSuccessfulBodyAndAddsReplayHeader() throws Exception {
        OrdersVO order = new OrdersVO();
        order.setOrderId(42);
        when(orderService.addOrder(anyInt(), eq(VALID_KEY), any()))
                .thenReturn(OrderCheckoutResult.replayed(order));

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.orderId").value(42));
    }

    @Test
    void conflictAndDatabaseTimeoutUseActualHttpStatusWithResponseWrapper() throws Exception {
        when(orderService.addOrder(anyInt(), eq(VALID_KEY), any()))
                .thenThrow(new OrderCheckoutConflictException());
        mockMvc.perform(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409"))
                .andExpect(jsonPath("$.data").doesNotExist());

        when(orderService.addOrder(anyInt(), eq(VALID_KEY), any()))
                .thenThrow(new OrderCheckoutUnavailableException());
        mockMvc.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("503"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        String token = tokenUtilForTest().generateToken(1);
        return post("/api/cart/checkout")
                .cookie(new Cookie("token", token))
                .header("Idempotency-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
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
