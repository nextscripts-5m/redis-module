package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class RecentWorkerEvents {

    private final Deque<WorkerEvent> events = new ArrayDeque<>();
    private final int limit;

    public RecentWorkerEvents(LabProperties properties) {
        this.limit = Math.max(1, properties.recentEventLimit());
    }

    public synchronized void add(WorkerEvent event) {
        events.addFirst(event);
        while (events.size() > limit) {
            events.removeLast();
        }
    }

    public synchronized List<WorkerEvent> snapshot() {
        return new ArrayList<>(events);
    }
}
