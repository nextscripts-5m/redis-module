package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class OrderingTracker {

    private static final String CREATED_KEY_PREFIX = "ordering-lab:created:";
    private static final Duration CREATED_KEY_TTL = Duration.ofHours(2);

    private final LabProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Counter violationCounter;
    private final Deque<OrderingViolation> violations = new ArrayDeque<>();
    private final int limit;

    public OrderingTracker(
            LabProperties properties,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.limit = Math.max(1, properties.recentEventLimit());
        this.violationCounter = meterRegistry.counter(
                "redis.stream.ordering.violations",
                "mode", properties.mode()
        );
    }

    public void onOrderCreatedCompleted(String orderId) {
        if (!properties.unorderedMode()) {
            return;
        }
        redisTemplate.opsForValue().set(
                createdKey(orderId),
                Instant.now().toString(),
                CREATED_KEY_TTL
        );
    }

    public void onOrderPaidCompleted(String orderId, String paidMessageId, String consumer, String stream) {
        if (!properties.unorderedMode()) {
            return;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(createdKey(orderId)))) {
            return;
        }
        OrderingViolation violation = new OrderingViolation(
                orderId,
                paidMessageId,
                consumer,
                stream,
                "order-paid completed before order-created for the same orderId",
                Instant.now()
        );
        violationCounter.increment();
        synchronized (violations) {
            violations.addFirst(violation);
            while (violations.size() > limit) {
                violations.removeLast();
            }
        }
    }

    public List<OrderingViolation> snapshot() {
        synchronized (violations) {
            return new ArrayList<>(violations);
        }
    }

    private String createdKey(String orderId) {
        return CREATED_KEY_PREFIX + orderId;
    }
}
