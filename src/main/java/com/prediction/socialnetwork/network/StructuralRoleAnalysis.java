package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record StructuralRoleAnalysis(
        int nodeCount,
        int undirectedTieCount,
        String algorithmVersion,
        List<KCoreEntry> kCoreRanking,
        List<BetweennessEntry> betweennessRanking) {

    public StructuralRoleAnalysis {
        kCoreRanking = List.copyOf(kCoreRanking);
        betweennessRanking = List.copyOf(betweennessRanking);
    }

    public KCoreDistribution kCoreDistribution() {
        if (kCoreRanking.isEmpty()) {
            return new KCoreDistribution(0, null, null, List.of());
        }

        Map<Integer, List<KCoreNode>> nodesByCore = new TreeMap<>();
        for (KCoreEntry entry : kCoreRanking) {
            nodesByCore.computeIfAbsent(entry.coreNumber(), ignored -> new ArrayList<>())
                    .add(new KCoreNode(entry.nodeId(), entry.nodeType()));
        }
        nodesByCore.values().forEach(nodes -> nodes.sort(
                java.util.Comparator.comparing(KCoreNode::nodeId)));

        int maxCoreNumber = nodesByCore.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow();
        int[] cumulativeCounts = new int[maxCoreNumber + 1];
        int cumulativeCount = 0;
        for (int coreNumber = maxCoreNumber; coreNumber >= 0; coreNumber--) {
            cumulativeCount += nodesByCore.getOrDefault(coreNumber, List.of()).size();
            cumulativeCounts[coreNumber] = cumulativeCount;
        }

        List<KCoreShell> shells = new ArrayList<>(maxCoreNumber + 1);
        for (int coreNumber = 0; coreNumber <= maxCoreNumber; coreNumber++) {
            List<KCoreNode> nodes = List.copyOf(
                    nodesByCore.getOrDefault(coreNumber, List.of()));
            int exactCount = nodes.size();
            shells.add(new KCoreShell(
                    coreNumber,
                    exactCount,
                    exactCount / (double) nodeCount,
                    cumulativeCounts[coreNumber],
                    cumulativeCounts[coreNumber] / (double) nodeCount,
                    nodes));
        }

        int highestCoreCount = nodesByCore.get(maxCoreNumber).size();
        return new KCoreDistribution(
                nodeCount, maxCoreNumber, highestCoreCount, shells);
    }

    public record KCoreDistribution(
            int sampleSize,
            Integer maxCoreNumber,
            Integer highestCoreCount,
            List<KCoreShell> shells) {

        public KCoreDistribution {
            shells = List.copyOf(shells);
        }
    }

    public record KCoreShell(
            int coreNumber,
            int exactCount,
            double exactRatio,
            int cumulativeCount,
            double cumulativeRatio,
            List<KCoreNode> nodes) {

        public KCoreShell {
            nodes = List.copyOf(nodes);
        }
    }

    public record KCoreNode(String nodeId, NetworkNodeType nodeType) {
    }

    public record KCoreEntry(
            String nodeId,
            NetworkNodeType nodeType,
            int coreNumber) {
    }

    public record BetweennessEntry(
            String nodeId,
            NetworkNodeType nodeType,
            double rawBetweenness,
            Double normalizedBetweenness) {
    }
}
