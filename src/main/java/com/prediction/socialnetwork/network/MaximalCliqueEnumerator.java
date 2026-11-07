package com.prediction.socialnetwork.network;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Deterministic Bron-Kerbosch enumeration for small, controlled graphs.
 *
 * <p>Call limits bound runtime, enumerated clique count, and retained
 * membership bytes. Once the membership budget is filled, summary counting
 * continues without retaining additional member lists. These limits do not
 * impose a process-memory limit; real datasets must run in an externally
 * memory-constrained worker.</p>
 */
final class MaximalCliqueEnumerator {

    static final int MINIMUM_CLIQUE_SIZE = 3;

    CliqueEnumerationResult enumerate(
            List<NetworkNode> nodes, List<NetworkEdge> directedEdges) {
        return enumerate(nodes, directedEdges, CliqueEnumerationLimits.DEFAULT);
    }

    CliqueEnumerationResult enumerate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits limits) {
        return enumerate(nodes, directedEdges, limits, () -> false, System::nanoTime);
    }

    CliqueEnumerationResult enumerate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits limits,
            BooleanSupplier cancellationRequested) {
        return enumerate(nodes, directedEdges, limits, cancellationRequested, System::nanoTime);
    }

    CliqueEnumerationResult enumerate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits limits,
            BooleanSupplier cancellationRequested,
            LongSupplier nanoTime) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(directedEdges, "directedEdges");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(nanoTime, "nanoTime");

        EnumerationState state = new EnumerationState(
                limits, cancellationRequested, nanoTime, nanoTime.getAsLong());
        try {
            Map<String, Set<String>> adjacency = adjacency(nodes, directedEdges);
            adjacency.keySet().forEach(node -> state.nodeParticipationCounts.put(node, 0));
            bronKerbosch(
                    new TreeSet<>(),
                    new TreeSet<>(adjacency.keySet()),
                    new TreeSet<>(),
                    adjacency,
                    state);
            if (state.status == null) {
                state.cliques.sort(MaximalCliqueEnumerator::compareCliques);
                state.stopIfNeeded();
                if (state.status == null) {
                    state.status = CliqueEnumerationStatus.COMPLETED;
                }
            }
        } catch (RuntimeException exception) {
            state.status = CliqueEnumerationStatus.FAILED;
            state.terminationDetail = exception.getClass().getName();
        }
        return new CliqueEnumerationResult(
                state.cliques,
                state.status,
                limits,
                state.encodedCliqueArrayBytes,
                state.terminationDetail,
                state.enumeratedCliqueCount,
                state.cliqueSizeDistribution,
                state.nodeParticipationCounts,
                state.membershipListComplete);
    }

    private static Map<String, Set<String>> adjacency(
            List<NetworkNode> nodes, List<NetworkEdge> directedEdges) {
        Map<String, Set<String>> adjacency = new TreeMap<>();
        nodes.forEach(node -> adjacency.computeIfAbsent(node.id(), ignored -> new TreeSet<>()));
        for (NetworkEdge edge : directedEdges) {
            Set<String> sourceNeighbors = adjacency.get(edge.source());
            Set<String> targetNeighbors = adjacency.get(edge.target());
            if (sourceNeighbors == null || targetNeighbors == null
                    || edge.source().equals(edge.target())) {
                continue;
            }
            sourceNeighbors.add(edge.target());
            targetNeighbors.add(edge.source());
        }
        return adjacency;
    }

    private static void bronKerbosch(
            Set<String> current,
            Set<String> candidates,
            Set<String> excluded,
            Map<String, Set<String>> adjacency,
            EnumerationState state) {
        if (state.stopIfNeeded()) {
            return;
        }
        if (candidates.isEmpty() && excluded.isEmpty()) {
            if (current.size() >= MINIMUM_CLIQUE_SIZE) {
                recordClique(List.copyOf(current), state);
            }
            return;
        }

        String pivot = choosePivot(candidates, excluded, adjacency);
        Set<String> pivotNeighbors = pivot == null ? Set.of() : adjacency.get(pivot);
        List<String> extensions = new ArrayList<>(candidates);
        extensions.removeAll(pivotNeighbors);
        for (String node : extensions) {
            if (state.stopIfNeeded()) {
                return;
            }
            Set<String> nextCurrent = new TreeSet<>(current);
            nextCurrent.add(node);
            Set<String> nextCandidates = intersection(candidates, adjacency.get(node));
            Set<String> nextExcluded = intersection(excluded, adjacency.get(node));
            bronKerbosch(nextCurrent, nextCandidates, nextExcluded, adjacency, state);
            if (state.status != null) {
                return;
            }
            candidates.remove(node);
            excluded.add(node);
        }
    }

    private static void recordClique(List<String> clique, EnumerationState state) {
        if (state.enumeratedCliqueCount >= state.limits.maxCliques()) {
            state.status = CliqueEnumerationStatus.CLIQUE_LIMIT;
            return;
        }

        state.enumeratedCliqueCount++;
        state.cliqueSizeDistribution.merge(clique.size(), 1, Integer::sum);
        clique.forEach(node -> state.nodeParticipationCounts.merge(node, 1, Integer::sum));

        if (state.membershipListComplete) {
            long addedBytes = cliqueArrayBytes(clique)
                    + (state.cliques.isEmpty() ? 0 : 1);
            if (addedBytes > state.limits.maxCliqueArrayBytes()
                    - state.encodedCliqueArrayBytes) {
                state.membershipListComplete = false;
            } else {
                state.cliques.add(clique);
                state.encodedCliqueArrayBytes += addedBytes;
            }
        }
    }

    private static long cliqueArrayBytes(List<String> clique) {
        long bytes = 2;
        for (int index = 0; index < clique.size(); index++) {
            if (index > 0) {
                bytes++;
            }
            bytes += 2L + JsonStringEncoder.getInstance()
                    .quoteAsUTF8(clique.get(index)).length;
        }
        return bytes;
    }

    private static String choosePivot(
            Set<String> candidates,
            Set<String> excluded,
            Map<String, Set<String>> adjacency) {
        Set<String> possiblePivots = new TreeSet<>(candidates);
        possiblePivots.addAll(excluded);
        String bestPivot = null;
        int bestCandidateNeighbors = -1;
        for (String pivot : possiblePivots) {
            int candidateNeighbors = 0;
            for (String neighbor : adjacency.get(pivot)) {
                if (candidates.contains(neighbor)) {
                    candidateNeighbors++;
                }
            }
            if (candidateNeighbors > bestCandidateNeighbors) {
                bestPivot = pivot;
                bestCandidateNeighbors = candidateNeighbors;
            }
        }
        return bestPivot;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> intersection = new TreeSet<>(left);
        intersection.retainAll(right);
        return intersection;
    }

    private static final class EnumerationState {
        private final CliqueEnumerationLimits limits;
        private final BooleanSupplier cancellationRequested;
        private final LongSupplier nanoTime;
        private final long startedAt;
        private final List<List<String>> cliques = new ArrayList<>();
        private final Map<Integer, Integer> cliqueSizeDistribution = new TreeMap<>();
        private final Map<String, Integer> nodeParticipationCounts = new TreeMap<>();
        private long encodedCliqueArrayBytes = 2;
        private long enumeratedCliqueCount;
        private boolean membershipListComplete = true;
        private CliqueEnumerationStatus status;
        private String terminationDetail;

        private EnumerationState(
                CliqueEnumerationLimits limits,
                BooleanSupplier cancellationRequested,
                LongSupplier nanoTime,
                long startedAt) {
            this.limits = limits;
            this.cancellationRequested = cancellationRequested;
            this.nanoTime = nanoTime;
            this.startedAt = startedAt;
        }

        private boolean stopIfNeeded() {
            if (status != null) {
                return true;
            }
            if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
                status = CliqueEnumerationStatus.CANCELLED;
            } else if (nanoTime.getAsLong() - startedAt >= limits.timeLimitNanos()) {
                status = CliqueEnumerationStatus.TIME_LIMIT;
            }
            return status != null;
        }
    }

    private static int compareCliques(List<String> left, List<String> right) {
        int sharedLength = Math.min(left.size(), right.size());
        for (int index = 0; index < sharedLength; index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
