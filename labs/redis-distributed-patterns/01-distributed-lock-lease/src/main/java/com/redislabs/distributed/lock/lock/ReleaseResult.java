package com.redislabs.distributed.lock.lock;

public record ReleaseResult(
        ReleaseMode mode,
        boolean released,
        boolean deletedAnotherHolder,
        String message
) {
}
