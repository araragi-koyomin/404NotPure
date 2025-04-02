package com.example.tomatomall.po;
import com.example.tomatomall.vo.ProductContentImageVO;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
@Entity
@Table(name = "product_content_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductContentImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonBackReference
    private Product product;

    @Column(name = "image_url")
    private String imageUrl;

    public Integer getProductId() {
        return (product != null) ? product.getProductId() : null;
    }

    public ProductContentImageVO toVO(){
        ProductContentImageVO vo = new ProductContentImageVO();
        vo.setId(id);
        vo.setProductId(getProductId());
        vo.setImageUrl(getImageUrl());
        return vo;
    }
}