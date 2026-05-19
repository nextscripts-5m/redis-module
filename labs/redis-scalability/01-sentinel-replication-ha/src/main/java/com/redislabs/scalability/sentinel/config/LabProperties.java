package com.redislabs.scalability.sentinel.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String counterKey,
        long writeIntervalMs,
        long readIntervalMs,
        String replicaHosts) {

    public List<String> replicaHostList() {
        if (replicaHosts == null || replicaHosts.isBlank()) {
            return List.of();
        }
        return Arrays.stream(replicaHosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
