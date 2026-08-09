package com.example.tomatomall.controller;

import com.example.tomatomall.dto.ImageUsage;
import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * 图片管理控制器
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private final ImageService imageService;
  private final TokenUtil tokenUtil;

  public ImageController(ImageService imageService, TokenUtil tokenUtil) {
    this.imageService = imageService;
    this.tokenUtil = tokenUtil;
  }

  /**
   * 图片上传
   * @param file 文件
   * @return 成功信息
  */
  @PostMapping
  public Response<String> uploadImage(@RequestPart("file") MultipartFile file,
                                      @RequestParam(defaultValue = "AVATAR") ImageUsage usage,
                                      HttpServletRequest request) {
    if (usage != ImageUsage.AVATAR) {
      tokenUtil.validateAdminRole(request);
    }
    return Response.buildSuccess(imageService.upload(file, usage));
  }
}
