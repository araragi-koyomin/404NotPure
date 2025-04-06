package com.example.tomatomall.vo;

import com.example.tomatomall.po.ProductSpecification;
import lombok.Data;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

@Data
public class SpecificationVO {
    private int id;
    private String item;
    private String value;
    private int productId;

    public ProductSpecification toPO(){
        ProductSpecification productSpecification = new ProductSpecification();
        if (id > 0) {
            productSpecification.setId(id); // 只有更新场景才需要
        }
        productSpecification.setItem(item);
        productSpecification.setValue(value);
        return productSpecification;
    }
}
