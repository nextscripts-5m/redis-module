package com.redislabs.scalability.sentinel.web;

import com.redislabs.scalability.sentinel.config.LabProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab")
public class LabInfoController {

    private final LabProperties props;

    public LabInfoController(LabProperties props) {
        this.props = props;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "01-sentinel-replication-ha");
        body.put("slides", "slides/05-Redis Scalability.md");
        body.put("counterKey", props.counterKey());
        body.put("replicaHosts", props.replicaHostList());
        body.put("endpoints", Map.of(
                "startTraffic", "POST /api/traffic/start",
                "stopTraffic", "POST /api/traffic/stop",
                "stats", "GET /api/traffic/stats",
                "writeOnce", "POST /api/traffic/write-once"));
        return body;
    }
}
