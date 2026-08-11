package com.example.tomatomall.service;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.vo.ProductPageVO;
import com.example.tomatomall.vo.ProductVO;

import java.util.List;

public interface ProductService {
    Product createProduct(ProductVO productVO);
    List<ProductVO> getProductList();
    ProductPageVO getProductPage(String page, String size, String keyword, String categories, String sort);
    ProductVO getProductById(int id);

    String update(ProductVO vo);
    String delete(int id);
}
