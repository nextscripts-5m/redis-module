package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderEventRequest;
import com.redislabs.streams.stream.OrderShardResolver;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.StreamEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.role", havingValue = "producer")
public class OrderStreamController {

    private final LabProperties properties;
    private final OrderStreamService streamService;
    private final OrderShardResolver shardResolver;

    public OrderStreamController(
            LabProperties properties,
            OrderStreamService streamService,
            OrderShardResolver shardResolver
    ) {
        this.properties = properties;
        this.streamService = streamService;
        this.shardResolver = shardResolver;
    }

    @PostMapping("/orders")
    public StreamEvent appendOrderEvent(@RequestBody(required = false) OrderEventRequest request) {
        OrderEventRequest safeRequest = request == null ? new OrderEventRequest(null, null, Map.of()) : request;
        return streamService.append(safeRequest);
    }

    @PostMapping("/orders/lifecycle")
    public Map<String, Object> appendLifecycle(@RequestBody(required = false) OrderEventRequest request) {
        OrderEventRequest safeRequest = request == null ? new OrderEventRequest(null, "42", Map.of()) : request;
        List<StreamEvent> events = streamService.lifecycle(safeRequest);
        return Map.of(
                "orderId", safeRequest.eventOrderId(),
                "mode", properties.mode(),
                "events", events
        );
    }

    @GetMapping("/orders/route")
    public Map<String, Object> route(@RequestParam String orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("mode", properties.mode());
        if (properties.shardedMode()) {
            body.put("shard", shardResolver.shardFor(orderId));
            body.put("stream", shardResolver.streamFor(orderId));
        } else {
            body.put("stream", properties.streamName());
        }
        return body;
    }

    @GetMapping("/events")
    public List<StreamEvent> events(
            @RequestParam(required = false) String stream,
            @RequestParam(defaultValue = "50") int count
    ) {
        String targetStream = stream == null || stream.isBlank() ? defaultStreamForQuery() : stream;
        return streamService.listEvents(targetStream, count);
    }

    @DeleteMapping("/events")
    public Map<String, Object> clear() {
        long deleted = streamService.clearForMode();
        return Map.of("deletedStreamKeys", deleted, "mode", properties.mode());
    }

    private String defaultStreamForQuery() {
        if (properties.shardedMode()) {
            return properties.streamPrefix() + "0";
        }
        return properties.streamName();
    }
}
