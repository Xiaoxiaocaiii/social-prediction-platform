package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.CrossMetricAnalysisCalculator;
import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NodeMetricDistributionCalculator;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SocialNetworkEvidenceFactSummaryGenerator {

    private static final List<MetricDefinition> METRIC_DEFINITIONS = List.of(
            new MetricDefinition(
                    "indegree", "入度", "count",
                    "directed_simple_network", "有向简单网络"),
            new MetricDefinition(
                    "outdegree", "出度", "count",
                    "directed_simple_network", "有向简单网络"),
            new MetricDefinition(
                    "pageRank", "PageRank", "score",
                    "directed_simple_network", "有向简单网络"),
            new MetricDefinition(
                    "kCore", "k-core", "count",
                    "undirected_unweighted_simple_projection", "无向无权简单投影"),
            new MetricDefinition(
                    "normalizedBetweenness", "归一化介数中心性", "score",
                    "undirected_unweighted_simple_projection", "无向无权简单投影"));
    private static final List<String> LIMITATIONS = List.of(
            "当前关注关系及其网络位置不能单独证明真实互动、信息传播、议程设置、现实影响力、话语权、资源分配或因果关系。",
            "入度、出度、PageRank、k-core 与归一化介数衡量不同网络结构面向；本摘要不合成为综合分，也不据此判断账号或机构的社会重要性。",
            "当前分析没有经批准的地区或机构分类依据，不作相关群体比较。",
            "本摘要不计算未经批准的集中度指标、显著性检验或因果效应；结果只描述所标记的数据快照与计算范围。");

    public Summary generate(SocialNetworkWorkspaceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SocialNetworkGraphResult graph = snapshot.network();
        NetworkMetrics networkMetrics = graph.metrics();
        StructuralRoleAnalysis structuralRoles = graph.structuralRoles();
        int populationSize = graph.nodes().size();
        Map<String, Map<String, Double>> valuesByNode = valuesByNode(graph);
        Map<String, String> unavailableMetricReasons = unavailableMetricReasons(
                graph.pageRank(), populationSize);
        List<CrossMetricAnalysisCalculator.NodeMetricValues> crossMetricNodes =
                graph.nodes().stream()
                        .map(node -> new CrossMetricAnalysisCalculator.NodeMetricValues(
                                node.id(),
                                node.nodeType(),
                                valuesByNode.getOrDefault(node.id(), Map.of())))
                        .toList();
        CrossMetricAnalysisCalculator.Analysis crossMetric =
                new CrossMetricAnalysisCalculator().analyze(
                        crossMetricNodes, unavailableMetricReasons);

        List<Statement> facts = new ArrayList<>();
        List<Statement> interpretations = new ArrayList<>();
        facts.add(networkFact(snapshot, networkMetrics));
        facts.add(connectivityFact(snapshot, networkMetrics));
        facts.add(coreFact(snapshot, structuralRoles));
        for (MetricDefinition definition : METRIC_DEFINITIONS) {
            Statement distribution = metricDistributionFact(
                    snapshot,
                    definition,
                    valuesByNode,
                    populationSize,
                    graph.pageRank());
            facts.add(distribution);
            interpretations.add(metricInterpretation(definition, distribution));
        }
        for (CrossMetricAnalysisCalculator.SpearmanPair pair
                : crossMetric.spearmanPairs()) {
            facts.add(spearmanFact(snapshot, pair));
            interpretations.add(spearmanInterpretation(pair));
        }
        for (CrossMetricAnalysisCalculator.Top20Comparison comparison
                : crossMetric.top20Comparisons()) {
            facts.add(top20Fact(snapshot, comparison, crossMetric.nodeEvidence()));
            interpretations.add(top20Interpretation(comparison));
        }
        interpretations.add(connectivityInterpretation(networkMetrics));
        interpretations.add(coreInterpretation(structuralRoles));

        String state = summaryState(populationSize, facts);
        return new Summary(
                SocialNetworkNodeEvidenceAssembler.snapshotIdentity(snapshot),
                state,
                facts,
                interpretations,
                LIMITATIONS);
    }

    private static Statement networkFact(
            SocialNetworkWorkspaceSnapshot snapshot,
            NetworkMetrics metrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nodeCount", metrics.nodeCount());
        values.put("edgeCount", metrics.edgeCount());
        values.put("profileUserCount", metrics.profileUserCount());
        values.put("externalNodeCount", metrics.externalNodeCount());
        values.put("isolatedNodeCount", metrics.isolatedNodeCount());
        values.put("scope", "full_normalized_network");
        values.put("metricVersion", metrics.metricVersion());
        values.put("pageRankVersion", metrics.pagerankVersion());
        boolean empty = metrics.nodeCount() == 0;
        String text = empty
                ? "当前完整网络节点总体为空，无法对网络规模作实质判断。"
                : "数据集 " + snapshot.dataset().datasetId() + "（版本 "
                        + snapshot.dataset().datasetVersion() + "）的完整网络包含 "
                        + metrics.nodeCount() + " 个节点和 " + metrics.edgeCount()
                        + " 条有效有向关系；其中资料节点 "
                        + metrics.profileUserCount() + " 个、外部节点 "
                        + metrics.externalNodeCount() + " 个。";
        return statement(
                "network-scope",
                "fact",
                empty ? "empty" : "available",
                empty ? "当前完整网络没有节点。" : null,
                text,
                List.of(networkSummaryRef(snapshot), nodeEvidenceRef(snapshot)),
                values);
    }

    private static Statement connectivityFact(
            SocialNetworkWorkspaceSnapshot snapshot,
            NetworkMetrics metrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nodeCount", metrics.nodeCount());
        values.put("isolatedNodeCount", metrics.isolatedNodeCount());
        values.put("weaklyConnectedComponentCount",
                metrics.weaklyConnectedComponentCount());
        values.put("largestWeaklyConnectedComponentNodeCount",
                metrics.largestWeaklyConnectedComponentNodeCount());
        values.put("largestWeaklyConnectedComponentRatio",
                metrics.largestWeaklyConnectedComponentRatio());
        values.put("scope", "full_normalized_network");
        boolean empty = metrics.nodeCount() == 0;
        String text = empty
                ? "当前节点总体为空，连通结构无法判断。"
                : "完整网络有 " + metrics.weaklyConnectedComponentCount()
                        + " 个弱连通分量；最大分量包含 "
                        + metrics.largestWeaklyConnectedComponentNodeCount()
                        + "/" + metrics.nodeCount() + " 个节点（占比 "
                        + number(metrics.largestWeaklyConnectedComponentRatio())
                        + "），孤立节点数为 " + metrics.isolatedNodeCount() + "。";
        return statement(
                "connectivity",
                "fact",
                empty ? "empty" : "available",
                empty ? "当前节点总体为空，连通指标不可用。" : null,
                text,
                List.of(networkSummaryRef(snapshot), nodeEvidenceRef(snapshot)),
                values);
    }

    private static Statement coreFact(
            SocialNetworkWorkspaceSnapshot snapshot,
            StructuralRoleAnalysis analysis) {
        StructuralRoleAnalysis.KCoreDistribution distribution =
                analysis.kCoreDistribution();
        String state = distribution.sampleSize() == 0 ? "empty" : "available";
        String reason = distribution.sampleSize() == 0
                ? "当前完整网络范围内没有可供分解的节点。"
                : null;
        List<Map<String, Object>> shells = distribution.shells().stream()
                .map(shell -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("coreNumber", shell.coreNumber());
                    item.put("exactCount", shell.exactCount());
                    item.put("exactRatio", shell.exactRatio());
                    item.put("cumulativeCount", shell.cumulativeCount());
                    item.put("cumulativeRatio", shell.cumulativeRatio());
                    item.put("nodeIds",
                            shell.nodes().stream()
                                    .map(StructuralRoleAnalysis.KCoreNode::nodeId)
                                    .toList());
                    return item;
                })
                .toList();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sampleSize", distribution.sampleSize());
        values.put("maxCoreNumber", distribution.maxCoreNumber());
        values.put("highestCoreCount", distribution.highestCoreCount());
        values.put("shellCount", distribution.shells().size());
        values.put("shells", shells);
        values.put("projection", "undirected_unweighted_simple_projection");
        values.put("algorithmVersion", analysis.algorithmVersion());
        String text = distribution.sampleSize() == 0
                ? "当前数据无法判断 k-core 层级分布。"
                : "在 " + distribution.sampleSize()
                        + " 个全网节点的无向无权简单投影上，最高 core number 为 "
                        + distribution.maxCoreNumber() + "，该最高层有 "
                        + distribution.highestCoreCount()
                        + " 个节点；摘要同时保留各层精确数量和累计数量。";
        return statement(
                "k-core-distribution",
                "fact",
                state,
                reason,
                text,
                List.of(
                        structureRolesRef(snapshot, "kCoreDistribution"),
                        nodeEvidenceRef(snapshot)),
                values);
    }

    private static Statement metricDistributionFact(
            SocialNetworkWorkspaceSnapshot snapshot,
            MetricDefinition definition,
            Map<String, Map<String, Double>> valuesByNode,
            int populationSize,
            PageRankResult pageRank) {
        List<Double> values = valuesByNode.values().stream()
                .map(nodeValues -> nodeValues.get(definition.key()))
                .filter(value -> value != null && Double.isFinite(value))
                .sorted()
                .toList();
        int sampleSize = values.size();
        int invalidValueCount = populationSize - sampleSize;
        NodeMetricDistributionCalculator.Summary distribution =
                new NodeMetricDistributionCalculator().summarize(values);
        String state;
        String reason = null;
        if (populationSize == 0) {
            state = "empty";
            reason = "当前完整网络范围内没有节点。";
        } else if ("normalizedBetweenness".equals(definition.key())
                && populationSize < 3) {
            state = "unavailable";
            reason = "节点数不足 3，无法计算归一化介数中心性。";
        } else if (sampleSize == 0) {
            state = "unavailable";
            reason = "没有可用于该指标完整总体摘要的有效值。";
        } else if (invalidValueCount > 0) {
            state = "limited";
            reason = "只有 " + sampleSize + "/" + populationSize
                    + " 个节点具有有限指标值；摘要不代表完整总体。";
        } else if ("pageRank".equals(definition.key()) && !pageRank.converged()) {
            state = "limited";
            reason = "PageRank 未收敛；以下分布仅作诊断，不作为正式排序证据。";
        } else {
            state = "available";
        }

        Map<String, Object> measuredValues = new LinkedHashMap<>();
        measuredValues.put("metricKey", definition.key());
        measuredValues.put("unit", definition.unit());
        measuredValues.put("projection", definition.projection());
        measuredValues.put("algorithmVersion", metricAlgorithmVersion(
                snapshot.network().metrics(), snapshot.network().structuralRoles(), definition));
        measuredValues.put("populationSize", populationSize);
        measuredValues.put("sampleSize", sampleSize);
        measuredValues.put("invalidValueCount", invalidValueCount);
        measuredValues.put("zeroCount", distribution.zeroCount());
        measuredValues.put("zeroRatio", distribution.zeroRatio());
        measuredValues.put("min", distribution.min());
        measuredValues.put("mean", distribution.mean());
        measuredValues.put("median", distribution.median());
        measuredValues.put("p90", distribution.p90());
        measuredValues.put("p95", distribution.p95());
        measuredValues.put("max", distribution.max());
        if ("pageRank".equals(definition.key())) {
            measuredValues.put("converged", pageRank.converged());
            measuredValues.put("iterations", pageRank.iterations());
            measuredValues.put("finalDelta", pageRank.finalDelta());
            measuredValues.put("sum", pageRank.sum());
        }
        String text = state.equals("available")
                ? definition.label() + "分布（n=" + sampleSize + "/" + populationSize
                        + "）：零值 " + distribution.zeroCount()
                        + "（占比 " + number(distribution.zeroRatio())
                        + "），最小值 " + number(distribution.min())
                        + "，均值 " + number(distribution.mean())
                        + "，中位数 " + number(distribution.median())
                        + "，P90 " + number(distribution.p90())
                        + "，P95 " + number(distribution.p95())
                        + "，最大值 " + number(distribution.max()) + "。"
                : definition.label() + "分布状态为 " + state + "（n="
                        + sampleSize + "/" + populationSize + "）；"
                        + (reason == null ? "当前数据无法判断。" : reason);
        return statement(
                "distribution-" + definition.key(),
                "fact",
                state,
                reason,
                text,
                List.of(
                        structureRolesRef(snapshot, "metricDistributions/"
                                + definition.key()),
                        nodeEvidenceRef(snapshot)),
                measuredValues);
    }

    private static Statement spearmanFact(
            SocialNetworkWorkspaceSnapshot snapshot,
            CrossMetricAnalysisCalculator.SpearmanPair pair) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("metricA", pair.metricA());
        values.put("metricB", pair.metricB());
        values.put("rho", pair.rho());
        values.put("sampleSize", pair.sampleSize());
        values.put("tieMethod", pair.tieMethod());
        String text = "available".equals(pair.state().apiValue())
                ? pair.metricA() + " 与 " + pair.metricB()
                        + " 的 Spearman 排名相关系数为 "
                        + number(pair.rho()) + "（有效 n="
                        + pair.sampleSize() + "，并列秩方法="
                        + pair.tieMethod() + "）。"
                : pair.metricA() + " 与 " + pair.metricB()
                        + " 的 Spearman 结果状态为 "
                        + pair.state().apiValue()
                        + "；当前数据无法判断该相关系数。原因："
                        + nonBlank(pair.reason(), "指标对不可用。");
        return statement(
                "spearman-" + pair.metricA() + "-" + pair.metricB(),
                "fact",
                pair.state().apiValue(),
                pair.reason(),
                text,
                List.of(
                        crossMetricRef(snapshot, "spearmanPairs/"
                                + pair.metricA() + "-" + pair.metricB()),
                        nodeEvidenceRef(snapshot)),
                values);
    }

    private static Statement top20Fact(
            SocialNetworkWorkspaceSnapshot snapshot,
            CrossMetricAnalysisCalculator.Top20Comparison comparison,
            List<CrossMetricAnalysisCalculator.NodeEvidence> nodeEvidence) {
        Top20NodeEvidence selectedNodes = top20EvidenceNodeIds(comparison, nodeEvidence);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("metricA", comparison.metricA());
        values.put("metricB", comparison.metricB());
        values.put("k", comparison.k());
        values.put("thresholdA", comparison.thresholdA());
        values.put("thresholdB", comparison.thresholdB());
        values.put("setSizeA", comparison.setSizeA());
        values.put("setSizeB", comparison.setSizeB());
        values.put("intersectionCount", comparison.intersectionCount());
        values.put("jaccard", comparison.jaccard());
        values.put("positiveScoreNodeCountA", comparison.positiveScoreNodeCountA());
        values.put("positiveScoreNodeCountB", comparison.positiveScoreNodeCountB());
        values.put("tieExpandedA", comparison.tieExpandedA());
        values.put("tieExpandedB", comparison.tieExpandedB());
        values.put("setNodeIdsA", selectedNodes.metricA());
        values.put("setNodeIdsB", selectedNodes.metricB());
        values.put("intersectionNodeIds", selectedNodes.intersection());
        boolean available = comparison.state()
                == CrossMetricAnalysisCalculator.ResultState.AVAILABLE;
        String text = available
                ? "K=" + comparison.k() + "（边界并列扩展）下，"
                        + comparison.metricA() + " 集合大小为 "
                        + comparison.setSizeA() + "，"
                        + comparison.metricB() + " 集合大小为 "
                        + comparison.setSizeB() + "，交集 "
                        + comparison.intersectionCount() + " 个节点，Jaccard="
                        + number(comparison.jaccard()) + "。"
                : comparison.metricA() + " 与 " + comparison.metricB()
                        + " 的 K=" + comparison.k()
                        + " 重合结果状态为 " + comparison.state().apiValue()
                        + "；当前数据无法据此判断高位集合重合。原因："
                        + nonBlank(comparison.reason(), "集合对不可用。");
        return statement(
                "top20-" + comparison.metricA() + "-" + comparison.metricB(),
                "fact",
                comparison.state().apiValue(),
                comparison.reason(),
                text,
                List.of(
                        crossMetricRef(snapshot, "top20Comparisons/"
                                + comparison.metricA() + "-" + comparison.metricB()),
                        nodeEvidenceRef(snapshot)),
                values);
    }

    private static Statement metricInterpretation(
            MetricDefinition definition, Statement distribution) {
        boolean available = "available".equals(distribution.state());
        String text = available
                ? definition.label() + "描述的是 "
                        + definition.projectionLabel()
                        + " 上的结构位置；该指标本身不代表现实影响力或社会属性。"
                : "当前数据无法据 " + definition.label() + " 作正式结构解释；"
                        + nonBlank(distribution.reason(), "该指标当前不可用。");
        return statement(
                "interpretation-" + definition.key(),
                "network-interpretation",
                distribution.state(),
                distribution.reason(),
                text,
                List.of(
                        "EA04.metricProjections." + definition.key(),
                        distribution.evidenceReferences().get(0)),
                Map.of(
                        "metricKey", definition.key(),
                        "projection", definition.projection(),
                        "state", distribution.state()));
    }

    private static Statement connectivityInterpretation(NetworkMetrics metrics) {
        boolean empty = metrics.nodeCount() == 0;
        String text = empty
                ? "当前数据无法判断网络连通现象。"
                : "弱连通关系忽略边方向，只表示两个节点间存在无向路径；不能据此认为节点之间有直接关系或存在有向可达路径。";
        return statement(
                "interpretation-connectivity",
                "network-interpretation",
                empty ? "empty" : "available",
                empty ? "当前完整网络没有节点。" : null,
                text,
                List.of("EA04.networkScope.weaklyConnectedComponents"),
                Map.of(
                        "weaklyConnectedComponentCount",
                        metrics.weaklyConnectedComponentCount()));
    }

    private static Statement coreInterpretation(StructuralRoleAnalysis analysis) {
        boolean empty = analysis.nodeCount() == 0;
        String text = empty
                ? "当前数据无法判断核心层结构现象。"
                : "k-core 层级描述节点在无向无权简单投影中的递归邻接结构，不等同于机构层级、资源控制或社会重要性。";
        return statement(
                "interpretation-k-core",
                "network-interpretation",
                empty ? "empty" : "available",
                empty ? "当前完整网络没有节点。" : null,
                text,
                List.of("EA04.metricProjections.kCore"),
                Map.of("nodeCount", analysis.nodeCount()));
    }

    private static Statement spearmanInterpretation(
            CrossMetricAnalysisCalculator.SpearmanPair pair) {
        boolean available = pair.state()
                == CrossMetricAnalysisCalculator.ResultState.AVAILABLE;
        String text = available
                ? "该 rho 仅描述当前快照有效样本中的 " + pair.metricA()
                        + " 与 " + pair.metricB()
                        + " 排名关联，不证明因果关系、独立验证或两项指标对应同一社会属性。"
                : "当前数据无法判断 " + pair.metricA() + " 与 "
                        + pair.metricB() + " 的排名关联；"
                        + nonBlank(pair.reason(), "该指标对不可用。");
        return statement(
                "interpretation-spearman-" + pair.metricA() + "-" + pair.metricB(),
                "network-interpretation",
                pair.state().apiValue(),
                pair.reason(),
                text,
                List.of("EA07.spearman.averageRank"),
                Map.of(
                        "metricA", pair.metricA(),
                        "metricB", pair.metricB(),
                        "sampleSize", pair.sampleSize()));
    }

    private static Statement top20Interpretation(
            CrossMetricAnalysisCalculator.Top20Comparison comparison) {
        boolean available = comparison.state()
                == CrossMetricAnalysisCalculator.ResultState.AVAILABLE;
        String text = available
                ? "该 Jaccard 值只描述 EA07 并列扩展规则下两个 K=20 高位集合的重合，不构成综合分，也不表示指标等价。"
                : "当前数据无法判断这两个 K=20 高位集合的重合；"
                        + nonBlank(comparison.reason(), "该集合对不可用。");
        return statement(
                "interpretation-top20-" + comparison.metricA() + "-" + comparison.metricB(),
                "network-interpretation",
                comparison.state().apiValue(),
                comparison.reason(),
                text,
                List.of("EA07.topK20.tieExpanded"),
                Map.of(
                        "metricA", comparison.metricA(),
                        "metricB", comparison.metricB(),
                        "k", comparison.k()));
    }

    private static Map<String, Map<String, Double>> valuesByNode(
            SocialNetworkGraphResult graph) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        for (NetworkNode node : graph.nodes()) {
            Map<String, Double> nodeValues = new LinkedHashMap<>();
            nodeValues.put("indegree", (double) node.inDegree());
            nodeValues.put("outdegree", (double) node.outDegree());
            nodeValues.put("pageRank", node.pageRank());
            values.put(node.id(), nodeValues);
        }
        for (StructuralRoleAnalysis.KCoreEntry entry
                : graph.structuralRoles().kCoreRanking()) {
            values.computeIfAbsent(entry.nodeId(), ignored -> new LinkedHashMap<>())
                    .put("kCore", (double) entry.coreNumber());
        }
        for (StructuralRoleAnalysis.BetweennessEntry entry
                : graph.structuralRoles().betweennessRanking()) {
            if (entry.normalizedBetweenness() != null) {
                values.computeIfAbsent(entry.nodeId(), ignored -> new LinkedHashMap<>())
                        .put("normalizedBetweenness", entry.normalizedBetweenness());
            }
        }
        return values;
    }

    private static Map<String, String> unavailableMetricReasons(
            PageRankResult pageRank, int populationSize) {
        Map<String, String> reasons = new LinkedHashMap<>();
        if (!pageRank.converged()) {
            reasons.put("pageRank", "PageRank 未收敛；该指标不参与正式跨指标互核。");
        }
        if (populationSize < 3) {
            reasons.put("normalizedBetweenness",
                    "节点数不足 3，归一化介数中心性不可用。");
        }
        return reasons;
    }

    private static Top20NodeEvidence top20EvidenceNodeIds(
            CrossMetricAnalysisCalculator.Top20Comparison comparison,
            List<CrossMetricAnalysisCalculator.NodeEvidence> nodeEvidence) {
        List<String> idsA = comparison.setSizeA() == null
                        || (comparison.thresholdA() != null
                                && comparison.thresholdA() <= 0.0)
                ? List.of()
                : nodeEvidence.stream()
                        .filter(node -> node.metrics().stream().anyMatch(metric ->
                                comparison.metricA().equals(metric.metricKey())
                                        && metric.rank() <= comparison.k()))
                        .map(CrossMetricAnalysisCalculator.NodeEvidence::nodeId)
                        .sorted()
                        .toList();
        List<String> idsB = comparison.setSizeB() == null
                        || (comparison.thresholdB() != null
                                && comparison.thresholdB() <= 0.0)
                ? List.of()
                : nodeEvidence.stream()
                        .filter(node -> node.metrics().stream().anyMatch(metric ->
                                comparison.metricB().equals(metric.metricKey())
                                        && metric.rank() <= comparison.k()))
                        .map(CrossMetricAnalysisCalculator.NodeEvidence::nodeId)
                        .sorted()
                        .toList();
        List<String> intersection = idsA.stream().filter(idsB::contains).toList();
        return new Top20NodeEvidence(idsA, idsB, intersection);
    }

    private static String metricAlgorithmVersion(
            NetworkMetrics networkMetrics,
            StructuralRoleAnalysis structuralRoles,
            MetricDefinition definition) {
        return switch (definition.key()) {
            case "pageRank" -> networkMetrics.pagerankVersion();
            case "kCore", "normalizedBetweenness" ->
                    structuralRoles.algorithmVersion();
            default -> networkMetrics.metricVersion();
        };
    }

    private static String summaryState(int populationSize, List<Statement> facts) {
        if (populationSize == 0) {
            return "empty";
        }
        return facts.stream().allMatch(fact -> "available".equals(fact.state()))
                ? "available"
                : "limited";
    }

    private static String networkSummaryRef(SocialNetworkWorkspaceSnapshot snapshot) {
        return "GET /api/social-network/datasets/" + snapshot.dataset().datasetId()
                + "/network/summary#/data";
    }

    private static String structureRolesRef(
            SocialNetworkWorkspaceSnapshot snapshot, String fieldPath) {
        return "GET /api/social-network/datasets/" + snapshot.dataset().datasetId()
                + "/network/structure-roles#/data/" + fieldPath;
    }

    private static String crossMetricRef(
            SocialNetworkWorkspaceSnapshot snapshot, String fieldPath) {
        return structureRolesRef(snapshot, "crossMetric/" + fieldPath);
    }

    private static String nodeEvidenceRef(SocialNetworkWorkspaceSnapshot snapshot) {
        return "GET /api/social-network/datasets/" + snapshot.dataset().datasetId()
                + "/network/evidence/nodes#/data/items";
    }

    private static Statement statement(
            String id,
            String category,
            String state,
            String reason,
            String text,
            List<String> evidenceReferences,
            Map<String, Object> evidenceValues) {
        return new Statement(
                id,
                category,
                state,
                reason,
                text,
                evidenceReferences,
                evidenceValues);
    }

    private static String number(Number value) {
        return value == null ? "不可用" : value.toString();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(key, freeze(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SocialNetworkEvidenceFactSummaryGenerator::freeze).toList();
        }
        return value;
    }

    private record MetricDefinition(
            String key,
            String label,
            String unit,
            String projection,
            String projectionLabel) {
    }

    private record Top20NodeEvidence(
            List<String> metricA, List<String> metricB, List<String> intersection) {
    }

    public record Summary(
            Map<String, Object> analysisSnapshot,
            String state,
            List<Statement> facts,
            List<Statement> networkInterpretations,
            List<String> cannotConclude) {

        public Summary {
            @SuppressWarnings("unchecked")
            Map<String, Object> frozenSnapshot =
                    (Map<String, Object>) freeze(analysisSnapshot);
            analysisSnapshot = frozenSnapshot;
            facts = List.copyOf(facts);
            networkInterpretations = List.copyOf(networkInterpretations);
            cannotConclude = List.copyOf(cannotConclude);
        }
    }

    public record Statement(
            String id,
            String category,
            String state,
            String reason,
            String text,
            List<String> evidenceReferences,
            Map<String, Object> evidenceValues) {

        public Statement {
            evidenceReferences = List.copyOf(evidenceReferences);
            @SuppressWarnings("unchecked")
            Map<String, Object> frozenValues =
                    (Map<String, Object>) freeze(evidenceValues);
            evidenceValues = frozenValues;
        }
    }
}
