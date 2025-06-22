package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.util.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片服务实现类
 * 处理图片上传到OSS
 */
@Service
public class ImageServiceImpl implements ImageService {

  @Autowired
  private OssUtil ossUtil;

  /**
   * 上传图片到OSS
   * @param file 图片文件
   * @return 图片URL
   * @throws TomatoException 文件上传失败时抛出
   */
  public String upload(MultipartFile file) {
    try {
      return ossUtil.upload(file.getOriginalFilename(), file.getInputStream());
    } catch (Exception e) {
      throw TomatoException.failToUploadFile();
    }
  }
}