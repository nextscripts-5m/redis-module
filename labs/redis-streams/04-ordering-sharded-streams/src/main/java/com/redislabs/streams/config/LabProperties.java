package com.redislabs.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String role,
        String mode,
        String streamName,
        String streamPrefix,
        int shardCount,
        int shardIndex,
        String producerName,
        String groupName,
        String consumerName,
        long orderCreatedDelayMs,
        long orderPaidDelayMs,
        long defaultProcessingDelayMs,
        int readBatchSize,
        long readBlockMs,
        int recentEventLimit
) {
    public boolean producerRole() {
        return "producer".equalsIgnoreCase(role);
    }

    public boolean workerRole() {
        return "worker".equalsIgnoreCase(role);
    }

    public boolean unorderedMode() {
        return "unordered".equalsIgnoreCase(mode);
    }

    public boolean shardedMode() {
        return "sharded".equalsIgnoreCase(mode);
    }

    public String workerStreamName() {
        if (shardedMode()) {
            return streamPrefix + shardIndex;
        }
        return streamName;
    }
}
