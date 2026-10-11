package com.prediction.socialnetwork.network;

import java.util.Map;

public record PageRankResult(
        Map<String, Double> values,
        boolean converged,
        int iterations,
        double finalDelta,
        double sum) {

    public PageRankResult {
        values = Map.copyOf(values);
    }
}
