package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class CrossMetricAnalysisCalculator {

    public static final int TOP_K = 20;
    public static final List<String> METRIC_KEYS = List.of(
            "indegree",
            "outdegree",
            "pageRank",
            "kCore",
            "normalizedBetweenness");

    public Analysis analyze(
            List<NodeMetricValues> sourceNodes,
            Map<String, String> unavailableMetricReasons) {
        List<NodeMetricValues> nodes = sourceNodes.stream()
                .sorted(Comparator.comparing(NodeMetricValues::nodeId))
                .toList();
        ensureUniqueNodeIds(nodes);

        Map<String, List<MetricValue>> valuesByMetric = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> competitionRanks = new LinkedHashMap<>();
        Map<String, String> incompleteReasons = new LinkedHashMap<>(unavailableMetricReasons);
        Map<String, MetricTopData> topDataByMetric = new LinkedHashMap<>();

        for (String metricKey : METRIC_KEYS) {
            List<MetricValue> values = unavailableMetricReasons.containsKey(metricKey)
                    ? List.of()
                    : metricValues(nodes, metricKey);
            valuesByMetric.put(metricKey, values);
            competitionRanks.put(metricKey, competitionRanks(values));
            if (!incompleteReasons.containsKey(metricKey)
                    && values.size() < nodes.size()) {
                incompleteReasons.put(metricKey,
                        "仅有 " + values.size() + "/" + nodes.size()
                                + " 个节点具有有效值。");
            }
            topDataByMetric.put(metricKey, topData(
                    metricKey,
                    nodes.size(),
                    values,
                    incompleteReasons.get(metricKey)));
        }

        List<SpearmanPair> spearmanPairs = new ArrayList<>();
        List<Top20Comparison> top20Comparisons = new ArrayList<>();
        for (int first = 0; first < METRIC_KEYS.size(); first++) {
            for (int second = first + 1; second < METRIC_KEYS.size(); second++) {
                String metricA = METRIC_KEYS.get(first);
                String metricB = METRIC_KEYS.get(second);
                spearmanPairs.add(spearmanPair(
                        nodes,
                        metricA,
                        metricB,
                        unavailableMetricReasons));
                top20Comparisons.add(top20Comparison(
                        nodes.size(),
                        topDataByMetric.get(metricA),
                        topDataByMetric.get(metricB),
                        incompleteReasons));
            }
        }

        Set<String> candidateNodeIds = new TreeSet<>();
        topDataByMetric.values().stream()
                .map(MetricTopData::nodeIds)
                .filter(nodeIds -> nodeIds != null)
                .forEach(candidateNodeIds::addAll);
        Map<String, NodeMetricValues> nodesById = new HashMap<>();
        nodes.forEach(node -> nodesById.put(node.nodeId(), node));
        List<NodeEvidence> nodeEvidence = candidateNodeIds.stream()
                .map(nodeId -> nodeEvidence(
                        nodesById.get(nodeId),
                        valuesByMetric,
                        competitionRanks))
                .toList();

        ResultState nodeEvidenceState = nodeEvidenceState(
                nodes.size(), nodeEvidence, topDataByMetric);
        String nodeEvidenceReason = nodeEvidenceReason(
                nodes.size(), nodeEvidenceState, topDataByMetric);
        ResultState state = overallState(
                nodes.size(), spearmanPairs, top20Comparisons, nodeEvidenceState);
        String reason = state == ResultState.LIMITED
                ? "部分指标对或 Top-20 证据受限、不可用或不具区分力；请以各项状态和原因分别解读。"
                : state == ResultState.EMPTY ? "当前完整网络范围内没有节点。" : null;

        return new Analysis(
                state,
                reason,
                nodes.size(),
                spearmanPairs,
                top20Comparisons,
                nodeEvidenceState,
                nodeEvidenceReason,
                nodeEvidence);
    }

    private static SpearmanPair spearmanPair(
            List<NodeMetricValues> nodes,
            String metricA,
            String metricB,
            Map<String, String> unavailableMetricReasons) {
        if (nodes.isEmpty()) {
            return new SpearmanPair(
                    metricA,
                    metricB,
                    null,
                    0,
                    "average_rank",
                    ResultState.EMPTY,
                    "当前完整网络范围内没有节点。");
        }
        String unavailableReason = pairMetricReason(
                metricA, metricB, unavailableMetricReasons);
        if (unavailableReason != null) {
            return new SpearmanPair(
                    metricA, metricB, null, 0, "average_rank",
                    ResultState.UNAVAILABLE, unavailableReason);
        }

        List<NodeMetricValues> commonNodes = nodes.stream()
                .filter(node -> isFinite(node.values().get(metricA))
                        && isFinite(node.values().get(metricB)))
                .toList();
        int sampleSize = commonNodes.size();
        if (sampleSize < 2) {
            return new SpearmanPair(
                    metricA,
                    metricB,
                    null,
                    sampleSize,
                    "average_rank",
                    ResultState.UNAVAILABLE,
                    "有效样本数小于 2，无法计算相关系数。");
        }

        Map<String, Double> ranksA = averageRanks(commonNodes, metricA);
        Map<String, Double> ranksB = averageRanks(commonNodes, metricB);
        double meanRank = (sampleSize + 1) / 2.0;
        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        for (NodeMetricValues node : commonNodes) {
            double deltaA = ranksA.get(node.nodeId()) - meanRank;
            double deltaB = ranksB.get(node.nodeId()) - meanRank;
            covariance += deltaA * deltaB;
            varianceA += deltaA * deltaA;
            varianceB += deltaB * deltaB;
        }

        if (varianceA == 0.0 || varianceB == 0.0) {
            return new SpearmanPair(
                    metricA,
                    metricB,
                    null,
                    sampleSize,
                    "average_rank",
                    ResultState.UNAVAILABLE,
                    "至少一个指标列没有变异，Spearman 相关系数不可用。");
        }

        double rho = covariance / Math.sqrt(varianceA * varianceB);
        rho = Math.max(-1.0, Math.min(1.0, rho));
        boolean complete = sampleSize == nodes.size();
        return new SpearmanPair(
                metricA,
                metricB,
                rho,
                sampleSize,
                "average_rank",
                complete ? ResultState.AVAILABLE : ResultState.LIMITED,
                complete ? null : "按两项指标均有效的节点计算，未覆盖完整节点总体。");
    }

    private static Top20Comparison top20Comparison(
            int populationSize,
            MetricTopData metricA,
            MetricTopData metricB,
            Map<String, String> unavailableMetricReasons) {
        String metricReason = pairMetricReason(
                metricA.metricKey(), metricB.metricKey(), unavailableMetricReasons);
        if (populationSize == 0) {
            return emptyTop20Comparison(metricA.metricKey(), metricB.metricKey());
        }

        Set<String> idsA = metricA.nodeIds();
        Set<String> idsB = metricB.nodeIds();
        Integer intersectionCount = null;
        Double jaccard = null;
        if (idsA != null && idsB != null) {
            Set<String> intersection = new HashSet<>(idsA);
            intersection.retainAll(idsB);
            intersectionCount = intersection.size();
            Set<String> union = new HashSet<>(idsA);
            union.addAll(idsB);
            if (!union.isEmpty()) {
                jaccard = intersection.size() / (double) union.size();
            }
        }

        ResultState state;
        String reason;
        if (metricReason != null) {
            state = ResultState.UNAVAILABLE;
            reason = metricReason;
        } else if (populationSize <= TOP_K) {
            state = ResultState.LIMITED;
            reason = combineReasons(
                    combineReasons(
                            "完整节点数不大于 K=20，重合结果不具区分力。",
                            zeroThresholdReason(metricA, metricB)),
                    combineReasons(metricA.reason(), metricB.reason()));
        } else if ((metricA.threshold() != null && metricA.threshold() == 0.0)
                || (metricB.threshold() != null && metricB.threshold() == 0.0)) {
            state = ResultState.LIMITED;
            reason = combineReasons(
                    zeroThresholdReason(metricA, metricB),
                    combineReasons(metricA.reason(), metricB.reason()));
            jaccard = null;
            intersectionCount = null;
        } else if (metricA.state() == ResultState.UNAVAILABLE
                || metricB.state() == ResultState.UNAVAILABLE) {
            state = ResultState.UNAVAILABLE;
            reason = combineReasons(metricA.reason(), metricB.reason());
        } else if (idsA == null || idsB == null) {
            state = ResultState.UNAVAILABLE;
            reason = combineReasons(metricA.reason(), metricB.reason());
        } else {
            state = ResultState.AVAILABLE;
            reason = null;
        }
        if (state != ResultState.AVAILABLE) {
            jaccard = null;
        }

        return new Top20Comparison(
                metricA.metricKey(),
                metricB.metricKey(),
                TOP_K,
                metricA.threshold(),
                metricB.threshold(),
                metricA.nodeIds() == null ? null : metricA.nodeIds().size(),
                metricB.nodeIds() == null ? null : metricB.nodeIds().size(),
                intersectionCount,
                jaccard,
                metricA.positiveScoreNodeCount(),
                metricB.positiveScoreNodeCount(),
                metricA.tieExpanded(),
                metricB.tieExpanded(),
                state,
                reason);
    }

    private static Top20Comparison emptyTop20Comparison(String metricA, String metricB) {
        return new Top20Comparison(
                metricA,
                metricB,
                TOP_K,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ResultState.EMPTY,
                "当前完整网络范围内没有节点。");
    }

    private static MetricTopData topData(
            String metricKey,
            int populationSize,
            List<MetricValue> values,
            String unavailableReason) {
        if (populationSize == 0) {
            return new MetricTopData(
                    metricKey, null, null, null, null, ResultState.EMPTY,
                    "当前完整网络范围内没有节点。");
        }
        if (unavailableReason != null) {
            return new MetricTopData(
                    metricKey,
                    null,
                    null,
                    null,
                    null,
                    ResultState.UNAVAILABLE,
                    unavailableReason);
        }

        List<MetricValue> ranked = values.stream()
                .sorted(Comparator.comparingDouble(MetricValue::value)
                        .reversed()
                        .thenComparing(MetricValue::nodeId))
                .toList();
        if (ranked.isEmpty()) {
            return new MetricTopData(
                    metricKey, null, null, 0, false, ResultState.UNAVAILABLE,
                    "没有有效节点值，无法确定 Top-20。");
        }
        if (ranked.size() < populationSize) {
            return new MetricTopData(
                    metricKey,
                    null,
                    null,
                    positiveCount(ranked),
                    null,
                    ResultState.UNAVAILABLE,
                    "指标值未覆盖完整节点总体，不能据此确定 Top-20。");
        }

        Integer positiveScoreNodeCount = positiveCount(ranked);
        if (populationSize < TOP_K) {
            Set<String> nodeIds = ranked.stream()
                    .map(MetricValue::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return new MetricTopData(
                    metricKey,
                    null,
                    nodeIds,
                    positiveScoreNodeCount,
                    false,
                    ResultState.LIMITED,
                    "完整节点数小于 K=20，Top-20 包含全部节点且不具区分力。");
        }

        double threshold = ranked.get(TOP_K - 1).value();
        if (threshold == 0.0) {
            return new MetricTopData(
                    metricKey,
                    threshold,
                    null,
                    positiveScoreNodeCount,
                    false,
                    ResultState.LIMITED,
                    "第 20 位阈值为 0；该指标不形成 Top-20 集合。");
        }

        Set<String> nodeIds = ranked.stream()
                .filter(value -> value.value() >= threshold)
                .map(MetricValue::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean tieExpanded = nodeIds.size() > TOP_K;
        return new MetricTopData(
                metricKey,
                threshold,
                nodeIds,
                positiveScoreNodeCount,
                tieExpanded,
                ResultState.AVAILABLE,
                null);
    }

    private static List<MetricValue> metricValues(
            List<NodeMetricValues> nodes,
            String metricKey) {
        List<MetricValue> values = new ArrayList<>();
        for (NodeMetricValues node : nodes) {
            Double value = node.values().get(metricKey);
            if (isFinite(value)) {
                values.add(new MetricValue(node.nodeId(), node.nodeType(), value));
            }
        }
        return List.copyOf(values);
    }

    private static Integer positiveCount(List<MetricValue> values) {
        return (int) values.stream().filter(value -> value.value() > 0.0).count();
    }

    private static Map<String, Double> averageRanks(
            List<NodeMetricValues> nodes,
            String metricKey) {
        List<MetricValue> sorted = metricValues(nodes, metricKey).stream()
                .sorted(Comparator.comparingDouble(MetricValue::value)
                        .thenComparing(MetricValue::nodeId))
                .toList();
        Map<String, Double> ranks = new HashMap<>();
        int start = 0;
        while (start < sorted.size()) {
            int end = start + 1;
            double value = sorted.get(start).value();
            while (end < sorted.size() && sorted.get(end).value() == value) {
                end++;
            }
            double averageRank = ((start + 1) + end) / 2.0;
            for (int index = start; index < end; index++) {
                ranks.put(sorted.get(index).nodeId(), averageRank);
            }
            start = end;
        }
        return ranks;
    }

    private static Map<String, Integer> competitionRanks(List<MetricValue> values) {
        List<MetricValue> ranked = values.stream()
                .sorted(Comparator.comparingDouble(MetricValue::value)
                        .reversed()
                        .thenComparing(MetricValue::nodeId))
                .toList();
        Map<String, Integer> ranks = new HashMap<>();
        double previousValue = Double.NaN;
        int currentRank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            MetricValue value = ranked.get(index);
            if (index == 0 || value.value() != previousValue) {
                currentRank = index + 1;
                previousValue = value.value();
            }
            ranks.put(value.nodeId(), currentRank);
        }
        return ranks;
    }

    private static NodeEvidence nodeEvidence(
            NodeMetricValues node,
            Map<String, List<MetricValue>> valuesByMetric,
            Map<String, Map<String, Integer>> competitionRanks) {
        List<MetricEvidence> metrics = new ArrayList<>();
        for (String metricKey : METRIC_KEYS) {
            MetricValue value = valuesByMetric.get(metricKey).stream()
                    .filter(candidate -> candidate.nodeId().equals(node.nodeId()))
                    .findFirst()
                    .orElse(null);
            Integer rank = competitionRanks.get(metricKey).get(node.nodeId());
            if (value != null && rank != null) {
                metrics.add(new MetricEvidence(metricKey, value.value(), rank));
            }
        }
        return new NodeEvidence(node.nodeId(), node.nodeType(), metrics);
    }

    private static ResultState nodeEvidenceState(
            int populationSize,
            List<NodeEvidence> evidence,
            Map<String, MetricTopData> topDataByMetric) {
        if (populationSize == 0) {
            return ResultState.EMPTY;
        }
        if (evidence.isEmpty()) {
            return ResultState.UNAVAILABLE;
        }
        boolean limited = populationSize <= TOP_K
                || topDataByMetric.values().stream()
                        .anyMatch(metric -> metric.state() != ResultState.AVAILABLE);
        return limited ? ResultState.LIMITED : ResultState.AVAILABLE;
    }

    private static String nodeEvidenceReason(
            int populationSize,
            ResultState state,
            Map<String, MetricTopData> topDataByMetric) {
        if (state == ResultState.EMPTY) {
            return "当前完整网络范围内没有节点。";
        }
        if (state == ResultState.UNAVAILABLE) {
            return "没有可用于形成 Top-20 并集的指标节点集合；查看正分节点数及各指标状态。";
        }
        if (state == ResultState.LIMITED) {
            if (populationSize <= TOP_K) {
                return "完整节点数不大于 K=20，节点表不具区分力。";
            }
            return "部分指标 Top-20 受限或不可用；节点表只覆盖可用指标集合的并集。";
        }
        return null;
    }

    private static ResultState overallState(
            int populationSize,
            List<SpearmanPair> spearmanPairs,
            List<Top20Comparison> top20Comparisons,
            ResultState nodeEvidenceState) {
        if (populationSize == 0) {
            return ResultState.EMPTY;
        }
        boolean limited = nodeEvidenceState != ResultState.AVAILABLE
                || spearmanPairs.stream()
                        .anyMatch(pair -> pair.state() != ResultState.AVAILABLE)
                || top20Comparisons.stream()
                        .anyMatch(comparison -> comparison.state() != ResultState.AVAILABLE);
        return limited ? ResultState.LIMITED : ResultState.AVAILABLE;
    }

    private static String pairMetricReason(
            String metricA,
            String metricB,
            Map<String, String> unavailableMetricReasons) {
        return combineReasons(
                unavailableMetricReasons.get(metricA),
                unavailableMetricReasons.get(metricB));
    }

    private static String zeroThresholdReason(
            MetricTopData metricA,
            MetricTopData metricB) {
        boolean zeroThreshold = (metricA.threshold() != null && metricA.threshold() == 0.0)
                || (metricB.threshold() != null && metricB.threshold() == 0.0);
        return zeroThreshold
                ? "至少一项指标的第 20 位阈值为 0；不计算该对重合度，改报告正分节点数。"
                : null;
    }

    private static String combineReasons(String reasonA, String reasonB) {
        if (reasonA == null) {
            return reasonB;
        }
        if (reasonB == null || reasonA.equals(reasonB)) {
            return reasonA;
        }
        return reasonA + "；" + reasonB;
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static void ensureUniqueNodeIds(List<NodeMetricValues> nodes) {
        Set<String> nodeIds = new HashSet<>();
        for (NodeMetricValues node : nodes) {
            if (node.nodeId() == null || node.nodeId().isBlank()
                    || !nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException(
                        "cross-metric analysis requires unique non-blank node ids");
            }
        }
    }

    private record MetricValue(String nodeId, NetworkNodeType nodeType, double value) {
    }

    private record MetricTopData(
            String metricKey,
            Double threshold,
            Set<String> nodeIds,
            Integer positiveScoreNodeCount,
            Boolean tieExpanded,
            ResultState state,
            String reason) {
    }

    public enum ResultState {
        AVAILABLE,
        LIMITED,
        UNAVAILABLE,
        EMPTY;

        public String apiValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record NodeMetricValues(
            String nodeId,
            NetworkNodeType nodeType,
            Map<String, Double> values) {

        public NodeMetricValues {
            values = Map.copyOf(values);
        }
    }

    public record Analysis(
            ResultState state,
            String reason,
            int populationSize,
            List<SpearmanPair> spearmanPairs,
            List<Top20Comparison> top20Comparisons,
            ResultState nodeEvidenceState,
            String nodeEvidenceReason,
            List<NodeEvidence> nodeEvidence) {

        public Analysis {
            spearmanPairs = List.copyOf(spearmanPairs);
            top20Comparisons = List.copyOf(top20Comparisons);
            nodeEvidence = List.copyOf(nodeEvidence);
        }
    }

    public record SpearmanPair(
            String metricA,
            String metricB,
            Double rho,
            int sampleSize,
            String tieMethod,
            ResultState state,
            String reason) {
    }

    public record Top20Comparison(
            String metricA,
            String metricB,
            int k,
            Double thresholdA,
            Double thresholdB,
            Integer setSizeA,
            Integer setSizeB,
            Integer intersectionCount,
            Double jaccard,
            Integer positiveScoreNodeCountA,
            Integer positiveScoreNodeCountB,
            Boolean tieExpandedA,
            Boolean tieExpandedB,
            ResultState state,
            String reason) {
    }

    public record NodeEvidence(
            String nodeId,
            NetworkNodeType nodeType,
            List<MetricEvidence> metrics) {

        public NodeEvidence {
            metrics = List.copyOf(metrics);
        }
    }

    public record MetricEvidence(String metricKey, double value, int rank) {
    }
}
