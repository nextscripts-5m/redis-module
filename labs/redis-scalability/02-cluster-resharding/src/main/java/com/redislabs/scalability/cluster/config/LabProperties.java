package com.redislabs.scalability.cluster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record LabProperties(double hotspotRatio, long loadIntervalMs, String hotspotTag) {}
