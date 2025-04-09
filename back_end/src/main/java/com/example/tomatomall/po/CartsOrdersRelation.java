package com.example.tomatomall.po;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Entity
@Table(name = "carts_orders_relation")
public class CartsOrdersRelation implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "cartitem_id", referencedColumnName = "cartItemId", nullable = false)
    private Carts carts;

    @ManyToOne
    @JoinColumn(name = "order_id", referencedColumnName = "orderId", nullable = false)
    private Orders orders;
}
