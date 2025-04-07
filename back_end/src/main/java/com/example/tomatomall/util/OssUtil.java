package com.example.tomatomall.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class OssUtil {

  private final String endpoint;
  private final String accessKeyId;
  private final String accessKeySecret;
  private final String bucketName;

  public OssUtil() {
    this.endpoint = System.getenv("ALIYUN_OSS_ENDPOINT");
    this.accessKeyId = System.getenv("ALIYUN_OSS_ACCESS_KEY_ID");
    this.accessKeySecret = System.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET");
    this.bucketName = System.getenv("ALIYUN_OSS_BUCKET_NAME");

    if (endpoint == null || accessKeyId == null || accessKeySecret == null || bucketName == null) {
      throw new IllegalStateException("部分 OSS 环境变量未设置，请检查！");
    }
  }

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