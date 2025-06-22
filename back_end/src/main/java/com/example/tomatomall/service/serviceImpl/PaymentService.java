package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.service.PaymentServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/**
 * 支付服务实现类
 * 处理订单支付状态更新和库存扣减
 */
@Service
public class PaymentService implements PaymentServiceImpl {

  @Autowired
  private OrdersRepository ordersRepository;
  @Autowired
  private StockPileRepository stockPileRepository;

  /**
   * 更新订单支付状态
   * @param orderId 订单ID
   * @param alipayTradeNo 支付宝交易号
   * @param amount 支付金额
   * @throws TomatoException 订单不存在或状态异常时抛出
   */
  @Transactional
  @Override
  public void updateOrderStatus(String orderId, String alipayTradeNo, String amount) {
    // 1. 幂等性检查：确保订单状态未支付
    Orders order = ordersRepository.findById(Integer.parseInt(orderId))
        .orElseThrow(() -> new RuntimeException("订单不存在"));
    if ("PAID".equals(order.getStatus())) {
      return; // 已处理过，直接返回
    }

    // 2. 更新订单状态
    order.setStatus("PAID");
    order.setCreateTime(new Timestamp(System.currentTimeMillis())); // 支付时间
    ordersRepository.save(order);

    // 3. 扣减冻结库存
    order.getOrderItems().forEach(item -> {
      StockPile stock = stockPileRepository.findByProductId(item.getProduct().getId())
          .orElseThrow(() -> new RuntimeException("库存记录不存在"));
      stock.setFrozen(stock.getFrozen() - item.getQuantity());
      stockPileRepository.save(stock);
    });
  }
}
