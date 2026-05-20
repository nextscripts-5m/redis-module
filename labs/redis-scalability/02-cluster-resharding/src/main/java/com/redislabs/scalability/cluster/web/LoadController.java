package com.redislabs.scalability.cluster.web;

import com.redislabs.scalability.cluster.load.ClusterLoadService;
import com.redislabs.scalability.cluster.load.ClusterLoadService.LoadProfile;
import com.redislabs.scalability.cluster.load.ClusterLoadService.LoadStats;
import com.redislabs.scalability.cluster.load.ClusterLoadService.SlotInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LoadController {

    private final ClusterLoadService load;

    public LoadController(ClusterLoadService load) {
        this.load = load;
    }

    @PostMapping("/load/start")
    public LoadStats start(@RequestParam(defaultValue = "hotspot") String profile) {
        LoadProfile loadProfile =
                "uniform".equalsIgnoreCase(profile) ? LoadProfile.UNIFORM : LoadProfile.HOTSPOT;
        return load.start(loadProfile);
    }

    @PostMapping("/load/stop")
    public LoadStats stop() {
        return load.stop();
    }

    @GetMapping("/load/stats")
    public LoadStats stats() {
        return load.stats();
    }

    @GetMapping("/keys/{key}/slot")
    public SlotInfo slot(@PathVariable String key) {
        return load.slotForKey(key);
    }
}
