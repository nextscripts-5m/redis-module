package com.redislabs.pubsub.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislabs.pubsub.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PubSubSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PubSubSubscriber.class);

    private final ObjectMapper objectMapper;
    private final LabProperties properties;
    private final RecentMessages recentMessages;
    private final Counter receivedCounter;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public PubSubSubscriber(
            ObjectMapper objectMapper,
            LabProperties properties,
            RecentMessages recentMessages,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.recentMessages = recentMessages;
        this.receivedCounter = meterRegistry.counter(
                "redis.pubsub.messages.received",
                "subscriber", properties.subscriberName(),
                "channel", properties.channel()
        );
        this.processedCounter = meterRegistry.counter(
                "redis.pubsub.messages.processed",
                "subscriber", properties.subscriberName(),
                "channel", properties.channel()
        );
        this.failedCounter = meterRegistry.counter(
                "redis.pubsub.messages.failed",
                "subscriber", properties.subscriberName(),
                "channel", properties.channel()
        );
        this.processingTimer = meterRegistry.timer(
                "redis.pubsub.processing",
                "subscriber", properties.subscriberName(),
                "channel", properties.channel()
        );
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        receivedCounter.increment();
        processingTimer.record(() -> handle(channel, body));
    }

    private void handle(String channel, String body) {
        try {
            PubSubEvent event = objectMapper.readValue(body, PubSubEvent.class);
            sleepIfConfigured();

            if (shouldFail()) {
                failedCounter.increment();
                recentMessages.add(new ReceivedMessage(
                        properties.subscriberName(),
                        channel,
                        event.id(),
                        event.type(),
                        event.orderId(),
                        false,
                        "simulated failure; Pub/Sub will not retry",
                        Instant.now()
                ));
                log.warn("Subscriber {} failed event {} from channel {}", properties.subscriberName(), event.id(), channel);
                return;
            }

            processedCounter.increment();
            recentMessages.add(new ReceivedMessage(
                    properties.subscriberName(),
                    channel,
                    event.id(),
                    event.type(),
                    event.orderId(),
                    true,
                    "processed",
                    Instant.now()
            ));
            log.info("Subscriber {} processed event {} ({})", properties.subscriberName(), event.id(), event.type());
        } catch (Exception ex) {
            failedCounter.increment();
            recentMessages.add(new ReceivedMessage(
                    properties.subscriberName(),
                    channel,
                    "(unreadable)",
                    "(unreadable)",
                    "(unreadable)",
                    false,
                    "invalid payload: " + ex.getClass().getSimpleName(),
                    Instant.now()
            ));
            log.warn("Subscriber {} could not parse Pub/Sub message", properties.subscriberName(), ex);
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
}
