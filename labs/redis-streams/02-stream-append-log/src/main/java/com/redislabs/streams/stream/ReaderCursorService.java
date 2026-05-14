package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReaderCursorService {

    private static final String INITIAL_CURSOR = "0-0";

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;

    public ReaderCursorService(StringRedisTemplate redisTemplate, LabProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String currentCursor() {
        String cursor = redisTemplate.opsForValue().get(properties.readerCursorKey());
        return cursor == null || cursor.isBlank() ? INITIAL_CURSOR : cursor;
    }

    public Map<String, Object> setCursor(String cursor) {
        String safeCursor = cursor == null || cursor.isBlank() ? INITIAL_CURSOR : cursor;
        redisTemplate.opsForValue().set(properties.readerCursorKey(), safeCursor);
        return Map.of(
                "reader", properties.readerName(),
                "cursorKey", properties.readerCursorKey(),
                "cursor", safeCursor
        );
    }

    public Map<String, Object> resetCursor() {
        redisTemplate.delete(properties.readerCursorKey());
        return Map.of(
                "reader", properties.readerName(),
                "cursorKey", properties.readerCursorKey(),
                "cursor", INITIAL_CURSOR
        );
    }
}
