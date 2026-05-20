package com.redislabs.scalability.cluster.load;

import com.redislabs.scalability.cluster.config.LabProperties;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

@Service
public class ClusterLoadService {

    private final StringRedisTemplate redis;
    private final LettuceConnectionFactory connectionFactory;
    private final LabProperties props;
    private final Counter commandsOk;
    private final Counter commandErrors;
    private final Counter movedTotal;
    private final Counter askTotal;
    private final Timer commandLatency;
    private final AtomicLong seq = new AtomicLong();

    private volatile boolean running;
    private volatile LoadProfile profile = LoadProfile.HOTSPOT;
    private ExecutorService executor;
    private Future<?> loadTask;

    public ClusterLoadService(
            StringRedisTemplate redis,
            LettuceConnectionFactory connectionFactory,
            LabProperties props,
            MeterRegistry registry) {
        this.redis = redis;
        this.connectionFactory = connectionFactory;
        this.props = props;
        this.commandsOk = registry.counter("lab_cluster_commands_ok_total");
        this.commandErrors = registry.counter("lab_cluster_command_errors_total");
        this.movedTotal = registry.counter("lab_cluster_moved_total");
        this.askTotal = registry.counter("lab_cluster_ask_total");
        this.commandLatency = registry.timer("lab_cluster_command_duration");
    }

    public synchronized LoadStats start(LoadProfile newProfile) {
        this.profile = newProfile;
        if (running) {
            return stats();
        }
        running = true;
        executor = Executors.newSingleThreadExecutor();
        loadTask = executor.submit(this::loadLoop);
        return stats();
    }

    public synchronized LoadStats stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        return stats();
    }

    public boolean isRunning() {
        return running;
    }

    public LoadStats stats() {
        return new LoadStats(
                running,
                profile.name(),
                (long) commandsOk.count(),
                (long) commandErrors.count(),
                (long) movedTotal.count(),
                (long) askTotal.count());
    }

    public SlotInfo slotForKey(String key) {
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            int slot = connection.clusterGetSlotForKey(key.getBytes());
            RedisClusterNode node = connection.clusterGetNodeForSlot(slot);
            String nodeId = node != null ? node.getId() : "unknown";
            String host = node != null ? node.getHost() + ":" + node.getPort() : "unknown";
            return new SlotInfo(key, slot, nodeId, host);
        }
    }

    private void loadLoop() {
        while (running) {
            String key = nextKey();
            Timer.Sample sample = Timer.start();
            try {
                redis.opsForValue().set(key, "v-" + seq.incrementAndGet());
                commandsOk.increment();
            } catch (Exception ex) {
                commandErrors.increment();
                trackRedirect(ex);
            } finally {
                sample.stop(commandLatency);
            }
            sleep(props.loadIntervalMs());
        }
    }

    private String nextKey() {
        long n = seq.incrementAndGet();
        boolean hot = profile == LoadProfile.HOTSPOT
                && ThreadLocalRandom.current().nextDouble() < props.hotspotRatio();
        if (hot) {
            return props.hotspotTag() + ":key:" + n;
        }
        return "user:key:" + n;
    }

    private void trackRedirect(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                if (message.contains("MOVED")) {
                    movedTotal.increment();
                }
                if (message.contains("ASK")) {
                    askTotal.increment();
                }
            }
            if (cursor instanceof RedisException) {
                break;
            }
            cursor = cursor.getCause();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public enum LoadProfile {
        HOTSPOT,
        UNIFORM
    }

    public record LoadStats(
            boolean running,
            String profile,
            long commandsOk,
            long commandErrors,
            long movedTotal,
            long askTotal) {}

    public record SlotInfo(String key, int slot, String nodeId, String host) {}
}
