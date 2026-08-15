package com.example.tomatomall.controller;

import com.example.tomatomall.service.OrderLifecycleService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.OrdersVO;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/orders")
public class OrderLifecycleController {
    private final OrderLifecycleService orderLifecycleService;
    private final TokenUtil tokenUtil;

    public OrderLifecycleController(OrderLifecycleService orderLifecycleService, TokenUtil tokenUtil) {
        this.orderLifecycleService = orderLifecycleService;
        this.tokenUtil = tokenUtil;
    }

    @PostMapping("/{orderId}/cancel")
    public Response<OrdersVO> cancelOrder(@PathVariable int orderId, HttpServletRequest request) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        return Response.buildSuccess(orderLifecycleService.cancelOrder(userId, orderId));
    }
}
