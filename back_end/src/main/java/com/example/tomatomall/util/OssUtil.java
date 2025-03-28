package com.example.tomatomall.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class OssUtil {
  @Value("${aliyun.oss.endpoint}")
  private String endpoint;

  @Value("${aliyun.oss.accessKeyId}")
  private String accessKeyId;

  @Value("${aliyun.oss.accessKeySecret}")
  private String accessKeySecret;

  @Value("${aliyun.oss.bucketName}")
  private String bucketName;


  public String upload(String objectName, InputStream inputStream) {
    OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream);
    try {
      ossClient.putObject(putObjectRequest);
      return generateFileUrl(ossClient, objectName); // 确保返回 URL
    } finally {
      ossClient.shutdown();
    }
  }

  private String generateFileUrl(OSS ossClient, String objectName) {
    return "https://" + bucketName + "." + endpoint + "/" + objectName;
  }
}