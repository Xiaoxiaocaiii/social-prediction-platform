package com.prediction.socialnetwork.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class LouvainQualityCalculator {

    public static final String QUALITY_CONTRACT_VERSION = "louvain-quality-v1.0";

    public LouvainQualitySummary calculate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            Map<String, String> communityByNodeId) {
        return calculate(
                nodes,
                directedEdges,
                communityByNodeId,
                LouvainAnalysisParameters.DEFAULT);
    }

    public LouvainQualitySummary calculate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            Map<String, String> communityByNodeId,
            LouvainAnalysisParameters parameters) {
        if (nodes == null || directedEdges == null || communityByNodeId == null) {
            throw new IllegalArgumentException("Louvain quality inputs must not be null");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Louvain parameters must not be null");
        }
        Set<String> nodeIds = new HashSet<>();
        for (NetworkNode node : nodes) {
            if (node == null
                    || node.id() == null
                    || node.id().isBlank()
                    || node.nodeType() == null
                    || !nodeIds.add(node.id())) {
                throw new IllegalArgumentException("Node list contains an invalid or duplicate id");
            }
        }
        for (Map.Entry<String, String> assignment : communityByNodeId.entrySet()) {
            if (!nodeIds.contains(assignment.getKey())
                    || assignment.getValue() == null
                    || assignment.getValue().isBlank()) {
                throw new IllegalArgumentException("Community assignment has an invalid node");
            }
        }

        Set<Tie> ties = new TreeSet<>((left, right) -> {
            int first = left.left().compareTo(right.left());
            return first != 0 ? first : left.right().compareTo(right.right());
        });
        Set<DirectedRelationship> relationships = new HashSet<>();
        Map<String, Integer> degreeByNodeId = new HashMap<>();
        nodeIds.forEach(nodeId -> degreeByNodeId.put(nodeId, 0));
        for (NetworkEdge edge : directedEdges) {
            if (edge == null) {
                throw new IllegalArgumentException("Relationship must not be null");
            }
            if (!nodeIds.contains(edge.source())
                    || !nodeIds.contains(edge.target())
                    || edge.source().equals(edge.target())) {
                continue;
            }
            relationships.add(new DirectedRelationship(edge.source(), edge.target()));
        }
        for (DirectedRelationship relationship : relationships) {
            if (parameters.relationProjection()
                            == LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION
                    || relationships.contains(
                            new DirectedRelationship(
                                    relationship.target(), relationship.source()))) {
                ties.add(relationship.source().compareTo(relationship.target()) < 0
                        ? new Tie(relationship.source(), relationship.target())
                        : new Tie(relationship.target(), relationship.source()));
            }
        }
        for (Tie tie : ties) {
            degreeByNodeId.merge(tie.left(), 1, Integer::sum);
            degreeByNodeId.merge(tie.right(), 1, Integer::sum);
        }

        List<String> unassignedNodeIds = nodeIds.stream()
                .filter(nodeId -> !communityByNodeId.containsKey(nodeId))
                .sorted()
                .toList();
        for (String nodeId : unassignedNodeIds) {
            if (degreeByNodeId.get(nodeId) > 0) {
                throw new IllegalArgumentException(
                        "A node with projected ties is missing from the partition");
            }
        }

        Map<String, Double> degreeByCommunity = new TreeMap<>();
        Map<String, Integer> internalTiesByCommunity = new TreeMap<>();
        for (NetworkNode node : nodes) {
            String communityId = communityByNodeId.get(node.id());
            if (communityId != null) {
                degreeByCommunity.merge(
                        communityId, (double) degreeByNodeId.get(node.id()), Double::sum);
                internalTiesByCommunity.putIfAbsent(communityId, 0);
            }
        }

        int internalTieCount = 0;
        int crossCommunityTieCount = 0;
        for (Tie tie : ties) {
            String leftCommunity = communityByNodeId.get(tie.left());
            String rightCommunity = communityByNodeId.get(tie.right());
            if (leftCommunity.equals(rightCommunity)) {
                internalTiesByCommunity.merge(leftCommunity, 1, Integer::sum);
                internalTieCount++;
            } else {
                crossCommunityTieCount++;
            }
        }

        Double modularity = null;
        if (!ties.isEmpty()) {
            double edgeCount = ties.size();
            modularity = 0.0;
            for (Map.Entry<String, Double> community : degreeByCommunity.entrySet()) {
                double internalTies =
                        internalTiesByCommunity.getOrDefault(community.getKey(), 0);
                double degreeShare = community.getValue() / (2.0 * edgeCount);
                modularity += internalTies / edgeCount - degreeShare * degreeShare;
            }
        }

        return new LouvainQualitySummary(
                nodes.size(),
                directedEdges.size(),
                communityByNodeId.size(),
                unassignedNodeIds,
                ties.size(),
                internalTieCount,
                crossCommunityTieCount,
                internalTiesByCommunity,
                modularity);
    }

    private record Tie(String left, String right) {
    }

    private record DirectedRelationship(String source, String target) {
    }
}
