package com.prediction.socialnetwork.isolation;

public record WindowsJobLimits(
        long jobMemoryBytes,
        long processMemoryBytes,
        int cpuRatePer10k,
        int maxActiveProcesses) {

    public static final long MEBIBYTE = 1024L * 1024L;
    public static final long GIBIBYTE = 1024L * MEBIBYTE;
    public static final WindowsJobLimits DEFAULT =
            new WindowsJobLimits(256 * MEBIBYTE, 256 * MEBIBYTE, 2500, 1);

    public WindowsJobLimits {
        if (jobMemoryBytes <= 0 || processMemoryBytes <= 0) {
            throw new IllegalArgumentException("Memory limits must be positive");
        }
        if (cpuRatePer10k < 1 || cpuRatePer10k > 10_000) {
            throw new IllegalArgumentException("CPU rate must be between 1 and 10000");
        }
        if (maxActiveProcesses < 1) {
            throw new IllegalArgumentException("At least one process must be allowed");
        }
    }
}
