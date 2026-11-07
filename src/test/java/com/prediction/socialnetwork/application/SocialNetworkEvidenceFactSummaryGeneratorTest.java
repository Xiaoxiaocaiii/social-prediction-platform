package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import com.prediction.socialnetwork.network.StructuralRoleCalculator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SocialNetworkEvidenceFactSummaryGeneratorTest {

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    private final SocialNetworkEvidenceFactSummaryGenerator generator =
            new SocialNetworkEvidenceFactSummaryGenerator();

    @Test
    void generatesDeterministicFactsWithTraceableValuesFromTheFullSnapshot() {
        SocialNetworkWorkspaceSnapshot snapshot =
                workspace.get("think-tank-social-network");

        SocialNetworkEvidenceFactSummaryGenerator.Summary summary =
                generator.generate(snapshot);
        SocialNetworkEvidenceFactSummaryGenerator.Summary repeated =
                generator.generate(snapshot);

        assertEquals(summary, repeated);
        assertEquals(
                SocialNetworkNodeEvidenceAssembler.snapshotIdentity(snapshot),
                summary.analysisSnapshot());
        assertEquals("available", summary.state());
        assertEquals(28, summary.facts().size());
        assertEquals(27, summary.networkInterpretations().size());
        assertEquals(4, summary.cannotConclude().size());

        SocialNetworkEvidenceFactSummaryGenerator.Statement network =
                fact(summary, "network-scope");
        assertEquals(
                snapshot.network().metrics().nodeCount(),
                network.evidenceValues().get("nodeCount"));
        assertEquals(
                snapshot.network().metrics().edgeCount(),
                network.evidenceValues().get("edgeCount"));
        assertTrue(network.text().contains(
                snapshot.network().metrics().nodeCount() + " 个节点"));
        assertTrue(network.evidenceReferences().get(0).contains("/network/summary"));

        SocialNetworkEvidenceFactSummaryGenerator.Statement core =
                fact(summary, "k-core-distribution");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shells =
                (List<Map<String, Object>>) core.evidenceValues().get("shells");
        assertEquals(
                snapshot.network().structuralRoles().kCoreDistribution().shells().size(),
                shells.size());
        Set<String> shellNodeIds = new HashSet<>();
        shells.forEach(shell -> {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) shell.get("nodeIds");
            shellNodeIds.addAll(ids);
        });
        assertEquals(snapshot.network().nodes().size(), shellNodeIds.size());
        assertTrue(core.evidenceReferences().stream()
                .anyMatch(reference -> reference.contains("/network/evidence/nodes")));

        List<SocialNetworkEvidenceFactSummaryGenerator.Statement> distributionFacts =
                summary.facts().stream()
                        .filter(item -> item.id().startsWith("distribution-"))
                        .toList();
        assertEquals(5, distributionFacts.size());
        distributionFacts.forEach(item -> {
            assertEquals("available", item.state());
            assertEquals(
                    snapshot.network().metrics().nodeCount(),
                    item.evidenceValues().get("sampleSize"));
            assertEquals(0, item.evidenceValues().get("invalidValueCount"));
            assertFalse(item.evidenceReferences().isEmpty());
        });

        List<SocialNetworkEvidenceFactSummaryGenerator.Statement> spearmanFacts =
                summary.facts().stream()
                        .filter(item -> item.id().startsWith("spearman-"))
                        .toList();
        List<SocialNetworkEvidenceFactSummaryGenerator.Statement> top20Facts =
                summary.facts().stream()
                        .filter(item -> item.id().startsWith("top20-"))
                        .toList();
        assertEquals(10, spearmanFacts.size());
        assertEquals(10, top20Facts.size());
        summary.facts().forEach(item -> assertFalse(item.evidenceReferences().isEmpty()));
        top20Facts.forEach(item -> {
            assertTrue(item.evidenceValues().containsKey("setNodeIdsA"));
            assertTrue(item.evidenceValues().containsKey("setNodeIdsB"));
            assertTrue(item.evidenceValues().containsKey("intersectionNodeIds"));
            assertEquals(
                    ((List<?>) item.evidenceValues().get("intersectionNodeIds")).size(),
                    item.evidenceValues().get("intersectionCount"));
            assertTrue(item.evidenceReferences().stream()
                    .anyMatch(reference -> reference.contains("/network/evidence/nodes")));
        });
        assertTrue(summary.cannotConclude().stream()
                .anyMatch(text -> text.contains("不能单独证明真实互动")));
        assertTrue(summary.cannotConclude().stream()
                .anyMatch(text -> text.contains("不作相关群体比较")));
    }

    @Test
    void describesNonConvergenceAndSmallSamplesWithoutInventingResults() {
        SocialNetworkEvidenceFactSummaryGenerator.Summary summary =
                generator.generate(smallSnapshot(false));

        assertEquals("limited", summary.state());
        SocialNetworkEvidenceFactSummaryGenerator.Statement pageRank =
                fact(summary, "distribution-pageRank");
        assertEquals("limited", pageRank.state());
        assertEquals(false, pageRank.evidenceValues().get("converged"));
        assertTrue(pageRank.text().contains("仅作诊断"));

        SocialNetworkEvidenceFactSummaryGenerator.Statement betweenness =
                fact(summary, "distribution-normalizedBetweenness");
        assertEquals("unavailable", betweenness.state());
        assertEquals(0, betweenness.evidenceValues().get("sampleSize"));
        assertTrue(betweenness.reason().contains("节点数不足 3"));

        SocialNetworkEvidenceFactSummaryGenerator.Statement spearman =
                fact(summary, "spearman-indegree-pageRank");
        assertEquals("unavailable", spearman.state());
        assertTrue(spearman.text().contains("当前数据无法判断"));
        assertTrue(spearman.reason().contains("PageRank 未收敛"));

        SocialNetworkEvidenceFactSummaryGenerator.Statement top20 =
                fact(summary, "top20-indegree-outdegree");
        assertEquals("limited", top20.state());
        assertEquals(null, top20.evidenceValues().get("jaccard"));
        assertEquals(
                top20.evidenceValues().get("setSizeA"),
                ((List<?>) top20.evidenceValues().get("setNodeIdsA")).size());
        assertEquals(
                top20.evidenceValues().get("setSizeB"),
                ((List<?>) top20.evidenceValues().get("setNodeIdsB")).size());
        assertTrue(top20.text().contains("当前数据无法据此判断"));
        assertFalse(top20.text().contains("交集 0 个节点"));
        assertEquals(
                "unavailable",
                summary.networkInterpretations().stream()
                        .filter(item -> "interpretation-normalizedBetweenness".equals(item.id()))
                        .findFirst()
                        .orElseThrow()
                        .state());
    }

    @Test
    void distinguishesZeroThresholdFromAnObservedZeroOverlap() {
        List<NetworkNode> nodes = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> new NetworkNode(
                        "profile-" + index,
                        NetworkNodeType.PROFILE,
                        0,
                        0,
                        1.0 / 21,
                        true))
                .toList();
        SocialNetworkEvidenceFactSummaryGenerator.Summary summary =
                generator.generate(smallSnapshot(true, nodes, List.of()));

        SocialNetworkEvidenceFactSummaryGenerator.Statement comparison =
                fact(summary, "top20-indegree-outdegree");
        assertEquals("limited", comparison.state());
        assertEquals(null, comparison.evidenceValues().get("jaccard"));
        assertEquals(null, comparison.evidenceValues().get("intersectionCount"));
        assertTrue(comparison.reason().contains("阈值为 0"));
        assertFalse(comparison.text().contains("交集 0 个节点"));
    }

    @Test
    void reportsAnEmptySnapshotAsEmptyInsteadOfZeroValuedFindings() {
        SocialNetworkEvidenceFactSummaryGenerator.Summary summary =
                generator.generate(smallSnapshot(true, true));

        assertEquals("empty", summary.state());
        assertTrue(fact(summary, "network-scope").text().contains("总体为空"));
        assertTrue(fact(summary, "connectivity").text().contains("连通结构无法判断"));
        assertEquals("empty", fact(summary, "distribution-pageRank").state());
        assertTrue(summary.facts().stream()
                .filter(item -> item.id().startsWith("spearman-"))
                .allMatch(item -> "empty".equals(item.state())));
    }

    private static SocialNetworkEvidenceFactSummaryGenerator.Statement fact(
            SocialNetworkEvidenceFactSummaryGenerator.Summary summary, String id) {
        return summary.facts().stream()
                .filter(item -> id.equals(item.id()))
                .findFirst()
                .orElseThrow();
    }

    static SocialNetworkWorkspaceSnapshot smallSnapshot(boolean converged) {
        List<NetworkNode> nodes = List.of(
                new NetworkNode("profile-a", NetworkNodeType.PROFILE, 0, 1, 0.5, false),
                new NetworkNode("profile-b", NetworkNodeType.PROFILE, 1, 0, 0.5, false));
        List<NetworkEdge> edges =
                List.of(new NetworkEdge("profile-a", "profile-b", "following"));
        return smallSnapshot(converged, nodes, edges);
    }

    static SocialNetworkWorkspaceSnapshot smallSnapshot(
            boolean converged, List<NetworkNode> nodes, List<NetworkEdge> edges) {
        StructuralRoleAnalysis roles =
                new StructuralRoleCalculator().calculate(nodes, edges);
        int nodeCount = nodes.size();
        int edgeCount = edges.size();
        boolean edgeless = edgeCount == 0;
        int componentCount = nodeCount == 0 ? 0 : edgeless ? nodeCount : 1;
        int largestComponentSize = nodeCount == 0 ? 0 : edgeless ? 1 : nodeCount;
        NetworkMetrics metrics = new NetworkMetrics(
                nodeCount,
                edgeCount,
                nodeCount,
                0,
                edgeless ? nodeCount : 0,
                componentCount,
                largestComponentSize,
                nodeCount == 0 ? null : largestComponentSize / (double) nodeCount,
                0.0,
                0.0,
                nodeCount < 2 ? null : 1.0,
                0.0,
                "network-test",
                "pagerank-test",
                true,
                true);
        Map<String, Double> pageRankValues = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        NetworkNode::id, NetworkNode::pageRank));
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                nodes,
                edges,
                metrics,
                new PageRankResult(
                        pageRankValues,
                        converged,
                        converged ? 2 : 100,
                        converged ? 0.0 : 0.01,
                        pageRankValues.values().stream()
                                .mapToDouble(Double::doubleValue).sum()),
                List.of(),
                new CommunityDetectionResult(
                        List.of(), Map.of(), 0, 0, true, "test", "undirected"),
                roles,
                java.time.Instant.parse("2026-10-29T12:00:00Z"));
        SocialNetworkDataset dataset = new SocialNetworkDataset(
                "test-dataset",
                "Test dataset",
                DatasetType.BUILTIN,
                "test-version",
                DatasetStatus.HEALTHY,
                "test dataset",
                "test source",
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
                List.of(),
                List.of(),
                List.of());
    }

    static SocialNetworkWorkspaceSnapshot smallSnapshot(
            boolean converged, boolean empty) {
        return empty
                ? smallSnapshot(converged, List.of(), List.of())
                : smallSnapshot(converged);
    }
}
