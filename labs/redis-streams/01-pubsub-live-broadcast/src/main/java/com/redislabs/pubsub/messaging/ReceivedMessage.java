package com.redislabs.pubsub.messaging;

import java.time.Instant;

public record ReceivedMessage(
        String subscriber,
        String channel,
        String eventId,
        String type,
        String orderId,
        boolean processed,
        String outcome,
        Instant receivedAt
) {
}
