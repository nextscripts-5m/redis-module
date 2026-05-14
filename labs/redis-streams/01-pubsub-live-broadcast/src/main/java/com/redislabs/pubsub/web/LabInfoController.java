package com.redislabs.pubsub.web;

import com.redislabs.pubsub.config.LabProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class LabInfoController {

    private final LabProperties properties;
    private final Environment environment;

    public LabInfoController(LabProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        body.put("role", properties.role());
        body.put("channel", properties.channel());
        body.put("publisherName", properties.publisherName());
        body.put("subscriberName", properties.subscriberName());
        body.put("failureProbability", properties.failureProbability());
        body.put("processingDelayMs", properties.processingDelayMs());
        body.put("hint", "Pub/Sub is live broadcast: disconnected subscribers and failed processing are not retried.");
        return body;
    }
}
