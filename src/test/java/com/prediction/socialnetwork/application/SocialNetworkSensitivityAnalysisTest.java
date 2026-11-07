package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.CrossMetricAnalysisCalculator;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import com.prediction.socialnetwork.network.StructuralRoleCalculator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SocialNetworkSensitivityAnalysisTest {

    private static final String DATASET_ID = "think-tank-social-network";
    private static final List<Integer> TOP_K_VALUES = List.of(10, 20, 30);
    private static final List<String> METRIC_ORDER = List.of(
            "indegree", "outdegree", "pageRank", "kCore", "normalizedBetweenness");

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    @Test
    void reportsCoreThresholdTopKTieAndNetworkScopeSensitivity() {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(DATASET_ID);
        SocialNetworkGraphResult fullGraph = snapshot.network();
        Map<String, Map<String, Double>> fullValues = metricValues(fullGraph);
        Map<String, Integer> fullOutdegree = fullValues.get("outdegree").entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().intValue()));
        PageRankResult fullPageRankCheck =
                calculatePageRank(fullGraph.nodes(), fullGraph.edges(), fullOutdegree);
        assertTrue(fullPageRankCheck.converged());
        fullGraph.nodes().forEach(node -> assertEquals(
                node.pageRank(), fullPageRankCheck.values().get(node.id()), 1e-12));
        List<Set<String>> components = weakComponents(fullGraph.nodes(), fullGraph.edges());
        int largestComponentSize = components.stream().mapToInt(Set::size).max().orElse(0);
        List<Set<String>> tiedLargestComponents = components.stream()
                .filter(component -> component.size() == largestComponentSize)
                .toList();
        Set<String> largestScopeIds = new TreeSet<>();
        tiedLargestComponents.forEach(largestScopeIds::addAll);
        int edgesOutsideLargestScope = (int) fullGraph.edges().stream()
                .filter(edge -> !largestScopeIds.contains(edge.source())
                        || !largestScopeIds.contains(edge.target()))
                .count();

        List<NetworkNode> largestNodes = fullGraph.nodes().stream()
                .filter(node -> largestScopeIds.contains(node.id()))
                .toList();
        List<NetworkEdge> largestEdges = fullGraph.edges().stream()
                .filter(edge -> largestScopeIds.contains(edge.source())
                        && largestScopeIds.contains(edge.target()))
                .toList();
        ScopeResult largestScope = recalculateScope(largestNodes, largestEdges);
        Map<String, Map<String, Double>> largestValues = largestScope.values();

        assertTrue(fullGraph.pageRank().converged());
        assertTrue(largestScope.pageRank().converged());
        assertEquals(fullGraph.nodes().size(), fullGraph.metrics().nodeCount());
        assertEquals(largestScopeIds.size(), largestScope.nodes().size());
        assertEquals(largestScopeIds, nodeIds(largestScope.nodes()));
        assertEquals(
                snapshot.dataset().datasetVersion(),
                workspace.get(DATASET_ID).dataset().datasetVersion());

        StringBuilder report = new StringBuilder("EA14_SENSITIVITY ")
                .append("dataset=").append(snapshot.dataset().datasetId())
                .append(" version=").append(snapshot.dataset().datasetVersion())
                .append(" calculatedAt=").append(fullGraph.calculatedAt())
                .append(" metricVersion=").append(fullGraph.metrics().metricVersion())
                .append(" roleVersion=").append(fullGraph.structuralRoles().algorithmVersion())
                .append(" N=").append(fullGraph.nodes().size())
                .append(" E=").append(fullGraph.edges().size())
                .append(" weakComponents=").append(components.size())
                .append(" largestComponentN=").append(largestComponentSize)
                .append(" tiedLargestComponents=").append(tiedLargestComponents.size())
                .append(" largestScopeN=").append(largestScopeIds.size())
                .append(" largestScopeE=").append(largestScope.edges().size())
                .append(" edgesOutsideLargest=").append(edgesOutsideLargestScope);
        assertEquals(0, edgesOutsideLargestScope,
                "this snapshot's non-largest components should be isolated nodes");
        assertEquals(
                fullGraph.edges().size(),
                largestScope.edges().size(),
                "every relationship edge should remain inside the largest component");

        appendCoreThresholds(
                report, fullGraph.structuralRoles(), largestScope.structuralRoles());
        appendTopKSensitivity(report, fullValues, largestValues);
        appendScopeRankComparison(report, fullValues, largestValues, largestScopeIds);
        appendScopeValueChanges(report, fullValues, largestValues, largestScopeIds);
        appendCrossMetricTopKSensitivity(report, fullValues);
        verifyApprovedTop20Results(fullGraph, fullValues);
        System.out.println(report);
    }

    private static void appendCoreThresholds(
            StringBuilder report,
            StructuralRoleAnalysis fullRoles,
            StructuralRoleAnalysis largestRoles) {
        StructuralRoleAnalysis.KCoreDistribution full = fullRoles.kCoreDistribution();
        StructuralRoleAnalysis.KCoreDistribution largest = largestRoles.kCoreDistribution();
        Map<String, Integer> largestCoreByNode = coreNumbers(largestRoles);
        Map<String, Integer> fullCoreByNode = coreNumbers(fullRoles);
        long matchingCoreNumbers = largestCoreByNode.entrySet().stream()
                .filter(entry -> entry.getValue().equals(fullCoreByNode.get(entry.getKey())))
                .count();
        report.append(" coreThresholds=[");
        for (int threshold = 0; threshold <= full.maxCoreNumber(); threshold++) {
            if (threshold > 0) {
                report.append(';');
            }
            StructuralRoleAnalysis.KCoreShell shell = full.shells().get(threshold);
            report.append(threshold).append(':')
                    .append(shell.cumulativeCount()).append('/')
                    .append(full.sampleSize()).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.4f", shell.cumulativeRatio()));
        }
        report.append("] fullMaxCore=").append(full.maxCoreNumber())
                .append(" fullHighestShellN=").append(full.highestCoreCount())
                .append(" largestMaxCore=").append(largest.maxCoreNumber())
                .append(" largestHighestShellN=").append(largest.highestCoreCount())
                .append(" matchedLargestCoreNumbers=").append(matchingCoreNumbers)
                .append('/').append(largest.sampleSize());
        assertEquals(largestCoreByNode.size(), matchingCoreNumbers,
                "k-core number must be invariant when disconnected components are removed");
    }

    private static void appendTopKSensitivity(
            StringBuilder report,
            Map<String, Map<String, Double>> fullValues,
            Map<String, Map<String, Double>> largestValues) {
        for (String metric : METRIC_ORDER) {
            report.append(" topK_").append(metric).append("=[");
            for (int index = 0; index < TOP_K_VALUES.size(); index++) {
                int k = TOP_K_VALUES.get(index);
                if (index > 0) {
                    report.append(';');
                }
                TopSet fullExpanded = topSet(fullValues.get(metric), k, true);
                TopSet fullStrict = topSet(fullValues.get(metric), k, false);
                TopSet largestExpanded = topSet(largestValues.get(metric), k, true);
                TopSet largestStrict = topSet(largestValues.get(metric), k, false);
                Double scopeJaccard =
                        jaccard(fullExpanded.members(), largestExpanded.members());
                report.append(k).append("{full=")
                        .append(fullExpanded.state()).append(",threshold=")
                        .append(fullExpanded.threshold()).append(",setN=")
                        .append(fullExpanded.members().size()).append(",ties=")
                        .append(fullExpanded.members().size() - fullStrict.members().size())
                        .append(",scopeJ=")
                        .append(scopeJaccard)
                        .append(",strictJ=")
                        .append(jaccard(fullStrict.members(), fullExpanded.members()))
                        .append('}');
                if ("available".equals(fullExpanded.state())) {
                    assertEquals(k, fullStrict.members().size());
                    assertTrue(fullExpanded.members().size() >= k);
                    assertEquals(1.0, scopeJaccard,
                            "expanded top-K sets should match across these scopes");
                } else {
                    assertTrue(fullExpanded.members().isEmpty());
                }
            }
            report.append(']');
        }
    }

    private static void appendScopeRankComparison(
            StringBuilder report,
            Map<String, Map<String, Double>> fullValues,
            Map<String, Map<String, Double>> largestValues,
            Set<String> largestScopeIds) {
        for (String metric : METRIC_ORDER) {
            Map<String, Double> full = fullValues.get(metric);
            Map<String, Double> scoped = largestValues.get(metric);
            double rho = spearman(full, scoped, largestScopeIds);
            report.append(" scopeRho_").append(metric).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.6f", rho));
            assertEquals(1.0, rho, 1e-12,
                    metric + " ranking should be invariant within the component");
        }
    }

    private static void appendScopeValueChanges(
            StringBuilder report,
            Map<String, Map<String, Double>> fullValues,
            Map<String, Map<String, Double>> largestValues,
            Set<String> largestScopeIds) {
        for (String metric : METRIC_ORDER) {
            int identicalCount = 0;
            double maxAbsoluteDelta = 0.0;
            Map<String, Double> full = fullValues.get(metric);
            Map<String, Double> scoped = largestValues.get(metric);
            for (String nodeId : largestScopeIds) {
                Double fullValue = full.get(nodeId);
                Double scopedValue = scoped.get(nodeId);
                if (fullValue != null && scopedValue != null) {
                    if (Double.compare(fullValue, scopedValue) == 0) {
                        identicalCount++;
                    }
                    maxAbsoluteDelta = Math.max(
                            maxAbsoluteDelta, Math.abs(fullValue - scopedValue));
                }
            }
            report.append(" scopeValue_").append(metric)
                    .append("{identical=").append(identicalCount)
                    .append('/').append(largestScopeIds.size())
                    .append(",maxAbsDelta=")
                    .append(String.format(java.util.Locale.ROOT, "%.12g", maxAbsoluteDelta))
                    .append('}');
        }
    }

    private static void appendCrossMetricTopKSensitivity(
            StringBuilder report,
            Map<String, Map<String, Double>> values) {
        for (int firstIndex = 0; firstIndex < METRIC_ORDER.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1;
                    secondIndex < METRIC_ORDER.size();
                    secondIndex++) {
                String firstMetric = METRIC_ORDER.get(firstIndex);
                String secondMetric = METRIC_ORDER.get(secondIndex);
                report.append(" pairTopK_").append(firstMetric)
                        .append('_').append(secondMetric).append("=[");
                for (int index = 0; index < TOP_K_VALUES.size(); index++) {
                    int k = TOP_K_VALUES.get(index);
                    if (index > 0) {
                        report.append(';');
                    }
                    TopSet first = topSet(values.get(firstMetric), k, true);
                    TopSet second = topSet(values.get(secondMetric), k, true);
                    report.append(k).append(':')
                            .append(first.members().size()).append('/')
                            .append(second.members().size()).append('/')
                            .append(jaccard(first.members(), second.members()));
                }
                report.append(']');
            }
        }
    }

    private static void verifyApprovedTop20Results(
            SocialNetworkGraphResult graph,
            Map<String, Map<String, Double>> values) {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = graph.nodes().stream()
                .map(node -> {
                    Map<String, Double> nodeValues = new LinkedHashMap<>();
                    METRIC_ORDER.forEach(metric ->
                            nodeValues.put(metric, values.get(metric).get(node.id())));
                    return new CrossMetricAnalysisCalculator.NodeMetricValues(
                            node.id(), node.nodeType(), nodeValues);
                })
                .toList();
        CrossMetricAnalysisCalculator.Analysis approved =
                new CrossMetricAnalysisCalculator().analyze(nodes, Map.of());
        for (CrossMetricAnalysisCalculator.Top20Comparison comparison
                : approved.top20Comparisons()) {
            TopSet first = topSet(values.get(comparison.metricA()), 20, true);
            TopSet second = topSet(values.get(comparison.metricB()), 20, true);
            Set<String> intersection = new HashSet<>(first.members());
            intersection.retainAll(second.members());
            assertEquals(first.members().size(), comparison.setSizeA());
            assertEquals(second.members().size(), comparison.setSizeB());
            assertEquals(intersection.size(), comparison.intersectionCount());
            assertEquals(
                    jaccard(first.members(), second.members()),
                    comparison.jaccard(),
                    1e-12,
                    "K=20 sensitivity calculation must match EA12's approved rule");
        }
    }

    private static Map<String, Map<String, Double>> metricValues(
            SocialNetworkGraphResult graph) {
        return metricValues(
                graph.nodes(),
                graph.structuralRoles(),
                graph.pageRank().values(),
                graph.nodes().stream().collect(java.util.stream.Collectors.toMap(
                        NetworkNode::id, NetworkNode::inDegree)),
                graph.nodes().stream().collect(java.util.stream.Collectors.toMap(
                        NetworkNode::id, NetworkNode::outDegree)));
    }

    private static ScopeResult recalculateScope(
            List<NetworkNode> nodes, List<NetworkEdge> edges) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> outdegree = new HashMap<>();
        nodes.forEach(node -> {
            indegree.put(node.id(), 0);
            outdegree.put(node.id(), 0);
        });
        edges.forEach(edge -> {
            outdegree.compute(edge.source(), (ignored, degree) -> degree + 1);
            indegree.compute(edge.target(), (ignored, degree) -> degree + 1);
        });
        PageRankResult pageRank = calculatePageRank(nodes, edges, outdegree);
        StructuralRoleAnalysis structuralRoles =
                new StructuralRoleCalculator().calculate(nodes, edges);
        Map<String, Map<String, Double>> values =
                metricValues(nodes, structuralRoles, pageRank.values(), indegree, outdegree);
        return new ScopeResult(nodes, edges, structuralRoles, pageRank, values);
    }

    private static Map<String, Map<String, Double>> metricValues(
            List<NetworkNode> nodes,
            StructuralRoleAnalysis structuralRoles,
            Map<String, Double> pageRank,
            Map<String, Integer> indegree,
            Map<String, Integer> outdegree) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        METRIC_ORDER.forEach(key -> result.put(key, new LinkedHashMap<>()));
        for (NetworkNode node : nodes) {
            result.get("indegree").put(node.id(), (double) indegree.get(node.id()));
            result.get("outdegree").put(node.id(), (double) outdegree.get(node.id()));
            result.get("pageRank").put(node.id(), pageRank.get(node.id()));
        }
        structuralRoles.kCoreRanking().forEach(entry ->
                result.get("kCore").put(entry.nodeId(), (double) entry.coreNumber()));
        structuralRoles.betweennessRanking().stream()
                .filter(entry -> entry.normalizedBetweenness() != null)
                .forEach(entry -> result.get("normalizedBetweenness")
                        .put(entry.nodeId(), entry.normalizedBetweenness()));
        return result;
    }

    private static PageRankResult calculatePageRank(
            List<NetworkNode> nodes,
            List<NetworkEdge> edges,
            Map<String, Integer> outdegree) {
        List<String> nodeIds = nodes.stream().map(NetworkNode::id).sorted().toList();
        if (nodeIds.isEmpty()) {
            return new PageRankResult(Map.of(), true, 0, 0.0, 0.0);
        }
        Map<String, List<String>> incoming = new HashMap<>();
        edges.forEach(edge -> incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>())
                .add(edge.source()));
        Map<String, Double> current = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            current.put(nodeId, 1.0 / nodeIds.size());
        }
        double delta = Double.POSITIVE_INFINITY;
        int iterations = 0;
        while (iterations < 100 && delta >= 1e-8) {
            Map<String, Double> previous = current;
            double danglingMass = nodeIds.stream()
                    .filter(nodeId -> outdegree.get(nodeId) == 0)
                    .mapToDouble(previous::get)
                    .sum();
            Map<String, Double> next = new LinkedHashMap<>();
            for (String nodeId : nodeIds) {
                double incomingMass = incoming.getOrDefault(nodeId, List.of()).stream()
                        .mapToDouble(source -> previous.get(source) / outdegree.get(source))
                        .sum();
                next.put(nodeId, (1.0 - 0.85) / nodeIds.size()
                        + 0.85 * (incomingMass + danglingMass / nodeIds.size()));
            }
            delta = nodeIds.stream()
                    .mapToDouble(nodeId -> Math.abs(next.get(nodeId) - previous.get(nodeId)))
                    .sum();
            current = next;
            iterations++;
        }
        return new PageRankResult(
                current,
                delta < 1e-8,
                iterations,
                delta,
                current.values().stream().mapToDouble(Double::doubleValue).sum());
    }

    private static Map<String, Integer> coreNumbers(StructuralRoleAnalysis roles) {
        Map<String, Integer> result = new HashMap<>();
        roles.kCoreRanking().forEach(entry -> result.put(entry.nodeId(), entry.coreNumber()));
        return result;
    }

    private static List<Set<String>> weakComponents(
            List<NetworkNode> nodes, List<NetworkEdge> edges) {
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        nodes.stream().map(NetworkNode::id).sorted()
                .forEach(nodeId -> adjacency.put(nodeId, new TreeSet<>()));
        for (NetworkEdge edge : edges) {
            adjacency.get(edge.source()).add(edge.target());
            adjacency.get(edge.target()).add(edge.source());
        }
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();
        for (String start : adjacency.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            Set<String> component = new TreeSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                String current = queue.remove();
                component.add(current);
                for (String neighbor : adjacency.get(current)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }
        components.sort(Comparator.<Set<String>>comparingInt(Set::size)
                .reversed()
                .thenComparing(component -> component.iterator().next()));
        return List.copyOf(components);
    }

    private static Set<String> nodeIds(List<NetworkNode> nodes) {
        return nodes.stream().map(NetworkNode::id)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private static TopSet topSet(Map<String, Double> values, int k, boolean expandTies) {
        List<Map.Entry<String, Double>> ranked = values.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        if (ranked.size() < k) {
            return new TopSet(Set.of(), "population_below_k", null);
        }
        double threshold = ranked.get(k - 1).getValue();
        long positiveCount = values.values().stream().filter(value -> value > 0.0).count();
        if (threshold <= 0.0) {
            return new TopSet(
                    Set.of(), "zero_threshold_positive=" + positiveCount, threshold);
        }
        List<Map.Entry<String, Double>> selected = expandTies
                ? ranked.stream().filter(entry -> entry.getValue() >= threshold).toList()
                : ranked.subList(0, k);
        Set<String> members = selected.stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new TopSet(members, "available", threshold);
    }

    private static Double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return null;
        }
        Set<String> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return intersection.size() / (double) union.size();
    }

    private static double spearman(
            Map<String, Double> first,
            Map<String, Double> second,
            Set<String> ids) {
        Map<String, Double> firstRanks = averageRanks(first, ids);
        Map<String, Double> secondRanks = averageRanks(second, ids);
        double meanFirst = firstRanks.values().stream().mapToDouble(Double::doubleValue)
                .average().orElse(Double.NaN);
        double meanSecond = secondRanks.values().stream().mapToDouble(Double::doubleValue)
                .average().orElse(Double.NaN);
        double covariance = 0.0;
        double varianceFirst = 0.0;
        double varianceSecond = 0.0;
        for (String id : firstRanks.keySet()) {
            double deltaFirst = firstRanks.get(id) - meanFirst;
            double deltaSecond = secondRanks.get(id) - meanSecond;
            covariance += deltaFirst * deltaSecond;
            varianceFirst += deltaFirst * deltaFirst;
            varianceSecond += deltaSecond * deltaSecond;
        }
        return covariance / Math.sqrt(varianceFirst * varianceSecond);
    }

    private static Map<String, Double> averageRanks(
            Map<String, Double> values, Set<String> ids) {
        List<Map.Entry<String, Double>> ranked = values.entrySet().stream()
                .filter(entry -> ids.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        Map<String, Double> ranks = new LinkedHashMap<>();
        int start = 0;
        while (start < ranked.size()) {
            int end = start + 1;
            while (end < ranked.size()
                    && Double.compare(ranked.get(start).getValue(),
                            ranked.get(end).getValue()) == 0) {
                end++;
            }
            double averageRank = ((start + 1) + end) / 2.0;
            for (int index = start; index < end; index++) {
                ranks.put(ranked.get(index).getKey(), averageRank);
            }
            start = end;
        }
        return ranks;
    }

    private record TopSet(Set<String> members, String state, Double threshold) {
    }

    private record ScopeResult(
            List<NetworkNode> nodes,
            List<NetworkEdge> edges,
            StructuralRoleAnalysis structuralRoles,
            PageRankResult pageRank,
            Map<String, Map<String, Double>> values) {
    }
}
