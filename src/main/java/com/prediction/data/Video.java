package com.prediction.data;

/**
 * A video with its 30-day popularity curve.
 *
 * @param peakDay  1-based day index of the curve peak
 * @param decayRate (day01 - day30) / day01, 0 when day01 is 0
 */
public record Video(
        String videoId,
        String title,
        String category,
        String tags,
        String description,
        String userId,
        String userImage,
        String videoUrl,
        double[] curve,
        double day30,
        int peakDay,
        double decayRate) {
}
