package com.redislabs.streams.web;

import com.redislabs.streams.config.LabProperties;
import com.redislabs.streams.stream.OrderStreamService;
import com.redislabs.streams.stream.ReaderCursorService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class LabInfoController {

    private final LabProperties properties;
    private final Environment environment;
    private final OrderStreamService streamService;
    private final ReaderCursorService cursorService;

    public LabInfoController(
            LabProperties properties,
            Environment environment,
            OrderStreamService streamService,
            ReaderCursorService cursorService
    ) {
        this.properties = properties;
        this.environment = environment;
        this.streamService = streamService;
        this.cursorService = cursorService;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        body.put("role", properties.role());
        body.put("streamName", properties.streamName());
        body.put("producerName", properties.producerName());
        body.put("readerName", properties.readerName());
        body.put("readerCursorKey", properties.readerCursorKey());
        body.put("readerCursor", cursorService.currentCursor());
        body.put("streamLength", streamService.streamLength());
        body.put("failureProbability", properties.failureProbability());
        body.put("processingDelayMs", properties.processingDelayMs());
        body.put("hint", "Redis Streams persist entries until trimming/deletion; normal XREAD cursors are client-managed.");
        return body;
    }
}
