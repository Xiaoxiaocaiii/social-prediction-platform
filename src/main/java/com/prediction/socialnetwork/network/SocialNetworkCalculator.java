package com.prediction.socialnetwork.network;

import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class SocialNetworkCalculator {

    public static final double DAMPING_FACTOR = 0.85;
    public static final double CONVERGENCE_TOLERANCE = 1e-8;
    public static final int MAX_ITERATIONS = 100;
    public static final int MAX_GRAPH_NODES = 100;
    public static final int MAX_GRAPH_EDGES = 500;

    public SocialNetworkGraphResult calculate(
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        Map<String, NetworkNodeType> nodeTypes = collectNodes(users, relationships);
        List<NetworkEdge> edges = collectEdges(relationships, nodeTypes);
        Map<String, Integer> inDegrees = new HashMap<>();
        Map<String, Integer> outDegrees = new HashMap<>();
        for (String nodeId : nodeTypes.keySet()) {
            inDegrees.put(nodeId, 0);
            outDegrees.put(nodeId, 0);
        }
        for (NetworkEdge edge : edges) {
            outDegrees.compute(edge.source(), (key, value) -> value + 1);
            inDegrees.compute(edge.target(), (key, value) -> value + 1);
        }

        PageRankResult pageRank = pageRank(nodeTypes.keySet(), edges, outDegrees);
        List<NetworkNode> nodes = nodeTypes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int in = inDegrees.getOrDefault(entry.getKey(), 0);
                    int out = outDegrees.getOrDefault(entry.getKey(), 0);
                    return new NetworkNode(
                            entry.getKey(),
                            entry.getValue(),
                            in,
                            out,
                            pageRank.values().getOrDefault(entry.getKey(), 0.0),
                            in + out == 0);
                })
                .toList();
        int nodeCount = nodes.size();
        Double density = nodeCount > 1
                ? edges.size() * 1.0 / (nodeCount * (nodeCount - 1))
                : null;
        NetworkMetrics metrics = new NetworkMetrics(
                nodeCount,
                edges.size(),
                (int) nodes.stream().filter(node -> node.nodeType() == NetworkNodeType.PROFILE).count(),
                (int) nodes.stream().filter(node -> node.nodeType() == NetworkNodeType.EXTERNAL).count(),
                (int) nodes.stream().filter(NetworkNode::isolated).count(),
                density,
                NetworkMetrics.METRIC_VERSION,
                NetworkMetrics.PAGERANK_VERSION,
                true,
                true);
        List<NetworkRankingEntry> ranking = ranking(pageRank.values(), nodeTypes);
        return new SocialNetworkGraphResult(
                nodes, edges, metrics, pageRank, ranking, Instant.now());
    }

    public NetworkGraphView localView(
            SocialNetworkGraphResult graph,
            String centerNodeId,
            int depth,
            int topK,
            boolean includeExternalNodes) {
        if (centerNodeId == null || centerNodeId.isBlank()) {
            throw new IllegalArgumentException("centerNodeId must not be blank");
        }
        if (depth < 1 || depth > 2) {
            throw new IllegalArgumentException("depth must be 1 or 2");
        }
        Map<String, NetworkNode> nodes = graph.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(NetworkNode::id, node -> node));
        if (!nodes.containsKey(centerNodeId)) {
            throw new IllegalArgumentException("center node is not in the current network");
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (NetworkEdge edge : graph.edges()) {
            adjacency.computeIfAbsent(edge.source(), key -> new LinkedHashSet<>()).add(edge.target());
            adjacency.computeIfAbsent(edge.target(), key -> new LinkedHashSet<>()).add(edge.source());
        }
        Set<String> selected = new LinkedHashSet<>();
        selected.add(centerNodeId);
        Set<String> frontier = new LinkedHashSet<>();
        frontier.add(centerNodeId);
        for (int level = 0; level < depth; level++) {
            Set<String> next = new LinkedHashSet<>();
            for (String current : frontier) {
                next.addAll(adjacency.getOrDefault(current, Set.of()));
            }
            next.removeAll(selected);
            selected.addAll(next);
            frontier = next;
        }
        if (!includeExternalNodes) {
            selected.removeIf(id -> id.equals(centerNodeId)
                    ? false
                    : nodes.get(id).nodeType() == NetworkNodeType.EXTERNAL);
        }

        List<String> orderedNeighbors = selected.stream()
                .filter(id -> !id.equals(centerNodeId))
                .sorted(nodeComparator(nodes))
                .toList();
        if (topK > 0 && orderedNeighbors.size() > topK) {
            orderedNeighbors = orderedNeighbors.subList(0, topK);
        }
        LinkedHashSet<String> bounded = new LinkedHashSet<>();
        bounded.add(centerNodeId);
        bounded.addAll(orderedNeighbors);
        boolean truncated = bounded.size() > MAX_GRAPH_NODES;
        List<String> boundedIds = bounded.stream()
                .sorted(Comparator.comparing((String id) -> !id.equals(centerNodeId))
                        .thenComparing(nodeComparator(nodes)))
                .limit(MAX_GRAPH_NODES)
                .toList();
        Set<String> boundedSet = new HashSet<>(boundedIds);
        List<NetworkEdge> boundedEdges = graph.edges().stream()
                .filter(edge -> boundedSet.contains(edge.source()) && boundedSet.contains(edge.target()))
                .toList();
        if (boundedEdges.size() > MAX_GRAPH_EDGES) {
            truncated = true;
            boundedEdges = boundedEdges.subList(0, MAX_GRAPH_EDGES);
        }
        String reason = truncated ? "局部网络超过默认节点或边上限，已截取当前范围。" : null;
        List<NetworkNode> boundedNodes = boundedIds.stream().map(nodes::get).toList();
        return new NetworkGraphView(
                boundedNodes,
                boundedEdges,
                truncated,
                reason,
                centerNodeId,
                depth,
                topK,
                includeExternalNodes);
    }

    private static Map<String, NetworkNodeType> collectNodes(
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        Map<String, NetworkNodeType> nodes = new LinkedHashMap<>();
        for (NormalizedUser user : users) {
            if (user.userId() != null
                    && !user.userId().isBlank()
                    && user.normalizationStatus() != NormalizationStatus.REJECTED_REQUIRED_FIELD
                    && user.normalizationStatus() != NormalizationStatus.REJECTED_INVALID_FORMAT) {
                nodes.put(user.userId(), NetworkNodeType.PROFILE);
            }
        }
        for (NormalizedRelationship relationship : relationships) {
            if (!relationship.analysisIncluded()
                    || relationship.relationStatus() != RelationStatus.VALID) {
                continue;
            }
            if (validId(relationship.sourceUserId())) {
                nodes.putIfAbsent(relationship.sourceUserId(),
                        relationship.sourceNodeStatus().name().equals("EXTERNAL_NODE")
                                ? NetworkNodeType.EXTERNAL : NetworkNodeType.PROFILE);
            }
            if (validId(relationship.targetUserId())) {
                nodes.putIfAbsent(relationship.targetUserId(),
                        relationship.targetNodeStatus().name().equals("EXTERNAL_NODE")
                                ? NetworkNodeType.EXTERNAL : NetworkNodeType.PROFILE);
            }
        }
        return nodes;
    }

    private static List<NetworkEdge> collectEdges(
            List<NormalizedRelationship> relationships,
            Map<String, NetworkNodeType> nodes) {
        Set<String> seen = new HashSet<>();
        List<NetworkEdge> edges = new ArrayList<>();
        for (NormalizedRelationship relationship : relationships) {
            String source = relationship.sourceUserId();
            String target = relationship.targetUserId();
            if (!relationship.analysisIncluded()
                    || relationship.relationStatus() != RelationStatus.VALID
                    || !validId(source)
                    || !validId(target)
                    || source.equals(target)
                    || !nodes.containsKey(source)
                    || !nodes.containsKey(target)
                    || !seen.add(source + "\u0000" + target)) {
                continue;
            }
            edges.add(new NetworkEdge(source, target, "following"));
        }
        return edges.stream()
                .sorted(Comparator.comparing(NetworkEdge::source)
                        .thenComparing(NetworkEdge::target))
                .toList();
    }

    private static PageRankResult pageRank(
            Set<String> nodeIds,
            List<NetworkEdge> edges,
            Map<String, Integer> outDegrees) {
        if (nodeIds.isEmpty()) {
            return new PageRankResult(Map.of(), true, 0, 0.0, 0.0);
        }
        List<String> orderedIds = nodeIds.stream().sorted().toList();
        int nodeCount = orderedIds.size();
        Map<String, Double> current = new LinkedHashMap<>();
        for (String nodeId : orderedIds) {
            current.put(nodeId, 1.0 / nodeCount);
        }
        Map<String, List<String>> incoming = new HashMap<>();
        for (NetworkEdge edge : edges) {
            incoming.computeIfAbsent(edge.target(), key -> new ArrayList<>()).add(edge.source());
        }
        double delta = Double.POSITIVE_INFINITY;
        int iterations = 0;
        while (iterations < MAX_ITERATIONS && delta >= CONVERGENCE_TOLERANCE) {
            double danglingMass = 0.0;
            for (String nodeId : orderedIds) {
                if (outDegrees.getOrDefault(nodeId, 0) == 0) {
                    danglingMass += current.get(nodeId);
                }
            }
            Map<String, Double> next = new LinkedHashMap<>();
            for (String nodeId : orderedIds) {
                double incomingMass = 0.0;
                for (String source : incoming.getOrDefault(nodeId, List.of())) {
                    incomingMass += current.get(source) / outDegrees.get(source);
                }
                next.put(nodeId, (1.0 - DAMPING_FACTOR) / nodeCount
                        + DAMPING_FACTOR * (incomingMass + danglingMass / nodeCount));
            }
            delta = 0.0;
            for (String nodeId : orderedIds) {
                delta += Math.abs(next.get(nodeId) - current.get(nodeId));
            }
            current = next;
            iterations++;
        }
        double sum = current.values().stream().mapToDouble(Double::doubleValue).sum();
        return new PageRankResult(
                current, delta < CONVERGENCE_TOLERANCE, iterations, delta, sum);
    }

    private static List<NetworkRankingEntry> ranking(
            Map<String, Double> values,
            Map<String, NetworkNodeType> nodeTypes) {
        List<String> ids = values.keySet().stream()
                .sorted(Comparator.comparingDouble((String id) -> values.get(id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        List<NetworkRankingEntry> ranking = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            ranking.add(new NetworkRankingEntry(
                    index + 1,
                    id,
                    values.get(id),
                    nodeTypes.get(id),
                    NetworkMetrics.METRIC_VERSION,
                    NetworkMetrics.PAGERANK_VERSION));
        }
        return ranking;
    }

    private static Comparator<String> nodeComparator(Map<String, NetworkNode> nodes) {
        return Comparator.comparingDouble(
                        (String id) -> nodes.get(id).pageRank())
                .reversed()
                .thenComparing(Comparator.naturalOrder());
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank();
    }
}
