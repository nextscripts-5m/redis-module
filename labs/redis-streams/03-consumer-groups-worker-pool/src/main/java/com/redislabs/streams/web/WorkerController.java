package com.redislabs.streams.web;

import com.redislabs.streams.stream.PendingEntriesService;
import com.redislabs.streams.stream.RecentWorkerEvents;
import com.redislabs.streams.stream.WorkerEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worker")
@ConditionalOnProperty(name = "app.role", havingValue = "group-worker")
public class WorkerController {

    private final RecentWorkerEvents recentWorkerEvents;
    private final PendingEntriesService pendingEntriesService;

    public WorkerController(RecentWorkerEvents recentWorkerEvents, PendingEntriesService pendingEntriesService) {
        this.recentWorkerEvents = recentWorkerEvents;
        this.pendingEntriesService = pendingEntriesService;
    }

    @GetMapping("/messages")
    public List<WorkerEvent> messages() {
        return recentWorkerEvents.snapshot();
    }

    @GetMapping("/pending")
    public Map<String, Object> pendingSummary() {
        return pendingEntriesService.pendingSummary();
    }

    @GetMapping("/pending/entries")
    public Map<String, Object> pendingEntries(@RequestParam(defaultValue = "20") int count) {
        return pendingEntriesService.consumerPending(count);
    }
}
