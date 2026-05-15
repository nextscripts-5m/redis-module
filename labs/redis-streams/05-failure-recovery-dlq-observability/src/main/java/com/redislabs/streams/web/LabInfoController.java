package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderStreamService;
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
    private final OrderStreamService orderStreamService;

    public LabInfoController(LabProperties properties, Environment environment, OrderStreamService orderStreamService) {
        this.properties = properties;
        this.environment = environment;
        this.orderStreamService = orderStreamService;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        body.put("role", properties.role());
        body.put("streamName", properties.streamName());
        body.put("dlqStreamName", properties.dlqStreamName());
        body.put("groupName", properties.groupName());
        body.put("consumerName", properties.consumerName());
        body.put("failureProbability", properties.failureProbability());
        body.put("recoveryMinIdleMs", properties.recoveryMinIdleMs());
        body.put("recoveryFailureProbability", properties.recoveryFailureProbability());
        body.put("maxRetriesBeforeDlq", properties.maxRetriesBeforeDlq());
        body.put("mainStreamLength", orderStreamService.mainStreamLength());
        body.put("dlqLength", orderStreamService.dlqLength());
        body.put(
                "hint",
                "PEL + XAUTOCLAIM + DLQ: payment-worker may fail without XACK; recovery-worker reclaims idle pending entries; poison messages go to order-events:dlq after max retries."
        );
        return body;
    }
}
