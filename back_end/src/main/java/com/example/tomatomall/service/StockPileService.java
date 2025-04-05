package com.example.tomatomall.service;

import com.example.tomatomall.dto.StockPileUpdateRequest;
import com.example.tomatomall.vo.StockPileVO;

public interface StockPileService {
  StockPileVO getStockPileById(int id);

  public String adjust(StockPileUpdateRequest request, int product_id);

}