package com.prediction.socialnetwork.network;

public record NetworkCommunity(
        String id,
        int nodeCount,
        int profileNodeCount,
        int externalNodeCount,
        int internalTieCount) {
}
