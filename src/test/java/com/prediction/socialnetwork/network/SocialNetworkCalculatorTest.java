package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkCalculatorTest {

    private final SocialNetworkCalculator calculator = new SocialNetworkCalculator();

    @Test
    void calculatesDirectedDegreesDensityAndSymmetricCyclePageRank() {
        var users = List.of(user("A"), user("B"), user("C"));
        var relationships = List.of(
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("B", "C", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("C", "A", AssociationStatus.LINKED, AssociationStatus.LINKED));

        var graph = calculator.calculate(users, relationships);

        assertEquals(3, graph.metrics().nodeCount());
        assertEquals(3, graph.metrics().edgeCount());
        assertEquals(0.5, graph.metrics().density(), 1e-12);
        assertEquals(1, node(graph, "A").inDegree());
        assertEquals(1, node(graph, "A").outDegree());
        assertEquals(1.0 / 3.0, graph.pageRank().values().get("A"), 1e-8);
        assertTrue(graph.pageRank().converged());
        assertEquals(1.0, graph.pageRank().sum(), 1e-8);
    }

    @Test
    void buildsAnalysisInputWithoutCalculatingUnneededNetworkMetrics() {
        var users = List.of(user("A"), user("B"), user("isolated"));
        var relationships = List.of(
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED));

        var graph = calculator.calculateAnalysisInput(users, relationships);

        assertEquals(3, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals(1, node(graph, "A").outDegree());
        assertEquals(1, node(graph, "B").inDegree());
        assertTrue(node(graph, "isolated").isolated());
        assertNull(graph.metrics());
        assertNull(graph.pageRank());
        assertNull(graph.structuralRoles());
        assertEquals(CommunityDetectionStatus.NOT_REQUESTED,
                graph.communityDetection().status());
    }

    @Test
    void includesExternalNodesButExcludesSelfLoopsAndDuplicateCopies() {
        var users = List.of(user("A"), user("B"));
        var relationships = List.of(
                relation("A", "A", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.SELF_LOOP, false),
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.DUPLICATE, false),
                relation("B", "X", AssociationStatus.LINKED, AssociationStatus.EXTERNAL_NODE));

        var graph = calculator.calculateWithCommunities(users, relationships);

        assertEquals(3, graph.metrics().nodeCount());
        assertEquals(2, graph.metrics().edgeCount());
        assertEquals(1, graph.metrics().externalNodeCount());
        assertEquals(NetworkNodeType.EXTERNAL, node(graph, "X").nodeType());
        assertEquals(1, node(graph, "X").inDegree());
        assertNotEquals(0, node(graph, "A").outDegree());
        assertTrue(graph.communityDetection().communityByNodeId().containsKey("X"));
        assertEquals(1, graph.communityDetection().communities().stream()
                .mapToInt(NetworkCommunity::externalNodeCount)
                .sum());
    }

    @Test
    void retainsIsolatedNodeAndUsesUndefinedDensityForSingleNode() {
        var graph = calculator.calculate(List.of(user("A")), List.of());

        assertEquals(1, graph.metrics().nodeCount());
        assertEquals(0, graph.metrics().edgeCount());
        assertEquals(1, graph.metrics().isolatedNodeCount());
        assertEquals(null, graph.metrics().density());
        assertEquals(CommunityDetectionStatus.NOT_REQUESTED,
                graph.communityDetection().status());
        assertTrue(graph.communityDetection().communityByNodeId().isEmpty());
        assertEquals(1.0, graph.pageRank().values().get("A"), 1e-12);
        assertTrue(graph.pageRank().converged());
    }

    @Test
    void calculatesCommunitiesOnlyWhenExplicitlyRequested() {
        var users = List.of(user("A"), user("B"), user("C"));
        var relationships = List.of(
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("B", "C", AssociationStatus.LINKED, AssociationStatus.LINKED));

        var ordinaryGraph = calculator.calculate(users, relationships);
        var communityGraph = calculator.calculateWithCommunities(users, relationships);

        assertEquals(CommunityDetectionStatus.NOT_REQUESTED,
                ordinaryGraph.communityDetection().status());
        assertTrue(ordinaryGraph.communityDetection().communities().isEmpty());
        assertEquals(CommunityDetectionStatus.COMPLETED,
                communityGraph.communityDetection().status());
        assertEquals("Louvain", communityGraph.communityDetection().algorithm());
    }

    @Test
    void returnsEmptyNetworkStructureMetricBoundaries() {
        var metrics = calculator.calculate(List.of(), List.of()).metrics();

        assertEquals(0, metrics.weaklyConnectedComponentCount());
        assertEquals(0, metrics.largestWeaklyConnectedComponentNodeCount());
        assertNull(metrics.largestWeaklyConnectedComponentRatio());
        assertNull(metrics.averageClusteringCoefficient());
        assertNull(metrics.reciprocityRate());
        assertNull(metrics.largestWeaklyConnectedComponentAveragePathLength());
        assertEquals("network-metrics-v2.0", metrics.metricVersion());
        assertEquals("pagerank-v1.0", metrics.pagerankVersion());
    }

    @Test
    void countsIsolatedNodesAsSingletonComponentsAndMakesPathUnavailable() {
        var metrics = calculator.calculate(
                List.of(user("A"), user("B"), user("C")), List.of()).metrics();

        assertEquals(3, metrics.weaklyConnectedComponentCount());
        assertEquals(1, metrics.largestWeaklyConnectedComponentNodeCount());
        assertEquals(1.0 / 3.0, metrics.largestWeaklyConnectedComponentRatio(), 1e-12);
        assertEquals(0.0, metrics.averageClusteringCoefficient(), 1e-12);
        assertNull(metrics.reciprocityRate());
        assertNull(metrics.largestWeaklyConnectedComponentAveragePathLength());
    }

    @Test
    void averagesPathLengthAcrossTiedLargestComponentsAndIncludesIsolatesInMetrics() {
        var users = List.of(
                user("A"), user("B"), user("C"),
                user("D"), user("E"), user("F"), user("G"));
        var relationships = List.of(
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.DUPLICATE, false),
                relation("B", "C", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("C", "A", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("D", "E", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("E", "F", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("G", "G", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.SELF_LOOP, false));

        var metrics = calculator.calculate(users, relationships).metrics();

        assertEquals(7, metrics.nodeCount());
        assertEquals(5, metrics.edgeCount());
        assertEquals(3, metrics.weaklyConnectedComponentCount());
        assertEquals(3, metrics.largestWeaklyConnectedComponentNodeCount());
        assertEquals(3.0 / 7.0, metrics.largestWeaklyConnectedComponentRatio(), 1e-12);
        assertEquals(3.0 / 7.0, metrics.averageClusteringCoefficient(), 1e-12);
        assertEquals(0.0, metrics.reciprocityRate(), 1e-12);
        assertEquals(7.0 / 6.0,
                metrics.largestWeaklyConnectedComponentAveragePathLength(), 1e-12);
    }

    @Test
    void countsBothDirectionsOfReciprocalDyadAndUsesUndirectedSimpleProjection() {
        var users = List.of(user("P"), user("Q"), user("R"));
        var relationships = List.of(
                relation("P", "Q", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("Q", "P", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("Q", "R", AssociationStatus.LINKED, AssociationStatus.LINKED));

        var metrics = calculator.calculate(users, relationships).metrics();

        assertEquals(1, metrics.weaklyConnectedComponentCount());
        assertEquals(3, metrics.largestWeaklyConnectedComponentNodeCount());
        assertEquals(1.0, metrics.largestWeaklyConnectedComponentRatio(), 1e-12);
        assertEquals(0.0, metrics.averageClusteringCoefficient(), 1e-12);
        assertEquals(2.0 / 3.0, metrics.reciprocityRate(), 1e-12);
        assertEquals(4.0 / 3.0,
                metrics.largestWeaklyConnectedComponentAveragePathLength(), 1e-12);
    }

    @Test
    void localViewKeepsDirectionAndAppliesNodeAndEdgeLimits() {
        var users = List.of(user("A"), user("B"), user("C"), user("D"));
        var relationships = List.of(
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("B", "C", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("C", "D", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("D", "A", AssociationStatus.LINKED, AssociationStatus.LINKED));
        var graph = calculator.calculate(users, relationships);

        var view = calculator.localView(graph, "A", 1, 1, true);

        assertEquals("A", view.centerNodeId());
        assertEquals(1, view.depth());
        assertEquals(2, view.nodes().size());
        assertTrue(view.edges().stream().allMatch(
                edge -> edge.source().equals("A") || edge.target().equals("A")));
        assertFalse(view.truncated());
    }

    private static NormalizedUser user(String id) {
        return new NormalizedUser(
                "users.csv",
                2,
                id,
                Map.of("user_id", id),
                NormalizationStatus.NORMALIZED,
                java.util.Set.of());
    }

    private static NormalizedRelationship relation(
            String source,
            String target,
            AssociationStatus sourceStatus,
            AssociationStatus targetStatus) {
        return relation(source, target, sourceStatus, targetStatus, RelationStatus.VALID, true);
    }

    private static NormalizedRelationship relation(
            String source,
            String target,
            AssociationStatus sourceStatus,
            AssociationStatus targetStatus,
            RelationStatus relationStatus,
            boolean analysisIncluded) {
        return new NormalizedRelationship(
                "relationships.csv",
                2,
                0,
                source,
                target,
                sourceStatus,
                targetStatus,
                relationStatus,
                NormalizationStatus.NORMALIZED,
                analysisIncluded);
    }

    private static NetworkNode node(SocialNetworkGraphResult graph, String id) {
        return graph.nodes().stream()
                .filter(node -> node.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
