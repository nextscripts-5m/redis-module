package com.redislabs.scalability.sentinel.traffic;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public record RedisNodeConnection(
        String id,
        RedisClient client,
        StatefulRedisConnection<String, String> connection) implements AutoCloseable {

    public RedisCommands<String, String> commands() {
        return connection.sync();
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }
}
