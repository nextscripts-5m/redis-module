package com.redislabs.streams.stream;

import com.redislabs.streams.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "stream-reader")
public class StreamReaderWorker implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StreamReaderWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final OrderStreamService streamService;
    private final ReaderCursorService cursorService;
    private final RecentReaderEvents recentReaderEvents;
    private final Counter readCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public StreamReaderWorker(
            StringRedisTemplate redisTemplate,
            LabProperties properties,
            OrderStreamService streamService,
            ReaderCursorService cursorService,
            RecentReaderEvents recentReaderEvents,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.streamService = streamService;
        this.cursorService = cursorService;
        this.recentReaderEvents = recentReaderEvents;
        this.readCounter = meterRegistry.counter(
                "redis.stream.messages.read",
                "stream", properties.streamName(),
                "reader", properties.readerName()
        );
        this.failedCounter = meterRegistry.counter(
                "redis.stream.messages.failed",
                "stream", properties.streamName(),
                "reader", properties.readerName()
        );
        this.processingTimer = meterRegistry.timer(
                "redis.stream.processing",
                "stream", properties.streamName(),
                "reader", properties.readerName()
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::readLoop, properties.readerName() + "-xread-loop");
        worker.setDaemon(true);
        worker.start();
    }

    private void readLoop() {
        log.info("Starting XREAD loop for stream {} with cursor key {}", properties.streamName(), properties.readerCursorKey());
        while (running.get()) {
            try {
                String cursor = cursorService.currentCursor();
                StreamReadOptions options = StreamReadOptions.empty()
                        .count(Math.max(1, properties.readBatchSize()))
                        .block(Duration.ofMillis(Math.max(1, properties.readBlockMs())));
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                        .read(options, StreamOffset.create(properties.streamName(), ReadOffset.from(cursor)));

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    processingTimer.record(() -> process(record));
                }
            } catch (Exception ex) {
                failedCounter.increment();
                log.warn("Reader {} failed while polling stream {}", properties.readerName(), properties.streamName(), ex);
                sleepAfterFailure();
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        StreamEvent event = streamService.toEvent(record);
        try {
            sleepIfConfigured();
            if (shouldFail()) {
                failedCounter.increment();
                recentReaderEvents.add(new ReaderEvent(
                        properties.readerName(),
                        properties.streamName(),
                        event.id(),
                        event.type(),
                        event.orderId(),
                        false,
                        "simulated failure; cursor not advanced",
                        Instant.now()
                ));
                log.warn("Reader {} failed event {}. Cursor remains at {}", properties.readerName(), event.id(), cursorService.currentCursor());
                return;
            }

            cursorService.setCursor(event.id());
            readCounter.increment();
            recentReaderEvents.add(new ReaderEvent(
                    properties.readerName(),
                    properties.streamName(),
                    event.id(),
                    event.type(),
                    event.orderId(),
                    true,
                    "processed; cursor advanced",
                    Instant.now()
            ));
            log.info("Reader {} processed event {}", properties.readerName(), event.id());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFail() {
        double probability = Math.max(0.0, Math.min(1.0, properties.failureProbability()));
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private void sleepIfConfigured() throws InterruptedException {
        if (properties.processingDelayMs() > 0) {
            Thread.sleep(properties.processingDelayMs());
        }
    }

    private void sleepAfterFailure() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        running.set(false);
    }
}
