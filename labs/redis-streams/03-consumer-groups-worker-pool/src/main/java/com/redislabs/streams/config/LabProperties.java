package com.redislabs.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String role,
        String streamName,
        String producerName,
        String groupName,
        String consumerName,
        int readBatchSize,
        long readBlockMs,
        long processingDelayMs,
        double failureProbability,
        int recentEventLimit
) {
    public boolean producerRole() {
        return "producer".equalsIgnoreCase(role);
    }

    public boolean groupWorkerRole() {
        return "group-worker".equalsIgnoreCase(role);
    }
}
