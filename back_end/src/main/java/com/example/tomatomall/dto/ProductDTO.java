package com.example.tomatomall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductDTO {
    private int id;
    private String title;
    private BigDecimal price;
    private Double rate;
    private String description;
    private String detail;
    private String cover;
    private String category;
    private List<SpecificationDTO> specifications;
    private List<ContentImageDTO> contentImages;
}