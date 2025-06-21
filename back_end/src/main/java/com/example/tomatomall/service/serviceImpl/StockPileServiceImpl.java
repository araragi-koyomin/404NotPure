package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.StockPileUpdateRequest;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.service.StockPileService;
import com.example.tomatomall.vo.StockPileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 库存服务实现类
 * 处理库存查询和调整
 */
@Service
public class StockPileServiceImpl implements StockPileService {

  @Autowired
  StockPileRepository stockPileRepository;

  /**
   * 根据商品ID获取库存信息
   * @param id 商品ID
   * @return 库存视图对象
   * @throws TomatoException 库存记录不存在时抛出
   */
  @Override
  public StockPileVO getStockPileById(int id) {
    StockPile stockPile = stockPileRepository.findByProductId(id)
        .orElseThrow(TomatoException::productNotExist);  // 统一处理Optional
    return stockPile.toVO();
  }

  /**
   * 调整库存
   * @param request 库存调整请求
   * @param productId 商品ID
   * @return 操作结果
   * @throws TomatoException 库存记录不存在时抛出
   */
  @Override
  public String adjust(StockPileUpdateRequest request, int productId) {
    StockPile stockPile = stockPileRepository.findByProductId(productId)
        .orElseThrow(TomatoException::productNotExist);  // 明确处理不存在的情况

    if (request.getAmount() != null) {
      stockPile.setAmount(request.getAmount());
    }

    stockPileRepository.save(stockPile);
    return "调整库存成功";
  }
}
