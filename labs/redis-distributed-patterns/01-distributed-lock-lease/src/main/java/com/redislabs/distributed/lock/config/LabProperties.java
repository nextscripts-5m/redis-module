package com.redislabs.distributed.lock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String workerName,
        String lockKeyPrefix,
        long defaultLockTtlMs,
        long defaultProcessingDelayMs,
        int recentEventLimit
) {
}
