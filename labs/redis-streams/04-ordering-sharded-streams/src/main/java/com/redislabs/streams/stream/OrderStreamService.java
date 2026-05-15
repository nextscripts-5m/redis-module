package com.redislabs.streams.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderStreamService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LabProperties properties;
    private final OrderShardResolver shardResolver;
    private final Counter producedCounter;

    public OrderStreamService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LabProperties properties,
            OrderShardResolver shardResolver,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.shardResolver = shardResolver;
        this.producedCounter = meterRegistry.counter(
                "redis.stream.messages.produced",
                "mode", properties.mode(),
                "producer", properties.producerName()
        );
    }

    public StreamEvent append(OrderEventRequest request) {
        String orderId = request.eventOrderId();
        String targetStream = resolveTargetStream(orderId);
        int shard = properties.shardedMode() ? shardResolver.shardFor(orderId) : -1;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", request.eventType());
        fields.put("orderId", orderId);
        fields.put("producer", properties.producerName());
        fields.put("createdAt", Instant.now().toString());
        fields.put("payload", serialize(request.eventPayload()));
        if (properties.shardedMode()) {
            fields.put("shard", String.valueOf(shard));
        }

        RecordId id = redisTemplate.opsForStream().add(targetStream, fields);
        producedCounter.increment();
        return toEvent(id.getValue(), targetStream, shard, fields);
    }

    public List<StreamEvent> lifecycle(OrderEventRequest request) {
        String orderId = request.eventOrderId();
        List<StreamEvent> events = new ArrayList<>(2);
        events.add(append(new OrderEventRequest("order-created", orderId, request.eventPayload())));
        events.add(append(new OrderEventRequest("order-paid", orderId, request.eventPayload())));
        return events;
    }

    public List<StreamEvent> listEvents(String stream, int count) {
        return redisTemplate.opsForStream()
                .range(stream, Range.unbounded(), Limit.limit().count(Math.max(1, count)))
                .stream()
                .map(record -> toEvent(record, stream))
                .toList();
    }

    public long clearForMode() {
        long deleted = 0;
        if (properties.unorderedMode()) {
            deleted += deleteIfExists(properties.streamName());
        }
        if (properties.shardedMode()) {
            for (int shard = 0; shard < properties.shardCount(); shard++) {
                deleted += deleteIfExists(properties.streamPrefix() + shard);
            }
        }
        return deleted;
    }

    private long deleteIfExists(String key) {
        Boolean deleted = redisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted) ? 1L : 0L;
    }

    private String resolveTargetStream(String orderId) {
        if (properties.shardedMode()) {
            return shardResolver.streamFor(orderId);
        }
        return properties.streamName();
    }

    StreamEvent toEvent(MapRecord<String, Object, Object> record, String stream) {
        Map<String, String> fields = new LinkedHashMap<>();
        record.getValue().forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        int shard = parseShard(fields.get("shard"));
        return toEvent(record.getId().getValue(), stream, shard, fields);
    }

    private StreamEvent toEvent(String id, String stream, int shard, Map<String, String> fields) {
        return new StreamEvent(
                id,
                stream,
                fields.getOrDefault("type", "(missing)"),
                fields.getOrDefault("orderId", "(missing)"),
                shard,
                fields.getOrDefault("producer", "(missing)"),
                parseInstant(fields.get("createdAt")),
                deserialize(fields.get("payload"))
        );
    }

    private int parseShard(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize stream payload", ex);
        }
    }

    private Map<String, Object> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of("raw", payload);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        return Instant.parse(value);
    }
}
