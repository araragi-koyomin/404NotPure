package com.example.tomatomall.controller;

import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Carts;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.CartsRepository;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class CartOwnershipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenUtil tokenUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockPileRepository stockPileRepository;
    @Autowired private CartsRepository cartsRepository;
    @Autowired private OrdersRepository ordersRepository;

    private Account owner;
    private Account otherUser;
    private Account administrator;
    private Carts cartItem;
    private Product product;
    private long cartCountAfterSetup;

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        owner = createAccount("cart-owner-" + marker, "USER", "15");
        otherUser = createAccount("cart-other-" + marker, "USER", "16");
        administrator = createAccount("cart-admin-" + marker, "ADMIN", "17");

        product = Product.builder()
                .title("cart-ownership-" + marker)
                .price(new BigDecimal("19.99"))
                .rate(5.0)
                .description("test")
                .detail("test")
                .cover("test")
                .category("test")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
        product = productRepository.saveAndFlush(product);
        stockPileRepository.saveAndFlush(StockPile.builder()
                .productId(product.getId())
                .amount(8)
                .frozen(0)
                .build());

        cartItem = new Carts();
        cartItem.setAccount(owner);
        cartItem.setProduct(product);
        cartItem.setQuantity(1);
        cartItem = cartsRepository.saveAndFlush(cartItem);
        cartCountAfterSetup = cartsRepository.count();
    }

    @Test
    void ownerCanUpdateOwnCartItemUsingCookie() throws Exception {
        mockMvc.perform(patch("/api/cart/{id}", cartItem.getCartItemId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        assertEquals(2, reloadCartItem().getQuantity());
        assertStockUnchanged();
    }

    @Test
    void anonymousUserCannotReadCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));

        assertEquals(cartCountAfterSetup, cartsRepository.count());
        assertStockUnchanged();
    }

    @Test
    void conflictingIdentitiesCannotAddToCart() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId())))
                        .header("token", tokenUtil.generateToken(otherUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product.getId() + ",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));

        assertEquals(cartCountAfterSetup, cartsRepository.count());
        assertEquals(1, reloadCartItem().getQuantity());
        assertStockUnchanged();
    }

    @Test
    void anonymousUserCannotCheckoutCart() throws Exception {
        long ordersBefore = ordersRepository.count();

        mockMvc.perform(post("/api/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"Alipay\",\"items\":[{\"productId\":"
                                + product.getId() + ",\"amount\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));

        assertEquals(ordersBefore, ordersRepository.count());
        assertEquals(1, reloadCartItem().getQuantity());
        assertStockUnchanged();
    }

    @Test
    void otherUserCannotUpdateOwnersCartItemUsingHeader() throws Exception {
        mockMvc.perform(patch("/api/cart/{id}", cartItem.getCartItemId())
                        .header("token", tokenUtil.generateToken(otherUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(1, reloadCartItem().getQuantity());
        assertStockUnchanged();
    }

    @Test
    void otherUserCannotDeleteOwnersCartItem() throws Exception {
        mockMvc.perform(delete("/api/cart/{id}", cartItem.getCartItemId())
                        .header("token", tokenUtil.generateToken(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));

        assertTrue(cartsRepository.findById(cartItem.getCartItemId()).isPresent());
        assertStockUnchanged();
    }

    @Test
    void administratorCannotDeleteAnotherUsersCartItem() throws Exception {
        mockMvc.perform(delete("/api/cart/{id}", cartItem.getCartItemId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(administrator.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));

        assertTrue(cartsRepository.findById(cartItem.getCartItemId()).isPresent());
        assertStockUnchanged();
    }

    @Test
    void ownerCanDeleteOwnCartItem() throws Exception {
        mockMvc.perform(delete("/api/cart/{id}", cartItem.getCartItemId())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        assertTrue(cartsRepository.findById(cartItem.getCartItemId()).isEmpty());
        assertStockUnchanged();
    }

    private Account createAccount(String username, String role, String phonePrefix) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("test-password");
        account.setName(username);
        account.setRole(role);
        account.setPoints(0);
        account.setTelephone(phonePrefix + String.format("%09d", Math.abs(username.hashCode()) % 1_000_000_000));
        return userRepository.saveAndFlush(account);
    }

    private Carts reloadCartItem() {
        cartsRepository.flush();
        return cartsRepository.findById(cartItem.getCartItemId()).orElseThrow(AssertionError::new);
    }

    private void assertStockUnchanged() {
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(8, stock.getAmount());
        assertEquals(0, stock.getFrozen());
    }
}
