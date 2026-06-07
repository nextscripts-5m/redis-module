package com.redislabs.scalability.sentinel.traffic;

import com.redislabs.scalability.sentinel.config.LabProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReplicationTrafficService {

    private final StringRedisTemplate masterTemplate;
    private final LabProperties props;
    private final RedisNodeRoleChecker roleChecker;
    private final Counter writesTotal;
    private final Counter readsTotal;
    private final Counter staleReadsTotal;
    private final Counter writeErrorsTotal;
    private final AtomicReference<String> lastWritten = new AtomicReference<>("");
    private final AtomicLong writeSeq = new AtomicLong();
    private final AtomicReference<String> currentMaster = new AtomicReference<>("");
    private final AtomicReference<List<String>> activeReadTargets = new AtomicReference<>(List.of());
    private final Map<String, RedisNodeConnection> nodeConnections = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> activeReaders = new ConcurrentHashMap<>();

    private volatile boolean running;
    private ExecutorService executor;
    private Future<?> writerTask;
    private Future<?> topologyTask;

    public ReplicationTrafficService(
            StringRedisTemplate masterTemplate,
            LabProperties props,
            RedisNodeRoleChecker roleChecker,
            MeterRegistry registry) {
        this.masterTemplate = masterTemplate;
        this.props = props;
        this.roleChecker = roleChecker;
        this.writesTotal = registry.counter("lab_writes_total");
        this.readsTotal = registry.counter("lab_reads_total");
        this.staleReadsTotal = registry.counter("lab_stale_reads_total");
        this.writeErrorsTotal = registry.counter("lab_write_errors_total");
    }

    public synchronized TrafficStats start() {
        if (running) {
            return stats();
        }
        running = true;
        int poolSize = Math.max(4, props.redisNodeList().size() + 2);
        executor = Executors.newFixedThreadPool(poolSize);
        writerTask = executor.submit(this::writerLoop);
        refreshReadPool();
        topologyTask = executor.submit(this::topologyLoop);
        return stats();
    }

    public synchronized TrafficStats stop() {
        running = false;
        stopAllReaders();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        closeAllConnections();
        currentMaster.set("");
        activeReadTargets.set(List.of());
        return stats();
    }

    public boolean isRunning() {
        return running;
    }

    public TrafficStats stats() {
        return new TrafficStats(
                running,
                (long) writesTotal.count(),
                (long) readsTotal.count(),
                (long) staleReadsTotal.count(),
                (long) writeErrorsTotal.count(),
                lastWritten.get(),
                currentMaster.get(),
                activeReadTargets.get());
    }

    public String readFromReplica(String hostOrNodeId) {
        String nodeId = resolveNodeId(hostOrNodeId);
        if (!activeReadTargets.get().contains(nodeId)) {
            throw new IllegalArgumentException("Node is not an active read target (not a replica): " + hostOrNodeId);
        }
        RedisNodeConnection node = nodeConnections.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node: " + hostOrNodeId);
        }
        return node.commands().get(props.counterKey());
    }

    private String resolveNodeId(String hostOrNodeId) {
        if (hostOrNodeId.contains(":")) {
            return hostOrNodeId;
        }
        for (String nodeId : props.redisNodeList()) {
            if (nodeId.equals(hostOrNodeId) || nodeId.startsWith(hostOrNodeId + ":")) {
                return nodeId;
            }
        }
        throw new IllegalArgumentException("Unknown node: " + hostOrNodeId);
    }

    public String writeOnce() {
        String value = System.currentTimeMillis() + "-" + writeSeq.incrementAndGet();
        masterTemplate.opsForValue().set(props.counterKey(), value);
        lastWritten.set(value);
        writesTotal.increment();
        return value;
    }

    private void writerLoop() {
        while (running) {
            try {
                writeOnce();
                sleep(props.writeIntervalMs());
            } catch (Exception ex) {
                writeErrorsTotal.increment();
                sleep(500);
            }
        }
    }

    private void topologyLoop() {
        while (running) {
            try {
                refreshReadPool();
            } catch (Exception ex) {
                // keep polling through transient errors
            }
            sleep(Math.max(500, props.topologyRefreshMs()));
        }
    }

    private void refreshReadPool() {
        Set<String> slaveIds = new HashSet<>();
        String masterId = "";

        for (String nodeId : props.redisNodeList()) {
            try {
                RedisNodeConnection node = connectionFor(nodeId);
                String role = roleChecker.role(node);
                if ("master".equals(role)) {
                    masterId = nodeId;
                } else if ("slave".equals(role)) {
                    slaveIds.add(nodeId);
                }
            } catch (Exception ex) {
                removeConnection(nodeId);
            }
        }

        if (!masterId.isEmpty()) {
            currentMaster.set(masterId);
        }
        syncReaders(slaveIds);
        activeReadTargets.set(List.copyOf(new ArrayList<>(slaveIds)));
    }

    private void syncReaders(Set<String> slaveIds) {
        Set<String> toStop = new HashSet<>(activeReaders.keySet());
        toStop.removeAll(slaveIds);
        toStop.forEach(this::stopReader);

        for (String slaveId : slaveIds) {
            if (!activeReaders.containsKey(slaveId)) {
                startReader(slaveId);
            }
        }
    }

    private void startReader(String nodeId) {
        Future<?> task = executor.submit(() -> readerLoop(nodeId));
        activeReaders.put(nodeId, task);
    }

    private void stopReader(String nodeId) {
        Future<?> task = activeReaders.remove(nodeId);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void stopAllReaders() {
        new ArrayList<>(activeReaders.keySet()).forEach(this::stopReader);
    }

    private void readerLoop(String nodeId) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                RedisNodeConnection node = connectionFor(nodeId);
                if (!"slave".equals(roleChecker.role(node))) {
                    return;
                }
                String read = node.commands().get(props.counterKey());
                readsTotal.increment();
                String expected = lastWritten.get();
                if (!expected.isEmpty() && read != null && !read.equals(expected)) {
                    staleReadsTotal.increment();
                }
                sleep(props.readIntervalMs());
            } catch (Exception ex) {
                if (running) {
                    sleep(200);
                }
            }
        }
    }

    private RedisNodeConnection connectionFor(String nodeId) {
        return nodeConnections.computeIfAbsent(nodeId, this::openConnection);
    }

    private RedisNodeConnection openConnection(String nodeId) {
        String[] parts = nodeId.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
        RedisURI uri = RedisURI.Builder.redis(host, port).build();
        RedisClient client = RedisClient.create(uri);
        return new RedisNodeConnection(nodeId, client, client.connect());
    }

    private void removeConnection(String nodeId) {
        stopReader(nodeId);
        RedisNodeConnection connection = nodeConnections.remove(nodeId);
        if (connection != null) {
            connection.close();
        }
    }

    private void closeAllConnections() {
        nodeConnections.values().forEach(RedisNodeConnection::close);
        nodeConnections.clear();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record TrafficStats(
            boolean running,
            long writes,
            long reads,
            long staleReads,
            long writeErrors,
            String lastWrittenValue,
            String currentMaster,
            List<String> readTargets) {}
}
