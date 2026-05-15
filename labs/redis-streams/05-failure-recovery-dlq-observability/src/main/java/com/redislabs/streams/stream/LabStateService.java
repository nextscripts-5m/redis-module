package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LabStateService {

    private static final String PROCESSED_PREFIX = "lab05:processed:";
    private static final String ATTEMPT_PREFIX = "lab05:recovery_attempts:";

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;

    public LabStateService(StringRedisTemplate redisTemplate, LabProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean alreadyProcessed(String messageId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(processedKey(messageId)));
    }

    public void markProcessed(String messageId) {
        redisTemplate.opsForValue().set(processedKey(messageId), "1", Duration.ofHours(24));
    }

    public long incrementRecoveryAttempts(String messageId) {
        Long v = redisTemplate.opsForValue().increment(attemptKey(messageId));
        redisTemplate.expire(attemptKey(messageId), Duration.ofHours(24));
        return v == null ? 0L : v;
    }

    public void resetAttempts(String messageId) {
        redisTemplate.delete(attemptKey(messageId));
    }

    private String processedKey(String messageId) {
        return PROCESSED_PREFIX + messageId;
    }

    private String attemptKey(String messageId) {
        return ATTEMPT_PREFIX + messageId;
    }

    public int maxRetries() {
        return Math.max(1, properties.maxRetriesBeforeDlq());
    }
}
