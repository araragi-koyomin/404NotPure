package com.example.tomatomall.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.Valid;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Data
public class CreateOrderDTO {
  @NotBlank
  private String paymentMethod;

  @NotEmpty
  @Valid
  private List<@NotNull OrderItemDTO> items;

  @Data
  public static class OrderItemDTO {
    @NotNull
    private Integer productId;

    @NotNull
    @Min(1)
    private Integer amount;
  }
}
