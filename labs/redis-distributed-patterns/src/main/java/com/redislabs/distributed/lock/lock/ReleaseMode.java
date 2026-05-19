package com.redislabs.distributed.lock.lock;

import java.util.Locale;

public enum ReleaseMode {
    SAFE,
    UNSAFE;

    public static ReleaseMode parse(String value) {
        if (value == null || value.isBlank()) {
            return SAFE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "safe" -> SAFE;
            case "unsafe" -> UNSAFE;
            default -> throw new IllegalArgumentException("releaseMode must be 'safe' or 'unsafe'");
        };
    }
}
