package com.prediction.data;

/** Pearson correlation between Day 1 and Day 30 popularity. */
public record CorrelationStat(double pearson, long sampleCount, double[][] scatter) {
}
