package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.stereotype.Component;

@Component
public class ProcessingDelayPolicy {

    private final LabProperties properties;

    public ProcessingDelayPolicy(LabProperties properties) {
        this.properties = properties;
    }

    public long delayFor(String eventType) {
        if ("order-created".equalsIgnoreCase(eventType)) {
            return Math.max(0, properties.orderCreatedDelayMs());
        }
        if ("order-paid".equalsIgnoreCase(eventType)) {
            return Math.max(0, properties.orderPaidDelayMs());
        }
        return Math.max(0, properties.defaultProcessingDelayMs());
    }
}
