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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  public OrdersVO addOrder(Integer userId, CreateOrderDTO dto) {
    Account persistedUser = userRepository.findById(userId).orElseThrow(TomatoException::userNotExist);

    List<Integer> productIds = dto.getItems().stream()
        .map(CreateOrderDTO.OrderItemDTO::getProductId)
        .collect(Collectors.toList());
    Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
        .collect(Collectors.toMap(Product::getId, p -> p));
    Map<Integer, StockPile> stockPileMap = stockPileRepository.findByProductIdIn(productIds).stream()
        .collect(Collectors.toMap(StockPile::getProductId, p -> p));

    Orders order = new Orders();
    order.setAccount(persistedUser);
    order.setPaymentMethod(dto.getPaymentMethod());
    order.setStatus("PENDING");
    order.setCreateTime(new Timestamp(System.currentTimeMillis()));

    List<OrderItem> orderItems = new ArrayList<>();
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (CreateOrderDTO.OrderItemDTO item : dto.getItems()) {
      Product product = productMap.get(item.getProductId());
      if (product == null) {
        throw TomatoException.productNotExist();
      }

      StockPile stockPile = stockPileMap.get(item.getProductId());
      if (stockPile == null || stockPile.getAmount() < item.getAmount()) {
        throw TomatoException.stockNotEnough();
      }

      stockPile.setAmount(stockPile.getAmount() - item.getAmount());
      stockPile.setFrozen(stockPile.getFrozen() + item.getAmount());
      stockPileRepository.save(stockPile);

      BigDecimal price = product.getPrice().multiply(BigDecimal.valueOf(item.getAmount()));
      totalAmount = totalAmount.add(price);

      OrderItem orderItem = new OrderItem();
      orderItem.setProduct(product);
      orderItem.setQuantity(item.getAmount());
      orderItem.setOrder(order);
      orderItems.add(orderItem);
    }

    order.setOrderItems(orderItems);
    order.setTotalAmount(totalAmount);

    ordersRepository.save(order);

    return order.toVO();
  }
}
