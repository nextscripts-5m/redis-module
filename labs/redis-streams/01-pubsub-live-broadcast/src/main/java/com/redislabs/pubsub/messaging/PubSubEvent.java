package com.redislabs.pubsub.messaging;

import java.time.Instant;
import java.util.Map;

public record PubSubEvent(
        String id,
        String type,
        String orderId,
        String publisher,
        Instant createdAt,
        Map<String, Object> payload
) {
}
