package com.example.tomatomall.po;

import com.example.tomatomall.vo.ProductVO;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private int id;

    private String title;
    private BigDecimal price;
    private Double rate;
    private String description;
    private String detail;
    private String cover;
    private String category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<ProductSpecification> specifications = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<ProductContentImage> contentImages = new ArrayList<>();

    public ProductVO toVO(){
        ProductVO vo = new ProductVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setPrice(price);
        vo.setRate(rate);
        vo.setDetail(detail);
        vo.setDescription(description);
        vo.setCover(cover);
        vo.setCategory(category);
        vo.setSpecifications(specifications.stream().map(ProductSpecification::toVO).collect(Collectors.toList()));
        vo.setContentImages(contentImages.stream().map(ProductContentImage::toVO).collect(Collectors.toList()));
        return vo;
    }
}
