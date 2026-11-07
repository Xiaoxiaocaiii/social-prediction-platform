package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SocialNetworkNodeEvidenceAssembler {

    private static final List<String> METRIC_ORDER = List.of(
            "indegree", "outdegree", "pageRank", "kCore", "normalizedBetweenness");

    public Map<String, Object> assemble(SocialNetworkWorkspaceSnapshot snapshot) {
        List<NetworkNode> nodes = snapshot.network().nodes().stream()
                .sorted(Comparator.comparing(NetworkNode::id))
                .toList();
        StructuralRoleAnalysis structuralRoles = snapshot.network().structuralRoles();
        PageRankResult pageRank = snapshot.network().pageRank();
        NetworkMetrics networkMetrics = snapshot.network().metrics();
        Map<String, NormalizedUser> profilesById = new LinkedHashMap<>();
        snapshot.users().stream()
                .filter(user -> user.userId() != null && !user.userId().isBlank())
                .forEach(user -> profilesById.putIfAbsent(user.userId(), user));

        Map<String, Double> kCoreValues = new LinkedHashMap<>();
        structuralRoles.kCoreRanking().forEach(entry ->
                kCoreValues.put(entry.nodeId(), (double) entry.coreNumber()));
        Map<String, Double> betweennessValues = new LinkedHashMap<>();
        structuralRoles.betweennessRanking().stream()
                .filter(entry -> entry.normalizedBetweenness() != null)
                .forEach(entry -> betweennessValues.put(
                        entry.nodeId(), entry.normalizedBetweenness()));

        Map<String, Map<String, Double>> metricValues = metricValues(
                nodes, kCoreValues, betweennessValues);
        Map<String, Map<String, Integer>> ranks = new LinkedHashMap<>();
        for (String metricKey : METRIC_ORDER) {
            ranks.put(metricKey, competitionRanks(metricValues.get(metricKey)));
        }

        int profileAvailableCount = 0;
        List<Map<String, Object>> items = new ArrayList<>(nodes.size());
        for (NetworkNode node : nodes) {
            NormalizedUser profile = node.nodeType() == NetworkNodeType.PROFILE
                    ? profilesById.get(node.id())
                    : null;
            String profileState = profile == null ? "unavailable" : "available";
            if (profile != null) {
                profileAvailableCount++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", node.id());
            item.put("nodeType", node.nodeType().value());
            item.put("profileState", profileState);
            item.put("profileReason", profile == null
                    ? node.nodeType() == NetworkNodeType.EXTERNAL
                            ? "外部节点没有对应的资料记录。"
                            : "当前数据集中没有该节点的资料记录。"
                    : null);
            item.put("profile", profileFields(profile));
            item.put("metrics", metricEvidence(
                    node,
                    metricValues,
                    ranks,
                    pageRank.converged(),
                    nodes.size(),
                    networkMetrics,
                    structuralRoles.algorithmVersion()));
            items.add(item);
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("state", nodes.isEmpty() ? "empty" : "available");
        evidence.put("reason", nodes.isEmpty()
                ? "当前完整网络范围内没有节点。"
                : null);
        evidence.put("scope",
                "完整规范化网络节点总体；包含孤立资料节点及有效关系涉及的外部节点。");
        evidence.put("metricOrder", METRIC_ORDER);
        evidence.put("sampleSize", nodes.size());
        evidence.put("returnedNodeCount", items.size());
        evidence.put("profileAvailableCount", profileAvailableCount);
        evidence.put("profileUnavailableCount", nodes.size() - profileAvailableCount);
        evidence.put("complete", true);
        evidence.put("truncated", false);
        evidence.put("analysisSnapshot", snapshotIdentity(snapshot));
        evidence.put("items", items);
        return evidence;
    }

    public static Map<String, Object> snapshotIdentity(
            SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("normalizationVersion", snapshot.dataset().normalizationVersion());
        versions.put("networkMetricVersion",
                snapshot.network().metrics().metricVersion());
        versions.put("pageRankVersion", snapshot.network().metrics().pagerankVersion());
        versions.put("structuralRoleAlgorithmVersion",
                snapshot.network().structuralRoles().algorithmVersion());

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("datasetId", snapshot.dataset().datasetId());
        identity.put("datasetVersion", snapshot.dataset().datasetVersion());
        identity.put("snapshotCalculatedAt",
                snapshot.network().calculatedAt().toString());
        identity.put("versions", versions);
        return identity;
    }

    private static Map<String, Map<String, Double>> metricValues(
            List<NetworkNode> nodes,
            Map<String, Double> kCoreValues,
            Map<String, Double> betweennessValues) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        METRIC_ORDER.forEach(key -> values.put(key, new LinkedHashMap<>()));
        for (NetworkNode node : nodes) {
            values.get("indegree").put(node.id(), (double) node.inDegree());
            values.get("outdegree").put(node.id(), (double) node.outDegree());
            values.get("pageRank").put(node.id(), node.pageRank());
            if (kCoreValues.containsKey(node.id())) {
                values.get("kCore").put(node.id(), kCoreValues.get(node.id()));
            }
            if (betweennessValues.containsKey(node.id())) {
                values.get("normalizedBetweenness")
                        .put(node.id(), betweennessValues.get(node.id()));
            }
        }
        return values;
    }

    private static List<Map<String, Object>> metricEvidence(
            NetworkNode node,
            Map<String, Map<String, Double>> metricValues,
            Map<String, Map<String, Integer>> ranks,
            boolean pageRankConverged,
            int populationSize,
            NetworkMetrics networkMetrics,
            String structuralRoleVersion) {
        List<Map<String, Object>> metrics = new ArrayList<>(METRIC_ORDER.size());
        addMetricEvidence(metrics, node.id(), "indegree", metricValues, ranks,
                "count", "directed_simple_network", networkMetrics.metricVersion(),
                true, null);
        addMetricEvidence(metrics, node.id(), "outdegree", metricValues, ranks,
                "count", "directed_simple_network", networkMetrics.metricVersion(),
                true, null);
        addMetricEvidence(metrics, node.id(), "pageRank", metricValues, ranks,
                "score", "directed_simple_network", networkMetrics.pagerankVersion(),
                pageRankConverged,
                pageRankConverged
                        ? null
                        : "PageRank 未收敛；数值仅作诊断，不作为正式排序证据。");
        addMetricEvidence(metrics, node.id(), "kCore", metricValues, ranks,
                "count", "undirected_unweighted_simple_projection", structuralRoleVersion,
                true, null);
        boolean betweennessAvailable = populationSize >= 3;
        addMetricEvidence(metrics, node.id(), "normalizedBetweenness", metricValues, ranks,
                "score", "undirected_unweighted_simple_projection", structuralRoleVersion,
                betweennessAvailable,
                betweennessAvailable
                        ? null
                        : "节点数不足 3，无法计算归一化介数中心性。");
        return metrics;
    }

    private static void addMetricEvidence(
            List<Map<String, Object>> metrics,
            String nodeId,
            String metricKey,
            Map<String, Map<String, Double>> metricValues,
            Map<String, Map<String, Integer>> ranks,
            String unit,
            String projection,
            String algorithmVersion,
            boolean available,
            String unavailableReason) {
        Double value = metricValues.get(metricKey).get(nodeId);
        boolean valid = value != null && Double.isFinite(value);
        String state = "pageRank".equals(metricKey) && !available
                ? "limited"
                : valid && available ? "available" : "unavailable";
        String reason = state.equals("available")
                ? null
                : unavailableReason != null
                        ? unavailableReason
                        : "该节点没有可用于完整证据表的有限指标值。";

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("metricKey", metricKey);
        item.put("value", valid ? value : null);
        item.put("rank", available && valid ? ranks.get(metricKey).get(nodeId) : null);
        item.put("state", state);
        item.put("reason", reason);
        item.put("unit", unit);
        item.put("projection", projection);
        item.put("algorithmVersion", algorithmVersion);
        metrics.add(item);
    }

    private static Map<String, Integer> competitionRanks(Map<String, Double> values) {
        List<Map.Entry<String, Double>> ranked = values.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.<String, Double>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        Map<String, Integer> ranks = new LinkedHashMap<>();
        double previousValue = Double.NaN;
        int currentRank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            Map.Entry<String, Double> entry = ranked.get(index);
            if (index == 0 || Double.compare(entry.getValue(), previousValue) != 0) {
                currentRank = index + 1;
                previousValue = entry.getValue();
            }
            ranks.put(entry.getKey(), currentRank);
        }
        return ranks;
    }

    private static Map<String, Object> profileFields(NormalizedUser profile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", profileField(profile, "username"));
        fields.put("displayName", profileField(profile, "display_name"));
        return fields;
    }

    private static String profileField(NormalizedUser profile, String key) {
        if (profile == null) {
            return "";
        }
        String value = profile.fields().get(key);
        return value == null ? "" : value;
    }
}
