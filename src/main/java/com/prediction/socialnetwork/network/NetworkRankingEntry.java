package com.prediction.socialnetwork.network;

public record NetworkRankingEntry(
        int rank,
        String nodeId,
        double value,
        NetworkNodeType nodeType,
        String metricVersion,
        String pagerankVersion) {
}
