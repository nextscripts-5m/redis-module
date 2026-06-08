package com.redislabs.scalability.sentinel.traffic;

import org.springframework.stereotype.Component;

@Component
public class RedisNodeRoleChecker {

    public String role(RedisNodeConnection node) {
        // Redis: INFO replication
        String info = node.commands().info("replication");
        if (info == null) {
            throw new IllegalStateException("No replication INFO from " + node.id());
        }
        for (String line : info.split("\r?\n")) {
            if (line.startsWith("role:")) {
                return line.substring("role:".length()).trim();
            }
        }
        throw new IllegalStateException("role not found for " + node.id());
    }
}
