package com.example.tomatomall.controller;

import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.vo.Response;
import com.example.tomatomall.exception.TomatoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageController {

  @Autowired
  private ImageService imageService;

  @PostMapping
  public Response<String> uploadImage(@RequestPart("file") MultipartFile file) {
    try {
      String url = imageService.upload(file);
      System.out.println(url);
      return Response.buildSuccess(url); // 确保 ResultVO.buildSuccess 正确包装数据
    } catch (TomatoException e) {
      e.printStackTrace();
      throw TomatoException.uploadFailure();
    }
  }
}