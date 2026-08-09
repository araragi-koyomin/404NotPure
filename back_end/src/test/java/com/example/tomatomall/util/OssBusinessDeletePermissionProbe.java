package com.example.tomatomall.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class OssBusinessDeletePermissionProbe {

    private static final Map<String, String> BUSINESS_DIRECTORIES = businessDirectories();

    interface BusinessObjectClient extends AutoCloseable {
        boolean exists(String objectKey);

        boolean deleteWasDenied(String objectKey);

        @Override
        void close();
    }

    private final BusinessObjectClient client;

    OssBusinessDeletePermissionProbe(BusinessObjectClient client) {
        this.client = client;
    }

    Result run() {
        int deniedDeletionCount = 0;
        try {
            for (Map.Entry<String, String> directory : BUSINESS_DIRECTORIES.entrySet()) {
                String objectKey = directory.getValue()
                        + "permission-check-" + UUID.randomUUID() + ".png";

                boolean exists;
                try {
                    exists = client.exists(objectKey);
                } catch (RuntimeException exception) {
                    throw failure("existence-check", directory.getKey(), exception);
                }
                if (exists) {
                    throw new ProbeFailure(
                            "existence-check",
                            directory.getKey(),
                            "GeneratedNameAlreadyExists"
                    );
                }

                boolean denied;
                try {
                    denied = client.deleteWasDenied(objectKey);
                } catch (RuntimeException exception) {
                    throw failure("delete-permission", directory.getKey(), exception);
                }
                if (!denied) {
                    throw new ProbeFailure(
                            "delete-permission",
                            directory.getKey(),
                            "DeleteUnexpectedlyAllowed"
                    );
                }
                deniedDeletionCount++;
            }
            return new Result(deniedDeletionCount);
        } finally {
            client.close();
        }
    }

    private static ProbeFailure failure(
            String stage,
            String directory,
            RuntimeException exception
    ) {
        String category = exception.getClass().getSimpleName();
        if (category == null || category.isEmpty()) {
            category = "UnexpectedClientFailure";
        }
        return new ProbeFailure(stage, directory, category);
    }

    private static Map<String, String> businessDirectories() {
        Map<String, String> directories = new LinkedHashMap<>();
        directories.put("avatar", "tomatomall/images/avatar/");
        directories.put("product", "tomatomall/images/product/");
        directories.put("advertisement", "tomatomall/images/advertisement/");
        return directories;
    }

    static final class Result {
        private final int deniedDeletionCount;

        private Result(int deniedDeletionCount) {
            this.deniedDeletionCount = deniedDeletionCount;
        }

        int getDeniedDeletionCount() {
            return deniedDeletionCount;
        }
    }

    static final class ProbeFailure extends RuntimeException {
        private final String stage;

        private ProbeFailure(String stage, String directory, String category) {
            super("OSS business image delete permission check failed at "
                    + stage + " for " + directory + " (" + category + ")");
            this.stage = stage;
        }

        String getStage() {
            return stage;
        }
    }
}
