package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossMetricAnalysisCalculatorTest {

    private final CrossMetricAnalysisCalculator calculator =
            new CrossMetricAnalysisCalculator();

    @Test
    void calculatesSpearmanWithAverageRanksForTiedValues() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                node("A", 1, 4, 0.1, 1, 0.1),
                node("B", 1, 3, 0.2, 2, 0.2),
                node("C", 3, 2, 0.3, 3, 0.3),
                node("D", 4, 1, 0.4, 4, 0.4));

        CrossMetricAnalysisCalculator.SpearmanPair pair =
                spearmanPair(calculator.analyze(nodes, Map.of()), "indegree", "outdegree");

        assertEquals(4, pair.sampleSize());
        assertEquals("average_rank", pair.tieMethod());
        assertEquals(-4.5 / Math.sqrt(4.5 * 5.0), pair.rho(), 1e-12);
        assertEquals(CrossMetricAnalysisCalculator.ResultState.AVAILABLE, pair.state());
    }

    @Test
    void expandsTop20BoundaryTiesAndReportsZeroThresholdWithoutJaccard() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            double indegree = index <= 19 ? 101 - index : index <= 22 ? 1 : 0;
            double outdegree = index <= 20 ? 2 : 1;
            double pageRank = index <= 19 ? (20 - index) / 20.0 : 0.0;
            double kCore = index <= 21 ? 3 : 2;
            double betweenness = index <= 20 ? 0.5 : 0.2;
            nodes.add(node(String.format("N%02d", index),
                    indegree, outdegree, pageRank, kCore, betweenness));
        }

        CrossMetricAnalysisCalculator.Analysis analysis =
                calculator.analyze(nodes, Map.of());
        CrossMetricAnalysisCalculator.Top20Comparison tiedPair =
                top20Comparison(analysis, "indegree", "outdegree");
        CrossMetricAnalysisCalculator.Top20Comparison zeroThresholdPair =
                top20Comparison(analysis, "indegree", "pageRank");

        assertEquals(10, analysis.spearmanPairs().size());
        assertEquals(10, analysis.top20Comparisons().size());
        assertEquals(22, tiedPair.setSizeA());
        assertTrue(tiedPair.tieExpandedA());
        assertEquals(20, tiedPair.intersectionCount());
        assertEquals(20.0 / 22.0, tiedPair.jaccard(), 1e-12);
        assertEquals(CrossMetricAnalysisCalculator.ResultState.AVAILABLE, tiedPair.state());

        assertEquals(0.0, zeroThresholdPair.thresholdB());
        assertEquals(19, zeroThresholdPair.positiveScoreNodeCountB());
        assertNull(zeroThresholdPair.intersectionCount());
        assertNull(zeroThresholdPair.jaccard());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.LIMITED,
                zeroThresholdPair.state());
        assertEquals(22, analysis.nodeEvidence().size());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.LIMITED,
                analysis.nodeEvidenceState());
        CrossMetricAnalysisCalculator.NodeEvidence boundaryNode =
                analysis.nodeEvidence().stream()
                        .filter(item -> item.nodeId().equals("N20"))
                        .findFirst()
                        .orElseThrow();
        CrossMetricAnalysisCalculator.MetricEvidence tiedMetric =
                metricEvidence(boundaryNode, "indegree");
        assertEquals(1.0, tiedMetric.value());
        assertEquals(20, tiedMetric.rank());
    }

    @Test
    void marksTop20OverlapAsLimitedWhenPopulationDoesNotExceedK() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                node("A", 1, 4, 0.1, 1, 0.1),
                node("B", 2, 3, 0.2, 2, 0.2),
                node("C", 3, 2, 0.3, 3, 0.3),
                node("D", 4, 1, 0.4, 4, 0.4));

        CrossMetricAnalysisCalculator.Top20Comparison comparison = top20Comparison(
                calculator.analyze(nodes, Map.of()), "indegree", "outdegree");

        assertEquals(4, comparison.intersectionCount());
        assertNull(comparison.jaccard());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.LIMITED, comparison.state());
    }

    @Test
    void computesPairwiseEffectiveSampleWhenSomeMetricValuesAreMissing() {
        Map<String, Double> missingPageRank = new LinkedHashMap<>();
        missingPageRank.put("indegree", 4.0);
        missingPageRank.put("outdegree", 1.0);
        missingPageRank.put("kCore", 4.0);
        missingPageRank.put("normalizedBetweenness", 0.4);
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                node("A", 1, 4, 0.1, 1, 0.1),
                node("B", 2, 3, 0.2, 2, 0.2),
                node("C", 3, 2, 0.3, 3, 0.3),
                new CrossMetricAnalysisCalculator.NodeMetricValues(
                        "D", NetworkNodeType.PROFILE, missingPageRank));

        CrossMetricAnalysisCalculator.SpearmanPair pair = spearmanPair(
                calculator.analyze(nodes, Map.of()), "indegree", "pageRank");

        assertEquals(3, pair.sampleSize());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.LIMITED, pair.state());
        assertTrue(pair.reason().contains("未覆盖完整节点总体"));
    }

    @Test
    void omitsMetricsThatAreNotAdmissibleForCrossChecks() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                node("A", 1, 3, 0.3, 1, 0.1),
                node("B", 2, 2, 0.2, 2, 0.2),
                node("C", 3, 1, 0.1, 3, 0.3));
        CrossMetricAnalysisCalculator.Analysis analysis = calculator.analyze(
                nodes,
                Map.of("pageRank", "PageRank 未收敛。"));
        CrossMetricAnalysisCalculator.SpearmanPair pair =
                spearmanPair(analysis, "indegree", "pageRank");

        assertNull(pair.rho());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.UNAVAILABLE, pair.state());
        assertFalse(analysis.nodeEvidence().isEmpty());
        assertTrue(analysis.nodeEvidence().stream()
                .flatMap(evidence -> evidence.metrics().stream())
                .noneMatch(metric -> metric.metricKey().equals("pageRank")));
    }

    @Test
    void omitsBetweennessWhenThePopulationIsTooSmall() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                new CrossMetricAnalysisCalculator.NodeMetricValues(
                        "A", NetworkNodeType.PROFILE,
                        Map.of("indegree", 1.0, "outdegree", 1.0,
                                "pageRank", 0.5, "kCore", 1.0)),
                new CrossMetricAnalysisCalculator.NodeMetricValues(
                        "B", NetworkNodeType.PROFILE,
                        Map.of("indegree", 2.0, "outdegree", 2.0,
                                "pageRank", 0.5, "kCore", 1.0)));

        CrossMetricAnalysisCalculator.Analysis analysis = calculator.analyze(
                nodes,
                Map.of("normalizedBetweenness", "节点数不足 3。"));
        CrossMetricAnalysisCalculator.SpearmanPair pair =
                spearmanPair(analysis, "indegree", "normalizedBetweenness");

        assertEquals(CrossMetricAnalysisCalculator.ResultState.UNAVAILABLE, pair.state());
        assertTrue(analysis.nodeEvidence().stream()
                .flatMap(evidence -> evidence.metrics().stream())
                .noneMatch(metric -> metric.metricKey().equals("normalizedBetweenness")));
    }

    @Test
    void marksCorrelationUnavailableForAConstantMetric() {
        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes = List.of(
                node("A", 1, 3, 0.3, 2, 0.1),
                node("B", 2, 2, 0.2, 2, 0.2),
                node("C", 3, 1, 0.1, 2, 0.3));
        CrossMetricAnalysisCalculator.SpearmanPair pair =
                spearmanPair(calculator.analyze(nodes, Map.of()), "indegree", "kCore");

        assertNull(pair.rho());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.UNAVAILABLE, pair.state());
        assertTrue(pair.reason().contains("没有变异"));
    }

    @Test
    void returnsEmptyStatesWhenTheFullNetworkHasNoNodes() {
        CrossMetricAnalysisCalculator.Analysis analysis =
                calculator.analyze(List.of(), Map.of());

        assertEquals(CrossMetricAnalysisCalculator.ResultState.EMPTY, analysis.state());
        assertEquals(CrossMetricAnalysisCalculator.ResultState.EMPTY,
                analysis.nodeEvidenceState());
        assertEquals(10, analysis.spearmanPairs().size());
        assertTrue(analysis.spearmanPairs().stream()
                .allMatch(pair -> pair.state() == CrossMetricAnalysisCalculator.ResultState.EMPTY));
        assertTrue(analysis.top20Comparisons().stream()
                .allMatch(comparison ->
                        comparison.state() == CrossMetricAnalysisCalculator.ResultState.EMPTY));
        assertTrue(analysis.nodeEvidence().isEmpty());
    }

    private static CrossMetricAnalysisCalculator.NodeMetricValues node(
            String id,
            double indegree,
            double outdegree,
            double pageRank,
            double kCore,
            double betweenness) {
        return new CrossMetricAnalysisCalculator.NodeMetricValues(
                id,
                NetworkNodeType.PROFILE,
                Map.of(
                        "indegree", indegree,
                        "outdegree", outdegree,
                        "pageRank", pageRank,
                        "kCore", kCore,
                        "normalizedBetweenness", betweenness));
    }

    private static CrossMetricAnalysisCalculator.SpearmanPair spearmanPair(
            CrossMetricAnalysisCalculator.Analysis analysis,
            String metricA,
            String metricB) {
        return analysis.spearmanPairs().stream()
                .filter(pair -> (pair.metricA().equals(metricA)
                        && pair.metricB().equals(metricB)
                        || pair.metricA().equals(metricB)
                        && pair.metricB().equals(metricA)))
                .findFirst()
                .orElseThrow();
    }

    private static CrossMetricAnalysisCalculator.Top20Comparison top20Comparison(
            CrossMetricAnalysisCalculator.Analysis analysis,
            String metricA,
            String metricB) {
        return analysis.top20Comparisons().stream()
                .filter(comparison -> (comparison.metricA().equals(metricA)
                        && comparison.metricB().equals(metricB)
                        || comparison.metricA().equals(metricB)
                        && comparison.metricB().equals(metricA)))
                .findFirst()
                .orElseThrow();
    }

    private static CrossMetricAnalysisCalculator.MetricEvidence metricEvidence(
            CrossMetricAnalysisCalculator.NodeEvidence evidence,
            String metricKey) {
        return evidence.metrics().stream()
                .filter(metric -> metric.metricKey().equals(metricKey))
                .findFirst()
                .orElseThrow();
    }
}
