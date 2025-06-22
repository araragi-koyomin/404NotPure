package com.example.tomatomall.repository;

import com.example.tomatomall.po.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findByUsername(String username);
    Optional<Account> findByTelephone(String telephone);
    List<Account> findByRole(String role);
    Optional<Account> findById(Integer id);
}
