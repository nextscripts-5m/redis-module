package com.redislabs.streams.stream;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConsumerGroupSupport {

    private final StringRedisTemplate redisTemplate;

    public ConsumerGroupSupport(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void ensureGroupExists(String streamName, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0-0"), groupName);
        } catch (RedisSystemException | InvalidDataAccessApiUsageException ex) {
            if (isBusyGroup(ex)) {
                return;
            }
            if (isMissingStream(ex)) {
                createGroupWithMkStream(streamName, groupName);
                return;
            }
            throw ex;
        }
    }

    private void createGroupWithMkStream(String streamName, String groupName) {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.execute(
                    "XGROUP",
                    "CREATE".getBytes(),
                    streamName.getBytes(),
                    groupName.getBytes(),
                    ReadOffset.from("0-0").getOffset().getBytes(),
                    "MKSTREAM".getBytes()
            );
            return null;
        });
    }

    private boolean isBusyGroup(Throwable ex) {
        return rootMessage(ex).contains("BUSYGROUP");
    }

    private boolean isMissingStream(Throwable ex) {
        String message = rootMessage(ex);
        return message.contains("requires the key to exist") || message.contains("no such key");
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
