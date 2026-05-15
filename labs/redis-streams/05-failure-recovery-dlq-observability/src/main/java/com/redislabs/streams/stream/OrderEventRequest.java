package com.redislabs.streams.stream;

import java.util.Map;

public record OrderEventRequest(
        String type,
        String orderId,
        Map<String, Object> payload
) {
    public String eventType() {
        return type == null || type.isBlank() ? "payment-requested" : type;
    }

    public String eventOrderId() {
        return orderId == null || orderId.isBlank() ? "42" : orderId;
    }

    public Map<String, Object> eventPayload() {
        return payload == null ? Map.of() : payload;
    }
}
