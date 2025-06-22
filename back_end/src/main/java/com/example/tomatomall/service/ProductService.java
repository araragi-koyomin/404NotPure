package com.example.tomatomall.service;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.vo.ProductVO;

import java.util.List;

public interface ProductService {
    Product createProduct(ProductVO productVO);
    List<ProductVO> getProductList();
    ProductVO getProductById(int id);

    String update(ProductVO vo);
    String delete(int id);
}