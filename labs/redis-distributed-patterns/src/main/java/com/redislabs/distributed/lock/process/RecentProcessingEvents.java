package com.redislabs.distributed.lock.process;

import com.redislabs.distributed.lock.config.LabProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RecentProcessingEvents {

    private final Deque<ProcessingEvent> events = new ConcurrentLinkedDeque<>();
    private final int limit;

    public RecentProcessingEvents(LabProperties properties) {
        this.limit = properties.recentEventLimit();
    }

    public void record(ProcessingEvent event) {
        events.addFirst(event);
        while (events.size() > limit) {
            events.pollLast();
        }
    }

    public List<ProcessingEvent> list() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}
