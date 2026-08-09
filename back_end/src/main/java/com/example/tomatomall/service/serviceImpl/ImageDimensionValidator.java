package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

final class ImageDimensionValidator {

    private ImageDimensionValidator() {
    }

    static void validate(byte[] content, long maxPixels) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (input == null) {
                throw TomatoException.invalidImageFile();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw TomatoException.invalidImageFile();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw TomatoException.invalidImageFile();
                }
            } finally {
                reader.dispose();
            }
        } catch (TomatoException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw TomatoException.invalidImageFile();
        }
    }
}
