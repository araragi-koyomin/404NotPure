package com.example.tomatomall.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OssUtilTest {

    @Test
    void publicUrlNormalizesEndpointScheme() {
        OSS client = mock(OSS.class);
        OssUtil util = util(client, "https://oss-cn-beijing.aliyuncs.com");

        String url = util.upload(
                "tomatomall/images/avatar/id.png",
                new ByteArrayInputStream(new byte[]{1})
        );

        assertEquals(
                "https://bucket.example.oss-cn-beijing.aliyuncs.com/tomatomall/images/avatar/id.png",
                url
        );
        verify(client).shutdown();
    }

    @Test
    void uploadSetsImageContentTypeAndForbidsOverwrite() {
        OSS client = mock(OSS.class);
        OssUtil util = util(client, "oss-cn-beijing.aliyuncs.com");

        util.upload("tomatomall/images/avatar/id.png", new ByteArrayInputStream(new byte[]{1}));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(requestCaptor.capture());
        ObjectMetadata metadata = requestCaptor.getValue().getMetadata();
        assertNotNull(metadata);
        assertEquals("image/png", metadata.getContentType());
        assertEquals("true", metadata.getRawMetadata().get("x-oss-forbid-overwrite"));
    }

    @Test
    void blankConfigurationIsRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> new OssUtil(" ", "access-id", "access-secret", "bucket.example")
        );
    }

    @Test
    void validationObjectCanBeDeletedAndClientIsClosed() {
        OSS client = mock(OSS.class);
        OssUtil util = util(client, "oss-cn-beijing.aliyuncs.com");
        String key = "tomatomall/images/_validation/probe-123.png";

        util.deleteValidationObject(key);

        verify(client).deleteObject("bucket.example", key);
        verify(client).shutdown();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tomatomall/images/avatar/business.png",
            "tomatomall/images/product/business.png",
            "tomatomall/images/advertisement/business.png",
            "tomatomall/images/_validation/../avatar/business.png",
            "tomatomall/images/_validation/nested/probe.png",
            "tomatomall/images/_validation/"
    })
    void deletionRejectsAnythingOutsideFlatValidationPrefix(String key) {
        OSS client = mock(OSS.class);
        OssUtil util = util(client, "oss-cn-beijing.aliyuncs.com");

        assertThrows(IllegalArgumentException.class, () -> util.deleteValidationObject(key));

        verifyNoInteractions(client);
    }

    @Test
    void deleteFailureStillClosesClient() {
        OSS client = mock(OSS.class);
        OssUtil util = util(client, "oss-cn-beijing.aliyuncs.com");
        String key = "tomatomall/images/_validation/probe-123.png";
        doThrow(new RuntimeException("simulated delete failure"))
                .when(client).deleteObject("bucket.example", key);

        assertThrows(RuntimeException.class, () -> util.deleteValidationObject(key));

        verify(client).shutdown();
    }

    private OssUtil util(OSS client, String endpoint) {
        return new OssUtil(endpoint, "access-id", "access-secret", "bucket.example") {
            @Override
            protected OSS createClient() {
                return client;
            }
        };
    }
}
