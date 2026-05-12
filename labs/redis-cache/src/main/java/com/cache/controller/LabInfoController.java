package com.cache.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small diagnostic endpoint for live demos (shows delay + active cache implementation).
 */
@RestController
@RequestMapping("/api/lab")
public class LabInfoController {

    private final CacheManager cacheManager;
    private final Environment environment;

    @Value("${app.simulated-db-delay-ms:0}")
    private long simulatedDbDelayMs;

    public LabInfoController(CacheManager cacheManager, Environment environment) {
        this.cacheManager = cacheManager;
        this.environment = environment;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("simulatedDbDelayMs", simulatedDbDelayMs);
        body.put("springCacheType", environment.getProperty("spring.cache.type", "(auto / redis)"));
        body.put("cacheManagerClass", cacheManager.getClass().getName());
        body.put("hint", "Use profile 'nocache' to disable caching and compare repeated GET latency.");
        return body;
    }
}
