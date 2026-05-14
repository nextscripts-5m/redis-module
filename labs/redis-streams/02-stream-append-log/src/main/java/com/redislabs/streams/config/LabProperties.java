package com.redislabs.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String role,
        String streamName,
        String producerName,
        String readerName,
        String readerCursorKey,
        int readBatchSize,
        long readBlockMs,
        long processingDelayMs,
        double failureProbability,
        int recentEventLimit
) {
    public boolean readerRole() {
        return "stream-reader".equalsIgnoreCase(role);
    }
}
