package com.example.tomatomall.dto;

import lombok.Data;

@Data
public class StockPileUpdateRequest {
  private Integer amount;  // 仅允许更新amount字段
}
