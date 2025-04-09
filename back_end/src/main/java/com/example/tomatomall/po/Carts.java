package com.example.tomatomall.po;

import com.example.tomatomall.vo.CartsVO;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Entity
@Table(name = "carts")
public class Carts implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cartItemId")
    private Integer cartItemId;

    @ManyToOne
    @JoinColumn(name = "userId", referencedColumnName = "id", nullable = false)
    private Account account;

    @ManyToOne
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer quantity;

    public CartsVO toVO(){
        CartsVO vo = new CartsVO();
        vo.setCartItemId(this.cartItemId);
        vo.setQuantity(this.quantity);
        vo.setProductId(this.product.getId());
        vo.setDescription(this.product.getDescription());
        vo.setPrice(this.product.getPrice());
        vo.setDetail(this.product.getDetail());
        vo.setCover(this.product.getCover());
        vo.setTitle(this.product.getTitle());
        return vo;
    }
}
