package com.prediction.socialnetwork.network;

import java.time.Duration;
import java.util.Objects;

public record CliqueEnumerationLimits(
        Duration timeLimit,
        int maxCliques,
        long maxCliqueArrayBytes) {

    public static final long KIBIBYTE = 1024L;
    public static final long MEBIBYTE = 1024L * 1024L;
    public static final CliqueEnumerationLimits DEFAULT =
            new CliqueEnumerationLimits(Duration.ofSeconds(30), 100_000, 2 * MEBIBYTE);

    public CliqueEnumerationLimits {
        Objects.requireNonNull(timeLimit, "timeLimit");
        if (timeLimit.isNegative()) {
            throw new IllegalArgumentException("timeLimit cannot be negative");
        }
        try {
            timeLimit.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("timeLimit is too large", exception);
        }
        if (maxCliques < 1) {
            throw new IllegalArgumentException("maxCliques must be positive");
        }
        if (maxCliqueArrayBytes < 2) {
            throw new IllegalArgumentException("maxCliqueArrayBytes must fit an empty JSON array");
        }
    }

    long timeLimitNanos() {
        return timeLimit.toNanos();
    }
}
