package com.example.tomatomall.controller;

import com.example.tomatomall.dto.StockPileUpdateRequest;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.service.StockPileService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.Response;
import com.example.tomatomall.vo.StockPileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/products/stockpile")
public class StockPileController {

  @Autowired
  private StockPileService stockPileService;

  @Autowired
  private TokenUtil tokenUtil;

  @GetMapping("/{productId}")
  public Response<StockPileVO> getStockPile(@PathVariable int productId) {
    StockPileVO stockPileVO = stockPileService.getStockPileById(productId);
    return Response.buildSuccess(stockPileVO);
  }

  @PatchMapping("/{productId}")
  public Response<String> adjustStockPile(
      @PathVariable int productId,
      @RequestBody StockPileUpdateRequest request,  // 改用DTO接收请求体
      HttpServletRequest httpRequest
  ) {
    // ...权限检查逻辑不变...
    return Response.buildSuccess(stockPileService.adjust(request, productId));
  }
}
