package com.example.tomatomall.controller;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 商品管理控制器
 * 提供商品的增删改查功能，仅管理员可操作
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private TokenUtil tokenUtil;

    /**
     * 创建商品（管理员权限）
     * @param productVO 商品信息视图对象
     * @param request HTTP请求对象
     * @return 创建的商品实体
     * @throws TomatoException 未登录或权限不足时抛出
     */
    @PostMapping()
    public Response<Product> createProduct(@RequestBody ProductVO productVO, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        Product product = productService.createProduct(productVO);
        return Response.buildSuccess(product);
    }

    /**
     * 获取商品列表
     * @return 商品列表
     */
    @GetMapping()
    public Response<List<ProductVO>> getAllProducts() {
        List<ProductVO> productList = productService.getProductList();
        return Response.buildSuccess(productList);
    }

    /**
     * 根据ID获取商品信息
     * @param id 商品ID
     * @return 商品信息视图对象
     */
    @GetMapping("/{id}")
    public Response<ProductVO> getProductById(@PathVariable int id) {
        ProductVO productVO = productService.getProductById(id);
        return Response.buildSuccess(productVO);
    }

    /**
     * 更新商品信息（管理员权限）
     * @param product 商品信息视图对象
     * @param request HTTP请求对象
     * @return 操作结果
     * @throws TomatoException 未登录或权限不足时抛出
     */
    @PutMapping()
    public Response<String> updateProduct(@RequestBody ProductVO product, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        return Response.buildSuccess(productService.update(product));
    }

    /**
     * 删除商品（管理员权限）
     * @param id 商品ID
     * @param request HTTP请求对象
     * @return 操作结果
     * @throws TomatoException 未登录或权限不足时抛出
     */
    @DeleteMapping("/{id}")
    public Response<String> deleteProduct(@PathVariable int id, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        return Response.buildSuccess(productService.delete(id));
    }
}