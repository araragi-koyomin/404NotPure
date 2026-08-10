package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.OrderItem;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.service.PaymentServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeMap;

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
    Integer parsedOrderId = parseOrderId(orderId);
    String normalizedTradeNo = normalizeTradeNo(alipayTradeNo);
    BigDecimal notifiedAmount = parseAmount(amount);

    Orders order = ordersRepository.findById(parsedOrderId)
        .orElseThrow(TomatoException::orderNotExist);
    if (order.getTotalAmount().compareTo(notifiedAmount) != 0) {
      throw TomatoException.paymentAmountMismatch();
    }

    if (OrderStatus.PAID.name().equals(order.getStatus())) {
      ensureSameTradeNumber(order, normalizedTradeNo);
      return;
    }
    if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
      throw TomatoException.illegalOrderStatusForPayment();
    }

    Map<Integer, Integer> quantitiesByProduct = aggregateOrderItems(order);
    Timestamp paidTime = new Timestamp(System.currentTimeMillis());
    int updatedRows;
    try {
      updatedRows = ordersRepository.markPaidIfPending(
          parsedOrderId,
          normalizedTradeNo,
          paidTime,
          OrderStatus.PENDING.name(),
          OrderStatus.PAID.name()
      );
    } catch (DataIntegrityViolationException exception) {
      throw TomatoException.paymentTradeConflict();
    }

    if (updatedRows == 0) {
      Orders current = ordersRepository.findByIdForUpdate(parsedOrderId)
          .orElseThrow(TomatoException::orderNotExist);
      if (OrderStatus.PAID.name().equals(current.getStatus())) {
        ensureSameTradeNumber(current, normalizedTradeNo);
        return;
      }
      throw TomatoException.illegalOrderStatusForPayment();
    }
    if (updatedRows != 1) {
      throw TomatoException.paymentTradeConflict();
    }

    for (Map.Entry<Integer, Integer> entry : quantitiesByProduct.entrySet()) {
      int productId = entry.getKey();
      int quantity = entry.getValue();
      int releasedRows = stockPileRepository.releaseFrozenStockIfAvailable(productId, quantity);
      if (releasedRows == 0) {
        if (stockPileRepository.countByProductId(productId) > 1) {
          throw TomatoException.stockDataInconsistent();
        }
        throw TomatoException.frozenStockReleaseFailure();
      }
      if (releasedRows != 1) {
        throw TomatoException.stockDataInconsistent();
      }
    }
  }

  private Integer parseOrderId(String orderId) {
    if (orderId == null || orderId.trim().isEmpty()) {
      throw TomatoException.invalidPaymentNotification();
    }
    try {
      int parsed = Integer.parseInt(orderId.trim());
      if (parsed <= 0) {
        throw TomatoException.invalidPaymentNotification();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw TomatoException.invalidPaymentNotification();
    }
  }

  private String normalizeTradeNo(String tradeNo) {
    if (tradeNo == null || tradeNo.trim().isEmpty() || tradeNo.trim().length() > 64) {
      throw TomatoException.invalidPaymentNotification();
    }
    return tradeNo.trim();
  }

  private BigDecimal parseAmount(String amount) {
    if (amount == null || amount.trim().isEmpty()) {
      throw TomatoException.invalidPaymentNotification();
    }
    try {
      BigDecimal parsed = new BigDecimal(amount.trim());
      if (parsed.signum() <= 0) {
        throw TomatoException.invalidPaymentNotification();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw TomatoException.invalidPaymentNotification();
    }
  }

  private Map<Integer, Integer> aggregateOrderItems(Orders order) {
    if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
      throw TomatoException.invalidPaymentNotification();
    }
    Map<Integer, Integer> quantitiesByProduct = new TreeMap<>();
    try {
      for (OrderItem item : order.getOrderItems()) {
        if (item == null || item.getProduct() == null || item.getQuantity() == null
            || item.getQuantity() <= 0) {
          throw TomatoException.invalidPaymentNotification();
        }
        quantitiesByProduct.merge(item.getProduct().getId(), item.getQuantity(), Math::addExact);
      }
    } catch (ArithmeticException exception) {
      throw TomatoException.invalidPaymentNotification();
    }
    return quantitiesByProduct;
  }

  private void ensureSameTradeNumber(Orders order, String tradeNo) {
    if (!tradeNo.equals(order.getAlipayTradeNo())) {
      throw TomatoException.paymentTradeConflict();
    }
  }
}
