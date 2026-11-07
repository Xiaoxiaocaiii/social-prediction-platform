package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StructuralRoleCalculatorTest {

    private final StructuralRoleCalculator calculator = new StructuralRoleCalculator();

    @Test
    void computesCoreNumbersAndNormalizedBetweennessForAPath() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("A", "B", "C"),
                List.of(edge("A", "B"), edge("C", "B")));

        assertEquals(3, result.nodeCount());
        assertEquals(2, result.undirectedTieCount());
        assertEquals(List.of(1, 1, 1), result.kCoreRanking().stream()
                .map(StructuralRoleAnalysis.KCoreEntry::coreNumber)
                .toList());
        assertEquals("B", result.betweennessRanking().get(0).nodeId());
        assertEquals(1.0, result.betweennessRanking().get(0).rawBetweenness(), 1e-12);
        assertEquals(1.0, result.betweennessRanking().get(0).normalizedBetweenness(), 1e-12);
        assertEquals(0.0, result.betweennessRanking().get(1).normalizedBetweenness(), 1e-12);
    }

    @Test
    void preservesIsolatesAndSeparatesTriangleCoreFromZeroShell() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("A", "B", "C", "D"),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A")));

        assertEquals(3, result.undirectedTieCount());
        assertEquals(2, coreNumber(result, "A"));
        assertEquals(2, coreNumber(result, "B"));
        assertEquals(2, coreNumber(result, "C"));
        assertEquals(0, coreNumber(result, "D"));
        assertTrue(result.betweennessRanking().stream()
                .allMatch(entry -> entry.normalizedBetweenness() == 0.0));
    }

    @Test
    void returnsCompleteShellAndCumulativeCountsIncludingEmptyLayersAndNodeEvidence() {
        List<NetworkNode> nodes = new java.util.ArrayList<>(profileNodes("B", "C", "D"));
        nodes.add(new NetworkNode("A", NetworkNodeType.EXTERNAL, 0, 0, 0.0, false));
        StructuralRoleAnalysis result = calculator.calculate(
                nodes,
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A")));

        StructuralRoleAnalysis.KCoreDistribution distribution =
                result.kCoreDistribution();

        assertEquals(4, distribution.sampleSize());
        assertEquals(2, distribution.maxCoreNumber());
        assertEquals(3, distribution.highestCoreCount());
        assertEquals(List.of(0, 1, 2), distribution.shells().stream()
                .map(StructuralRoleAnalysis.KCoreShell::coreNumber)
                .toList());
        assertEquals(List.of(1, 0, 3), distribution.shells().stream()
                .map(StructuralRoleAnalysis.KCoreShell::exactCount)
                .toList());
        assertEquals(List.of(4, 3, 3), distribution.shells().stream()
                .map(StructuralRoleAnalysis.KCoreShell::cumulativeCount)
                .toList());
        assertEquals(1.0 / 4.0, distribution.shells().get(0).exactRatio(), 1e-12);
        assertEquals(0.0, distribution.shells().get(1).exactRatio(), 1e-12);
        assertEquals(3.0 / 4.0, distribution.shells().get(2).exactRatio(), 1e-12);
        assertEquals(List.of("D"), distribution.shells().get(0).nodes().stream()
                .map(StructuralRoleAnalysis.KCoreNode::nodeId)
                .toList());
        assertTrue(distribution.shells().get(2).nodes().stream()
                .anyMatch(node -> node.nodeId().equals("A")
                        && node.nodeType() == NetworkNodeType.EXTERNAL));
        assertEquals(4, distribution.shells().stream()
                .mapToInt(shell -> shell.nodes().size())
                .sum());
    }

    @Test
    void returnsAnEmptyDistributionForAnEmptyNetwork() {
        StructuralRoleAnalysis.KCoreDistribution distribution =
                calculator.calculate(List.of(), List.of()).kCoreDistribution();

        assertEquals(0, distribution.sampleSize());
        assertNull(distribution.maxCoreNumber());
        assertNull(distribution.highestCoreCount());
        assertTrue(distribution.shells().isEmpty());
    }

    @Test
    void collapsesReciprocalAndDuplicateDirectionsIntoOneUndirectedTie() {
        List<NetworkNode> nodes = profileNodes("A", "B", "C", "D");
        StructuralRoleAnalysis oneDirection = calculator.calculate(
                nodes, List.of(edge("A", "B"), edge("B", "C")));
        StructuralRoleAnalysis reciprocal = calculator.calculate(
                nodes, List.of(
                        edge("A", "B"), edge("B", "A"), edge("A", "B"), edge("B", "C")));

        assertEquals(2, oneDirection.undirectedTieCount());
        assertEquals(oneDirection.kCoreRanking(), reciprocal.kCoreRanking());
        assertEquals(oneDirection.betweennessRanking(), reciprocal.betweennessRanking());
        assertEquals("B", reciprocal.betweennessRanking().get(0).nodeId());
        assertEquals(1.0 / 3.0,
                reciprocal.betweennessRanking().get(0).normalizedBetweenness(), 1e-12);
    }

    @Test
    void appliesNormalizationBoundariesWithoutDroppingNodes() {
        StructuralRoleAnalysis empty = calculator.calculate(List.of(), List.of());
        StructuralRoleAnalysis twoIsolates = calculator.calculate(
                profileNodes("A", "B"), List.of());
        StructuralRoleAnalysis threeIsolates = calculator.calculate(
                profileNodes("A", "B", "C"), List.of());

        assertEquals(0, empty.nodeCount());
        assertTrue(empty.kCoreRanking().isEmpty());
        assertTrue(empty.betweennessRanking().isEmpty());
        assertNull(twoIsolates.betweennessRanking().get(0).normalizedBetweenness());
        assertEquals(3, threeIsolates.betweennessRanking().size());
        assertTrue(threeIsolates.betweennessRanking().stream()
                .allMatch(entry -> entry.normalizedBetweenness() == 0.0));
    }

    @Test
    void usesTheCompleteNormalizedNodeScopeIncludingExternalAndIsolatedNodes() {
        List<NormalizedUser> users = List.of(user("A"), user("B"));
        List<NormalizedRelationship> relationships = List.of(
                relationship("A", "X", AssociationStatus.LINKED,
                        AssociationStatus.EXTERNAL_NODE, RelationStatus.VALID, true),
                relationship("Y", "Y", AssociationStatus.EXTERNAL_NODE,
                        AssociationStatus.EXTERNAL_NODE, RelationStatus.SELF_LOOP, false));

        SocialNetworkGraphResult graph =
                new SocialNetworkCalculator().calculate(users, relationships);
        StructuralRoleAnalysis result = graph.structuralRoles();

        assertEquals(3, result.nodeCount());
        assertEquals(1, result.undirectedTieCount());
        assertEquals(0, coreNumber(result, "B"));
        assertTrue(result.kCoreRanking().stream()
                .anyMatch(entry -> entry.nodeId().equals("X")
                        && entry.nodeType() == NetworkNodeType.EXTERNAL));
        assertFalse(result.kCoreRanking().stream()
                .anyMatch(entry -> entry.nodeId().equals("Y")));
    }

    @Test
    void identifiesTheThreeCoreAroundAClusterWithAnAttachedLeaf() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("A", "B", "C", "D", "E"),
                List.of(
                        edge("A", "B"), edge("A", "C"), edge("A", "D"),
                        edge("B", "C"), edge("B", "D"), edge("C", "D"),
                        edge("A", "E")));

        assertEquals(Map.of("A", 3, "B", 3, "C", 3, "D", 3, "E", 1),
                coreNumbers(result));
        assertBetweenness(result, "A", 3.0, 0.5);
        for (String nodeId : List.of("B", "C", "D", "E")) {
            assertBetweenness(result, nodeId, 0.0, 0.0);
        }
    }

    @Test
    void ranksTheMiddleOfALinearChainByBetweenness() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("A", "B", "C", "D", "E"),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "D"), edge("D", "E")));

        assertEquals(Map.of("A", 1, "B", 1, "C", 1, "D", 1, "E", 1),
                coreNumbers(result));
        assertBetweenness(result, "A", 0.0, 0.0);
        assertBetweenness(result, "B", 3.0, 0.5);
        assertBetweenness(result, "C", 4.0, 2.0 / 3.0);
        assertBetweenness(result, "D", 3.0, 0.5);
        assertBetweenness(result, "E", 0.0, 0.0);
    }

    @Test
    void givesAStarCenterMaximumBetweennessButTheSameCoreAsItsLeaves() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("O", "A", "B", "C", "D"),
                List.of(edge("O", "A"), edge("O", "B"), edge("O", "C"), edge("O", "D")));

        assertEquals(Map.of("O", 1, "A", 1, "B", 1, "C", 1, "D", 1),
                coreNumbers(result));
        assertBetweenness(result, "O", 6.0, 1.0);
        for (String nodeId : List.of("A", "B", "C", "D")) {
            assertBetweenness(result, nodeId, 0.0, 0.0);
        }
    }

    @Test
    void preservesCoreAndBetweennessTiesInASymmetricFourCycle() {
        StructuralRoleAnalysis result = calculator.calculate(
                profileNodes("A", "B", "C", "D"),
                List.of(edge("A", "B"), edge("C", "B"), edge("C", "D"), edge("A", "D")));

        assertEquals(Map.of("A", 2, "B", 2, "C", 2, "D", 2), coreNumbers(result));
        for (String nodeId : List.of("A", "B", "C", "D")) {
            assertBetweenness(result, nodeId, 0.5, 1.0 / 6.0);
        }
    }

    private static int coreNumber(StructuralRoleAnalysis result, String nodeId) {
        return result.kCoreRanking().stream()
                .filter(entry -> entry.nodeId().equals(nodeId))
                .mapToInt(StructuralRoleAnalysis.KCoreEntry::coreNumber)
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Integer> coreNumbers(StructuralRoleAnalysis result) {
        return result.kCoreRanking().stream().collect(Collectors.toMap(
                StructuralRoleAnalysis.KCoreEntry::nodeId,
                StructuralRoleAnalysis.KCoreEntry::coreNumber));
    }

    private static void assertBetweenness(
            StructuralRoleAnalysis result,
            String nodeId,
            double expectedRaw,
            double expectedNormalized) {
        StructuralRoleAnalysis.BetweennessEntry entry = result.betweennessRanking().stream()
                .filter(candidate -> candidate.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedRaw, entry.rawBetweenness(), 1e-12);
        assertEquals(expectedNormalized, entry.normalizedBetweenness(), 1e-12);
    }

    private static List<NetworkNode> profileNodes(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, true))
                .toList();
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }

    private static NormalizedUser user(String id) {
        return new NormalizedUser(
                "users.csv", 2, id, Map.of("user_id", id),
                NormalizationStatus.NORMALIZED, Set.of());
    }

    private static NormalizedRelationship relationship(
            String source,
            String target,
            AssociationStatus sourceStatus,
            AssociationStatus targetStatus,
            RelationStatus relationStatus,
            boolean analysisIncluded) {
        return new NormalizedRelationship(
                "relationships.csv", 2, -1, source, target,
                sourceStatus, targetStatus, relationStatus,
                NormalizationStatus.NORMALIZED, analysisIncluded);
    }
}
