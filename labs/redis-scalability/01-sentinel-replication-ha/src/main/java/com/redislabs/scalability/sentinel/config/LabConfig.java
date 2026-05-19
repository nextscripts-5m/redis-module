package com.redislabs.scalability.sentinel.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LabProperties.class)
public class LabConfig {

    @Bean(destroyMethod = "close")
    ReplicaConnectionPool replicaConnections(LabProperties props) {
        List<ReplicaConnection> connections = new ArrayList<>();
        for (String hostPort : props.replicaHostList()) {
            String[] parts = hostPort.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
            RedisURI uri = RedisURI.Builder.redis(host, port).build();
            RedisClient client = RedisClient.create(uri);
            StatefulRedisConnection<String, String> connection = client.connect();
            connections.add(new ReplicaConnection(host + ":" + port, client, connection));
        }
        return new ReplicaConnectionPool(connections);
    }

    public static final class ReplicaConnectionPool implements AutoCloseable {

        private final List<ReplicaConnection> connections;

        public ReplicaConnectionPool(List<ReplicaConnection> connections) {
            this.connections = List.copyOf(connections);
        }

        public List<ReplicaConnection> connections() {
            return connections;
        }

        @Override
        public void close() {
            connections.forEach(ReplicaConnection::close);
        }
    }

    public record ReplicaConnection(
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
}
