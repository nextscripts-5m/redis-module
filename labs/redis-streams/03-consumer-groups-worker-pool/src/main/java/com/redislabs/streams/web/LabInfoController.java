package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.PendingEntriesService;
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
    private final OrderStreamService streamService;
    private final PendingEntriesService pendingEntriesService;

    public LabInfoController(
            LabProperties properties,
            Environment environment,
            OrderStreamService streamService,
            PendingEntriesService pendingEntriesService
    ) {
        this.properties = properties;
        this.environment = environment;
        this.streamService = streamService;
        this.pendingEntriesService = pendingEntriesService;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        body.put("role", properties.role());
        body.put("streamName", properties.streamName());
        body.put("producerName", properties.producerName());
        body.put("groupName", properties.groupName());
        body.put("consumerName", properties.consumerName());
        body.put("streamLength", streamService.streamLength());
        body.put("failureProbability", properties.failureProbability());
        body.put("processingDelayMs", properties.processingDelayMs());
        body.put("pending", pendingEntriesService.pendingSummary());
        body.put(
                "hint",
                "Consumer groups use XREADGROUP and XACK. Workers in the same group divide work; different groups observe the same stream independently."
        );
        return body;
    }
}
