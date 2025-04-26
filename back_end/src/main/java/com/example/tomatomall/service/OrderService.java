package com.example.tomatomall.service;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.vo.OrdersVO;

public interface OrderService {
  public OrdersVO addOrder(Integer userId, CreateOrderDTO dto);
}
