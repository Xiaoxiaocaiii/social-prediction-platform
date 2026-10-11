package com.prediction.socialnetwork.network;

public record NetworkNode(
        String id,
        NetworkNodeType nodeType,
        int inDegree,
        int outDegree,
        double pageRank,
        boolean isolated) {

    public NetworkNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
    }
}
