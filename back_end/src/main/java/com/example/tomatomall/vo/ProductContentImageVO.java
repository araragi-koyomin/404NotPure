package com.example.tomatomall.vo;

import com.example.tomatomall.po.ProductContentImage;
import lombok.Data;
@Data
public class ProductContentImageVO {
    private int id;
    private int productId;
    private String imageUrl;

    public ProductContentImage toPO(){
        ProductContentImage po = new ProductContentImage();
        po.setImageUrl(imageUrl);
        return po;
    }
}
