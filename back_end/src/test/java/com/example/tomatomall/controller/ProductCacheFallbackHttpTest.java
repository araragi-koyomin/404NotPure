package com.example.tomatomall.controller;

import com.example.tomatomall.exception.ExceptionHandle;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.cache.ProductCacheFallbackRejectedException;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductCacheFallbackHttpTest {

    @Mock
    private ProductService productService;

    @Mock
    private TokenUtil tokenUtil;

    @InjectMocks
    private ProductController productController;

    @Test
    void exhaustedDatabaseFallbackReturnsHttp503WithUnifiedResponse() throws Exception {
        when(productService.getProductById(71)).thenThrow(new ProductCacheFallbackRejectedException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new ExceptionHandle())
                .build();

        mockMvc.perform(get("/api/products/71"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("503"))
                .andExpect(jsonPath("$.msg").value("商品服务暂时繁忙，请稍后重试"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
