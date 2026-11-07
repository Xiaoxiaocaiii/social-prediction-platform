package com.prediction.socialnetwork.network;

public record NetworkMetrics(
        int nodeCount,
        int edgeCount,
        int profileUserCount,
        int externalNodeCount,
        int isolatedNodeCount,
        int weaklyConnectedComponentCount,
        int largestWeaklyConnectedComponentNodeCount,
        Double largestWeaklyConnectedComponentRatio,
        Double averageClusteringCoefficient,
        Double reciprocityRate,
        Double largestWeaklyConnectedComponentAveragePathLength,
        Double density,
        String metricVersion,
        String pagerankVersion,
        boolean includeExternalNodes,
        boolean excludeSelfLoops) {

    public static final String METRIC_VERSION = "network-metrics-v2.0";
    public static final String PAGERANK_VERSION = "pagerank-v1.0";
}
