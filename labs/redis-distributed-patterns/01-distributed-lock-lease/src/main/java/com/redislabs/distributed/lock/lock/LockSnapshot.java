package com.redislabs.distributed.lock.lock;

public record LockSnapshot(
        String lockKey,
        String holder,
        long ttlMs,
        boolean locked
) {
}
