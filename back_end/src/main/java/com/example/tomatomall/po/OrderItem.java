package com.example.tomatomall.po;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table
@Data
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "order_id", nullable = false)
  private Orders order;

  @ManyToOne
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  private Integer quantity;
}
