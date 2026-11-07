package com.prediction.socialnetwork.network;

import java.util.List;
import java.util.Map;

public record LouvainQualitySummary(
        int inputNodeCount,
        int inputDirectedRelationshipCount,
        int assignedNodeCount,
        List<String> unassignedNodeIds,
        int projectedUndirectedTieCount,
        int internalTieCount,
        int crossCommunityTieCount,
        Map<String, Integer> internalTieCountByCommunity,
        Double modularity) {

    public LouvainQualitySummary {
        unassignedNodeIds = List.copyOf(unassignedNodeIds);
        internalTieCountByCommunity = Map.copyOf(internalTieCountByCommunity);
    }

    public int unassignedNodeCount() {
        return unassignedNodeIds.size();
    }
}
