package com.prediction.socialnetwork.network;

import java.util.List;
import java.util.Map;

public record CommunityDetectionResult(
        List<NetworkCommunity> communities,
        Map<String, String> communityByNodeId,
        int unassignedNodeCount,
        int iterations,
        boolean converged,
        String algorithm,
        String relationProjection,
        CommunityDetectionStatus status) {

    public CommunityDetectionResult {
        communities = List.copyOf(communities);
        communityByNodeId = Map.copyOf(communityByNodeId);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public CommunityDetectionResult(
            List<NetworkCommunity> communities,
            Map<String, String> communityByNodeId,
            int unassignedNodeCount,
            int iterations,
            boolean converged,
            String algorithm,
            String relationProjection) {
        this(communities, communityByNodeId, unassignedNodeCount, iterations,
                converged, algorithm, relationProjection, CommunityDetectionStatus.COMPLETED);
    }

    public static CommunityDetectionResult notRequested() {
        return new CommunityDetectionResult(
                List.of(),
                Map.of(),
                0,
                0,
                false,
                null,
                null,
                CommunityDetectionStatus.NOT_REQUESTED);
    }
}
