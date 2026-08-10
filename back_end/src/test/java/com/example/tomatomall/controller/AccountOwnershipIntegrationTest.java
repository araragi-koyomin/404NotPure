package com.example.tomatomall.controller;

import com.example.tomatomall.po.Account;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AccountOwnershipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenUtil tokenUtil;
    @Autowired private UserRepository userRepository;

    private Account owner;
    private Account otherUser;
    private Account administrator;

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        owner = createAccount("account-owner-" + marker, "USER", "18", 12);
        otherUser = createAccount("account-other-" + marker, "USER", "19", 34);
        administrator = createAccount("account-admin-" + marker, "ADMIN", "20", 56);
    }

    @Test
    void ownerCanReadOwnProfileUsingCookie() throws Exception {
        mockMvc.perform(get("/api/accounts/{username}", owner.getUsername())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.username").value(owner.getUsername()));
    }

    @Test
    void ownerCanReadOwnProfileUsingTelephoneLoginIdentifier() throws Exception {
        mockMvc.perform(get("/api/accounts/{identifier}", owner.getTelephone())
                        .cookie(new Cookie("token", tokenUtil.generateToken(owner.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.username").value(owner.getUsername()));
    }

    @Test
    void anonymousLogoutExpiresAuthenticationCookie() throws Exception {
        mockMvc.perform(post("/api/accounts/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("token="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax")
                )));
    }

    @Test
    void otherUserCannotReadOwnersProfile() throws Exception {
        mockMvc.perform(get("/api/accounts/{username}", owner.getUsername())
                        .header("token", tokenUtil.generateToken(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));
    }

    @Test
    void ownerCanReadOwnPoints() throws Exception {
        mockMvc.perform(get("/api/accounts/{username}/points", owner.getUsername())
                        .header("token", tokenUtil.generateToken(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value(12));
    }

    @Test
    void otherUserCannotReadOwnersPoints() throws Exception {
        mockMvc.perform(get("/api/accounts/{username}/points", owner.getUsername())
                        .cookie(new Cookie("token", tokenUtil.generateToken(otherUser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));
    }

    @Test
    void ownerCannotDirectlyChangePoints() throws Exception {
        assertPointsUpdateRejected(owner);
    }

    @Test
    void administratorCannotDirectlyChangeAnotherUsersPoints() throws Exception {
        assertPointsUpdateRejected(administrator);
    }

    private void assertPointsUpdateRejected(Account actor) throws Exception {
        mockMvc.perform(patch("/api/accounts/{username}/points", owner.getUsername())
                        .header("token", tokenUtil.generateToken(actor.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));

        userRepository.flush();
        assertEquals(12, userRepository.findById(owner.getId()).orElseThrow(AssertionError::new).getPoints());
    }

    private Account createAccount(String username, String role, String phonePrefix, int points) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("test-password");
        account.setName(username);
        account.setRole(role);
        account.setPoints(points);
        account.setTelephone(phonePrefix + String.format("%09d", Math.abs(username.hashCode()) % 1_000_000_000));
        return userRepository.saveAndFlush(account);
    }
}
