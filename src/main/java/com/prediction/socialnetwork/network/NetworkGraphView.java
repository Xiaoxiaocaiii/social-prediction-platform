package com.prediction.socialnetwork.network;

import java.util.List;

public record NetworkGraphView(
        List<NetworkNode> nodes,
        List<NetworkEdge> edges,
        boolean truncated,
        String truncationReason,
        String centerNodeId,
        int depth,
        int topK,
        boolean includeExternalNodes) {

    public NetworkGraphView {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
