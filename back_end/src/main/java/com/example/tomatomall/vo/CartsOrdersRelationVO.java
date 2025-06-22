package com.example.tomatomall.vo;
import lombok.Data;

import java.io.Serializable;

@Data
public class CartsOrdersRelationVO implements Serializable {
    private Integer id;
    private Integer cartitemId;
    private Integer orderId;
}