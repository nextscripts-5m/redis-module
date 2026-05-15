package com.redislabs.streams.stream;

import java.time.Instant;

public record WorkerEvent(
        String consumer,
        String group,
        String stream,
        String messageId,
        String type,
        String orderId,
        boolean acknowledged,
        String detail,
        Instant processedAt
) {
}
