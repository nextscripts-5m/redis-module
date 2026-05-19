package com.redislabs.distributed.lock.process;

import com.redislabs.distributed.lock.lock.LockSnapshot;
import com.redislabs.distributed.lock.lock.ReleaseMode;

import java.time.Instant;

public record ProcessingEvent(
        Instant at,
        String orderId,
        String workerName,
        boolean acquired,
        boolean processed,
        ReleaseMode releaseMode,
        boolean released,
        boolean deletedAnotherHolder,
        long processingDelayMs,
        long lockTtlMs,
        String ownerToken,
        String message,
        LockSnapshot lockAfterRelease
) {
}
