package com.example.tomatomall.repository;

import com.example.tomatomall.po.StockPile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockPileRepository extends JpaRepository<StockPile, Integer> {
  Optional<StockPile> findByProductId(int id);

  void deleteByProductId(int id);
}
