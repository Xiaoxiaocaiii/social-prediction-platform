package com.prediction.socialnetwork.application;

import java.util.Map;

public record SocialNetworkInteractionScale(
        boolean available,
        String reason,
        Map<String, InteractionMetricSummary> metrics,
        int analysisSampleCount,
        int linkedAuthorSampleCount,
        int unassignedSourceSampleCount,
        int unlinkedAuthorSampleCount) {

    public SocialNetworkInteractionScale {
        metrics = Map.copyOf(metrics);
    }
}
