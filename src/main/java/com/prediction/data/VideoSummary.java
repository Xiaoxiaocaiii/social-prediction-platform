package com.prediction.data;

/** Lightweight row for browsing lists. */
public record VideoSummary(
        String videoId,
        String title,
        String category,
        String coverPath,
        double day30,
        String userId,
        String userImage) {
}
