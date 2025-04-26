package com.example.tomatomall.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Data
public class CreateOrderDTO {
  @NotNull
  private String paymentMethod;

  @NotEmpty
  private List<OrderItemDTO> items;

  @Data
  public static class OrderItemDTO {
    @NotNull
    private Integer productId;

    @Min(1)
    private Integer amount;
  }
}