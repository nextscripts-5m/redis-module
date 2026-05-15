package com.redislabs.streams.stream;

import java.time.Instant;

public record OrderingViolation(
        String orderId,
        String paidMessageId,
        String consumer,
        String stream,
        String detail,
        Instant detectedAt
) {
}
