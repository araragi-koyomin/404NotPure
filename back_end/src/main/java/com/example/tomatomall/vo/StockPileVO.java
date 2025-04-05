package com.example.tomatomall.vo;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Data
public class StockPileVO {
  private Integer id;
  private Integer productId;
  private Integer amount;
  private Integer frozen;
}
