package com.redislabs.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String role,
        String streamName,
        String dlqStreamName,
        String groupName,
        String consumerName,
        String producerName,
        int readBatchSize,
        long readBlockMs,
        long processingDelayMs,
        double failureProbability,
        long recoveryMinIdleMs,
        int recoveryClaimBatchSize,
        long recoveryPollMs,
        double recoveryFailureProbability,
        int maxRetriesBeforeDlq,
        int recentEventLimit
) {
    public boolean producerRole() {
        return "producer".equalsIgnoreCase(role);
    }

    public boolean paymentWorkerRole() {
        return "payment-worker".equalsIgnoreCase(role);
    }

    public boolean recoveryWorkerRole() {
        return "recovery-worker".equalsIgnoreCase(role);
    }

    public boolean dlqInspectorRole() {
        return "dlq-inspector".equalsIgnoreCase(role);
    }
}
