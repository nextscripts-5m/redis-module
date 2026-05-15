package com.redislabs.streams;

import com.redislabs.streams.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class OrderingShardedStreamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderingShardedStreamsApplication.class, args);
    }
}
