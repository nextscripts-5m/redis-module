package com.redislabs.streams.web;

import com.redislabs.streams.stream.OrderingTracker;
import com.redislabs.streams.stream.OrderingViolation;
import com.redislabs.streams.stream.RecentWorkerEvents;
import com.redislabs.streams.stream.WorkerEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/worker")
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class WorkerController {

    private final RecentWorkerEvents recentWorkerEvents;
    private final OrderingTracker orderingTracker;

    public WorkerController(RecentWorkerEvents recentWorkerEvents, OrderingTracker orderingTracker) {
        this.recentWorkerEvents = recentWorkerEvents;
        this.orderingTracker = orderingTracker;
    }

    @GetMapping("/messages")
    public List<WorkerEvent> messages() {
        return recentWorkerEvents.snapshot();
    }

    @GetMapping("/ordering-violations")
    public List<OrderingViolation> orderingViolations() {
        return orderingTracker.snapshot();
    }
}
