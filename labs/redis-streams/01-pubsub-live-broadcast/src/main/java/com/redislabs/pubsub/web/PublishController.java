package com.redislabs.pubsub.web;

import com.redislabs.pubsub.messaging.PubSubPublisher;
import com.redislabs.pubsub.messaging.PublishRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pubsub")
public class PublishController {

    private final PubSubPublisher publisher;

    public PublishController(PubSubPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/publish")
    public PubSubPublisher.PublishedEvent publish(@RequestBody(required = false) PublishRequest request) {
        return publisher.publish(request == null ? new PublishRequest(null, null, Map.of()) : request);
    }
}
