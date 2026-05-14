package com.redislabs.streams.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final Counter producedCounter;
    private final Counter replayCounter;

    public OrderStreamService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LabProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.producedCounter = meterRegistry.counter(
                "redis.stream.messages.produced",
                "stream", properties.streamName(),
                "producer", properties.producerName()
        );
        this.replayCounter = meterRegistry.counter(
                "redis.stream.replay.requests",
                "stream", properties.streamName()
        );
        Gauge.builder("redis.stream.length", this, OrderStreamService::streamLength)
                .tag("stream", properties.streamName())
                .register(meterRegistry);
    }

    public StreamEvent append(OrderEventRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", request.eventType());
        fields.put("orderId", request.eventOrderId());
        fields.put("producer", properties.producerName());
        fields.put("createdAt", Instant.now().toString());
        fields.put("payload", serialize(request.eventPayload()));

        RecordId id = redisTemplate.opsForStream().add(properties.streamName(), fields);
        producedCounter.increment();
        return toEvent(id.getValue(), fields);
    }

    public List<StreamEvent> range(String from, int count) {
        replayCounter.increment();
        Range<String> range = Range.rightUnbounded(Range.Bound.inclusive(from));
        return redisTemplate.opsForStream()
                .range(properties.streamName(), range, Limit.limit().count(Math.max(1, count)))
                .stream()
                .map(this::toEvent)
                .toList();
    }

    public List<StreamEvent> all(int count) {
        replayCounter.increment();
        return redisTemplate.opsForStream()
                .range(properties.streamName(), Range.unbounded(), Limit.limit().count(Math.max(1, count)))
                .stream()
                .map(this::toEvent)
                .toList();
    }

    public long clearStreamAndCursor() {
        Boolean streamDeleted = redisTemplate.delete(properties.streamName());
        redisTemplate.delete(properties.readerCursorKey());
        return Boolean.TRUE.equals(streamDeleted) ? 1L : 0L;
    }

    public long streamLength() {
        try {
            Long size = redisTemplate.opsForStream().size(properties.streamName());
            return size == null ? 0L : size;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    StreamEvent toEvent(MapRecord<String, Object, Object> record) {
        Map<String, String> fields = new LinkedHashMap<>();
        record.getValue().forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        return toEvent(record.getId().getValue(), fields);
    }

    private StreamEvent toEvent(String id, Map<String, String> fields) {
        return new StreamEvent(
                id,
                fields.getOrDefault("type", "(missing)"),
                fields.getOrDefault("orderId", "(missing)"),
                fields.getOrDefault("producer", "(missing)"),
                parseInstant(fields.get("createdAt")),
                deserialize(fields.get("payload"))
        );
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
