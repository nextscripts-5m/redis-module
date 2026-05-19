package com.redislabs.distributed.lock.web;

import com.redislabs.distributed.lock.config.LabProperties;
import com.redislabs.distributed.lock.lock.ReleaseMode;
import com.redislabs.distributed.lock.process.OrderProcessingService;
import com.redislabs.distributed.lock.process.ProcessingEvent;
import com.redislabs.distributed.lock.process.RecentProcessingEvents;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderProcessingController {

    private final OrderProcessingService processingService;
    private final RecentProcessingEvents recentEvents;
    private final LabProperties properties;

    public OrderProcessingController(
            OrderProcessingService processingService,
            RecentProcessingEvents recentEvents,
            LabProperties properties
    ) {
        this.processingService = processingService;
        this.recentEvents = recentEvents;
        this.properties = properties;
    }

    @PostMapping("/orders/{orderId}/process")
    public ProcessingEvent process(
            @PathVariable String orderId,
            @RequestParam(required = false) Long lockTtlMs,
            @RequestParam(required = false) Long processingDelayMs,
            @RequestParam(defaultValue = "safe") String releaseMode
    ) {
        ReleaseMode mode;
        try {
            mode = ReleaseMode.parse(releaseMode);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        try {
            return processingService.process(orderId, lockTtlMs, processingDelayMs, mode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Processing interrupted");
        }
    }

    @GetMapping("/workers/events")
    public Map<String, Object> recentEvents() {
        List<ProcessingEvent> events = recentEvents.list();
        return Map.of(
                "workerName", properties.workerName(),
                "count", events.size(),
                "events", events
        );
    }
}
