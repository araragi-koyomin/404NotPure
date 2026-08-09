package com.example.tomatomall.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.regex.Pattern;

@Component
public class OssUtil {

  private static final String VALIDATION_PREFIX = "tomatomall/images/_validation/";
  private static final Pattern VALIDATION_FILE_NAME = Pattern.compile(
      "[A-Za-z0-9][A-Za-z0-9_-]{0,127}\\.(?i:png|jpe?g|gif)"
  );

  private final String endpoint;
  private final String accessKeyId;
  private final String accessKeySecret;
  private final String bucketName;

  public OssUtil() {
    this(
        System.getenv("ALIYUN_OSS_ENDPOINT"),
        System.getenv("ALIYUN_OSS_ACCESS_KEY_ID"),
        System.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET"),
        System.getenv("ALIYUN_OSS_BUCKET_NAME")
    );
  }

  OssUtil(String endpoint, String accessKeyId, String accessKeySecret, String bucketName) {
    if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(accessKeySecret)
        || isBlank(bucketName)) {
      throw new IllegalStateException("OSS environment variables are incomplete");
    }
    this.endpoint = normalizeEndpoint(endpoint);
    this.accessKeyId = accessKeyId.trim();
    this.accessKeySecret = accessKeySecret.trim();
    this.bucketName = bucketName.trim();
  }

  public String upload(String objectName, InputStream inputStream) {
    OSS ossClient = createClient();
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentType(contentTypeFor(objectName));
    metadata.setHeader("x-oss-forbid-overwrite", "true");
    PutObjectRequest putObjectRequest =
        new PutObjectRequest(bucketName, objectName, inputStream, metadata);
    try {
      ossClient.putObject(putObjectRequest);
      return generateFileUrl(objectName);
    } finally {
      ossClient.shutdown();
    }
  }

  public void deleteValidationObject(String objectName) {
    if (!isValidationObjectName(objectName)) {
      throw new IllegalArgumentException("Only isolated OSS validation objects can be deleted");
    }

    OSS ossClient = createClient();
    try {
      ossClient.deleteObject(bucketName, objectName);
    } finally {
      ossClient.shutdown();
    }
  }

  protected OSS createClient() {
    return new OSSClientBuilder().build("https://" + endpoint, accessKeyId, accessKeySecret);
  }

  String bucketNameForPermissionCheck() {
    return bucketName;
  }

  String generateFileUrl(String objectName) {
    return "https://" + bucketName + "." + endpoint + "/" + objectName;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static boolean isValidationObjectName(String objectName) {
    if (objectName == null || !objectName.startsWith(VALIDATION_PREFIX)) {
      return false;
    }
    String fileName = objectName.substring(VALIDATION_PREFIX.length());
    return VALIDATION_FILE_NAME.matcher(fileName).matches();
  }

  private static String normalizeEndpoint(String endpoint) {
    String normalized = endpoint.trim();
    if (normalized.regionMatches(true, 0, "https://", 0, 8)) {
      normalized = normalized.substring(8);
    } else if (normalized.regionMatches(true, 0, "http://", 0, 7)) {
      normalized = normalized.substring(7);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isEmpty()) {
      throw new IllegalStateException("OSS endpoint is empty");
    }
    return normalized;
  }

  private static String contentTypeFor(String objectName) {
    String normalized = objectName.toLowerCase(java.util.Locale.ROOT);
    if (normalized.endsWith(".png")) {
      return "image/png";
    }
    if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (normalized.endsWith(".gif")) {
      return "image/gif";
    }
    return "application/octet-stream";
  }
}
