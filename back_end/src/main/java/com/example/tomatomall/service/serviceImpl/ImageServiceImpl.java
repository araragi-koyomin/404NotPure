package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.ImageUsage;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.util.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片服务实现类
 * 处理图片上传到OSS
 */
@Service
public class ImageServiceImpl implements ImageService {

  private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
  private static final long MAX_IMAGE_PIXELS = 40_000_000L;
  private static final String AVATAR_PREFIX = "tomatomall/images/avatar/";
  private static final String PRODUCT_PREFIX = "tomatomall/images/product/";
  private static final String ADVERTISEMENT_PREFIX = "tomatomall/images/advertisement/";

  @Autowired
  private OssUtil ossUtil;

  /**
   * 上传图片到OSS
   * @param file 图片文件
   * @return 图片URL
   * @throws TomatoException 文件上传失败时抛出
   */
  public String upload(MultipartFile file) {
    return upload(file, ImageUsage.AVATAR);
  }

  @Override
  public String upload(MultipartFile file, ImageUsage usage) {
    try {
      ValidatedImage image = validate(file);
      String objectName = prefixFor(usage) + UUID.randomUUID() + "." + image.extension;
      return ossUtil.upload(objectName, new ByteArrayInputStream(image.content));
    } catch (TomatoException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw TomatoException.failToUploadFile();
    }
  }

  private String prefixFor(ImageUsage usage) {
    if (usage == ImageUsage.AVATAR) {
      return AVATAR_PREFIX;
    }
    if (usage == ImageUsage.PRODUCT) {
      return PRODUCT_PREFIX;
    }
    if (usage == ImageUsage.ADVERTISEMENT) {
      return ADVERTISEMENT_PREFIX;
    }
    throw TomatoException.invalidImageFile();
  }

  private ValidatedImage validate(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
      throw TomatoException.invalidImageFile();
    }

    byte[] content = file.getBytes();
    String extension = detectExtension(content);
    if (extension == null || !matchesContentType(extension, file.getContentType())) {
      throw TomatoException.invalidImageFile();
    }

    ImageDimensionValidator.validate(content, MAX_IMAGE_PIXELS);
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
    if (image == null) {
      throw TomatoException.invalidImageFile();
    }

    return new ValidatedImage(content, extension);
  }

  private String detectExtension(byte[] content) {
    if (content.length >= 8
        && (content[0] & 0xff) == 0x89
        && content[1] == 0x50
        && content[2] == 0x4e
        && content[3] == 0x47
        && content[4] == 0x0d
        && content[5] == 0x0a
        && content[6] == 0x1a
        && content[7] == 0x0a) {
      return "png";
    }
    if (content.length >= 3
        && (content[0] & 0xff) == 0xff
        && (content[1] & 0xff) == 0xd8
        && (content[2] & 0xff) == 0xff) {
      return "jpg";
    }
    if (content.length >= 6
        && content[0] == 'G'
        && content[1] == 'I'
        && content[2] == 'F'
        && content[3] == '8'
        && (content[4] == '7' || content[4] == '9')
        && content[5] == 'a') {
      return "gif";
    }
    return null;
  }

  private boolean matchesContentType(String extension, String contentType) {
    if (contentType == null) {
      return false;
    }
    String normalized = contentType.toLowerCase(Locale.ROOT);
    if ("png".equals(extension)) {
      return "image/png".equals(normalized);
    }
    if ("jpg".equals(extension)) {
      return "image/jpeg".equals(normalized) || "image/jpg".equals(normalized);
    }
    return "gif".equals(extension) && "image/gif".equals(normalized);
  }

  private static final class ValidatedImage {
    private final byte[] content;
    private final String extension;

    private ValidatedImage(byte[] content, String extension) {
      this.content = content;
      this.extension = extension;
    }
  }
}
