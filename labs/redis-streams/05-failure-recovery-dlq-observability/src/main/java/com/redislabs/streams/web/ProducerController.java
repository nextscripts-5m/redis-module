package com.redislabs.streams.web;

import com.redislabs.streams.stream.OrderEventRequest;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.StreamEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.role", havingValue = "producer")
public class ProducerController {

    private final OrderStreamService streamService;

    public ProducerController(OrderStreamService streamService) {
        this.streamService = streamService;
    }

    @PostMapping("/orders")
    public StreamEvent append(@RequestBody(required = false) OrderEventRequest request) {
        OrderEventRequest safe = request == null ? new OrderEventRequest(null, null, Map.of()) : request;
        return streamService.append(safe);
    }

    @DeleteMapping("/events")
    public Map<String, Object> reset() {
        long deleted = streamService.clearMainStreamAndLabKeys();
        return Map.of(
                "deletedMainStream", deleted,
                "mainStreamLength", streamService.mainStreamLength(),
                "dlqLength", streamService.dlqLength()
        );
    }
}
