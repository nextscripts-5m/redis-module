package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class RecentReaderEvents {

    private final Deque<ReaderEvent> events = new ArrayDeque<>();
    private final int limit;

    public RecentReaderEvents(LabProperties properties) {
        this.limit = Math.max(1, properties.recentEventLimit());
    }

    public synchronized void add(ReaderEvent event) {
        events.addFirst(event);
        while (events.size() > limit) {
            events.removeLast();
        }
    }

    public synchronized List<ReaderEvent> snapshot() {
        return new ArrayList<>(events);
    }
}
