package com.prediction.data;

/** A similar historical video found by the baseline predictor. */
public record ReferenceHit(
        String videoId,
        String title,
        String coverPath,
        String userId,
        double day30,
        double score) {
}
