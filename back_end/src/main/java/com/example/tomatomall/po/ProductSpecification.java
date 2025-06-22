package com.example.tomatomall.po;

import com.example.tomatomall.vo.SpecificationVO;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "product_specifications")
@Getter
@Setter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonBackReference
    private Product product;

    private String item;

    private String value;

    public Integer getProductId() {
        return (product != null) ? product.getId() : null;
    }

    public SpecificationVO toVO(){
        SpecificationVO vo = new SpecificationVO();
        vo.setId(id);
        vo.setItem(item);
        vo.setValue(value);
        vo.setProductId(getProductId());
        return vo;
    }
}