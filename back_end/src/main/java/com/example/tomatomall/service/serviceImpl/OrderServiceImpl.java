package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.vo.OrdersVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * 处理订单创建及相关业务逻辑
 */
@Service
public class OrderServiceImpl implements OrderService {

  @Autowired
  UserRepository userRepository;

  @Autowired
  OrdersRepository ordersRepository;

  @Autowired
  ProductRepository productRepository;

  @Autowired
  StockPileRepository stockPileRepository;

  /**
   * 创建新订单
   * @param userId 用户ID
   * @param dto 订单创建DTO
   * @return 订单视图对象
   * @throws TomatoException 用户不存在、商品不存在或库存不足时抛出
   */
  @Override
  @Transactional
  public OrdersVO addOrder(Integer userId, CreateOrderDTO dto) {
    validateOrderRequest(dto);
    Map<Integer, Integer> quantitiesByProduct = aggregateQuantities(dto);
    Account persistedUser = userRepository.findById(userId).orElseThrow(TomatoException::userNotExist);

    List<Integer> productIds = new ArrayList<>(quantitiesByProduct.keySet());
    Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
        .collect(Collectors.toMap(Product::getId, p -> p));

    if (productMap.size() != productIds.size()) {
      throw TomatoException.productNotExist();
    }

    Orders order = new Orders();
    order.setAccount(persistedUser);
    order.setPaymentMethod(dto.getPaymentMethod());
    order.setStatus("PENDING");
    order.setCreateTime(new Timestamp(System.currentTimeMillis()));

    List<OrderItem> orderItems = new ArrayList<>();
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (Map.Entry<Integer, Integer> entry : quantitiesByProduct.entrySet()) {
      Integer productId = entry.getKey();
      Integer quantity = entry.getValue();
      Product product = productMap.get(productId);

      int updatedRows = stockPileRepository.freezeStockIfAvailable(productId, quantity);
      if (updatedRows == 0) {
        if (stockPileRepository.countByProductId(productId) > 1) {
          throw TomatoException.stockDataInconsistent();
        }
        throw TomatoException.stockNotEnough();
      }
      if (updatedRows != 1) {
        throw TomatoException.stockDataInconsistent();
      }

      BigDecimal price = product.getPrice().multiply(BigDecimal.valueOf(quantity));
      totalAmount = totalAmount.add(price);

      OrderItem orderItem = new OrderItem();
      orderItem.setProduct(product);
      orderItem.setQuantity(quantity);
      orderItem.setOrder(order);
      orderItems.add(orderItem);
    }

    order.setOrderItems(orderItems);
    order.setTotalAmount(totalAmount);

    ordersRepository.save(order);

    return order.toVO();
  }

  private void validateOrderRequest(CreateOrderDTO dto) {
    if (dto == null || dto.getPaymentMethod() == null || dto.getPaymentMethod().trim().isEmpty()
        || dto.getItems() == null || dto.getItems().isEmpty()) {
      throw TomatoException.invalidOrderRequest();
    }

    for (CreateOrderDTO.OrderItemDTO item : dto.getItems()) {
      if (item == null || item.getProductId() == null || item.getAmount() == null || item.getAmount() <= 0) {
        throw TomatoException.invalidOrderRequest();
      }
    }
  }

  private Map<Integer, Integer> aggregateQuantities(CreateOrderDTO dto) {
    Map<Integer, Integer> quantitiesByProduct = new TreeMap<>();
    try {
      for (CreateOrderDTO.OrderItemDTO item : dto.getItems()) {
        quantitiesByProduct.merge(item.getProductId(), item.getAmount(), Math::addExact);
      }
    } catch (ArithmeticException exception) {
      throw TomatoException.invalidOrderRequest();
    }
    return quantitiesByProduct;
  }
}
