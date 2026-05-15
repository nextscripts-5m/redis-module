package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.StreamEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@ConditionalOnProperty(name = "app.role", havingValue = "dlq-inspector")
public class DlqInspectorController {

    private final LabProperties properties;
    private final OrderStreamService streamService;
    private final StringRedisTemplate redisTemplate;

    public DlqInspectorController(
            LabProperties properties,
            OrderStreamService streamService,
            StringRedisTemplate redisTemplate
    ) {
        this.properties = properties;
        this.streamService = streamService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/events")
    public List<StreamEvent> dlqEvents(@RequestParam(defaultValue = "50") int count) {
        return streamService.rangeDlq(count);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mainStream", properties.streamName());
        body.put("mainStreamLength", streamService.mainStreamLength());
        body.put("dlqStream", properties.dlqStreamName());
        body.put("dlqLength", streamService.dlqLength());
        body.put("group", properties.groupName());
        try {
            PendingMessagesSummary pending = redisTemplate.opsForStream()
                    .pending(properties.streamName(), properties.groupName());
            if (pending != null) {
                body.put("pendingTotal", pending.getTotalPendingMessages());
                body.put("pendingMinId", pending.minMessageId());
                body.put("pendingMaxId", pending.maxMessageId());
            } else {
                body.put("pendingTotal", 0);
            }
        } catch (Exception ex) {
            body.put("pendingError", ex.getMessage());
        }
        return body;
    }
}
