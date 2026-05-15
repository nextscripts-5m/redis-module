package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DlqService {

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final Counter dlqCounter;

    public DlqService(StringRedisTemplate redisTemplate, LabProperties properties, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.dlqCounter = meterRegistry.counter(
                "redis.stream.dlq.messages",
                "stream", properties.dlqStreamName()
        );
    }

    public void appendPoisonMessage(MapRecord<String, Object, Object> record, String reason, long recoveryAttempts) {
        Map<String, String> fields = new LinkedHashMap<>();
        record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));
        fields.put("dlqReason", reason);
        fields.put("originalMessageId", record.getId().getValue());
        fields.put("recoveryAttempts", String.valueOf(recoveryAttempts));
        fields.put("movedAt", Instant.now().toString());

        redisTemplate.opsForStream().add(properties.dlqStreamName(), fields);
        dlqCounter.increment();
    }

    public void acknowledge(String group, MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(group, record);
    }
}
