package com.prediction.socialnetwork.application;

public record InteractionMetricSummary(
        String metric,
        long total,
        double average,
        double median,
        int validSampleCount,
        int missingCount,
        int zeroCount) {
}
