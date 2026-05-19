package com.redislabs.distributed.lock.lock;

public record AcquireResult(
        boolean acquired,
        String ownerToken,
        String lockKey,
        LockSnapshot currentLock
) {
    public static AcquireResult acquired(String ownerToken, String lockKey) {
        return new AcquireResult(true, ownerToken, lockKey, null);
    }

    public static AcquireResult denied(String lockKey, LockSnapshot currentLock) {
        return new AcquireResult(false, null, lockKey, currentLock);
    }
}
