package com.redislabs.distributed.lock.process;

import com.redislabs.distributed.lock.config.LabProperties;
import com.redislabs.distributed.lock.lock.AcquireResult;
import com.redislabs.distributed.lock.lock.DistributedLockService;
import com.redislabs.distributed.lock.lock.ReleaseMode;
import com.redislabs.distributed.lock.lock.ReleaseResult;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderProcessingService {

    private final DistributedLockService lockService;
    private final LabProperties properties;
    private final RecentProcessingEvents recentEvents;

    public OrderProcessingService(
            DistributedLockService lockService,
            LabProperties properties,
            RecentProcessingEvents recentEvents
    ) {
        this.lockService = lockService;
        this.properties = properties;
        this.recentEvents = recentEvents;
    }

    public ProcessingEvent process(
            String orderId,
            Long lockTtlMs,
            Long processingDelayMs,
            ReleaseMode releaseMode
    ) throws InterruptedException {
        long ttl = lockTtlMs != null ? lockTtlMs : properties.defaultLockTtlMs();
        long delay = processingDelayMs != null ? processingDelayMs : properties.defaultProcessingDelayMs();
        String owner = lockService.newOwnerToken();

        AcquireResult acquire = lockService.tryAcquire(orderId, owner, ttl);
        if (!acquire.acquired()) {
            ProcessingEvent denied = new ProcessingEvent(
                    Instant.now(),
                    orderId,
                    properties.workerName(),
                    false,
                    false,
                    releaseMode,
                    false,
                    false,
                    delay,
                    ttl,
                    null,
                    "Lock denied — holder=" + acquire.currentLock().holder()
                            + ", ttlMs=" + acquire.currentLock().ttlMs(),
                    acquire.currentLock()
            );
            recentEvents.record(denied);
            return denied;
        }

        Thread.sleep(delay);

        ReleaseResult release = lockService.release(orderId, owner, releaseMode);
        var lockAfter = lockService.inspect(orderId);
        ProcessingEvent completed = new ProcessingEvent(
                Instant.now(),
                orderId,
                properties.workerName(),
                true,
                true,
                releaseMode,
                release.released(),
                release.deletedAnotherHolder(),
                delay,
                ttl,
                owner,
                release.message(),
                lockAfter
        );
        recentEvents.record(completed);
        return completed;
    }
}
