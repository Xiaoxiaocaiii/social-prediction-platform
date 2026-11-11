package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkBridgeCandidateCalculatorTest {

    @Test
    void countsSingleOutgoingAndIncomingRelationshipsByDirection() {
        var outgoing = calculate(
                List.of(profile("a"), external("b")),
                List.of(edge("a", "b")),
                Map.of("a", "C1", "b", "C2"));
        var outgoingCandidate = outgoing.get(0);
        assertEquals("a", outgoingCandidate.accountId());
        assertEquals(1, outgoingCandidate.connectedCommunityCount());
        assertEquals(1, outgoingCandidate.distinctOtherAccountCount());
        assertEquals(1, outgoingCandidate.outgoingRelationshipCount());
        assertEquals(0, outgoingCandidate.incomingRelationshipCount());
        assertEquals(1, outgoingCandidate.directedRelationshipCount());

        var incoming = calculate(
                List.of(profile("a"), external("b")),
                List.of(edge("b", "a")),
                Map.of("a", "C1", "b", "C2"));
        var incomingCandidate = incoming.get(0);
        assertEquals(0, incomingCandidate.outgoingRelationshipCount());
        assertEquals(1, incomingCandidate.incomingRelationshipCount());
        assertEquals(1, incomingCandidate.directedRelationshipCount());
    }

    @Test
    void countsBidirectionalRelationshipsAsTwoButTheOtherAccountOnce() {
        var candidates = calculate(
                List.of(profile("a"), external("b")),
                List.of(edge("a", "b"), edge("b", "a")),
                Map.of("a", "C1", "b", "C2"));

        assertEquals(1, candidates.size());
        var candidate = candidates.get(0);
        assertEquals(1, candidate.distinctOtherAccountCount());
        assertEquals(1, candidate.outgoingRelationshipCount());
        assertEquals(1, candidate.incomingRelationshipCount());
        assertEquals(2, candidate.directedRelationshipCount());
        assertEquals(new SocialNetworkBridgeCandidateCalculator.CommunitySummary(
                        "C2", 1, 1, 1, 2),
                candidate.communities().get(0));
    }

    @Test
    void deduplicatesRepeatedDirectedRelationshipsButKeepsTheReverseDirection() {
        var candidates = calculate(
                List.of(profile("a"), external("b")),
                List.of(
                        edge("a", "b"),
                        edge("a", "b"),
                        edge("a", "b"),
                        edge("b", "a"),
                        edge("b", "a")),
                Map.of("a", "C1", "b", "C2"));

        var candidate = candidates.get(0);
        assertEquals(1, candidate.outgoingRelationshipCount());
        assertEquals(1, candidate.incomingRelationshipCount());
        assertEquals(2, candidate.directedRelationshipCount());
    }

    @Test
    void ignoresSelfSameCommunityAndUnassignedRelationshipsAndListsOnlyProfileNodes() {
        var candidates = calculate(
                List.of(profile("a"), profile("unassigned"), external("same"),
                        external("other"), external("other-2")),
                List.of(
                        edge("a", "a"),
                        edge("a", "same"),
                        edge("a", "missing"),
                        edge("missing", "a"),
                        edge("a", "other"),
                        edge("other-2", "a"),
                        edge("other", "a")),
                Map.of("a", "C1", "same", "C1", "other", "C2", "other-2", "C2"));

        assertEquals(1, candidates.size());
        var candidate = candidates.get(0);
        assertEquals("a", candidate.accountId());
        assertEquals(2, candidate.distinctOtherAccountCount());
        assertEquals(1, candidate.outgoingRelationshipCount());
        assertEquals(2, candidate.incomingRelationshipCount());
        assertEquals(3, candidate.directedRelationshipCount());
    }

    @Test
    void sortsByCommunityCountThenRelationshipCountThenAccountId() {
        var candidates = calculate(
                List.of(profile("z"), profile("b"), profile("a"), profile("y"),
                        external("c2"), external("c3"), external("c4")),
                List.of(
                        edge("z", "c2"),
                        edge("b", "c2"),
                        edge("b", "c3"),
                        edge("a", "c2"),
                        edge("a", "c3"),
                        edge("y", "c2"),
                        edge("y", "c4")),
                Map.of(
                        "z", "C1", "b", "C1", "a", "C1", "y", "C1",
                        "c2", "C2", "c3", "C3", "c4", "C2"));

        assertEquals(List.of("a", "b", "y", "z"),
                candidates.stream()
                        .map(SocialNetworkBridgeCandidateCalculator.Candidate::accountId)
                        .toList());
        assertEquals(2, candidates.get(0).connectedCommunityCount());
        assertEquals(2, candidates.get(1).directedRelationshipCount());
        assertEquals(2, candidates.get(2).directedRelationshipCount());
        assertEquals(1, candidates.get(3).directedRelationshipCount());
        assertEquals(List.of(
                        new SocialNetworkBridgeCandidateCalculator.CommunitySummary(
                                "C2", 1, 1, 0, 1),
                        new SocialNetworkBridgeCandidateCalculator.CommunitySummary(
                                "C3", 1, 1, 0, 1)),
                candidates.get(0).communities());
        assertEquals(List.of(
                        new SocialNetworkBridgeCandidateCalculator.CommunitySummary(
                                "C2", 2, 2, 0, 2)),
                candidates.get(2).communities());
    }

    @Test
    void returnsNoCandidatesWhenOnlyUnassignedOrInternalRelationshipsExist() {
        var candidates = calculate(
                List.of(profile("a"), external("b"), external("unassigned")),
                List.of(edge("a", "b"), edge("a", "unassigned")),
                Map.of("a", "C1", "b", "C1"));

        assertTrue(candidates.isEmpty());
    }

    private static List<SocialNetworkBridgeCandidateCalculator.Candidate> calculate(
            List<NetworkNode> nodes,
            List<NetworkEdge> edges,
            Map<String, String> communities) {
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                nodes, edges, null, null, List.of(), null, null, null);
        return SocialNetworkBridgeCandidateCalculator.calculate(graph, communities);
    }

    private static NetworkNode profile(String id) {
        return node(id, NetworkNodeType.PROFILE);
    }

    private static NetworkNode external(String id) {
        return node(id, NetworkNodeType.EXTERNAL);
    }

    private static NetworkNode node(String id, NetworkNodeType type) {
        return new NetworkNode(id, type, 0, 0, 0.0, false);
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
