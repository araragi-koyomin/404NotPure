package com.example.tomatomall.repository;

import com.example.tomatomall.po.StockPile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockPileRepository extends JpaRepository<StockPile, Integer> {
  Optional<StockPile> findByProductId(int id);

  void deleteByProductId(int id);

  long countByProductId(int productId);

  @Modifying
  @Query(value = "update stockpile stock_row "
      + "left join stockpile duplicate_row "
      + "on duplicate_row.product_id = stock_row.product_id and duplicate_row.id <> stock_row.id "
      + "set stock_row.amount = stock_row.amount - :quantity, "
      + "stock_row.frozen = stock_row.frozen + :quantity "
      + "where stock_row.product_id = :productId "
      + "and stock_row.amount >= :quantity and duplicate_row.id is null",
      nativeQuery = true)
  int freezeStockIfAvailable(
      @Param("productId") int productId,
      @Param("quantity") int quantity
  );
}
