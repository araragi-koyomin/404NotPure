package com.example.tomatomall.service;

import com.example.tomatomall.dto.ImageUsage;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
  String upload(MultipartFile file);

  default String upload(MultipartFile file, ImageUsage usage) {
    return upload(file);
  }
}
