package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "group-worker")
public class ConsumerGroupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupInitializer.class);

    private final LabProperties properties;
    private final ConsumerGroupSupport consumerGroupSupport;

    public ConsumerGroupInitializer(LabProperties properties, ConsumerGroupSupport consumerGroupSupport) {
        this.properties = properties;
        this.consumerGroupSupport = consumerGroupSupport;
    }

    @Override
    public void run(ApplicationArguments args) {
        consumerGroupSupport.ensureGroupExists(properties.streamName(), properties.groupName());
        log.info(
                "Consumer group {} on stream {} is ready for consumer {}",
                properties.groupName(),
                properties.streamName(),
                properties.consumerName()
        );
    }
}
