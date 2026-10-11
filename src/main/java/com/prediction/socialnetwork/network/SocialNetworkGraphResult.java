package com.prediction.socialnetwork.network;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SocialNetworkGraphResult(
        List<NetworkNode> nodes,
        List<NetworkEdge> edges,
        NetworkMetrics metrics,
        PageRankResult pageRank,
        List<NetworkRankingEntry> pageRankRanking,
        Instant calculatedAt) {

    public SocialNetworkGraphResult {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        pageRankRanking = List.copyOf(pageRankRanking);
    }
}
