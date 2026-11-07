package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UndirectedModularityQualityTest {

    private final LouvainCommunityDetector detector = new LouvainCommunityDetector();

    @Test
    void independentlyChecksPartitionCoverageTieCountsAndModularity() {
        List<NetworkNode> nodes = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I")) {
            nodes.add(node(id));
        }
        List<NetworkEdge> edges = new ArrayList<>();
        addClique(edges, List.of("A", "B", "C", "D"));
        addClique(edges, List.of("E", "F", "G", "H"));
        edges.add(edge("D", "E"));

        CommunityDetectionResult result = detector.detect(nodes, edges);
        UndirectedModularity.QualityReport report =
                UndirectedModularity.analyze(nodes, edges, result.communityByNodeId());

        assertEquals(9, report.nodeCount());
        assertEquals(8, report.assignedNodeCount());
        assertEquals(List.of("I"), report.unassignedNodeIds());
        assertEquals(13, report.undirectedTieCount());
        assertEquals(12, report.internalTieCount());
        assertEquals(1, report.crossCommunityTieCount());
        assertEquals(13, report.internalTieCount() + report.crossCommunityTieCount());
        assertEquals(Map.of("C001", 6, "C002", 6),
                report.internalTieCountByCommunity());
        assertEquals(11.0 / 26.0, report.modularity(), 1e-12);

        Map<String, Integer> detectorInternalTies = result.communities().stream()
                .collect(java.util.stream.Collectors.toMap(
                        NetworkCommunity::id, NetworkCommunity::internalTieCount));
        assertEquals(report.internalTieCountByCommunity(), detectorInternalTies);
        assertEquals(report.nodeCount(),
                report.assignedNodeCount() + report.unassignedNodeCount());
    }

    @Test
    void reciprocalAndDuplicateDirectionsCollapseToSingleUndirectedTies() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"), node("I"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"),
                edge("B", "A"),
                edge("A", "B"),
                edge("B", "C"),
                edge("C", "C"),
                edge("missing", "A"));
        Map<String, String> assignments = Map.of(
                "A", "C001",
                "B", "C001",
                "C", "C002");

        UndirectedModularity.QualityReport report =
                UndirectedModularity.analyze(nodes, edges, assignments);

        assertEquals(4, report.nodeCount());
        assertEquals(List.of("I"), report.unassignedNodeIds());
        assertEquals(2, report.undirectedTieCount());
        assertEquals(1, report.internalTieCount());
        assertEquals(1, report.crossCommunityTieCount());
        assertEquals(-0.125, report.modularity(), 1e-12);
    }

    @Test
    void marksModularityUndefinedWhenThereAreNoProjectedTies() {
        UndirectedModularity.QualityReport report = UndirectedModularity.analyze(
                List.of(node("A"), node("I")),
                List.of(),
                Map.of());

        assertEquals(2, report.nodeCount());
        assertEquals(0, report.assignedNodeCount());
        assertEquals(List.of("A", "I"), report.unassignedNodeIds());
        assertEquals(0, report.undirectedTieCount());
        assertNull(report.modularity());
    }

    @Test
    void independentlyValidatesMutualOnlyProjection() {
        List<NetworkNode> nodes = List.of(node("A"), node("B"), node("C"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("B", "A"), edge("A", "C"));
        LouvainAnalysisParameters parameters = new LouvainAnalysisParameters(
                1.0,
                LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY);

        LouvainQualitySummary quality = new LouvainQualityCalculator().calculate(
                nodes, edges, Map.of("A", "C001", "B", "C001"), parameters);

        assertEquals(3, quality.inputDirectedRelationshipCount());
        assertEquals(1, quality.projectedUndirectedTieCount());
        assertEquals(1, quality.internalTieCount());
        assertEquals(0, quality.crossCommunityTieCount());
        assertEquals(List.of("C"), quality.unassignedNodeIds());
        assertEquals(0.0, quality.modularity(), 1e-12);
    }

    @Test
    void rejectsAPartitionThatOmitsANodeWithProjectedTies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UndirectedModularity.analyze(
                        List.of(node("A"), node("B")),
                        List.of(edge("A", "B")),
                        Map.of()));
    }

    private static void addClique(List<NetworkEdge> edges, List<String> nodeIds) {
        for (int left = 0; left < nodeIds.size(); left++) {
            for (int right = left + 1; right < nodeIds.size(); right++) {
                edges.add(edge(nodeIds.get(left), nodeIds.get(right)));
            }
        }
    }

    private static NetworkNode node(String id) {
        return new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, true);
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
