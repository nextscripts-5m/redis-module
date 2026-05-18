package com.redislabs.distributed.lock;

import com.redislabs.distributed.lock.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class DistributedLockLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockLabApplication.class, args);
    }
}
