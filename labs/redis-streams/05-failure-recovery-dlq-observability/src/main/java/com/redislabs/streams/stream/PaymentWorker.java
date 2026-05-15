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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "payment-worker")
public class PaymentWorker implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final LabProperties properties;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter ackCounter;
    private final Timer processingTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PaymentWorker(
            StringRedisTemplate redisTemplate,
            LabProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.processedCounter = meterRegistry.counter(
                "redis.stream.messages.processed",
                "stream", properties.streamName(),
                "consumer", properties.consumerName(),
                "role", "payment"
        );
        this.failedCounter = meterRegistry.counter(
                "redis.stream.messages.failed",
                "stream", properties.streamName(),
                "consumer", properties.consumerName(),
                "role", "payment"
        );
        this.ackCounter = meterRegistry.counter(
                "redis.stream.messages.acked",
                "stream", properties.streamName(),
                "consumer", properties.consumerName(),
                "role", "payment"
        );
        this.processingTimer = meterRegistry.timer(
                "redis.stream.processing",
                "stream", properties.streamName(),
                "consumer", properties.consumerName(),
                "role", "payment"
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::readLoop, properties.consumerName() + "-payment-loop");
        worker.setDaemon(true);
        worker.start();
    }

    private void readLoop() {
        Consumer consumer = Consumer.from(properties.groupName(), properties.consumerName());
        log.info("Payment worker {} reading group {} stream {}", properties.consumerName(), properties.groupName(), properties.streamName());
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
                log.warn("Payment worker poll failed", ex);
                sleepQuietly(500);
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();
        try {
            sleepDelay();
            if (shouldFail()) {
                failedCounter.increment();
                log.warn(
                        "Simulated payment failure for {} — no XACK; message stays in PEL for {}",
                        messageId,
                        properties.consumerName()
                );
                return;
            }
            Long acked = redisTemplate.opsForStream().acknowledge(properties.groupName(), record);
            if (acked != null && acked > 0) {
                ackCounter.increment();
            }
            processedCounter.increment();
            log.info("Payment worker acked {}", messageId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFail() {
        double p = Math.max(0.0, Math.min(1.0, properties.failureProbability()));
        return ThreadLocalRandom.current().nextDouble() < p;
    }

    private void sleepDelay() throws InterruptedException {
        if (properties.processingDelayMs() > 0) {
            Thread.sleep(properties.processingDelayMs());
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        running.set(false);
    }
}
