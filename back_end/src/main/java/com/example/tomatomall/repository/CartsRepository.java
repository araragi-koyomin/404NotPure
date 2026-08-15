package com.example.tomatomall.repository;

import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Carts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartsRepository extends JpaRepository<Carts, Integer> {
    List<Carts> findByAccount(Account account);
    Optional<Carts> findByAccountIdAndProductId(Integer accountId, int productId);
}
