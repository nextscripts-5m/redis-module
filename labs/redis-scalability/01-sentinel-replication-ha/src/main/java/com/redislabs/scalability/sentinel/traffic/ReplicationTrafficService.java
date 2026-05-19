package com.redislabs.scalability.sentinel.traffic;

import com.redislabs.scalability.sentinel.config.LabConfig.ReplicaConnection;
import com.redislabs.scalability.sentinel.config.LabConfig.ReplicaConnectionPool;
import com.redislabs.scalability.sentinel.config.LabProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
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
    private final List<ReplicaConnection> replicas;
    private final Counter writesTotal;
    private final Counter readsTotal;
    private final Counter staleReadsTotal;
    private final Counter writeErrorsTotal;
    private final AtomicReference<String> lastWritten = new AtomicReference<>("");
    private final AtomicLong writeSeq = new AtomicLong();

    private volatile boolean running;
    private ExecutorService executor;
    private Future<?> writerTask;

    public ReplicationTrafficService(
            StringRedisTemplate masterTemplate,
            LabProperties props,
            ReplicaConnectionPool replicaPool,
            MeterRegistry registry) {
        this.masterTemplate = masterTemplate;
        this.props = props;
        this.replicas = replicaPool.connections();
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
        executor = Executors.newFixedThreadPool(1 + replicas.size());
        writerTask = executor.submit(this::writerLoop);
        replicas.forEach(replica -> executor.submit(() -> readerLoop(replica)));
        return stats();
    }

    public synchronized TrafficStats stop() {
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

    public TrafficStats stats() {
        return new TrafficStats(
                running,
                (long) writesTotal.count(),
                (long) readsTotal.count(),
                (long) staleReadsTotal.count(),
                (long) writeErrorsTotal.count(),
                lastWritten.get());
    }

    public String readFromReplica(String replicaId) {
        ReplicaConnection replica = replicas.stream()
                .filter(r -> r.id().equals(replicaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown replica: " + replicaId));
        return replica.commands().get(props.counterKey());
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

    private void readerLoop(ReplicaConnection replica) {
        while (running) {
            try {
                String read = replica.commands().get(props.counterKey());
                readsTotal.increment();
                String expected = lastWritten.get();
                if (!expected.isEmpty() && read != null && !read.equals(expected)) {
                    staleReadsTotal.increment();
                }
                sleep(props.readIntervalMs());
            } catch (Exception ex) {
                sleep(200);
            }
        }
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
            String lastWrittenValue) {}
}
