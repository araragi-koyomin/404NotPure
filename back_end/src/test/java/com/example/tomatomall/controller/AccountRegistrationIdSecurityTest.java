package com.example.tomatomall.controller;

import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AccountRegistrationIdSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void publicRegistrationCannotOverwriteAccountSelectedByClientId() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String phoneSuffix = String.format("%09d", Math.abs(suffix.hashCode()) % 1_000_000_000);

        Account existing = new Account();
        existing.setUsername("existing-" + suffix);
        existing.setPassword("existing-password-hash");
        existing.setName("Existing Account");
        existing.setRole("ADMIN");
        existing.setPoints(777);
        existing.setTelephone("17" + phoneSuffix);
        existing = userRepository.saveAndFlush(existing);

        Integer existingId = existing.getId();
        String existingUsername = existing.getUsername();
        String attackerUsername = "attacker-" + suffix;

        ObjectNode registration = objectMapper.createObjectNode()
            .put("id", existingId)
            .put("username", attackerUsername)
            .put("password", "attacker-password")
            .put("name", "Attacker Account")
            .put("telephone", "18" + phoneSuffix)
            .put("role", "ADMIN")
            .put("points", 99999);

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(registration)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"));

        userRepository.flush();

        Account unchanged = userRepository.findById(existingId).orElseThrow(AssertionError::new);
        Account created = userRepository.findByUsername(attackerUsername).orElseThrow(AssertionError::new);

        assertEquals(existingUsername, unchanged.getUsername());
        assertEquals("existing-password-hash", unchanged.getPassword());
        assertEquals("ADMIN", unchanged.getRole());
        assertEquals(777, unchanged.getPoints());
        assertNotEquals(existingId, created.getId());
        assertEquals("USER", created.getRole());
        assertEquals(0, created.getPoints());
    }
}
