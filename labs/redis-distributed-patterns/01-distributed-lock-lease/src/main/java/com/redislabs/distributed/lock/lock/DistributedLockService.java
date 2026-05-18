package com.redislabs.distributed.lock.lock;

import com.redislabs.distributed.lock.config.LabProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DistributedLockService {

    private final StringRedisTemplate redis;
    private final LabProperties properties;
    private final DefaultRedisScript<Long> safeUnlockScript;

    public DistributedLockService(
            StringRedisTemplate redis,
            LabProperties properties,
            DefaultRedisScript<Long> safeUnlockScript
    ) {
        this.redis = redis;
        this.properties = properties;
        this.safeUnlockScript = safeUnlockScript;
    }

    public String lockKey(String orderId) {
        return properties.lockKeyPrefix() + orderId;
    }

    public String newOwnerToken() {
        return properties.workerName() + ":" + UUID.randomUUID();
    }

    public AcquireResult tryAcquire(String orderId, String ownerToken, long ttlMs) {
        String key = lockKey(orderId);
        Boolean acquired = redis.opsForValue().setIfAbsent(key, ownerToken, Duration.ofMillis(ttlMs));
        if (Boolean.TRUE.equals(acquired)) {
            return AcquireResult.acquired(ownerToken, key);
        }
        return AcquireResult.denied(key, inspect(orderId));
    }

    public ReleaseResult release(String orderId, String ownerToken, ReleaseMode mode) {
        String key = lockKey(orderId);
        LockSnapshot before = inspect(orderId);

        if (mode == ReleaseMode.SAFE) {
            Long deleted = redis.execute(safeUnlockScript, List.of(key), ownerToken);
            boolean released = deleted != null && deleted == 1L;
            if (released) {
                return new ReleaseResult(mode, true, false, "Lock released (owner matched).");
            }
            String message = before.locked() && !ownerToken.equals(before.holder())
                    ? "Safe release skipped: another holder owns the lock (lease may have expired)."
                    : "Safe release skipped: lock not held by this owner.";
            return new ReleaseResult(mode, false, false, message);
        }

        Boolean deleted = redis.delete(key);
        boolean released = Boolean.TRUE.equals(deleted);
        boolean deletedAnotherHolder = before.locked()
                && !ownerToken.equals(before.holder())
                && released;
        if (deletedAnotherHolder) {
            return new ReleaseResult(
                    mode,
                    true,
                    true,
                    "Unsafe DEL removed another holder's lock (Scenario 3 anti-pattern)."
            );
        }
        if (released) {
            return new ReleaseResult(mode, true, false, "Lock deleted unconditionally.");
        }
        return new ReleaseResult(mode, false, false, "No lock key present at release time.");
    }

    public LockSnapshot inspect(String orderId) {
        String key = lockKey(orderId);
        String holder = redis.opsForValue().get(key);
        Long ttlMs = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (holder == null) {
            return new LockSnapshot(key, null, -2, false);
        }
        long ttl = ttlMs == null ? -1 : ttlMs;
        return new LockSnapshot(key, holder, ttl, true);
    }
}
