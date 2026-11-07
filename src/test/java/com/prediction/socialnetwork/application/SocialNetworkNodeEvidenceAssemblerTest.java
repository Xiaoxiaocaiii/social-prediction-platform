package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SocialNetworkNodeEvidenceAssemblerTest {

    private final SocialNetworkNodeEvidenceAssembler assembler =
            new SocialNetworkNodeEvidenceAssembler();

    @Test
    void assemblesStableCompleteNodeEvidenceWithLimitedAndUnavailableMetricStates() {
        SocialNetworkWorkspaceSnapshot snapshot = snapshot(
                List.of(
                        new NetworkNode("z-profile", NetworkNodeType.PROFILE, 2, 1, 0.4, false),
                        new NetworkNode("a-external", NetworkNodeType.EXTERNAL, 2, 1, 0.4, false),
                        new NetworkNode("m-missing", NetworkNodeType.PROFILE, 2, 1, 0.4, false)),
                new PageRankResult(
                        Map.of("z-profile", 0.4, "a-external", 0.4, "m-missing", 0.4),
                        false,
                        100,
                        0.01,
                        1.2),
                new StructuralRoleAnalysis(
                        3,
                        1,
                        "node-roles-test",
                        List.of(
                                new StructuralRoleAnalysis.KCoreEntry(
                                        "z-profile", NetworkNodeType.PROFILE, 0),
                                new StructuralRoleAnalysis.KCoreEntry(
                                        "a-external", NetworkNodeType.EXTERNAL, 0),
                                new StructuralRoleAnalysis.KCoreEntry(
                                        "m-missing", NetworkNodeType.PROFILE, 0)),
                        List.of(
                                new StructuralRoleAnalysis.BetweennessEntry(
                                        "z-profile", NetworkNodeType.PROFILE, 0, 0.0),
                                new StructuralRoleAnalysis.BetweennessEntry(
                                        "a-external", NetworkNodeType.EXTERNAL, 0, 0.0),
                                new StructuralRoleAnalysis.BetweennessEntry(
                                        "m-missing", NetworkNodeType.PROFILE, 0, 0.0))),
                List.of(user("z-profile", "known-user", "Known User")));

        Map<String, Object> result = assembler.assemble(snapshot);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        assertEquals(List.of("a-external", "m-missing", "z-profile"),
                items.stream().map(item -> item.get("nodeId")).toList());
        assertEquals(3, result.get("sampleSize"));
        assertEquals(3, result.get("returnedNodeCount"));
        assertEquals(true, result.get("complete"));
        assertEquals(false, result.get("truncated"));
        assertEquals(1, result.get("profileAvailableCount"));
        assertEquals(2, result.get("profileUnavailableCount"));
        assertEquals("unavailable", items.get(0).get("profileState"));
        assertTrue(String.valueOf(items.get(0).get("profileReason")).contains("外部节点"));
        assertEquals("unavailable", items.get(1).get("profileState"));
        assertEquals("available", items.get(2).get("profileState"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> externalMetrics =
                (List<Map<String, Object>>) items.get(0).get("metrics");
        Map<String, Object> pageRank = metric(externalMetrics, "pageRank");
        assertEquals("limited", pageRank.get("state"));
        assertEquals(0.4, pageRank.get("value"));
        assertNull(pageRank.get("rank"));
        assertFalse(String.valueOf(pageRank.get("reason")).isBlank());
        assertEquals(1, metric(externalMetrics, "kCore").get("rank"));
        assertEquals(1, metric(externalMetrics, "normalizedBetweenness").get("rank"));

        Map<String, Object> identity =
                (Map<String, Object>) result.get("analysisSnapshot");
        assertEquals("test-dataset", identity.get("datasetId"));
        assertEquals("test-version", identity.get("datasetVersion"));
        assertEquals("2026-10-29T12:00:00Z", identity.get("snapshotCalculatedAt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> versions = (Map<String, Object>) identity.get("versions");
        assertEquals("normalization-test", versions.get("normalizationVersion"));
        assertEquals("network-test", versions.get("networkMetricVersion"));
        assertEquals("pagerank-test", versions.get("pageRankVersion"));
        assertEquals("node-roles-test", versions.get("structuralRoleAlgorithmVersion"));
    }

    @Test
    void marksBetweennessUnavailableWhenPopulationHasFewerThanThreeNodes() {
        SocialNetworkWorkspaceSnapshot snapshot = snapshot(
                List.of(
                        new NetworkNode("profile-a", NetworkNodeType.PROFILE, 1, 0, 0.5, false),
                        new NetworkNode("external-b", NetworkNodeType.EXTERNAL, 0, 1, 0.5, false)),
                new PageRankResult(
                        Map.of("profile-a", 0.5, "external-b", 0.5),
                        true,
                        4,
                        0.0001,
                        1.0),
                new StructuralRoleAnalysis(
                        2,
                        1,
                        "node-roles-test",
                        List.of(
                                new StructuralRoleAnalysis.KCoreEntry(
                                        "profile-a", NetworkNodeType.PROFILE, 1),
                                new StructuralRoleAnalysis.KCoreEntry(
                                        "external-b", NetworkNodeType.EXTERNAL, 1)),
                        List.of()),
                List.of(user("profile-a", "profile-a", "Profile A")));

        Map<String, Object> result = assembler.assemble(snapshot);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics =
                (List<Map<String, Object>>) items.get(0).get("metrics");
        Map<String, Object> betweenness = metric(metrics, "normalizedBetweenness");

        assertEquals("unavailable", betweenness.get("state"));
        assertNull(betweenness.get("value"));
        assertNull(betweenness.get("rank"));
        assertTrue(String.valueOf(betweenness.get("reason")).contains("节点数不足 3"));
    }

    private static Map<String, Object> metric(
            List<Map<String, Object>> metrics, String metricKey) {
        return metrics.stream()
                .filter(item -> metricKey.equals(item.get("metricKey")))
                .findFirst()
                .orElseThrow();
    }

    private static NormalizedUser user(String id, String username, String displayName) {
        return new NormalizedUser(
                "users.csv",
                2,
                id,
                Map.of("username", username, "display_name", displayName),
                NormalizationStatus.NORMALIZED,
                Set.of());
    }

    private static SocialNetworkWorkspaceSnapshot snapshot(
            List<NetworkNode> nodes,
            PageRankResult pageRank,
            StructuralRoleAnalysis structuralRoles,
            List<NormalizedUser> users) {
        NetworkMetrics metrics = new NetworkMetrics(
                nodes.size(),
                1,
                users.size(),
                (int) nodes.stream()
                        .filter(node -> node.nodeType() == NetworkNodeType.EXTERNAL)
                        .count(),
                0,
                1,
                nodes.size(),
                1.0,
                0.0,
                0.0,
                1.0,
                0.5,
                "network-test",
                "pagerank-test",
                true,
                true);
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                nodes,
                List.of(),
                metrics,
                pageRank,
                List.of(),
                new CommunityDetectionResult(
                        List.of(), Map.of(), 0, 0, true, "test", "undirected"),
                structuralRoles,
                Instant.parse("2026-10-29T12:00:00Z"));
        SocialNetworkDataset dataset = new SocialNetworkDataset(
                "test-dataset",
                "Test dataset",
                DatasetType.BUILTIN,
                "test-version",
                DatasetStatus.HEALTHY,
                "test",
                "test",
                null,
                null,
                List.of(),
                null,
                "normalization-test",
                "network-test",
                "text-test",
                "explanation-test");
        return new SocialNetworkWorkspaceSnapshot(
                dataset,
                null,
                null,
                graph,
                null,
                () -> null,
                users,
                List.of(),
                List.of());
    }
}
