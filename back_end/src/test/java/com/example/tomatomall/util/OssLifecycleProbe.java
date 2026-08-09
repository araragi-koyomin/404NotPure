package com.example.tomatomall.util;

import java.io.ByteArrayInputStream;

final class OssLifecycleProbe {

    interface AnonymousReader {
        int getStatus(String url);
    }

    private final OssUtil ossUtil;
    private final AnonymousReader anonymousReader;

    OssLifecycleProbe(OssUtil ossUtil, AnonymousReader anonymousReader) {
        this.ossUtil = ossUtil;
        this.anonymousReader = anonymousReader;
    }

    Result run(String objectKey, byte[] content) {
        String expectedUrl = ossUtil.generateFileUrl(objectKey);
        int beforeUploadStatus;
        try {
            beforeUploadStatus = anonymousReader.getStatus(expectedUrl);
        } catch (RuntimeException exception) {
            throw failure("pre-upload-read", exception, false);
        }
        if (beforeUploadStatus != 404) {
            throw new ProbeFailure(
                    "pre-upload-read",
                    "UnexpectedHttpStatus" + beforeUploadStatus,
                    false
            );
        }

        String url = null;
        int readableStatus = -1;
        ProbeFailure failure = null;
        boolean uploadConfirmed = false;

        try {
            try {
                url = ossUtil.upload(objectKey, new ByteArrayInputStream(content));
                uploadConfirmed = true;
            } catch (RuntimeException exception) {
                throw failure("upload", exception, false, true);
            }

            try {
                readableStatus = anonymousReader.getStatus(url);
            } catch (RuntimeException exception) {
                throw failure("anonymous-read", exception, false, true);
            }
            if (readableStatus != 200) {
                throw new ProbeFailure(
                        "anonymous-read",
                        "UnexpectedHttpStatus" + readableStatus,
                        false,
                        true
                );
            }
        } catch (ProbeFailure exception) {
            failure = exception;
        } finally {
            if (uploadConfirmed) {
                try {
                    ossUtil.deleteValidationObject(objectKey);
                } catch (RuntimeException cleanupException) {
                    if (failure == null) {
                        failure = failure("cleanup", cleanupException, true, true);
                    } else {
                        failure = failure.withCleanupFailure(category(cleanupException));
                    }
                }
            }
        }

        if (failure != null) {
            throw failure;
        }

        int deletedStatus;
        try {
            deletedStatus = anonymousReader.getStatus(url);
        } catch (RuntimeException exception) {
            throw failure("post-delete-read", exception, false, true);
        }
        if (deletedStatus != 404) {
            throw new ProbeFailure(
                    "post-delete-read",
                    "UnexpectedHttpStatus" + deletedStatus,
                    false,
                    true
            );
        }

        return new Result(readableStatus, deletedStatus);
    }

    private ProbeFailure failure(
            String stage,
            RuntimeException exception,
            boolean cleanupFailed
    ) {
        return new ProbeFailure(stage, category(exception), cleanupFailed);
    }

    private ProbeFailure failure(
            String stage,
            RuntimeException exception,
            boolean cleanupFailed,
            boolean residuePossible
    ) {
        return new ProbeFailure(stage, category(exception), cleanupFailed, residuePossible);
    }

    private static String category(RuntimeException exception) {
        return exception.getClass().getSimpleName();
    }

    static final class Result {
        private final int readableStatus;
        private final int deletedStatus;

        private Result(int readableStatus, int deletedStatus) {
            this.readableStatus = readableStatus;
            this.deletedStatus = deletedStatus;
        }

        int getReadableStatus() {
            return readableStatus;
        }

        int getDeletedStatus() {
            return deletedStatus;
        }
    }

    static final class ProbeFailure extends RuntimeException {
        private final String stage;
        private final String category;
        private final boolean cleanupFailed;
        private final boolean residuePossible;

        private ProbeFailure(
                String stage,
                String category,
                boolean cleanupFailed
        ) {
            this(stage, category, cleanupFailed, false);
        }

        private ProbeFailure(
                String stage,
                String category,
                boolean cleanupFailed,
                boolean residuePossible
        ) {
            super("OSS lifecycle probe failed at " + stage
                    + " (" + category
                    + ", cleanupFailed=" + cleanupFailed
                    + ", residuePossible=" + residuePossible + ")");
            this.stage = stage;
            this.category = category;
            this.cleanupFailed = cleanupFailed;
            this.residuePossible = residuePossible;
        }

        private ProbeFailure withCleanupFailure(String cleanupCategory) {
            return new ProbeFailure(
                    stage,
                    category + "+Cleanup" + cleanupCategory,
                    true,
                    true
            );
        }

        String getStage() {
            return stage;
        }
    }
}
