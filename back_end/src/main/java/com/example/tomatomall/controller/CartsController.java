package com.example.tomatomall.controller;

import com.example.tomatomall.dto.AddProductToCartDTO;
import com.example.tomatomall.dto.CartItemUpdateDTO;
import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.order.OrderCheckoutResult;
import com.example.tomatomall.service.order.OrderIdempotencyKey;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.CartsListVO;
import com.example.tomatomall.vo.CartsVO;
import com.example.tomatomall.vo.OrdersVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 购物车管理控制器
 * 提供购物车商品添加、删除、更新及结算功能
 */
@RestController
@RequestMapping("/api/cart")
public class CartsController {
    @Autowired
    private CartsService cartsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TokenUtil tokenUtil;

    /**
     * 添加商品到购物车
     * @param dto 添加商品数据传输对象
     * @param request HTTP请求对象
     * @return 购物车商品视图对象
     * @throws TomatoException 未登录时抛出
     */
    @PostMapping()
    public Response<CartsVO> addProductToCart(@Valid @RequestBody AddProductToCartDTO dto, HttpServletRequest request) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        CartsVO cartItemVO = cartsService.addProductToCart(userId, dto.getProductId(), dto.getQuantity());
        return Response.buildSuccess(cartItemVO);
    }

    /**
     * 从购物车删除商品
     * @param cartItemId 购物车商品ID
     * @param request HTTP请求对象
     * @return 操作结果
     * @throws TomatoException 未登录时抛出
     */
    @DeleteMapping("/{cartItemId}")
    public Response<String> deleteCartItem(@PathVariable int cartItemId, HttpServletRequest request) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        String result = cartsService.deleteCartItem(userId, cartItemId);
        return Response.buildSuccess(result);
    }

    /**
     * 更新购物车商品数量
     * @param cartItemId 购物车商品ID
     * @param dto 商品数量更新数据传输对象
     * @param request HTTP请求对象
     * @return 操作结果
     * @throws TomatoException 未登录时抛出
     */
    @PatchMapping("/{cartItemId}")
    public Response<String> updateCartItemQuantity(@PathVariable int cartItemId, @Valid @RequestBody CartItemUpdateDTO dto, HttpServletRequest request) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        String result = cartsService.updateCartItemQuantity(userId, cartItemId, dto.getQuantity());
        return Response.buildSuccess(result);
    }

    /**
     * 获取购物车列表
     * @param request HTTP请求对象
     * @return 购物车列表视图对象
     * @throws TomatoException 未登录时抛出
     */
    @GetMapping()
    public Response<CartsListVO> getCarts(HttpServletRequest request) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        CartsListVO listVO=cartsService.getCartItems(userId);
        return Response.buildSuccess(listVO);
    }

    /**
     * 结算购物车创建订单
     * @param request HTTP请求对象
     * @param dto 创建订单数据传输对象
     * @return 订单视图对象
     * @throws TomatoException 未登录时抛出
     */
    @PostMapping("/checkout")
    public ResponseEntity<Response<OrdersVO>> createOrder(
            HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderDTO dto
    ) {
        int userId = tokenUtil.getUserIdFromRequest(request);
        String canonicalKey = OrderIdempotencyKey.requireCanonical(idempotencyKey);
        OrderCheckoutResult result = orderService.addOrder(userId, canonicalKey, dto);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.isReplayed()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(Response.buildSuccess(result.getOrder()));
    }

}
