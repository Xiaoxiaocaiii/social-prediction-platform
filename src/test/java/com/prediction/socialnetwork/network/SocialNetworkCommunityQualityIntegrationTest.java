package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkCommunityQualityIntegrationTest {

    @Test
    void ordinaryGraphCalculationSkipsLouvainAndExplicitSmallGraphRunIsStable() {
        List<NormalizedUser> users = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D", "E", "F", "G", "H", "X")) {
            users.add(user(id));
        }
        List<NormalizedRelationship> relationships = new ArrayList<>();
        addClique(relationships, List.of("A", "B", "C", "D"));
        addClique(relationships, List.of("E", "F", "G", "H"));
        relationships.add(relation("D", "E"));

        SocialNetworkCalculator calculator = new SocialNetworkCalculator();
        SocialNetworkGraphResult ordinary = calculator.calculate(users, relationships);
        SocialNetworkGraphResult graph = calculator.calculateWithCommunities(users, relationships);
        CommunityDetectionResult initial = graph.communityDetection();
        CommunityDetectionResult rerun =
                new LouvainCommunityDetector().detect(graph.nodes(), graph.edges());
        List<NetworkNode> reversedNodes = new ArrayList<>(graph.nodes());
        List<NetworkEdge> reversedEdges = new ArrayList<>(graph.edges());
        Collections.reverse(reversedNodes);
        Collections.reverse(reversedEdges);
        CommunityDetectionResult reversed = new LouvainCommunityDetector().detect(
                reversedNodes, reversedEdges);

        double modularity = UndirectedModularity.calculate(
                graph.nodes(), graph.edges(), initial.communityByNodeId());
        double reversedModularity = UndirectedModularity.calculate(
                reversedNodes, reversedEdges, reversed.communityByNodeId());

        assertEquals(CommunityDetectionStatus.NOT_REQUESTED,
                ordinary.communityDetection().status());
        assertTrue(ordinary.communityDetection().communities().isEmpty());
        assertEquals(9, graph.metrics().nodeCount());
        assertEquals(13, graph.metrics().edgeCount());
        assertEquals(1, graph.metrics().isolatedNodeCount());
        assertEquals(0, graph.metrics().externalNodeCount());
        assertEquals(CommunityDetectionStatus.COMPLETED, initial.status());
        assertTrue(initial.converged());
        assertEquals(graph.metrics().nodeCount(),
                initial.communityByNodeId().size() + initial.unassignedNodeCount());
        assertEquals(1, initial.unassignedNodeCount());
        assertEquals(List.of(4, 4), initial.communities().stream()
                .map(NetworkCommunity::nodeCount)
                .toList());
        assertEquals(initial.communityByNodeId(), rerun.communityByNodeId());
        assertEquals(initial.communityByNodeId(), reversed.communityByNodeId());
        assertEquals(initial.communities(), reversed.communities());
        assertEquals(initial.iterations(), reversed.iterations());
        assertTrue(Double.isFinite(modularity));
        assertEquals(modularity, reversedModularity, 1e-12);
        assertEquals(11.0 / 26.0, modularity, 1e-12);
    }

    private static void addClique(List<NormalizedRelationship> relationships, List<String> ids) {
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                relationships.add(relation(ids.get(left), ids.get(right)));
            }
        }
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

    private static NormalizedRelationship relation(String source, String target) {
        return new NormalizedRelationship(
                "relationships.csv",
                2,
                0,
                source,
                target,
                AssociationStatus.LINKED,
                AssociationStatus.LINKED,
                RelationStatus.VALID,
                NormalizationStatus.NORMALIZED,
                true);
    }
}
