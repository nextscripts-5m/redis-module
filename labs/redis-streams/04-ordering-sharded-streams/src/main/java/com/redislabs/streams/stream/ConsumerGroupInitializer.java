package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
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
        String stream = properties.workerStreamName();
        consumerGroupSupport.ensureGroupExists(stream, properties.groupName());
        log.info(
                "Consumer group {} on stream {} ready for {} ({})",
                properties.groupName(),
                stream,
                properties.consumerName(),
                properties.mode()
        );
    }
}
