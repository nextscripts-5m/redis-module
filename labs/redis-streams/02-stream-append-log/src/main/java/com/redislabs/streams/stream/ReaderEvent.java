package com.redislabs.streams.stream;

import java.time.Instant;

public record ReaderEvent(
        String reader,
        String stream,
        String eventId,
        String type,
        String orderId,
        boolean processed,
        String outcome,
        Instant receivedAt
) {
}
