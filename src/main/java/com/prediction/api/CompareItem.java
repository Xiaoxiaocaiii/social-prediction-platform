package com.prediction.api;

public record CompareItem(
        String videoId,
        String title,
        String userId,
        double[] curve,
        double day30,
        int peakDay,
        double decayRate) {
}
