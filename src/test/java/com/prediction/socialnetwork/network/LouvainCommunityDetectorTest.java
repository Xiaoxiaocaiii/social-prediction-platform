package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LouvainCommunityDetectorTest {

    private final LouvainCommunityDetector detector = new LouvainCommunityDetector();

    @Test
    void separatesDenseGroupsConnectedByOneBridgeAndLeavesIsolatesUnassigned() {
        List<NetworkNode> nodes = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D", "E", "F", "G", "H", "X")) {
            nodes.add(node(id));
        }
        List<NetworkEdge> edges = new ArrayList<>();
        addClique(edges, List.of("A", "B", "C", "D"));
        addClique(edges, List.of("E", "F", "G", "H"));
        edges.add(edge("D", "E"));

        CommunityDetectionResult result = detector.detect(nodes, edges);

        assertEquals(2, result.communities().size());
        assertEquals(List.of(4, 4), result.communities().stream()
                .map(NetworkCommunity::nodeCount)
                .toList());
        assertEquals(result.communityByNodeId().get("A"),
                result.communityByNodeId().get("D"));
        assertEquals(result.communityByNodeId().get("E"),
                result.communityByNodeId().get("H"));
        assertFalse(result.communityByNodeId().get("A")
                .equals(result.communityByNodeId().get("E")));
        assertEquals(1, result.unassignedNodeCount());
        assertTrue(result.communityByNodeId().containsKey("A"));
        assertTrue(result.communityByNodeId().containsKey("H"));
        assertTrue(!result.communityByNodeId().containsKey("X"));
        assertTrue(result.converged());
        assertTrue(result.iterations() >= 3,
                "the detector should continue after aggregating the first-level communities");
        assertEquals("Louvain", result.algorithm());
        assertEquals("undirected_simple", result.relationProjection());
        assertEquals("louvain-greedy-local-move-v1",
                LouvainCommunityDetector.ALGORITHM_VERSION);
        assertEquals(1.0, LouvainCommunityDetector.RESOLUTION);
        assertEquals(100, LouvainCommunityDetector.MAX_ITERATIONS);
        assertEquals(1e-12, LouvainCommunityDetector.SCORE_EPSILON);
        assertEquals(11.0 / 26.0,
                UndirectedModularity.calculate(nodes, edges, result.communityByNodeId()),
                1e-12);
    }

    @Test
    void leavesEveryNodeUnassignedWhenTheGraphHasNoUndirectedTies() {
        CommunityDetectionResult result = detector.detect(
                List.of(node("A"), node("I"), node("X", NetworkNodeType.EXTERNAL)),
                List.of());

        assertTrue(result.communities().isEmpty());
        assertTrue(result.communityByNodeId().isEmpty());
        assertEquals(3, result.unassignedNodeCount());
        assertEquals(0, result.iterations());
        assertTrue(result.converged());
    }

    @Test
    void distinguishesAnIterationCappedResultFromAConvergedResult() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("B", "C"), edge("C", "A"));

        CommunityDetectionResult result =
                detector.detectForControlledVerification(nodes, edges, 1);

        assertEquals(CommunityDetectionStatus.COMPLETED, result.status());
        assertEquals(1, result.iterations());
        assertFalse(result.converged());
        assertEquals(3, result.communityByNodeId().size());
    }

    @Test
    void collapsesReciprocalDirectedEdgesIntoOneUndirectedTie() {
        CommunityDetectionResult result = detector.detect(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B"), edge("B", "A")));

        assertEquals(1, result.communities().size());
        assertEquals(2, result.communities().get(0).nodeCount());
        assertEquals(1, result.communities().get(0).internalTieCount());
        assertEquals(result.communityByNodeId().get("A"),
                result.communityByNodeId().get("B"));
    }

    @Test
    void mutualOnlyProjectionLeavesOneWayFollowersUnassigned() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"));
        List<NetworkEdge> edges = List.of(edge("A", "B"), edge("B", "C"));
        LouvainAnalysisParameters mutualOnly = new LouvainAnalysisParameters(
                1.0,
                LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY);

        CommunityDetectionResult result = detector.detect(nodes, edges, mutualOnly);

        assertTrue(result.communities().isEmpty());
        assertEquals(3, result.unassignedNodeCount());
        assertEquals("undirected_mutual", result.relationProjection());
    }

    @Test
    void acceptsSensitivityResolutionAndAlternateDeterministicNodeOrder() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"), node("D"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("B", "C"), edge("C", "D"), edge("D", "A"));
        LouvainAnalysisParameters parameters = new LouvainAnalysisParameters(
                2.0,
                LouvainAnalysisParameters.NodeOrder.DEGREE_ASCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION);

        CommunityDetectionResult first = detector.detect(nodes, edges, parameters);
        CommunityDetectionResult repeated = detector.detect(nodes, edges, parameters);

        assertEquals(first, repeated);
        assertEquals(nodes.size(), first.communityByNodeId().size());
        assertTrue(first.converged());
        assertEquals("undirected_simple", first.relationProjection());
    }

    @Test
    void producesStableCommunityIdsRegardlessOfInputOrdering() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"), node("D"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("B", "A"), edge("C", "D"));

        CommunityDetectionResult first = detector.detect(nodes, edges);
        CommunityDetectionResult reversed = detector.detect(
                List.of(node("D"), node("C"), node("B"), node("A")),
                List.of(edge("C", "D"), edge("B", "A"), edge("A", "B")));

        assertEquals(first.communityByNodeId(), reversed.communityByNodeId());
        assertEquals(first.communities(), reversed.communities());
    }

    @Test
    void assignsConnectedExternalNodesAndLeavesProfileAndExternalIsolatesUnassigned() {
        List<NetworkNode> nodes = List.of(
                node("A"),
                node("X", NetworkNodeType.EXTERNAL),
                node("I"),
                node("Y", NetworkNodeType.EXTERNAL));
        List<NetworkEdge> edges = List.of(
                edge("A", "X"),
                edge("X", "A"),
                edge("A", "A"),
                edge("missing", "X"));

        CommunityDetectionResult result = detector.detect(nodes, edges);

        assertEquals(1, result.communities().size());
        NetworkCommunity community = result.communities().get(0);
        assertEquals(2, community.nodeCount());
        assertEquals(1, community.profileNodeCount());
        assertEquals(1, community.externalNodeCount());
        assertEquals(1, community.internalTieCount());
        assertEquals(2, result.unassignedNodeCount());
        assertTrue(result.communityByNodeId().containsKey("X"));
        assertFalse(result.communityByNodeId().containsKey("I"));
        assertFalse(result.communityByNodeId().containsKey("Y"));
    }

    private static void addClique(List<NetworkEdge> edges, List<String> nodeIds) {
        for (int left = 0; left < nodeIds.size(); left++) {
            for (int right = left + 1; right < nodeIds.size(); right++) {
                edges.add(edge(nodeIds.get(left), nodeIds.get(right)));
            }
        }
    }

    private static NetworkNode node(String id) {
        return node(id, NetworkNodeType.PROFILE);
    }

    private static NetworkNode node(String id, NetworkNodeType type) {
        return new NetworkNode(id, type, 0, 0, 0.0, false);
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
