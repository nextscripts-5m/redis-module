package com.redislabs.streams.stream;

import java.time.Instant;
import java.util.Map;

public record StreamEvent(
        String id,
        String type,
        String orderId,
        String producer,
        Instant createdAt,
        Map<String, Object> payload
) {
}
