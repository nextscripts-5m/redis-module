package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import org.springframework.stereotype.Component;

@Component
public class OrderShardResolver {

    private final LabProperties properties;

    public OrderShardResolver(LabProperties properties) {
        this.properties = properties;
    }

    public int shardFor(String orderId) {
        return Math.floorMod(orderId.hashCode(), Math.max(1, properties.shardCount()));
    }

    public String streamFor(String orderId) {
        return properties.streamPrefix() + shardFor(orderId);
    }
}
