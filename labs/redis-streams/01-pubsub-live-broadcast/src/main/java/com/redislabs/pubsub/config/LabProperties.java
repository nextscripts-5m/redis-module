package com.redislabs.pubsub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String role,
        String channel,
        String publisherName,
        String subscriberName,
        double failureProbability,
        long processingDelayMs,
        int recentMessageLimit
) {
    public boolean subscriberRole() {
        return "subscriber".equalsIgnoreCase(role);
    }
}
