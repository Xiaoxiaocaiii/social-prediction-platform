package com.prediction.socialnetwork.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LouvainPartitionSimilarity {

    public Similarity compare(
            List<NetworkNode> nodes,
            Map<String, String> baseline,
            Map<String, String> candidate) {
        if (nodes == null || baseline == null || candidate == null) {
            throw new IllegalArgumentException("Partition comparison inputs must not be null");
        }
        Set<String> nodeIds = new TreeSet<>();
        for (NetworkNode node : nodes) {
            if (node == null || node.id() == null || !nodeIds.add(node.id())) {
                throw new IllegalArgumentException("Node list contains an invalid or duplicate id");
            }
        }
        validateAssignments(nodeIds, baseline);
        validateAssignments(nodeIds, candidate);

        Map<String, Integer> baselineCounts = new HashMap<>();
        Map<String, Integer> candidateCounts = new HashMap<>();
        Map<LabelPair, Integer> contingency = new HashMap<>();
        for (String nodeId : nodeIds) {
            String left = label(nodeId, baseline.get(nodeId));
            String right = label(nodeId, candidate.get(nodeId));
            baselineCounts.merge(left, 1, Integer::sum);
            candidateCounts.merge(right, 1, Integer::sum);
            contingency.merge(new LabelPair(left, right), 1, Integer::sum);
        }

        double samePairCount = contingency.values().stream()
                .mapToDouble(LouvainPartitionSimilarity::chooseTwo)
                .sum();
        double baselinePairCount = baselineCounts.values().stream()
                .mapToDouble(LouvainPartitionSimilarity::chooseTwo)
                .sum();
        double candidatePairCount = candidateCounts.values().stream()
                .mapToDouble(LouvainPartitionSimilarity::chooseTwo)
                .sum();
        double allPairCount = chooseTwo(nodeIds.size());
        boolean equivalent = equivalent(contingency, nodeIds.size());
        double adjustedRandIndex;
        if (allPairCount == 0) {
            adjustedRandIndex = 1.0;
        } else {
            double expected = baselinePairCount * candidatePairCount / allPairCount;
            double maximum = (baselinePairCount + candidatePairCount) / 2.0;
            double denominator = maximum - expected;
            adjustedRandIndex = Math.abs(denominator) < 1e-15
                    ? (equivalent ? 1.0 : 0.0)
                    : (samePairCount - expected) / denominator;
        }

        double baselineEntropy = entropy(baselineCounts, nodeIds.size());
        double candidateEntropy = entropy(candidateCounts, nodeIds.size());
        double mutualInformation = 0.0;
        if (nodeIds.size() > 0) {
            for (Map.Entry<LabelPair, Integer> cell : contingency.entrySet()) {
                double cellProbability = (double) cell.getValue() / nodeIds.size();
                double baselineProbability =
                        (double) baselineCounts.get(cell.getKey().baseline()) / nodeIds.size();
                double candidateProbability =
                        (double) candidateCounts.get(cell.getKey().candidate()) / nodeIds.size();
                mutualInformation += cellProbability * Math.log(
                        cellProbability / (baselineProbability * candidateProbability));
            }
        }
        double entropyProduct = baselineEntropy * candidateEntropy;
        double normalizedMutualInformation = entropyProduct == 0.0
                ? (equivalent ? 1.0 : 0.0)
                : mutualInformation / Math.sqrt(entropyProduct);

        return new Similarity(
                nodeIds.size(),
                clamp(adjustedRandIndex, -1.0, 1.0),
                clamp(normalizedMutualInformation, 0.0, 1.0));
    }

    private static void validateAssignments(
            Set<String> nodeIds, Map<String, String> assignments) {
        for (Map.Entry<String, String> assignment : assignments.entrySet()) {
            if (!nodeIds.contains(assignment.getKey())
                    || assignment.getValue() == null
                    || assignment.getValue().isBlank()) {
                throw new IllegalArgumentException("Partition contains an invalid assignment");
            }
        }
    }

    private static String label(String nodeId, String communityId) {
        return communityId == null ? "U:" + nodeId : "C:" + communityId;
    }

    private static double entropy(Map<String, Integer> counts, int total) {
        if (total == 0) {
            return 0.0;
        }
        double entropy = 0.0;
        for (int count : counts.values()) {
            double probability = (double) count / total;
            entropy -= probability * Math.log(probability);
        }
        return entropy;
    }

    private static double chooseTwo(int value) {
        return (double) value * (value - 1) / 2.0;
    }

    private static boolean equivalent(
            Map<LabelPair, Integer> contingency, int nodeCount) {
        Map<String, String> candidateByBaseline = new HashMap<>();
        Map<String, String> baselineByCandidate = new HashMap<>();
        int visitedNodes = 0;
        for (Map.Entry<LabelPair, Integer> cell : contingency.entrySet()) {
            visitedNodes += cell.getValue();
            String existingCandidate = candidateByBaseline.putIfAbsent(
                    cell.getKey().baseline(), cell.getKey().candidate());
            String existingBaseline = baselineByCandidate.putIfAbsent(
                    cell.getKey().candidate(), cell.getKey().baseline());
            if ((existingCandidate != null
                            && !existingCandidate.equals(cell.getKey().candidate()))
                    || (existingBaseline != null
                            && !existingBaseline.equals(cell.getKey().baseline()))) {
                return false;
            }
        }
        return visitedNodes == nodeCount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record LabelPair(String baseline, String candidate) {
    }

    public record Similarity(
            int nodeCount,
            double adjustedRandIndex,
            double normalizedMutualInformation) {
        public Similarity {
            if (nodeCount < 0
                    || !Double.isFinite(adjustedRandIndex)
                    || !Double.isFinite(normalizedMutualInformation)) {
                throw new IllegalArgumentException("Partition similarity must be finite");
            }
        }
    }
}
