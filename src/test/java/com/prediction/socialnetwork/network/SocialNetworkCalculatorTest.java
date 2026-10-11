package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void includesExternalNodesButExcludesSelfLoopsAndDuplicateCopies() {
        var users = List.of(user("A"), user("B"));
        var relationships = List.of(
                relation("A", "A", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.SELF_LOOP, false),
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED),
                relation("A", "B", AssociationStatus.LINKED, AssociationStatus.LINKED,
                        RelationStatus.DUPLICATE, false),
                relation("B", "X", AssociationStatus.LINKED, AssociationStatus.EXTERNAL_NODE));

        var graph = calculator.calculate(users, relationships);

        assertEquals(3, graph.metrics().nodeCount());
        assertEquals(2, graph.metrics().edgeCount());
        assertEquals(1, graph.metrics().externalNodeCount());
        assertEquals(NetworkNodeType.EXTERNAL, node(graph, "X").nodeType());
        assertEquals(1, node(graph, "X").inDegree());
        assertNotEquals(0, node(graph, "A").outDegree());
    }

    @Test
    void retainsIsolatedNodeAndUsesUndefinedDensityForSingleNode() {
        var graph = calculator.calculate(List.of(user("A")), List.of());

        assertEquals(1, graph.metrics().nodeCount());
        assertEquals(0, graph.metrics().edgeCount());
        assertEquals(1, graph.metrics().isolatedNodeCount());
        assertEquals(null, graph.metrics().density());
        assertEquals(1.0, graph.pageRank().values().get("A"), 1e-12);
        assertTrue(graph.pageRank().converged());
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
