package com.redislabs.streams.web;

import com.redislabs.streams.stream.OrderEventRequest;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.StreamEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderStreamController {

    private final OrderStreamService streamService;

    public OrderStreamController(OrderStreamService streamService) {
        this.streamService = streamService;
    }

    @PostMapping("/orders")
    public StreamEvent appendOrderEvent(@RequestBody(required = false) OrderEventRequest request) {
        OrderEventRequest safeRequest = request == null ? new OrderEventRequest(null, null, Map.of()) : request;
        return streamService.append(safeRequest);
    }

    @GetMapping("/events")
    public List<StreamEvent> events(@RequestParam(defaultValue = "50") int count) {
        return streamService.all(count);
    }

    @GetMapping("/replay")
    public List<StreamEvent> replay(
            @RequestParam(defaultValue = "0-0") String from,
            @RequestParam(defaultValue = "50") int count
    ) {
        return streamService.range(from, count);
    }

    @DeleteMapping("/events")
    public Map<String, Object> clear() {
        long deleted = streamService.clearStreamAndCursor();
        return Map.of(
                "deletedStreamKeys", deleted,
                "streamLength", streamService.streamLength()
        );
    }
}
