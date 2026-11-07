package com.prediction.socialnetwork.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class UndirectedModularity {

    private UndirectedModularity() {
    }

    static double calculate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            Map<String, String> communityByNodeId) {
        Double modularity = analyze(nodes, directedEdges, communityByNodeId).modularity();
        return modularity == null ? Double.NaN : modularity;
    }

    static QualityReport analyze(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            Map<String, String> communityByNodeId) {
        Set<String> nodeIds = new HashSet<>();
        for (NetworkNode node : nodes) {
            if (!nodeIds.add(node.id())) {
                throw new IllegalArgumentException("Duplicate node id");
            }
        }
        for (Map.Entry<String, String> assignment : communityByNodeId.entrySet()) {
            if (!nodeIds.contains(assignment.getKey()) || assignment.getValue() == null) {
                throw new IllegalArgumentException("Community assignment has an invalid node");
            }
        }

        Set<Tie> ties = new HashSet<>();
        Map<String, Integer> degreeByNodeId = new HashMap<>();
        nodeIds.forEach(nodeId -> degreeByNodeId.put(nodeId, 0));
        for (NetworkEdge edge : directedEdges) {
            if (!nodeIds.contains(edge.source())
                    || !nodeIds.contains(edge.target())
                    || edge.source().equals(edge.target())) {
                continue;
            }
            ties.add(edge.source().compareTo(edge.target()) < 0
                    ? new Tie(edge.source(), edge.target())
                    : new Tie(edge.target(), edge.source()));
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

        Map<String, Double> degreeByGroup = new TreeMap<>();
        Map<String, Integer> internalTiesByGroup = new TreeMap<>();
        for (NetworkNode node : nodes) {
            String group = communityByNodeId.get(node.id());
            if (group != null) {
                degreeByGroup.merge(group, (double) degreeByNodeId.get(node.id()), Double::sum);
                internalTiesByGroup.putIfAbsent(group, 0);
            }
        }

        int internalTieCount = 0;
        int crossCommunityTieCount = 0;
        for (Tie tie : ties) {
            String leftGroup = communityByNodeId.get(tie.left());
            String rightGroup = communityByNodeId.get(tie.right());
            if (leftGroup.equals(rightGroup)) {
                internalTiesByGroup.merge(leftGroup, 1, Integer::sum);
                internalTieCount++;
            } else {
                crossCommunityTieCount++;
            }
        }

        Double modularity = null;
        if (!ties.isEmpty()) {
            double edgeCount = ties.size();
            modularity = 0.0;
            for (Map.Entry<String, Double> group : degreeByGroup.entrySet()) {
                double internalTies = internalTiesByGroup.getOrDefault(group.getKey(), 0);
                double degreeShare = group.getValue() / (2.0 * edgeCount);
                modularity += internalTies / edgeCount - degreeShare * degreeShare;
            }
        }
        return new QualityReport(
                nodes.size(),
                communityByNodeId.size(),
                unassignedNodeIds,
                ties.size(),
                internalTieCount,
                crossCommunityTieCount,
                internalTiesByGroup,
                modularity);
    }

    record QualityReport(
            int nodeCount,
            int assignedNodeCount,
            List<String> unassignedNodeIds,
            int undirectedTieCount,
            int internalTieCount,
            int crossCommunityTieCount,
            Map<String, Integer> internalTieCountByCommunity,
            Double modularity) {

        QualityReport {
            unassignedNodeIds = List.copyOf(unassignedNodeIds);
            internalTieCountByCommunity = Map.copyOf(internalTieCountByCommunity);
        }

        int unassignedNodeCount() {
            return unassignedNodeIds.size();
        }
    }

    private record Tie(String left, String right) {
    }
}
