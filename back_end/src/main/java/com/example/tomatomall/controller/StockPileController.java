package com.example.tomatomall.controller;

import com.example.tomatomall.dto.StockPileUpdateRequest;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.service.StockPileService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.Response;
import com.example.tomatomall.vo.StockPileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 库存管理控制器
 * 提供库存查询和调整功能
 */
@RestController
@RequestMapping("/api/products/stockpile")
public class StockPileController {

  @Autowired
  private StockPileService stockPileService;

  @Autowired
  private TokenUtil tokenUtil;

  /**
   * 根据商品ID获取库存信息
   * @param productId 商品ID
   * @return 库存信息视图对象
   */
  @GetMapping("/{productId}")
  public Response<StockPileVO> getStockPile(@PathVariable int productId) {
    StockPileVO stockPileVO = stockPileService.getStockPileById(productId);
    return Response.buildSuccess(stockPileVO);
  }

  /**
   * 调整商品库存（管理员权限）
   * @param productId 商品ID
   * @param request 库存更新请求
   * @param httpRequest HTTP请求对象
   * @return 操作结果
   * @throws TomatoException 未登录或权限不足时抛出
   */
  @PatchMapping("/{productId}")
  public Response<String> adjustStockPile(
      @PathVariable int productId,
      @RequestBody StockPileUpdateRequest request,  // 改用DTO接收请求体
      HttpServletRequest httpRequest
  ) {
    tokenUtil.validateAdminRole(httpRequest);
    return Response.buildSuccess(stockPileService.adjust(request, productId));
  }
}
