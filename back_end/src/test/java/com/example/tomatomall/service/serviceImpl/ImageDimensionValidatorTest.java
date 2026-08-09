package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageDimensionValidatorTest {

    private static final long MAX_PIXELS = 40_000_000L;

    @Test
    void normalImageDimensionsAreAcceptedWithoutFullDecodeContract() {
        assertDoesNotThrow(() -> ImageDimensionValidator.validate(createPng(), MAX_PIXELS));
    }

    @Test
    void oversizedDimensionsAreRejectedFromSmallMetadataOnlyPayload() {
        byte[] oversizedHeader = withPngDimensions(createPng(), 10_000, 5_000);

        assertThrows(
                TomatoException.class,
                () -> ImageDimensionValidator.validate(oversizedHeader, MAX_PIXELS)
        );
    }

    private static byte[] createPng() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
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
