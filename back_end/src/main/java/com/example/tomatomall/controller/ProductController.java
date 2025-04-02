package com.example.tomatomall.controller;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 创建商品
     */
    @PostMapping()
    public Response<Product> createProduct(@RequestBody ProductVO productVO) {
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
}