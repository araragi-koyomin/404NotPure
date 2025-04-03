package com.example.tomatomall.vo;

import com.example.tomatomall.po.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
public class ProductVO {
    private int productId;
    private String title;
    private BigDecimal price;
    private Double rate;
    private String description;
    private String detail;
    private String cover;
    private String category;
    private List<SpecificationVO> specifications;
    private List<ProductContentImageVO> contentImages;
}
