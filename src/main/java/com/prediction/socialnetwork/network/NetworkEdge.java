package com.prediction.socialnetwork.network;

public record NetworkEdge(
        String source,
        String target,
        String relationType) {

    public NetworkEdge {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("edge source must not be blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("edge target must not be blank");
        }
        relationType = relationType == null || relationType.isBlank()
                ? "following"
                : relationType;
    }
}
