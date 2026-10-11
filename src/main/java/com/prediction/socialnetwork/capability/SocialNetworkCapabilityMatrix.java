package com.prediction.socialnetwork.capability;

import java.time.Instant;
import java.util.List;

public record SocialNetworkCapabilityMatrix(
        String datasetId,
        String datasetVersion,
        String capabilityVersion,
        Instant generatedAt,
        List<CapabilityItem> items) {

    public SocialNetworkCapabilityMatrix {
        items = List.copyOf(items);
    }
}
