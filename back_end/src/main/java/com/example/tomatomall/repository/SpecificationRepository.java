package com.example.tomatomall.repository;

import com.example.tomatomall.po.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SpecificationRepository extends JpaRepository<ProductSpecification, Integer> {
}
