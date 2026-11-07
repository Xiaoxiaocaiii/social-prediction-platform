package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitSetMaximalCliqueCrossCheckTest {

    @Test
    void matchesTheProductionEnumeratorForEveryFiveNodeUndirectedGraph() {
        List<String> ids = List.of("A", "B", "C", "D", "E");
        List<NetworkNode> nodes = ids.stream()
                .map(id -> new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, true))
                .toList();
        List<int[]> possibleEdges = new ArrayList<>();
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                possibleEdges.add(new int[] {left, right});
            }
        }
        MaximalCliqueEnumerator production = new MaximalCliqueEnumerator();
        BitSetMaximalCliqueCrossCheck independent = new BitSetMaximalCliqueCrossCheck();

        for (int edgeMask = 0; edgeMask < (1 << possibleEdges.size()); edgeMask++) {
            List<NetworkEdge> edges = new ArrayList<>();
            for (int edgeIndex = 0; edgeIndex < possibleEdges.size(); edgeIndex++) {
                if ((edgeMask & (1 << edgeIndex)) == 0) {
                    continue;
                }
                int[] pair = possibleEdges.get(edgeIndex);
                edges.add(new NetworkEdge(
                        ids.get(pair[0]), ids.get(pair[1]), "following"));
            }

            CliqueEnumerationResult expected =
                    production.enumerate(nodes, edges, CliqueEnumerationLimits.DEFAULT);
            CliqueEnumerationResult actual =
                    independent.enumerate(nodes, edges, CliqueEnumerationLimits.DEFAULT);

            assertEquals(expected.status(), actual.status(), "edge mask " + edgeMask);
            assertTrue(actual.membershipListComplete(), "edge mask " + edgeMask);
            assertEquals(expected.cliques(), actual.cliques(), "edge mask " + edgeMask);
            assertEquals(
                    expected.cliqueSizeDistribution(),
                    actual.cliqueSizeDistribution(),
                    "edge mask " + edgeMask);
            assertEquals(
                    expected.nodeParticipationCounts(),
                    actual.nodeParticipationCounts(),
                    "edge mask " + edgeMask);
            assertEquals(
                    expected.encodedCliqueArrayBytes(),
                    actual.encodedCliqueArrayBytes(),
                    "edge mask " + edgeMask);
        }
    }
}
