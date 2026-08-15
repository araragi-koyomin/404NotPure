package com.example.tomatomall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
public class OrdersVO implements Serializable {
    private Integer orderId;
    private Integer userId;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String status;
    private Timestamp createTime;
    private Timestamp paidTime;
    private Timestamp cancelledTime;
    private Timestamp closedTime;
}
