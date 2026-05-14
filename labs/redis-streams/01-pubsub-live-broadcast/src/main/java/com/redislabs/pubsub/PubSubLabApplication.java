package com.redislabs.pubsub;

import com.redislabs.pubsub.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class PubSubLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(PubSubLabApplication.class, args);
    }
}
