package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LouvainCommunityDetector {

    public static final String ALGORITHM = "Louvain";
    public static final String ALGORITHM_VERSION = "louvain-greedy-local-move-v1";
    public static final String RELATION_PROJECTION = "undirected_simple";
    public static final double RESOLUTION = 1.0;
    public static final int MAX_ITERATIONS = 100;
    public static final double SCORE_EPSILON = 1e-12;

    public CommunityDetectionResult detect(
            List<NetworkNode> nodes, List<NetworkEdge> directedEdges) {
        return detect(nodes, directedEdges, LouvainAnalysisParameters.DEFAULT);
    }

    public CommunityDetectionResult detect(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Louvain parameters must not be null");
        }
        return detectWithIterationLimit(
                nodes, directedEdges, MAX_ITERATIONS, parameters);
    }

    CommunityDetectionResult detectForControlledVerification(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            int iterationLimit) {
        if (iterationLimit < 1) {
            throw new IllegalArgumentException("iterationLimit must be positive");
        }
        return detectWithIterationLimit(
                nodes, directedEdges, iterationLimit, LouvainAnalysisParameters.DEFAULT);
    }

    private CommunityDetectionResult detectWithIterationLimit(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            int iterationLimit,
            LouvainAnalysisParameters parameters) {
        List<NetworkNode> orderedNodes = nodes.stream()
                .sorted(Comparator.comparing(NetworkNode::id))
                .toList();
        Map<String, Integer> indexById = new HashMap<>();
        for (int index = 0; index < orderedNodes.size(); index++) {
            indexById.put(orderedNodes.get(index).id(), index);
        }

        Set<DirectedPair> directedPairs = new HashSet<>();
        for (NetworkEdge edge : directedEdges) {
            Integer source = indexById.get(edge.source());
            Integer target = indexById.get(edge.target());
            if (source == null || target == null || source.equals(target)) {
                continue;
            }
            directedPairs.add(new DirectedPair(source, target));
        }
        Set<Long> undirectedPairs = new HashSet<>();
        for (DirectedPair pair : directedPairs) {
            if (parameters.relationProjection()
                            == LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION
                    || directedPairs.contains(new DirectedPair(pair.target(), pair.source()))) {
                undirectedPairs.add(pairKey(pair.source(), pair.target()));
            }
        }

        if (undirectedPairs.isEmpty()) {
            return new CommunityDetectionResult(
                    List.of(),
                    Map.of(),
                    orderedNodes.size(),
                    0,
                    true,
                    ALGORITHM,
                    parameters.relationProjection().resultName());
        }

        List<Map<Integer, Double>> adjacency = emptyAdjacency(orderedNodes.size());
        undirectedPairs.stream().sorted().forEach(pair -> {
            int left = leftNode(pair);
            int right = rightNode(pair);
            adjacency.get(left).put(right, 1.0);
            adjacency.get(right).put(left, 1.0);
        });
        WeightedGraph graph = weightedGraph(adjacency);

        int[] originalToCurrent = new int[orderedNodes.size()];
        Arrays.fill(originalToCurrent, -1);
        for (int index = 0; index < graph.degrees().length; index++) {
            if (graph.degrees()[index] > 0) {
                originalToCurrent[index] = index;
            }
        }

        int iterations = 0;
        boolean converged = false;
        while (iterations < iterationLimit) {
            LocalOptimization phase = optimize(
                    graph, iterationLimit - iterations, parameters);
            iterations += phase.iterations();
            for (int index = 0; index < originalToCurrent.length; index++) {
                int currentNode = originalToCurrent[index];
                if (currentNode >= 0) {
                    originalToCurrent[index] = phase.communityByNode()[currentNode];
                }
            }

            if (!phase.converged()) {
                break;
            }
            int activeNodeCount = (int) Arrays.stream(graph.degrees())
                    .filter(degree -> degree > 0)
                    .count();
            if (phase.communityCount() == activeNodeCount) {
                converged = true;
                break;
            }
            if (iterations >= iterationLimit) {
                break;
            }
            graph = aggregate(graph, phase.communityByNode(), phase.communityCount());
        }

        Map<Integer, List<Integer>> membersByGroup = new HashMap<>();
        for (int index = 0; index < originalToCurrent.length; index++) {
            int group = originalToCurrent[index];
            if (group >= 0) {
                membersByGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(index);
            }
        }
        List<List<Integer>> orderedGroups = membersByGroup.values().stream()
                .sorted(Comparator.comparing(group -> group.stream()
                        .map(index -> orderedNodes.get(index).id())
                        .min(String::compareTo)
                        .orElse("")))
                .toList();

        Map<String, String> communityByNodeId = new LinkedHashMap<>();
        int[] communityByIndex = new int[orderedNodes.size()];
        Arrays.fill(communityByIndex, -1);
        for (int groupIndex = 0; groupIndex < orderedGroups.size(); groupIndex++) {
            String communityId = communityId(groupIndex);
            for (int member : orderedGroups.get(groupIndex)) {
                communityByNodeId.put(orderedNodes.get(member).id(), communityId);
                communityByIndex[member] = groupIndex;
            }
        }

        int[] profileCounts = new int[orderedGroups.size()];
        int[] externalCounts = new int[orderedGroups.size()];
        for (int index = 0; index < orderedNodes.size(); index++) {
            int group = communityByIndex[index];
            if (group < 0) {
                continue;
            }
            if (orderedNodes.get(index).nodeType() == NetworkNodeType.PROFILE) {
                profileCounts[group]++;
            } else {
                externalCounts[group]++;
            }
        }
        int[] internalTieCounts = new int[orderedGroups.size()];
        for (long pair : undirectedPairs) {
            int leftGroup = communityByIndex[leftNode(pair)];
            int rightGroup = communityByIndex[rightNode(pair)];
            if (leftGroup >= 0 && leftGroup == rightGroup) {
                internalTieCounts[leftGroup]++;
            }
        }

        List<NetworkCommunity> communities = new ArrayList<>();
        for (int index = 0; index < orderedGroups.size(); index++) {
            communities.add(new NetworkCommunity(
                    communityId(index),
                    orderedGroups.get(index).size(),
                    profileCounts[index],
                    externalCounts[index],
                    internalTieCounts[index]));
        }
        int unassigned = orderedNodes.size() - communityByNodeId.size();
        return new CommunityDetectionResult(
                communities,
                communityByNodeId,
                unassigned,
                iterations,
                converged,
                ALGORITHM,
                parameters.relationProjection().resultName());
    }

    private static LocalOptimization optimize(
            WeightedGraph graph,
            int iterationLimit,
            LouvainAnalysisParameters parameters) {
        int nodeCount = graph.adjacency().size();
        double[] communityTotals = new double[nodeCount];
        int[] membership = new int[nodeCount];
        Arrays.fill(membership, -1);
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < nodeCount; index++) {
            if (graph.degrees()[index] > 0) {
                communityTotals[index] = graph.degrees()[index];
                membership[index] = index;
                order.add(index);
            }
        }
        Comparator<Integer> orderComparator =
                Comparator.<Integer>comparingDouble(index -> graph.degrees()[index]);
        if (parameters.nodeOrder()
                == LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING) {
            orderComparator = orderComparator.reversed();
        }
        order.sort(orderComparator.thenComparingInt(Integer::intValue));

        double twiceEdgeCount = Arrays.stream(graph.degrees()).sum();
        boolean converged = false;
        int iterations = 0;
        for (int pass = 0; pass < iterationLimit; pass++) {
            int moves = 0;
            for (int nodeIndex : order) {
                int currentCommunity = membership[nodeIndex];
                double degree = graph.degrees()[nodeIndex];
                communityTotals[currentCommunity] -= degree;

                Map<Integer, Double> weightsByCommunity = new TreeMap<>();
                graph.adjacency().get(nodeIndex).forEach((neighbor, weight) -> {
                    if (neighbor != nodeIndex) {
                        weightsByCommunity.merge(
                                membership[neighbor], weight, Double::sum);
                    }
                });

                int bestCommunity = currentCommunity;
                double bestScore = score(
                        weightsByCommunity.getOrDefault(currentCommunity, 0.0),
                        communityTotals[currentCommunity],
                        degree,
                        twiceEdgeCount,
                        parameters.resolution());
                for (Map.Entry<Integer, Double> candidate : weightsByCommunity.entrySet()) {
                    double candidateScore = score(
                            candidate.getValue(),
                            communityTotals[candidate.getKey()],
                            degree,
                            twiceEdgeCount,
                            parameters.resolution());
                    if (candidateScore > bestScore + SCORE_EPSILON) {
                        bestCommunity = candidate.getKey();
                        bestScore = candidateScore;
                    }
                }

                membership[nodeIndex] = bestCommunity;
                communityTotals[bestCommunity] += degree;
                if (bestCommunity != currentCommunity) {
                    moves++;
                }
            }
            iterations = pass + 1;
            if (moves == 0) {
                converged = true;
                break;
            }
        }

        Map<Integer, Integer> compressedMembership = new LinkedHashMap<>();
        int[] compactMembership = new int[nodeCount];
        Arrays.fill(compactMembership, -1);
        for (int index = 0; index < nodeCount; index++) {
            if (membership[index] >= 0) {
                int group = membership[index];
                compactMembership[index] = compressedMembership.computeIfAbsent(
                        group, ignored -> compressedMembership.size());
            }
        }
        return new LocalOptimization(
                compactMembership,
                compressedMembership.size(),
                iterations,
                converged);
    }

    private static WeightedGraph aggregate(
            WeightedGraph graph, int[] membership, int communityCount) {
        Map<Long, Double> aggregatedWeights = new TreeMap<>();
        for (int source = 0; source < graph.adjacency().size(); source++) {
            for (Map.Entry<Integer, Double> edge : graph.adjacency().get(source).entrySet()) {
                int target = edge.getKey();
                if (target < source) {
                    continue;
                }
                int sourceCommunity = membership[source];
                int targetCommunity = membership[target];
                if (sourceCommunity == targetCommunity && source != target) {
                    aggregatedWeights.merge(
                            pairKey(sourceCommunity, targetCommunity),
                            edge.getValue() * 2,
                            Double::sum);
                } else {
                    aggregatedWeights.merge(
                            pairKey(sourceCommunity, targetCommunity),
                            edge.getValue(),
                            Double::sum);
                }
            }
        }

        List<Map<Integer, Double>> adjacency = emptyAdjacency(communityCount);
        aggregatedWeights.forEach((pair, weight) -> {
            int left = leftNode(pair);
            int right = rightNode(pair);
            adjacency.get(left).put(right, weight);
            if (left != right) {
                adjacency.get(right).put(left, weight);
            }
        });
        return weightedGraph(adjacency);
    }

    private static WeightedGraph weightedGraph(List<Map<Integer, Double>> adjacency) {
        double[] degrees = new double[adjacency.size()];
        for (int index = 0; index < adjacency.size(); index++) {
            degrees[index] = adjacency.get(index).values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }
        return new WeightedGraph(adjacency, degrees);
    }

    private static List<Map<Integer, Double>> emptyAdjacency(int size) {
        List<Map<Integer, Double>> adjacency = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            adjacency.add(new TreeMap<>());
        }
        return adjacency;
    }

    private static long pairKey(int left, int right) {
        int first = Math.min(left, right);
        int second = Math.max(left, right);
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int leftNode(long pairKey) {
        return (int) (pairKey >>> 32);
    }

    private static int rightNode(long pairKey) {
        return (int) pairKey;
    }

    private static String communityId(int index) {
        return String.format(java.util.Locale.ROOT, "C%03d", index + 1);
    }

    private static double score(
            double weightToCommunity,
            double communityTotalDegree,
            double nodeDegree,
            double twiceEdgeCount,
            double resolution) {
        return weightToCommunity
                - resolution * communityTotalDegree * nodeDegree / twiceEdgeCount;
    }

    private record WeightedGraph(
            List<Map<Integer, Double>> adjacency,
            double[] degrees) {
    }

    private record DirectedPair(int source, int target) {
    }

    private record LocalOptimization(
            int[] communityByNode,
            int communityCount,
            int iterations,
            boolean converged) {
    }
}
