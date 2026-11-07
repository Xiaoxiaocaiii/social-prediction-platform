package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MaximalCliqueEnumeratorTest {

    private final MaximalCliqueEnumerator enumerator = new MaximalCliqueEnumerator();

    @Test
    void emitsMaximalTriangleButNotItsSubcliquesOrAnIsolatedNode() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D", "E");
        List<NetworkEdge> edges = edges(
                "A", "B",
                "B", "C",
                "C", "A",
                "D", "E",
                "E", "E");

        CliqueEnumerationResult result = enumerator.enumerate(nodes, edges);

        assertEquals(List.of(List.of("A", "B", "C")), result.cliques());
        assertEquals(Map.of(3, 1), result.cliqueSizeDistribution());
        assertEquals(Map.of("A", 1, "B", 1, "C", 1, "D", 0, "E", 0),
                result.observedParticipationCounts());
    }

    @Test
    void countsOverlappingMaximalCliquesAndNodeParticipation() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        List<NetworkEdge> edges = edges(
                "A", "B",
                "A", "C",
                "B", "C",
                "B", "D",
                "C", "D");

        List<List<String>> cliques = enumerator.enumerate(nodes, edges).cliques();
        Map<String, Long> participation = cliques.stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertEquals(
                List.of(List.of("A", "B", "C"), List.of("B", "C", "D")),
                cliques);
        assertEquals(Map.of("A", 1L, "B", 2L, "C", 2L, "D", 1L), participation);
    }

    @Test
    void mergesReciprocalAndDuplicateDirectedEdgesInUndirectedProjection() {
        List<NetworkNode> nodes = nodes("A", "B", "C");
        List<NetworkEdge> oneWayTriangle = edges(
                "A", "B",
                "B", "C",
                "C", "A");
        List<NetworkEdge> reciprocalAndDuplicateTriangle = edges(
                "A", "B",
                "B", "A",
                "A", "B",
                "B", "C",
                "C", "B",
                "C", "A",
                "A", "A",
                "missing", "A");

        List<List<String>> expected = List.of(List.of("A", "B", "C"));
        assertEquals(expected, enumerator.enumerate(nodes, oneWayTriangle).cliques());
        assertEquals(expected,
                enumerator.enumerate(nodes, reciprocalAndDuplicateTriangle).cliques());
    }

    @Test
    void matchesBruteForceReferenceForEveryFiveNodeUndirectedGraph() {
        List<String> ids = List.of("A", "B", "C", "D", "E");
        List<NetworkNode> nodes = nodes(ids.toArray(String[]::new));
        List<int[]> possibleEdges = new java.util.ArrayList<>();
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                possibleEdges.add(new int[] {left, right});
            }
        }

        for (int edgeMask = 0; edgeMask < (1 << possibleEdges.size()); edgeMask++) {
            boolean[][] adjacency = new boolean[ids.size()][ids.size()];
            List<NetworkEdge> graphEdges = new java.util.ArrayList<>();
            for (int edgeIndex = 0; edgeIndex < possibleEdges.size(); edgeIndex++) {
                if ((edgeMask & (1 << edgeIndex)) == 0) {
                    continue;
                }
                int[] edge = possibleEdges.get(edgeIndex);
                adjacency[edge[0]][edge[1]] = true;
                adjacency[edge[1]][edge[0]] = true;
                graphEdges.add(new NetworkEdge(
                        ids.get(edge[0]), ids.get(edge[1]), "following"));
            }

            assertEquals(
                    bruteForceReference(ids, adjacency),
                    enumerator.enumerate(nodes, graphEdges).cliques(),
                    "edge mask " + edgeMask);
        }
    }

    @Test
    void reportsCliqueLimitAsIncompleteButCompletesWhenTheLimitIsExact() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D", "E", "F");
        List<NetworkEdge> twoDisjointTriangles = edges(
                "A", "B", "A", "C", "B", "C",
                "D", "E", "D", "F", "E", "F");
        CliqueEnumerationLimits oneCliqueLimit =
                new CliqueEnumerationLimits(Duration.ofSeconds(5), 1, 1024);

        CliqueEnumerationResult partial =
                enumerator.enumerate(nodes, twoDisjointTriangles, oneCliqueLimit);
        CliqueEnumerationResult exact = enumerator.enumerate(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                oneCliqueLimit);

        assertEquals(CliqueEnumerationStatus.CLIQUE_LIMIT, partial.status());
        assertFalse(partial.isComplete());
        assertEquals(1, partial.retainedCliqueCount());
        assertEquals(1, partial.enumeratedCliqueCount());
        assertEquals(Map.of(3, 1), partial.cliqueSizeDistribution());
        assertEquals(3, partial.largestObservedCliqueSize());
        assertEquals(CliqueEnumerationStatus.COMPLETED, exact.status());
        assertTrue(exact.isComplete());
        assertEquals(1, exact.retainedCliqueCount());
    }

    @Test
    void memberByteLimitDoesNotInterruptCompleteSummaryEnumeration() throws Exception {
        List<String> ids = List.of("a\"", "é", "line\nbreak");
        List<NetworkNode> nodes = nodes(ids.toArray(String[]::new));
        List<NetworkEdge> edges = edges(
                ids.get(0), ids.get(1),
                ids.get(0), ids.get(2),
                ids.get(1), ids.get(2));
        List<List<String>> expected = List.of(ids);
        long jsonBytes = new ObjectMapper().writeValueAsBytes(expected).length;

        CliqueEnumerationResult exactSize = enumerator.enumerate(
                nodes,
                edges,
                new CliqueEnumerationLimits(Duration.ofSeconds(5), 10, jsonBytes));
        CliqueEnumerationResult oneByteTooSmall = enumerator.enumerate(
                nodes,
                edges,
                new CliqueEnumerationLimits(Duration.ofSeconds(5), 10, jsonBytes - 1));

        assertEquals(CliqueEnumerationStatus.COMPLETED, exactSize.status());
        assertEquals(jsonBytes, exactSize.encodedCliqueArrayBytes());
        assertEquals(CliqueEnumerationStatus.COMPLETED, oneByteTooSmall.status());
        assertTrue(oneByteTooSmall.isComplete());
        assertEquals(0, oneByteTooSmall.retainedCliqueCount());
        assertEquals(1, oneByteTooSmall.enumeratedCliqueCount());
        assertEquals(3, oneByteTooSmall.largestObservedCliqueSize());
        assertEquals(Map.of(3, 1), oneByteTooSmall.cliqueSizeDistribution());
        assertEquals(Map.of("a\"", 1, "é", 1, "line\nbreak", 1),
                oneByteTooSmall.nodeParticipationCounts());
        assertFalse(oneByteTooSmall.membershipListComplete());
    }

    @Test
    void continuesCountingAfterTheBoundedMembershipListFills() throws Exception {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D", "E", "F");
        List<NetworkEdge> twoDisjointTriangles = edges(
                "A", "B", "A", "C", "B", "C",
                "D", "E", "D", "F", "E", "F");
        long oneTriangleBytes = new ObjectMapper().writeValueAsBytes(
                List.of(List.of("A", "B", "C"))).length;
        CliqueEnumerationResult result = enumerator.enumerate(
                nodes,
                twoDisjointTriangles,
                new CliqueEnumerationLimits(Duration.ofSeconds(5), 10, oneTriangleBytes));

        assertEquals(CliqueEnumerationStatus.COMPLETED, result.status());
        assertEquals(2, result.enumeratedCliqueCount());
        assertEquals(1, result.retainedCliqueCount());
        assertEquals(List.of(List.of("A", "B", "C")), result.cliques());
        assertEquals(Map.of(3, 2), result.cliqueSizeDistribution());
        assertEquals(Map.of("A", 1, "B", 1, "C", 1, "D", 1, "E", 1, "F", 1),
                result.observedParticipationCounts());
        assertFalse(result.membershipListComplete());
    }

    @Test
    void reportsTimeoutCancellationAndUnexpectedFailureAsIncomplete() {
        List<NetworkNode> nodes = nodes("A", "B", "C");
        List<NetworkEdge> triangle = edges("A", "B", "A", "C", "B", "C");
        CliqueEnumerationLimits timeLimit =
                new CliqueEnumerationLimits(Duration.ofNanos(1), 10, 1024);
        AtomicLong ticks = new AtomicLong();

        CliqueEnumerationResult timedOut = enumerator.enumerate(
                nodes, triangle, timeLimit, () -> false, ticks::getAndIncrement);
        CliqueEnumerationResult cancelled = enumerator.enumerate(
                nodes, triangle, CliqueEnumerationLimits.DEFAULT, () -> true);
        CliqueEnumerationResult failed = enumerator.enumerate(
                nodes,
                triangle,
                CliqueEnumerationLimits.DEFAULT,
                () -> {
                    throw new IllegalStateException("controlled test failure");
                });

        assertEquals(CliqueEnumerationStatus.TIME_LIMIT, timedOut.status());
        assertFalse(timedOut.isComplete());
        assertEquals(CliqueEnumerationStatus.CANCELLED, cancelled.status());
        assertFalse(cancelled.isComplete());
        assertEquals(CliqueEnumerationStatus.FAILED, failed.status());
        assertFalse(failed.isComplete());
        assertEquals(IllegalStateException.class.getName(), failed.terminationDetail());
    }

    @Test
    void externallyReportedMemoryLimitCannotBeMarkedCompleteAndPagesDoNotMutateCounts() {
        CliqueEnumerationResult memoryLimited = CliqueEnumerationResult.externalTermination(
                CliqueEnumerationStatus.MEMORY_LIMIT,
                CliqueEnumerationLimits.DEFAULT,
                "job_memory_limit");
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        List<NetworkEdge> overlappingCliques = edges(
                "A", "B", "A", "C", "B", "C", "B", "D", "C", "D");
        CliqueEnumerationResult complete = enumerator.enumerate(nodes, overlappingCliques);
        int retainedCount = complete.retainedCliqueCount();
        Map<String, Integer> participation = complete.observedParticipationCounts();

        assertEquals(CliqueEnumerationStatus.MEMORY_LIMIT, memoryLimited.status());
        assertFalse(memoryLimited.isComplete());
        assertEquals(0, memoryLimited.retainedCliqueCount());
        assertEquals(List.of(List.of("A", "B", "C")), complete.page(0, 1));
        assertEquals(retainedCount, complete.retainedCliqueCount());
        assertEquals(participation, complete.observedParticipationCounts());
    }

    private static List<List<String>> bruteForceReference(
            List<String> ids, boolean[][] adjacency) {
        List<List<String>> maximalCliques = new java.util.ArrayList<>();
        int subsetLimit = 1 << ids.size();
        for (int subset = 0; subset < subsetLimit; subset++) {
            if (Integer.bitCount(subset) < MaximalCliqueEnumerator.MINIMUM_CLIQUE_SIZE
                    || !isClique(subset, adjacency)) {
                continue;
            }
            boolean maximal = true;
            for (int candidate = 0; candidate < ids.size() && maximal; candidate++) {
                if ((subset & (1 << candidate)) != 0) {
                    continue;
                }
                boolean connectsToAll = true;
                for (int member = 0; member < ids.size(); member++) {
                    if ((subset & (1 << member)) != 0 && !adjacency[candidate][member]) {
                        connectsToAll = false;
                        break;
                    }
                }
                if (connectsToAll) {
                    maximal = false;
                }
            }
            if (maximal) {
                List<String> members = new java.util.ArrayList<>();
                for (int member = 0; member < ids.size(); member++) {
                    if ((subset & (1 << member)) != 0) {
                        members.add(ids.get(member));
                    }
                }
                maximalCliques.add(List.copyOf(members));
            }
        }
        maximalCliques.sort((left, right) ->
                String.join(",", left).compareTo(String.join(",", right)));
        return List.copyOf(maximalCliques);
    }

    private static boolean isClique(int subset, boolean[][] adjacency) {
        for (int left = 0; left < adjacency.length; left++) {
            if ((subset & (1 << left)) == 0) {
                continue;
            }
            for (int right = left + 1; right < adjacency.length; right++) {
                if ((subset & (1 << right)) != 0 && !adjacency[left][right]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<NetworkNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, true))
                .toList();
    }

    private static List<NetworkEdge> edges(String... endpoints) {
        if (endpoints.length % 2 != 0) {
            throw new IllegalArgumentException("edge endpoints must be source/target pairs");
        }
        java.util.ArrayList<NetworkEdge> edges = new java.util.ArrayList<>();
        for (int index = 0; index < endpoints.length; index += 2) {
            edges.add(new NetworkEdge(endpoints[index], endpoints[index + 1], "following"));
        }
        return List.copyOf(edges);
    }
}
