package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", matchIfMissing = false, havingValue = "payment-worker")
public class PaymentConsumerGroupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumerGroupInitializer.class);

    private final LabProperties properties;
    private final ConsumerGroupSupport consumerGroupSupport;

    public PaymentConsumerGroupInitializer(LabProperties properties, ConsumerGroupSupport consumerGroupSupport) {
        this.properties = properties;
        this.consumerGroupSupport = consumerGroupSupport;
    }

    @Override
    public void run(ApplicationArguments args) {
        consumerGroupSupport.ensureGroupExists(properties.streamName(), properties.groupName());
        log.info("Group {} on stream {} ready for {}", properties.groupName(), properties.streamName(), properties.role());
    }
}
