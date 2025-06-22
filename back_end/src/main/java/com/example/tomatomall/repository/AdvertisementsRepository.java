package com.example.tomatomall.repository;

import com.example.tomatomall.po.Advertisements;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvertisementsRepository extends JpaRepository<Advertisements, Integer> {
    List<Advertisements> findAll();
}