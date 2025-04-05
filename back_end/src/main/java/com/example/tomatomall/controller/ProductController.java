package com.example.tomatomall.controller;

import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private TokenUtil tokenUtil;

    /**
     * 创建商品
     */
    @PostMapping()
    public Response<Product> createProduct(@RequestBody ProductVO productVO, HttpServletRequest request) {
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

        String role = tokenUtil.getUserRoleFromToken(token);
        if (!"admin".equals(role)) {
            throw TomatoException.noPermission();
        }

        Product product = productService.createProduct(productVO);
        return Response.buildSuccess(product);
    }

    /**
     * 获取商品列表
     */
    @GetMapping()
    public Response<List<ProductVO>> getProductList() {
        List<ProductVO> productList = productService.getProductList();
        return Response.buildSuccess(productList);
    }

    /**
     * 获取某 ID 商品信息
     */
    @GetMapping("/{id}")
    public Response<ProductVO> getProductById(@PathVariable int id) {
        ProductVO productVO = productService.getProductById(id);
        return Response.buildSuccess(productVO);
    }

    /**
     * 更新商品信息
     */
    @PutMapping()
    public Response<String> updateProduct(@RequestBody Product product, HttpServletRequest request) {
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

        String role = tokenUtil.getUserRoleFromToken(token);
        if (!"admin".equals(role)) {
            throw TomatoException.noPermission();
        }

        return Response.buildSuccess(productService.update(product.toVO()));
    }
}