package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.List;

public final class NodeMetricDistributionCalculator {

    public Summary summarize(List<Double> sourceValues) {
        List<Double> values = new ArrayList<>(sourceValues.size());
        for (Double value : sourceValues) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "node metric values must be finite and non-null");
            }
            values.add(value);
        }
        values.sort(Double::compare);

        int sampleSize = values.size();
        if (sampleSize == 0) {
            return new Summary(0, 0, null, null, null, null, null, null, null);
        }

        int zeroCount = (int) values.stream().filter(value -> value == 0.0).count();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double median = sampleSize % 2 == 1
                ? values.get(sampleSize / 2)
                : (values.get(sampleSize / 2 - 1) + values.get(sampleSize / 2)) / 2.0;

        return new Summary(
                sampleSize,
                zeroCount,
                zeroCount / (double) sampleSize,
                values.get(0),
                mean,
                median,
                nearestRank(values, 0.90),
                nearestRank(values, 0.95),
                values.get(sampleSize - 1));
    }

    private static double nearestRank(List<Double> sortedValues, double percentile) {
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(rank - 1);
    }

    public record Summary(
            int sampleSize,
            int zeroCount,
            Double zeroRatio,
            Double min,
            Double mean,
            Double median,
            Double p90,
            Double p95,
            Double max) {
    }
}
