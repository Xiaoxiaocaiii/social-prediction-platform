package com.prediction.socialnetwork.network;

public record NetworkMetrics(
        int nodeCount,
        int edgeCount,
        int profileUserCount,
        int externalNodeCount,
        int isolatedNodeCount,
        Double density,
        String metricVersion,
        String pagerankVersion,
        boolean includeExternalNodes,
        boolean excludeSelfLoops) {

    public static final String METRIC_VERSION = "network-metrics-v1.0";
    public static final String PAGERANK_VERSION = "pagerank-v1.0";
}
