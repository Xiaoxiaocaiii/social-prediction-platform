package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliqueEnumerationIndependentVerifierTest {

    private final MaximalCliqueEnumerator enumerator = new MaximalCliqueEnumerator();
    private final CliqueEnumerationIndependentVerifier verifier =
            new CliqueEnumerationIndependentVerifier();

    @Test
    void verifiesEveryMemberPairMaximalityAndRecomputedSummary() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        List<NetworkEdge> edges = List.of(
                edge("A", "B"),
                edge("B", "C"),
                edge("C", "A"),
                edge("B", "A"),
                edge("D", "D"),
                edge("missing", "A"));
        CliqueEnumerationResult result = enumerator.enumerate(nodes, edges);

        var report = verifier.verify(nodes, edges, result);

        assertEquals(
                new CliqueEnumerationIndependentVerifier.VerificationReport(
                        4, 3, 2, 1, 3, 3, 1, 3, 3),
                report);
    }

    @Test
    void rejectsAReportedCliqueThatIsNotMaximal() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        List<NetworkEdge> completeFourNodeGraph = edges(
                "A", "B", "A", "C", "A", "D",
                "B", "C", "B", "D", "C", "D");
        CliqueEnumerationResult falseTriangle = new CliqueEnumerationResult(
                List.of(List.of("A", "B", "C")),
                CliqueEnumerationStatus.COMPLETED,
                CliqueEnumerationLimits.DEFAULT,
                15,
                null);

        assertThrows(
                IllegalStateException.class,
                () -> verifier.verify(nodes, completeFourNodeGraph, falseTriangle));
    }

    @Test
    void rejectsAReportedCliqueWithAMissingProjectedTie() {
        List<NetworkNode> nodes = nodes("A", "B", "C");
        List<NetworkEdge> incompleteTriangle = edges("A", "B", "B", "C");
        CliqueEnumerationResult falseTriangle = new CliqueEnumerationResult(
                List.of(List.of("A", "B", "C")),
                CliqueEnumerationStatus.COMPLETED,
                CliqueEnumerationLimits.DEFAULT,
                15,
                null);

        assertThrows(
                IllegalStateException.class,
                () -> verifier.verify(nodes, incompleteTriangle, falseTriangle));
    }

    @Test
    void refusesToVerifyAnIncompleteMemberList() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D", "E", "F");
        List<NetworkEdge> twoTriangles = edges(
                "A", "B", "A", "C", "B", "C",
                "D", "E", "D", "F", "E", "F");
        CliqueEnumerationResult incomplete = enumerator.enumerate(
                nodes,
                twoTriangles,
                new CliqueEnumerationLimits(Duration.ofSeconds(5), 10, 15));

        assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify(nodes, twoTriangles, incomplete));
    }

    private static List<NetworkNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new NetworkNode(
                        id, NetworkNodeType.PROFILE, 0, 0, 0.0, true))
                .toList();
    }

    private static List<NetworkEdge> edges(String... endpoints) {
        java.util.ArrayList<NetworkEdge> edges = new java.util.ArrayList<>();
        for (int index = 0; index < endpoints.length; index += 2) {
            edges.add(edge(endpoints[index], endpoints[index + 1]));
        }
        return edges;
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
