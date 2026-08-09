package com.example.tomatomall.util;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OssBusinessDeletePermissionIT {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_REAL_OSS_PERMISSION_PROBE",
            matches = "true"
    )
    void realCloudAccountCannotDeleteBusinessImages() {
        OssUtil ossUtil = new OssUtil();
        OSS sdkClient = ossUtil.createClient();
        AliyunBusinessObjectClient client = new AliyunBusinessObjectClient(
                sdkClient,
                ossUtil.bucketNameForPermissionCheck()
        );

        OssBusinessDeletePermissionProbe.Result result =
                new OssBusinessDeletePermissionProbe(client).run();

        assertEquals(3, result.getDeniedDeletionCount());
    }

    private static final class AliyunBusinessObjectClient
            implements OssBusinessDeletePermissionProbe.BusinessObjectClient {

        private final OSS sdkClient;
        private final String bucketName;

        private AliyunBusinessObjectClient(OSS sdkClient, String bucketName) {
            this.sdkClient = sdkClient;
            this.bucketName = bucketName;
        }

        @Override
        public boolean exists(String objectKey) {
            try {
                return sdkClient.doesObjectExist(bucketName, objectKey);
            } catch (OSSException exception) {
                throw new UnexpectedCloudResponseException();
            } catch (ClientException exception) {
                throw new CloudTransportException();
            }
        }

        @Override
        public boolean deleteWasDenied(String objectKey) {
            try {
                sdkClient.deleteObject(bucketName, objectKey);
                return false;
            } catch (OSSException exception) {
                String errorCode = exception.getErrorCode();
                if ("AccessDenied".equals(errorCode)
                        || "AccessDeniedByBucketPolicy".equals(errorCode)) {
                    return true;
                }
                throw new UnexpectedCloudResponseException();
            } catch (ClientException exception) {
                throw new CloudTransportException();
            }
        }

        @Override
        public void close() {
            sdkClient.shutdown();
        }
    }

    private static final class UnexpectedCloudResponseException extends RuntimeException {
    }

    private static final class CloudTransportException extends RuntimeException {
    }
}
