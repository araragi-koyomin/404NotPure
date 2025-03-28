package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.util.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ImageServiceImpl implements ImageService {

  @Autowired
  private OssUtil ossUtil;

  public String upload(MultipartFile file) {
    try {
      return ossUtil.upload(file.getOriginalFilename(), file.getInputStream());
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("文件上传失败: " + e.getMessage());
    }
  }
}