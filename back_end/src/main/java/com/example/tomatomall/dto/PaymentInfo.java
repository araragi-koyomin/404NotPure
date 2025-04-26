package com.example.tomatomall.dto;

import lombok.Data;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
@Data
public class PaymentInfo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "order_id", unique = true)
  private Integer orderId;

  private String tradeNO;
  private String paymentStatus;
  private Timestamp payTime;
}
