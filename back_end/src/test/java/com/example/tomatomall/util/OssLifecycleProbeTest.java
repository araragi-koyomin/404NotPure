package com.example.tomatomall.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssLifecycleProbeTest {

    private static final String KEY =
            "tomatomall/images/_validation/probe-123.png";
    private static final String URL =
            "https://example.invalid/tomatomall/images/_validation/probe-123.png";

    @Test
    void successfulProbeReportsReadableThenDeleted() {
        OssUtil ossUtil = mock(OssUtil.class);
        AtomicInteger reads = new AtomicInteger();
        OssLifecycleProbe probe = new OssLifecycleProbe(
                ossUtil,
                ignored -> {
                    int readNumber = reads.getAndIncrement();
                    if (readNumber == 0 || readNumber == 2) {
                        return 404;
                    }
                    return 200;
                }
        );
        when(ossUtil.upload(eq(KEY), any(InputStream.class))).thenReturn(URL);

        OssLifecycleProbe.Result result = probe.run(KEY, new byte[]{1, 2, 3});

        assertEquals(200, result.getReadableStatus());
        assertEquals(404, result.getDeletedStatus());
        assertEquals(3, reads.get());
        verify(ossUtil).deleteValidationObject(KEY);
    }

    @Test
    void unexpectedReadStatusStillCleansUpAndFails() {
        OssUtil ossUtil = mock(OssUtil.class);
        when(ossUtil.upload(eq(KEY), any(InputStream.class))).thenReturn(URL);
        AtomicInteger reads = new AtomicInteger();
        OssLifecycleProbe probe = new OssLifecycleProbe(
                ossUtil,
                ignored -> reads.getAndIncrement() == 0 ? 404 : 503
        );

        OssLifecycleProbe.ProbeFailure failure = assertThrows(
                OssLifecycleProbe.ProbeFailure.class,
                () -> probe.run(KEY, new byte[]{1})
        );

        assertEquals("anonymous-read", failure.getStage());
        assertTrue(failure.getMessage().contains("cleanupFailed=false"));
        assertTrue(failure.getMessage().contains("residuePossible=true"));
        assertFalse(failure.getMessage().contains(KEY));
        verify(ossUtil).deleteValidationObject(KEY);
    }

    @Test
    void anonymousReadExceptionStillCleansUpAndReportsPossibleResidue() {
        OssUtil ossUtil = mock(OssUtil.class);
        when(ossUtil.upload(eq(KEY), any(InputStream.class))).thenReturn(URL);
        AtomicInteger reads = new AtomicInteger();
        OssLifecycleProbe probe = new OssLifecycleProbe(
                ossUtil,
                ignored -> {
                    if (reads.getAndIncrement() == 0) {
                        return 404;
                    }
                    throw new RuntimeException("simulated anonymous read failure");
                }
        );

        OssLifecycleProbe.ProbeFailure failure = assertThrows(
                OssLifecycleProbe.ProbeFailure.class,
                () -> probe.run(KEY, new byte[]{1})
        );

        assertEquals("anonymous-read", failure.getStage());
        assertTrue(failure.getMessage().contains("cleanupFailed=false"));
        assertTrue(failure.getMessage().contains("residuePossible=true"));
        assertFalse(failure.getMessage().contains(KEY));
        verify(ossUtil).deleteValidationObject(KEY);
    }

    @Test
    void uploadExceptionDoesNotDeleteAnObjectThatWasNotConfirmedCreated() {
        OssUtil ossUtil = mock(OssUtil.class);
        when(ossUtil.upload(eq(KEY), any(InputStream.class)))
                .thenThrow(new RuntimeException("simulated upload failure"));
        OssLifecycleProbe probe = new OssLifecycleProbe(ossUtil, ignored -> 404);

        OssLifecycleProbe.ProbeFailure failure = assertThrows(
                OssLifecycleProbe.ProbeFailure.class,
                () -> probe.run(KEY, new byte[]{1})
        );

        assertEquals("upload", failure.getStage());
        assertTrue(failure.getMessage().contains("residuePossible=true"));
        assertFalse(failure.getMessage().contains(KEY));
        verify(ossUtil, never()).deleteValidationObject(KEY);
    }

    @Test
    void cleanupExceptionIsReportedWithoutPretendingSuccess() {
        OssUtil ossUtil = mock(OssUtil.class);
        when(ossUtil.upload(eq(KEY), any(InputStream.class))).thenReturn(URL);
        doThrow(new RuntimeException("simulated cleanup failure"))
                .when(ossUtil).deleteValidationObject(KEY);
        AtomicInteger reads = new AtomicInteger();
        OssLifecycleProbe probe = new OssLifecycleProbe(
                ossUtil,
                ignored -> reads.getAndIncrement() == 0 ? 404 : 200
        );

        OssLifecycleProbe.ProbeFailure failure = assertThrows(
                OssLifecycleProbe.ProbeFailure.class,
                () -> probe.run(KEY, new byte[]{1})
        );

        assertEquals("cleanup", failure.getStage());
    }

    @Test
    void existingValidationObjectStopsBeforeUploadOrDelete() {
        OssUtil ossUtil = mock(OssUtil.class);
        OssLifecycleProbe probe = new OssLifecycleProbe(ossUtil, ignored -> 200);

        OssLifecycleProbe.ProbeFailure failure = assertThrows(
                OssLifecycleProbe.ProbeFailure.class,
                () -> probe.run(KEY, new byte[]{1})
        );

        assertEquals("pre-upload-read", failure.getStage());
        verify(ossUtil, never()).upload(eq(KEY), any(InputStream.class));
        verify(ossUtil, never()).deleteValidationObject(KEY);
    }
}
