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
import org.springframework.data.redis.connection.stream.Consumer;
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
@ConditionalOnProperty(name = "app.role", havingValue = "group-worker")
public class ConsumerGroupWorker implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final OrderStreamService streamService;
    private final RecentWorkerEvents recentWorkerEvents;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter ackCounter;
    private final Timer processingTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ConsumerGroupWorker(
            StringRedisTemplate redisTemplate,
            LabProperties properties,
            OrderStreamService streamService,
            RecentWorkerEvents recentWorkerEvents,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.streamService = streamService;
        this.recentWorkerEvents = recentWorkerEvents;
        this.processedCounter = meterRegistry.counter(
                "redis.stream.messages.processed",
                "stream", properties.streamName(),
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
        this.failedCounter = meterRegistry.counter(
                "redis.stream.messages.failed",
                "stream", properties.streamName(),
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
        this.ackCounter = meterRegistry.counter(
                "redis.stream.messages.acked",
                "stream", properties.streamName(),
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
        this.processingTimer = meterRegistry.timer(
                "redis.stream.processing",
                "stream", properties.streamName(),
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::readLoop, properties.consumerName() + "-xreadgroup-loop");
        worker.setDaemon(true);
        worker.start();
    }

    private void readLoop() {
        Consumer consumer = Consumer.from(properties.groupName(), properties.consumerName());
        log.info(
                "Starting XREADGROUP loop for stream {} group {} consumer {}",
                properties.streamName(),
                properties.groupName(),
                properties.consumerName()
        );
        while (running.get()) {
            try {
                StreamReadOptions options = StreamReadOptions.empty()
                        .count(Math.max(1, properties.readBatchSize()))
                        .block(Duration.ofMillis(Math.max(1, properties.readBlockMs())));
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                        .read(
                                consumer,
                                options,
                                StreamOffset.create(properties.streamName(), ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    processingTimer.record(() -> process(record));
                }
            } catch (Exception ex) {
                failedCounter.increment();
                log.warn(
                        "Consumer {} in group {} failed while polling stream {}",
                        properties.consumerName(),
                        properties.groupName(),
                        properties.streamName(),
                        ex
                );
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
                recentWorkerEvents.add(new WorkerEvent(
                        properties.consumerName(),
                        properties.groupName(),
                        properties.streamName(),
                        event.id(),
                        event.type(),
                        event.orderId(),
                        false,
                        "simulated failure; entry stays in PEL until XACK",
                        Instant.now()
                ));
                log.warn(
                        "Consumer {} failed event {}. Entry remains pending in group {}",
                        properties.consumerName(),
                        event.id(),
                        properties.groupName()
                );
                return;
            }

            Long acked = redisTemplate.opsForStream().acknowledge(properties.groupName(), record);
            if (acked != null && acked > 0) {
                ackCounter.increment();
            }
            processedCounter.increment();
            recentWorkerEvents.add(new WorkerEvent(
                    properties.consumerName(),
                    properties.groupName(),
                    properties.streamName(),
                    event.id(),
                    event.type(),
                    event.orderId(),
                    true,
                    "processed and acknowledged with XACK",
                    Instant.now()
            ));
            log.info(
                    "Consumer {} acknowledged event {} in group {}",
                    properties.consumerName(),
                    event.id(),
                    properties.groupName()
            );
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
