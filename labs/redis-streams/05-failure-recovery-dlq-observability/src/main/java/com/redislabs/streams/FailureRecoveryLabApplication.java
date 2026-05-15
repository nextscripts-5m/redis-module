package com.redislabs.streams;

import com.redislabs.streams.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
@EnableScheduling
public class FailureRecoveryLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(FailureRecoveryLabApplication.class, args);
    }
}
