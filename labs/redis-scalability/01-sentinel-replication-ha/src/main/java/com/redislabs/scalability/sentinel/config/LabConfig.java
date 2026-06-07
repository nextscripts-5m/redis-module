package com.redislabs.scalability.sentinel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LabProperties.class)
public class LabConfig {
}
