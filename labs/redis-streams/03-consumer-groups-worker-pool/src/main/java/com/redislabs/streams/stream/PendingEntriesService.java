package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PendingEntriesService {

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;

    public PendingEntriesService(StringRedisTemplate redisTemplate, LabProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Map<String, Object> pendingSummary() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stream", properties.streamName());
        body.put("group", properties.groupName());
        body.put("consumer", properties.consumerName());

        if (!properties.groupWorkerRole()) {
            body.put("pendingCount", 0);
            body.put("hint", "Pending entries are exposed on group-worker instances.");
            return body;
        }

        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(properties.streamName(), properties.groupName());
            if (summary == null) {
                body.put("pendingCount", 0);
                return body;
            }
            body.put("pendingCount", summary.getTotalPendingMessages());
            body.put("minId", summary.minMessageId());
            body.put("maxId", summary.maxMessageId());
            body.put("consumersWithPending", summary.getPendingMessagesPerConsumer());
        } catch (Exception ex) {
            body.put("pendingCount", 0);
            body.put("error", ex.getMessage());
        }
        return body;
    }

    public Map<String, Object> consumerPending(int count) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stream", properties.streamName());
        body.put("group", properties.groupName());
        body.put("consumer", properties.consumerName());

        if (!properties.groupWorkerRole()) {
            body.put("entries", java.util.List.of());
            return body;
        }

        try {
            Consumer consumer = Consumer.from(properties.groupName(), properties.consumerName());
            PendingMessages pending = redisTemplate.opsForStream().pending(
                    properties.streamName(),
                    consumer,
                    Range.unbounded(),
                    Math.max(1, count)
            );
            if (pending == null) {
                body.put("entries", java.util.List.of());
                return body;
            }
            body.put("entries", pending.stream()
                    .map(message -> Map.of(
                            "id", message.getIdAsString(),
                            "consumer", message.getConsumerName(),
                            "idleMs", message.getElapsedTimeSinceLastDelivery().toMillis(),
                            "deliveryCount", message.getTotalDeliveryCount()
                    ))
                    .toList());
        } catch (Exception ex) {
            body.put("entries", java.util.List.of());
            body.put("error", ex.getMessage());
        }
        return body;
    }
}
