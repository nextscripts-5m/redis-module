package com.redislabs.scalability.sentinel.web;

import com.redislabs.scalability.sentinel.traffic.ReplicationTrafficService;
import com.redislabs.scalability.sentinel.traffic.ReplicationTrafficService.TrafficStats;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final ReplicationTrafficService traffic;

    public TrafficController(ReplicationTrafficService traffic) {
        this.traffic = traffic;
    }

    @PostMapping("/start")
    public TrafficStats start() {
        return traffic.start();
    }

    @PostMapping("/stop")
    public TrafficStats stop() {
        return traffic.stop();
    }

    @GetMapping("/stats")
    public TrafficStats stats() {
        return traffic.stats();
    }

    @PostMapping("/write-once")
    public Map<String, String> writeOnce() {
        String value = traffic.writeOnce();
        return Map.of("key", "lab:counter", "value", value);
    }

    @GetMapping("/read/{replicaId}")
    public Map<String, String> readReplica(@PathVariable String replicaId) {
        String value = traffic.readFromReplica(replicaId);
        return Map.of("replicaId", replicaId, "value", value != null ? value : "");
    }
}
