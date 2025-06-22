package com.example.tomatomall.po;
import com.example.tomatomall.vo.AdvertisementsVO;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "advertisements")
public class Advertisements {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", insertable = false, updatable = false)
    private Product product;

    public AdvertisementsVO toVO(){
        AdvertisementsVO vo = new AdvertisementsVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setImgUrl(imageUrl);
        vo.setProductId(productId);
        return vo;
    }
}