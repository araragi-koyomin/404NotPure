package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplRoleSecurityTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AccountServiceImpl(
                userRepository,
                mock(TokenUtil.class),
                passwordEncoder
        );
    }

    @Test
    void publicRegistrationAlwaysCreatesUserEvenWhenAdminIsRequested() {
        Account requested = account("new-user", "ADMIN");
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(userRepository.findByTelephone("13800000000")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");

        service.register(requested);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(userRepository).save(saved.capture());
        assertEquals("USER", saved.getValue().getRole());
        assertEquals("encoded-password", saved.getValue().getPassword());
        assertEquals(0, saved.getValue().getPoints());
    }

    @Test
    void profileUpdateCannotPromoteExistingUser() {
        Account persisted = account("existing-user", "USER");
        when(userRepository.findByUsername("existing-user")).thenReturn(Optional.of(persisted));
        AccountUpdateDTO update = update("existing-user", "ADMIN", "new name");

        service.update(update);

        assertEquals("USER", persisted.getRole());
        assertEquals("new name", persisted.getName());
    }

    @Test
    void profileUpdateDoesNotDowngradeExistingAdmin() {
        Account persisted = account("existing-admin", "ADMIN");
        when(userRepository.findByUsername("existing-admin")).thenReturn(Optional.of(persisted));
        AccountUpdateDTO update = update("existing-admin", "USER", "renamed admin");

        service.update(update);

        assertEquals("ADMIN", persisted.getRole());
        assertEquals("renamed admin", persisted.getName());
    }

    private Account account(String username, String role) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("raw-password");
        account.setName(username);
        account.setTelephone("13800000000");
        account.setRole(role);
        return account;
    }

    private AccountUpdateDTO update(String username, String requestedRole, String name) {
        AccountUpdateDTO update = new AccountUpdateDTO();
        update.setUsername(username);
        update.setRole(requestedRole);
        update.setName(name);
        return update;
    }
}
