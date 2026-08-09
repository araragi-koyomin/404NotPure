package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.ImageUsage;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.util.OssUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageServiceImplTest {

    private static final byte[] PNG = createPng();

    private ImageServiceImpl service;
    private OssUtil ossUtil;

    @BeforeEach
    void setUp() {
        service = new ImageServiceImpl();
        ossUtil = mock(OssUtil.class);
        ReflectionTestUtils.setField(service, "ossUtil", ossUtil);
    }

    @Test
    void validPngUsesServerGeneratedUniqueKey() {
        when(ossUtil.upload(anyString(), any(InputStream.class))).thenReturn("https://example.invalid/image.png");

        service.upload(png("../../same-name.png"));
        service.upload(png("../../same-name.png"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ossUtil, times(2)).upload(keyCaptor.capture(), any(InputStream.class));
        List<String> keys = keyCaptor.getAllValues();
        assertTrue(keys.get(0).matches("tomatomall/images/avatar/[0-9a-f-]{36}\\.png"));
        assertTrue(keys.get(1).matches("tomatomall/images/avatar/[0-9a-f-]{36}\\.png"));
        assertNotEquals(keys.get(0), keys.get(1));
    }

    @Test
    void productAndAdvertisementUseSeparatePrefixes() {
        when(ossUtil.upload(anyString(), any(InputStream.class))).thenReturn("https://example.invalid/image.png");

        service.upload(png("product.png"), ImageUsage.PRODUCT);
        service.upload(png("advertisement.png"), ImageUsage.ADVERTISEMENT);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ossUtil, times(2)).upload(keyCaptor.capture(), any(InputStream.class));
        assertTrue(keyCaptor.getAllValues().get(0)
                .matches("tomatomall/images/product/[0-9a-f-]{36}\\.png"));
        assertTrue(keyCaptor.getAllValues().get(1)
                .matches("tomatomall/images/advertisement/[0-9a-f-]{36}\\.png"));
    }

    @Test
    void validJpegAndGifKeepDetectedServerSideExtensions() {
        when(ossUtil.upload(anyString(), any(InputStream.class))).thenReturn("https://example.invalid/image");

        service.upload(new MockMultipartFile(
                "file", "ignored.bin", "image/jpeg", createImage("jpg", BufferedImage.TYPE_INT_RGB)
        ));
        service.upload(new MockMultipartFile(
                "file", "ignored.bin", "image/gif", createImage("gif", BufferedImage.TYPE_INT_ARGB)
        ));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ossUtil, times(2)).upload(keyCaptor.capture(), any(InputStream.class));
        assertTrue(keyCaptor.getAllValues().get(0).matches(
                "tomatomall/images/avatar/[0-9a-f-]{36}\\.jpg"
        ));
        assertTrue(keyCaptor.getAllValues().get(1).matches(
                "tomatomall/images/avatar/[0-9a-f-]{36}\\.gif"
        ));
    }

    @Test
    void emptyFileIsRejectedBeforeCallingOss() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    @Test
    void oversizedFileIsRejectedBeforeCallingOss() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile("file", "large.png", "image/png", oversized))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    @Test
    void oversizedPixelDimensionsAreRejectedBeforeCallingOss() {
        byte[] oversizedDimensions = withPngDimensions(PNG, 10_000, 5_000);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile(
                        "file", "small-payload.png", "image/png", oversizedDimensions
                ))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    @Test
    void textDisguisedAsPngIsRejectedBeforeCallingOss() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile(
                        "file",
                        "fake.png",
                        "image/png",
                        "not an image".getBytes()
                ))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    @Test
    void unsupportedDeclaredContentTypeIsRejectedBeforeCallingOss() {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile("file", "image.png", "text/plain", PNG))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    @Test
    void declaredPngWithActualJpegContentIsRejectedBeforeCallingOss() {
        byte[] jpeg = createImage("jpg", BufferedImage.TYPE_INT_RGB);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> service.upload(new MockMultipartFile(
                        "file", "mismatch.png", "image/png", jpeg
                ))
        );

        assertEquals("400", exception.getCode());
        verify(ossUtil, never()).upload(anyString(), any(InputStream.class));
    }

    private MockMultipartFile png(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/png", PNG);
    }

    private static byte[] createPng() {
        return createImage("png", BufferedImage.TYPE_INT_ARGB);
    }

    private static byte[] createImage(String format, int imageType) {
        try {
            BufferedImage image = new BufferedImage(1, 1, imageType);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IOException("No ImageIO writer for test format " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static byte[] withPngDimensions(byte[] png, int width, int height) {
        byte[] result = png.clone();
        writeInt(result, 16, width);
        writeInt(result, 20, height);
        CRC32 crc = new CRC32();
        crc.update(result, 12, 17);
        writeInt(result, 29, (int) crc.getValue());
        return result;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
