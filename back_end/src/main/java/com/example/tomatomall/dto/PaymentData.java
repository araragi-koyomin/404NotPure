package com.example.tomatomall.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Data
public class PaymentData {
  private String paymentForm;
  private String orderId;
  private String totalAmount;
  private String paymentMethod;

}
