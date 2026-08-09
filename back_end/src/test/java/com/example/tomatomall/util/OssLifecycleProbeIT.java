package com.example.tomatomall.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OssLifecycleProbeIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_OSS_PROBE", matches = "true")
    void realPutReadDeleteLifecycleLeavesNoObject() {
        String key = "tomatomall/images/_validation/probe-" + UUID.randomUUID() + ".png";
        OssLifecycleProbe probe = new OssLifecycleProbe(
                new OssUtil(),
                OssLifecycleProbeIT::anonymousGetStatus
        );

        OssLifecycleProbe.Result result = probe.run(key, createPng());

        assertEquals(200, result.getReadableStatus());
        assertEquals(404, result.getDeletedStatus());
    }

    private static int anonymousGetStatus(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            drain(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return status;
        } catch (IOException exception) {
            throw new ProbeTransportException();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void drain(InputStream stream) throws IOException {
        if (stream == null) {
            return;
        }
        try (InputStream input = stream) {
            byte[] buffer = new byte[4096];
            while (input.read(buffer) != -1) {
                // Consume the response without logging its contents or URL.
            }
        }
    }

    private static byte[] createPng() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ProbeImageException();
        }
    }

    private static final class ProbeTransportException extends RuntimeException {
    }

    private static final class ProbeImageException extends RuntimeException {
    }
}
