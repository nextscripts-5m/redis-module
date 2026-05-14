package com.redislabs.pubsub.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislabs.pubsub.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PubSubPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LabProperties properties;
    private final Counter publishedCounter;

    public PubSubPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LabProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.publishedCounter = meterRegistry.counter(
                "redis.pubsub.messages.published",
                "publisher", properties.publisherName(),
                "channel", properties.channel()
        );
    }

    public PublishedEvent publish(PublishRequest request) {
        PubSubEvent event = new PubSubEvent(
                UUID.randomUUID().toString(),
                request.eventType(),
                request.eventOrderId(),
                properties.publisherName(),
                Instant.now(),
                request.eventPayload()
        );
        long receivers = redisTemplate.convertAndSend(properties.channel(), serialize(event));
        publishedCounter.increment();
        return new PublishedEvent(properties.channel(), receivers, event);
    }

    private String serialize(PubSubEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize Pub/Sub event", ex);
        }
    }

    public record PublishedEvent(String channel, long receiversAtPublishTime, PubSubEvent event) {
    }
}
