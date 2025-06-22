package com.example.tomatomall.repository;

import com.example.tomatomall.po.ProductContentImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentImageRepository extends JpaRepository<ProductContentImage, Integer>{
    void deleteByProduct_Id(int productId);
}
