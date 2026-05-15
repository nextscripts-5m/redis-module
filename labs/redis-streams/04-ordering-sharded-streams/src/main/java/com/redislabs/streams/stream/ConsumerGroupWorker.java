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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ConsumerGroupWorker implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final OrderStreamService streamService;
    private final ProcessingDelayPolicy delayPolicy;
    private final RecentWorkerEvents recentWorkerEvents;
    private final OrderingTracker orderingTracker;
    private final Counter processedCounter;
    private final Counter ackCounter;
    private final Timer processingTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ConsumerGroupWorker(
            StringRedisTemplate redisTemplate,
            LabProperties properties,
            OrderStreamService streamService,
            ProcessingDelayPolicy delayPolicy,
            RecentWorkerEvents recentWorkerEvents,
            OrderingTracker orderingTracker,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.streamService = streamService;
        this.delayPolicy = delayPolicy;
        this.recentWorkerEvents = recentWorkerEvents;
        this.orderingTracker = orderingTracker;
        String stream = properties.workerStreamName();
        this.processedCounter = meterRegistry.counter(
                "redis.stream.messages.processed",
                "mode", properties.mode(),
                "stream", stream,
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
        this.ackCounter = meterRegistry.counter(
                "redis.stream.messages.acked",
                "mode", properties.mode(),
                "stream", stream,
                "group", properties.groupName(),
                "consumer", properties.consumerName()
        );
        this.processingTimer = meterRegistry.timer(
                "redis.stream.processing",
                "mode", properties.mode(),
                "stream", stream,
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
        String stream = properties.workerStreamName();
        Consumer consumer = Consumer.from(properties.groupName(), properties.consumerName());
        log.info(
                "Starting XREADGROUP on stream {} group {} consumer {} mode {}",
                stream,
                properties.groupName(),
                properties.consumerName(),
                properties.mode()
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
                                StreamOffset.create(stream, ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    processingTimer.record(() -> process(record, stream));
                }
            } catch (Exception ex) {
                log.warn(
                        "Consumer {} failed while polling stream {}",
                        properties.consumerName(),
                        stream,
                        ex
                );
                sleepAfterFailure();
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record, String stream) {
        StreamEvent event = streamService.toEvent(record, stream);
        long delayMs = delayPolicy.delayFor(event.type());
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }

            Long acked = redisTemplate.opsForStream().acknowledge(properties.groupName(), record);
            if (acked != null && acked > 0) {
                ackCounter.increment();
            }
            processedCounter.increment();

            if ("order-created".equalsIgnoreCase(event.type())) {
                orderingTracker.onOrderCreatedCompleted(event.orderId());
            }
            if ("order-paid".equalsIgnoreCase(event.type())) {
                orderingTracker.onOrderPaidCompleted(
                        event.orderId(),
                        event.id(),
                        properties.consumerName(),
                        stream
                );
            }

            recentWorkerEvents.add(new WorkerEvent(
                    properties.consumerName(),
                    properties.groupName(),
                    stream,
                    event.id(),
                    event.type(),
                    event.orderId(),
                    delayMs,
                    true,
                    "processed with type-based delay and XACK",
                    Instant.now()
            ));
            log.info(
                    "Consumer {} acked {} ({}) for order {} after {}ms",
                    properties.consumerName(),
                    event.id(),
                    event.type(),
                    event.orderId(),
                    delayMs
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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
