package com.redislabs.distributed.lock.web;

import com.redislabs.distributed.lock.config.LabProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class LabInfoController {

    private final LabProperties properties;

    public LabInfoController(LabProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workerName", properties.workerName());
        body.put("defaultLockTtlMs", properties.defaultLockTtlMs());
        body.put("defaultProcessingDelayMs", properties.defaultProcessingDelayMs());
        body.put("releaseMode", "query param on POST /api/orders/{id}/process — default safe");
        body.put("scenarios", List.of(
                Map.of(
                        "name", "scenario-3-unsafe",
                        "releaseMode", "unsafe",
                        "curl", "curl -s -X POST 'http://localhost:18100/api/orders/99/process"
                                + "?lockTtlMs=2000&processingDelayMs=5000&releaseMode=unsafe' | jq ."
                ),
                Map.of(
                        "name", "scenario-4-safe",
                        "releaseMode", "safe",
                        "curl", "curl -s -X POST 'http://localhost:18100/api/orders/99/process"
                                + "?lockTtlMs=2000&processingDelayMs=5000&releaseMode=safe' | jq ."
                )
        ));
        body.put("companion", "Terminal steps: TESTING.md (redis-cli). Spring steps: TESTING-SPRING.md");
        return body;
    }
}
