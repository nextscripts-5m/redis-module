package com.redislabs.pubsub.messaging;

import com.redislabs.pubsub.config.LabProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class RecentMessages {

    private final Deque<ReceivedMessage> messages = new ArrayDeque<>();
    private final int limit;

    public RecentMessages(LabProperties properties) {
        this.limit = Math.max(1, properties.recentMessageLimit());
    }

    public synchronized void add(ReceivedMessage message) {
        messages.addFirst(message);
        while (messages.size() > limit) {
            messages.removeLast();
        }
    }

    public synchronized List<ReceivedMessage> snapshot() {
        return new ArrayList<>(messages);
    }
}
