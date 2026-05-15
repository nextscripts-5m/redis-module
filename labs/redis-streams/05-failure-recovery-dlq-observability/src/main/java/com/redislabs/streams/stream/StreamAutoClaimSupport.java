package com.redislabs.streams.stream;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StreamAutoClaimSupport {

    private final StringRedisTemplate redisTemplate;

    public StreamAutoClaimSupport(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Reclaims idle pending entries (same intent as {@code XAUTOCLAIM}): entries idle longer than
     * {@code minIdleMs} are assigned to {@code consumer}.
     * <p>
     * Implemented with {@code XPENDING} + {@code XCLAIM} via {@link RedisStreamCommands}. Raw
     * {@code connection.execute("XAUTOCLAIM", ...)} was brittle: the reply is not always exposed as
     * a {@link List}, so parsing returned no records and the recovery worker never processed PEL
     * entries.
     */
    public List<MapRecord<String, Object, Object>> autoClaim(
            String stream,
            String group,
            String consumer,
            long minIdleMs,
            long count
    ) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        return redisTemplate.execute((RedisCallback<List<MapRecord<String, Object, Object>>>) connection -> {
            byte[] key = stream.getBytes(StandardCharsets.UTF_8);
            RedisStreamCommands streamCommands = connection.streamCommands();

            long scanLimit = Math.min(1000L, Math.max(100L, count * 50L));
            PendingMessages pending = streamCommands.xPending(key, group, Range.unbounded(), scanLimit);
            if (pending == null || pending.isEmpty()) {
                return Collections.emptyList();
            }

            List<RecordId> ids = new ArrayList<>();
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().toMillis() <= minIdleMs) {
                    continue;
                }
                ids.add(message.getId());
                if (ids.size() >= count) {
                    break;
                }
            }
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }

            RedisStreamCommands.XClaimOptions options = RedisStreamCommands.XClaimOptions
                    .minIdleMs(minIdleMs)
                    .ids(ids);

            List<ByteRecord> claimed = streamCommands.xClaim(key, group, consumer, options);
            if (claimed == null || claimed.isEmpty()) {
                return Collections.emptyList();
            }
            return toMapRecords(stream, claimed);
        });
    }

    private static List<MapRecord<String, Object, Object>> toMapRecords(String stream, List<ByteRecord> claimed) {
        List<MapRecord<String, Object, Object>> out = new ArrayList<>(claimed.size());
        for (ByteRecord br : claimed) {
            Map<String, Object> fields = new LinkedHashMap<>();
            Map<byte[], byte[]> raw = br.getValue();
            if (raw != null) {
                raw.forEach((k, v) -> fields.put(
                        new String(k, StandardCharsets.UTF_8),
                        new String(v, StandardCharsets.UTF_8)
                ));
            }
            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = (MapRecord<String, Object, Object>) (MapRecord<?, ?, ?>)
                    StreamRecords.mapBacked(fields)
                            .withId(br.getId())
                            .withStreamKey(stream);
            out.add(record);
        }
        return out;
    }
}
