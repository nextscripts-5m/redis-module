package com.redislabs.pubsub.web;

import com.redislabs.pubsub.messaging.ReceivedMessage;
import com.redislabs.pubsub.messaging.RecentMessages;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriber")
public class SubscriberController {

    private final RecentMessages recentMessages;

    public SubscriberController(RecentMessages recentMessages) {
        this.recentMessages = recentMessages;
    }

    @GetMapping("/messages")
    public List<ReceivedMessage> messages() {
        return recentMessages.snapshot();
    }
}
