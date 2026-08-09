package com.example.tomatomall.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssBusinessDeletePermissionProbeTest {

    @Test
    void checksOnlyGeneratedNonexistentKeysInAllBusinessImageDirectories() {
        RecordingClient client = new RecordingClient();
        OssBusinessDeletePermissionProbe probe = new OssBusinessDeletePermissionProbe(client);

        OssBusinessDeletePermissionProbe.Result result = probe.run();

        assertEquals(3, result.getDeniedDeletionCount());
        assertEquals(3, client.existenceChecks.size());
        assertEquals(3, client.deleteAttempts.size());
        assertTrue(client.closed);
        assertTrue(client.deleteAttempts.stream().anyMatch(key -> key.matches(
                "tomatomall/images/avatar/permission-check-[0-9a-f-]{36}\\.png"
        )));
        assertTrue(client.deleteAttempts.stream().anyMatch(key -> key.matches(
                "tomatomall/images/product/permission-check-[0-9a-f-]{36}\\.png"
        )));
        assertTrue(client.deleteAttempts.stream().anyMatch(key -> key.matches(
                "tomatomall/images/advertisement/permission-check-[0-9a-f-]{36}\\.png"
        )));
    }

    @Test
    void existingObjectStopsBeforeAnyDeleteRequest() {
        RecordingClient client = new RecordingClient();
        client.objectExists = true;
        OssBusinessDeletePermissionProbe probe = new OssBusinessDeletePermissionProbe(client);

        OssBusinessDeletePermissionProbe.ProbeFailure failure = assertThrows(
                OssBusinessDeletePermissionProbe.ProbeFailure.class,
                probe::run
        );

        assertEquals("existence-check", failure.getStage());
        assertEquals(0, client.deleteAttempts.size());
        assertTrue(client.closed);
    }

    @Test
    void allowedDeleteIsReportedAsPermissionFailure() {
        RecordingClient client = new RecordingClient();
        client.deleteDenied = false;
        OssBusinessDeletePermissionProbe probe = new OssBusinessDeletePermissionProbe(client);

        OssBusinessDeletePermissionProbe.ProbeFailure failure = assertThrows(
                OssBusinessDeletePermissionProbe.ProbeFailure.class,
                probe::run
        );

        assertEquals("delete-permission", failure.getStage());
        assertEquals(1, client.deleteAttempts.size());
        assertTrue(client.closed);
    }

    private static final class RecordingClient
            implements OssBusinessDeletePermissionProbe.BusinessObjectClient {

        private final List<String> existenceChecks = new ArrayList<>();
        private final List<String> deleteAttempts = new ArrayList<>();
        private boolean objectExists;
        private boolean deleteDenied = true;
        private boolean closed;

        @Override
        public boolean exists(String objectKey) {
            existenceChecks.add(objectKey);
            return objectExists;
        }

        @Override
        public boolean deleteWasDenied(String objectKey) {
            deleteAttempts.add(objectKey);
            return deleteDenied;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
