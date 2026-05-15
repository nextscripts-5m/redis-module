package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "dlq-inspector")
public class StreamHealthMetrics {

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final OrderStreamService orderStreamService;
    private final AtomicLong mainLength = new AtomicLong();
    private final AtomicLong pendingTotal = new AtomicLong();
    private final AtomicLong dlqLength = new AtomicLong();

    public StreamHealthMetrics(
            StringRedisTemplate redisTemplate,
            LabProperties properties,
            OrderStreamService orderStreamService,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.orderStreamService = orderStreamService;
        Gauge.builder("redis.stream.length", mainLength, AtomicLong::get)
                .tag("stream", properties.streamName())
                .register(meterRegistry);
        Gauge.builder("redis.stream.pending.total", pendingTotal, AtomicLong::get)
                .tag("stream", properties.streamName())
                .tag("group", properties.groupName())
                .register(meterRegistry);
        Gauge.builder("redis.stream.dlq.length", dlqLength, AtomicLong::get)
                .tag("stream", properties.dlqStreamName())
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${APP_METRICS_REFRESH_MS:5000}")
    public void refresh() {
        mainLength.set(orderStreamService.mainStreamLength());
        dlqLength.set(orderStreamService.dlqLength());
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(properties.streamName(), properties.groupName());
            pendingTotal.set(summary == null ? 0L : summary.getTotalPendingMessages());
        } catch (Exception ex) {
            pendingTotal.set(0L);
        }
    }
}
