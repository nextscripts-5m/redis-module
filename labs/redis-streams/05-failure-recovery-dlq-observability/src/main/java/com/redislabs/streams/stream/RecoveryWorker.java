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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "recovery-worker")
public class RecoveryWorker implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RecoveryWorker.class);

    private final LabProperties properties;
    private final LabStateService labStateService;
    private final DlqService dlqService;
    private final StreamAutoClaimSupport autoClaimSupport;
    private final Counter claimedCounter;
    private final Counter recoveredCounter;
    private final Counter dlqMovedCounter;
    private final Counter idempotentSkipCounter;
    private final Counter recoveryFailedCounter;
    private final Timer recoveryTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public RecoveryWorker(
            LabProperties properties,
            LabStateService labStateService,
            DlqService dlqService,
            StreamAutoClaimSupport autoClaimSupport,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.labStateService = labStateService;
        this.dlqService = dlqService;
        this.autoClaimSupport = autoClaimSupport;
        this.claimedCounter = meterRegistry.counter(
                "redis.stream.recovery.claimed",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
        this.recoveredCounter = meterRegistry.counter(
                "redis.stream.recovery.succeeded",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
        this.dlqMovedCounter = meterRegistry.counter(
                "redis.stream.recovery.dlq_moved",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
        this.idempotentSkipCounter = meterRegistry.counter(
                "redis.stream.idempotent.skips",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
        this.recoveryFailedCounter = meterRegistry.counter(
                "redis.stream.recovery.failed",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
        this.recoveryTimer = meterRegistry.timer(
                "redis.stream.recovery.processing",
                "stream", properties.streamName(),
                "consumer", properties.consumerName()
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::recoveryLoop, properties.consumerName() + "-recovery-loop");
        worker.setDaemon(true);
        worker.start();
    }

    private void recoveryLoop() {
        log.info(
                "Recovery worker {} using XAUTOCLAIM on stream {} group {}",
                properties.consumerName(),
                properties.streamName(),
                properties.groupName()
        );
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> claimed = autoClaimSupport.autoClaim(
                        properties.streamName(),
                        properties.groupName(),
                        properties.consumerName(),
                        properties.recoveryMinIdleMs(),
                        properties.recoveryClaimBatchSize()
                );
                if (claimed.isEmpty()) {
                    sleepQuietly(properties.recoveryPollMs());
                    continue;
                }
                for (MapRecord<String, Object, Object> record : claimed) {
                    recoveryTimer.record(() -> handleClaimed(record));
                }
            } catch (Exception ex) {
                log.warn("Recovery loop error", ex);
                sleepQuietly(properties.recoveryPollMs());
            }
        }
    }

    private void handleClaimed(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();
        claimedCounter.increment();

        if (labStateService.alreadyProcessed(messageId)) {
            idempotentSkipCounter.increment();
            dlqService.acknowledge(properties.groupName(), record);
            log.info("Idempotent skip for already-processed message {}", messageId);
            return;
        }

        long attempts = labStateService.incrementRecoveryAttempts(messageId);
        if (attempts > labStateService.maxRetries()) {
            dlqService.appendPoisonMessage(record, "max recovery attempts exceeded", attempts);
            dlqService.acknowledge(properties.groupName(), record);
            labStateService.markProcessed(messageId);
            dlqMovedCounter.increment();
            log.warn("Moved message {} to DLQ after {} recovery attempts", messageId, attempts);
            return;
        }

        try {
            sleepRecoveryDelay();
            if (shouldFailRecovery()) {
                recoveryFailedCounter.increment();
                log.warn("Simulated recovery failure for {} — stays pending for next XAUTOCLAIM", messageId);
                return;
            }
            labStateService.markProcessed(messageId);
            dlqService.acknowledge(properties.groupName(), record);
            labStateService.resetAttempts(messageId);
            recoveredCounter.increment();
            log.info("Recovery worker acked {}", messageId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFailRecovery() {
        double p = Math.max(0.0, Math.min(1.0, properties.recoveryFailureProbability()));
        return ThreadLocalRandom.current().nextDouble() < p;
    }

    private void sleepRecoveryDelay() throws InterruptedException {
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
