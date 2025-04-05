package com.example.tomatomall.repository;

import com.example.tomatomall.po.ProductContentImage;
import com.example.tomatomall.po.ProductSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentImageRepository extends JpaRepository<ProductContentImage, Integer>{
}
