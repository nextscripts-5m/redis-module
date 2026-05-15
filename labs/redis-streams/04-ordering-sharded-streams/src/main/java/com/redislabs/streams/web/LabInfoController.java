package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderShardResolver;
import com.redislabs.streams.stream.OrderingTracker;
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
    private final OrderShardResolver shardResolver;
    private final OrderingTracker orderingTracker;

    public LabInfoController(
            LabProperties properties,
            Environment environment,
            OrderShardResolver shardResolver,
            OrderingTracker orderingTracker
    ) {
        this.properties = properties;
        this.environment = environment;
        this.shardResolver = shardResolver;
        this.orderingTracker = orderingTracker;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        body.put("role", properties.role());
        body.put("mode", properties.mode());
        body.put("streamName", properties.streamName());
        body.put("workerStreamName", properties.workerStreamName());
        body.put("streamPrefix", properties.streamPrefix());
        body.put("shardCount", properties.shardCount());
        body.put("shardIndex", properties.shardIndex());
        body.put("groupName", properties.groupName());
        body.put("consumerName", properties.consumerName());
        body.put("orderCreatedDelayMs", properties.orderCreatedDelayMs());
        body.put("orderPaidDelayMs", properties.orderPaidDelayMs());
        body.put("orderingViolations", orderingTracker.snapshot().size());
        if (properties.shardedMode()) {
            body.put("routing", "stream = orders:{hash(orderId) % " + properties.shardCount() + "}");
            body.put("exampleShardForOrder42", shardResolver.shardFor("42"));
        }
        body.put(
                "hint",
                properties.unorderedMode()
                        ? "Two billing workers share order-events; slow order-created vs fast order-paid can violate business order."
                        : "Each shard stream has one dedicated worker; events for the same orderId stay ordered per lane."
        );
        return body;
    }
}
