package com.example.tomatomall.controller;

import com.example.tomatomall.dto.AddProductToCartDTO;
import com.example.tomatomall.dto.CartItemUpdateDTO;
import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.CartsListVO;
import com.example.tomatomall.vo.CartsVO;
import com.example.tomatomall.vo.OrdersVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/cart")
public class CartsController {
    @Autowired
    private CartsService cartsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TokenUtil tokenUtil;

    @PostMapping()
    public Response<CartsVO> addProductToCart(@RequestBody AddProductToCartDTO dto, HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }

        int userId;
        try {
            userId = TokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw TomatoException.notLogin();
        }

        CartsVO cartItemVO = cartsService.addProductToCart(userId, dto.getProductId(), dto.getQuantity());
        return Response.buildSuccess(cartItemVO);
    }

    @DeleteMapping("/{cartItemId}")
    public Response<String> deleteCartItem(@PathVariable int cartItemId, HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }

        String result = cartsService.deleteCartItem(cartItemId);
        return Response.buildSuccess(result);
    }

    @PatchMapping("/{cartItemId}")
    public Response<String> updateCartItemQuantity(@PathVariable int cartItemId, @RequestBody CartItemUpdateDTO dto, HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }

        String result = cartsService.updateCartItemQuantity(cartItemId, dto.getQuantity());
        return Response.buildSuccess(result);
    }

    @GetMapping()
    public Response<CartsListVO> getCarts(HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }

        int userId;
        try {
            userId = TokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw TomatoException.notLogin();
        }

        CartsListVO listVO=cartsService.getCartItems(userId);
        return Response.buildSuccess(listVO);
    }

    @PostMapping("/checkout")
    public Response<OrdersVO> createOrder(HttpServletRequest request, @RequestBody CreateOrderDTO dto) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }

        int userId;
        try {
            userId = TokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw TomatoException.notLogin();
        }

        OrdersVO ordersVO = orderService.addOrder(userId, dto);
        return Response.buildSuccess(ordersVO);
    }

}
