package com.prediction.data;

import java.util.List;

public record InsightsPayload(
        List<CategoryStat> categoryStats,
        CorrelationStat correlation,
        DecayCurves decayCurves) {
}
