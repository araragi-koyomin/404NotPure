package com.example.tomatomall.dto;

import com.example.tomatomall.po.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    public static ProductDTO fromProduct(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setPrice(product.getPrice());
        dto.setRate(product.getRate());
        dto.setDescription(product.getDescription());
        dto.setDetail(product.getDetail());
        dto.setCover(product.getCover());
        dto.setCategory(product.getCategory());
        dto.setSpecifications(product.getSpecifications() == null
                ? Collections.emptyList()
                : product.getSpecifications().stream().map(specification -> {
                    SpecificationDTO specificationDTO = new SpecificationDTO();
                    specificationDTO.setId(specification.getId());
                    specificationDTO.setItem(specification.getItem());
                    specificationDTO.setValue(specification.getValue());
                    specificationDTO.setProductId(specification.getProductId());
                    return specificationDTO;
                }).collect(Collectors.toList()));
        dto.setContentImages(product.getContentImages() == null
                ? Collections.emptyList()
                : product.getContentImages().stream().map(image -> {
                    ContentImageDTO imageDTO = new ContentImageDTO();
                    imageDTO.setId(image.getId());
                    imageDTO.setProductId(image.getProductId());
                    imageDTO.setImageUrl(image.getImageUrl());
                    return imageDTO;
                }).collect(Collectors.toList()));
        return dto;
    }
}
