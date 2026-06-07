package com.redislabs.scalability.sentinel.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(
        String counterKey,
        long writeIntervalMs,
        long readIntervalMs,
        long topologyRefreshMs,
        String redisNodes) {

    public List<String> redisNodeList() {
        if (redisNodes == null || redisNodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(redisNodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
