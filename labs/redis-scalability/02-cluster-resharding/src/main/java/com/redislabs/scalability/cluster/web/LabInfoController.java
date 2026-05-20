package com.redislabs.scalability.cluster.web;

import com.redislabs.scalability.cluster.config.LabProperties;
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
        body.put("lab", "02-cluster-resharding");
        body.put("slides", "slides/05-Redis Scalability.md");
        body.put("hotspotTag", props.hotspotTag());
        body.put("hotspotRatio", props.hotspotRatio());
        body.put("endpoints", Map.of(
                "startHotspot", "POST /api/load/start?profile=hotspot",
                "startUniform", "POST /api/load/start?profile=uniform",
                "stop", "POST /api/load/stop",
                "stats", "GET /api/load/stats",
                "slot", "GET /api/keys/{key}/slot"));
        return body;
    }
}
