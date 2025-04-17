package com.example.tomatomall.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdvertisementsVO implements Serializable {
    private Integer id;
    private String title;
    private String content;
    private String imgUrl;
    private Integer productId;
}