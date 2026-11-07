package com.prediction.socialnetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class StructuralRoleCalculator {

    public static final String ALGORITHM_VERSION = "node-roles-v1.0";

    public StructuralRoleAnalysis calculate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges) {
        Map<String, NetworkNodeType> nodeTypes = new TreeMap<>();
        for (NetworkNode node : nodes) {
            nodeTypes.putIfAbsent(node.id(), node.nodeType());
        }

        Map<String, Set<String>> adjacency = new TreeMap<>();
        nodeTypes.keySet().forEach(nodeId -> adjacency.put(nodeId, new TreeSet<>()));
        int tieCount = 0;
        for (NetworkEdge edge : directedEdges) {
            String source = edge.source();
            String target = edge.target();
            if (source.equals(target)
                    || !adjacency.containsKey(source)
                    || !adjacency.containsKey(target)) {
                continue;
            }
            if (adjacency.get(source).add(target)) {
                adjacency.get(target).add(source);
                tieCount++;
            }
        }

        Map<String, Integer> coreNumbers = coreNumbers(adjacency);
        Map<String, Double> rawBetweenness = betweenness(adjacency);
        int nodeCount = nodeTypes.size();
        List<StructuralRoleAnalysis.KCoreEntry> kCoreRanking = coreNumbers.entrySet().stream()
                .map(entry -> new StructuralRoleAnalysis.KCoreEntry(
                        entry.getKey(), nodeTypes.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(StructuralRoleAnalysis.KCoreEntry::coreNumber)
                        .reversed()
                        .thenComparing(StructuralRoleAnalysis.KCoreEntry::nodeId))
                .toList();
        List<StructuralRoleAnalysis.BetweennessEntry> betweennessRanking =
                rawBetweenness.entrySet().stream()
                        .map(entry -> new StructuralRoleAnalysis.BetweennessEntry(
                                entry.getKey(),
                                nodeTypes.get(entry.getKey()),
                                entry.getValue(),
                                normalize(entry.getValue(), nodeCount)))
                        .sorted(Comparator.comparing(
                                        StructuralRoleAnalysis.BetweennessEntry
                                                ::normalizedBetweenness,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(
                                        StructuralRoleAnalysis.BetweennessEntry::nodeId))
                        .toList();

        return new StructuralRoleAnalysis(
                nodeCount,
                tieCount,
                ALGORITHM_VERSION,
                kCoreRanking,
                betweennessRanking);
    }

    private static Map<String, Integer> coreNumbers(Map<String, Set<String>> adjacency) {
        Map<String, Integer> degreeByNode = new HashMap<>();
        PriorityQueue<DegreeEntry> pending = new PriorityQueue<>(
                Comparator.comparingInt(DegreeEntry::degree)
                        .thenComparing(DegreeEntry::nodeId));
        adjacency.forEach((nodeId, neighbors) -> {
            int degree = neighbors.size();
            degreeByNode.put(nodeId, degree);
            pending.add(new DegreeEntry(nodeId, degree));
        });

        Map<String, Integer> result = new LinkedHashMap<>();
        Set<String> removed = new HashSet<>();
        int currentCore = 0;
        while (!pending.isEmpty()) {
            DegreeEntry entry = pending.remove();
            if (removed.contains(entry.nodeId())
                    || degreeByNode.get(entry.nodeId()) != entry.degree()) {
                continue;
            }

            int coreNumber = Math.max(currentCore, entry.degree());
            currentCore = coreNumber;
            removed.add(entry.nodeId());
            result.put(entry.nodeId(), coreNumber);
            for (String neighbor : adjacency.get(entry.nodeId())) {
                if (removed.contains(neighbor)) {
                    continue;
                }
                int updatedDegree = degreeByNode.compute(neighbor, (key, degree) -> degree - 1);
                pending.add(new DegreeEntry(neighbor, updatedDegree));
            }
        }
        return result;
    }

    private static Map<String, Double> betweenness(Map<String, Set<String>> adjacency) {
        Map<String, Double> centrality = new TreeMap<>();
        adjacency.keySet().forEach(nodeId -> centrality.put(nodeId, 0.0));

        for (String source : adjacency.keySet()) {
            ArrayDeque<String> queue = new ArrayDeque<>();
            ArrayDeque<String> stack = new ArrayDeque<>();
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> distance = new HashMap<>();
            Map<String, Double> pathCounts = new HashMap<>();

            adjacency.keySet().forEach(nodeId -> {
                predecessors.put(nodeId, new ArrayList<>());
                distance.put(nodeId, -1);
                pathCounts.put(nodeId, 0.0);
            });
            distance.put(source, 0);
            pathCounts.put(source, 1.0);
            queue.add(source);

            while (!queue.isEmpty()) {
                String current = queue.remove();
                stack.push(current);
                for (String neighbor : adjacency.get(current)) {
                    if (distance.get(neighbor) < 0) {
                        distance.put(neighbor, distance.get(current) + 1);
                        queue.add(neighbor);
                    }
                    if (distance.get(neighbor) == distance.get(current) + 1) {
                        pathCounts.put(
                                neighbor,
                                pathCounts.get(neighbor) + pathCounts.get(current));
                        predecessors.get(neighbor).add(current);
                    }
                }
            }

            Map<String, Double> dependencies = new HashMap<>();
            adjacency.keySet().forEach(nodeId -> dependencies.put(nodeId, 0.0));
            while (!stack.isEmpty()) {
                String target = stack.pop();
                for (String predecessor : predecessors.get(target)) {
                    double contribution = pathCounts.get(predecessor)
                            / pathCounts.get(target)
                            * (1.0 + dependencies.get(target));
                    dependencies.put(
                            predecessor, dependencies.get(predecessor) + contribution);
                }
                if (!target.equals(source)) {
                    centrality.put(
                            target, centrality.get(target) + dependencies.get(target));
                }
            }
        }

        centrality.replaceAll((nodeId, value) -> value / 2.0);
        return centrality;
    }

    private static Double normalize(double rawBetweenness, int nodeCount) {
        if (nodeCount < 3) {
            return null;
        }
        return 2.0 * rawBetweenness / ((nodeCount - 1.0) * (nodeCount - 2.0));
    }

    private record DegreeEntry(String nodeId, int degree) {
    }
}
