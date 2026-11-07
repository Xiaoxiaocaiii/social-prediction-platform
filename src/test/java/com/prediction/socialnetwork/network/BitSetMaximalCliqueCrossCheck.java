package com.prediction.socialnetwork.network;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Separate BitSet implementation used only for bounded full-result cross-checks.
 */
public final class BitSetMaximalCliqueCrossCheck {

    public CliqueEnumerationResult enumerate(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits limits) {
        List<String> nodeIds = nodes.stream()
                .map(NetworkNode::id)
                .distinct()
                .sorted()
                .toList();
        if (nodeIds.size() != nodes.size()) {
            throw new IllegalArgumentException("Cross-check input contains duplicate node ids");
        }
        Map<String, Integer> indexes = new java.util.HashMap<>();
        for (int index = 0; index < nodeIds.size(); index++) {
            indexes.put(nodeIds.get(index), index);
        }

        BitSet[] adjacency = new BitSet[nodeIds.size()];
        for (int index = 0; index < adjacency.length; index++) {
            adjacency[index] = new BitSet(adjacency.length);
        }
        for (NetworkEdge edge : directedEdges) {
            Integer source = indexes.get(edge.source());
            Integer target = indexes.get(edge.target());
            if (source == null || target == null || source.equals(target)) {
                continue;
            }
            adjacency[source].set(target);
            adjacency[target].set(source);
        }

        State state = new State(nodeIds, limits);
        BitSet candidates = new BitSet(nodeIds.size());
        candidates.set(0, nodeIds.size());
        enumerate(new BitSet(nodeIds.size()), candidates, new BitSet(nodeIds.size()),
                adjacency, state);
        if (state.status == null) {
            state.cliques.sort(BitSetMaximalCliqueCrossCheck::compareCliques);
            state.status = CliqueEnumerationStatus.COMPLETED;
        }
        return state.result();
    }

    private static void enumerate(
            BitSet current,
            BitSet candidates,
            BitSet excluded,
            BitSet[] adjacency,
            State state) {
        if (state.stopIfNeeded()) {
            return;
        }
        if (candidates.isEmpty() && excluded.isEmpty()) {
            if (current.cardinality() >= 3) {
                state.record(current);
            }
            return;
        }

        int pivot = choosePivot(candidates, excluded, adjacency);
        BitSet extensions = (BitSet) candidates.clone();
        if (pivot >= 0) {
            extensions.andNot(adjacency[pivot]);
        }
        for (int vertex = extensions.nextSetBit(0);
                vertex >= 0;
                vertex = extensions.nextSetBit(vertex + 1)) {
            if (state.stopIfNeeded()) {
                return;
            }
            BitSet nextCurrent = (BitSet) current.clone();
            nextCurrent.set(vertex);
            BitSet nextCandidates = (BitSet) candidates.clone();
            nextCandidates.and(adjacency[vertex]);
            BitSet nextExcluded = (BitSet) excluded.clone();
            nextExcluded.and(adjacency[vertex]);
            enumerate(nextCurrent, nextCandidates, nextExcluded, adjacency, state);
            if (state.status != null) {
                return;
            }
            candidates.clear(vertex);
            excluded.set(vertex);
        }
    }

    private static int choosePivot(
            BitSet candidates, BitSet excluded, BitSet[] adjacency) {
        BitSet possiblePivots = (BitSet) candidates.clone();
        possiblePivots.or(excluded);
        int bestPivot = -1;
        int bestCandidateNeighbors = -1;
        for (int pivot = possiblePivots.nextSetBit(0);
                pivot >= 0;
                pivot = possiblePivots.nextSetBit(pivot + 1)) {
            BitSet neighbors = (BitSet) candidates.clone();
            neighbors.and(adjacency[pivot]);
            int candidateNeighbors = neighbors.cardinality();
            if (candidateNeighbors > bestCandidateNeighbors) {
                bestPivot = pivot;
                bestCandidateNeighbors = candidateNeighbors;
            }
        }
        return bestPivot;
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

    private static final class State {
        private final List<String> nodeIds;
        private final CliqueEnumerationLimits limits;
        private final long startedAt = System.nanoTime();
        private final List<List<String>> cliques = new ArrayList<>();
        private final Map<Integer, Integer> sizeDistribution = new TreeMap<>();
        private final Map<String, Integer> participation = new TreeMap<>();
        private long encodedArrayBytes = 2;
        private long enumeratedCount;
        private boolean membershipListComplete = true;
        private CliqueEnumerationStatus status;
        private String terminationDetail;

        private State(List<String> nodeIds, CliqueEnumerationLimits limits) {
            this.nodeIds = nodeIds;
            this.limits = limits;
            nodeIds.forEach(id -> participation.put(id, 0));
        }

        private boolean stopIfNeeded() {
            if (status != null) {
                return true;
            }
            if (Thread.currentThread().isInterrupted()) {
                status = CliqueEnumerationStatus.CANCELLED;
            } else if (System.nanoTime() - startedAt >= limits.timeLimitNanos()) {
                status = CliqueEnumerationStatus.TIME_LIMIT;
            }
            return status != null;
        }

        private void record(BitSet current) {
            if (enumeratedCount >= limits.maxCliques()) {
                status = CliqueEnumerationStatus.CLIQUE_LIMIT;
                return;
            }
            List<String> clique = new ArrayList<>(current.cardinality());
            for (int vertex = current.nextSetBit(0);
                    vertex >= 0;
                    vertex = current.nextSetBit(vertex + 1)) {
                clique.add(nodeIds.get(vertex));
            }
            enumeratedCount++;
            sizeDistribution.merge(clique.size(), 1, Integer::sum);
            clique.forEach(id -> participation.merge(id, 1, Integer::sum));
            if (!membershipListComplete) {
                return;
            }
            long addedBytes = cliqueArrayBytes(clique) + (cliques.isEmpty() ? 0 : 1);
            if (addedBytes > limits.maxCliqueArrayBytes() - encodedArrayBytes) {
                membershipListComplete = false;
            } else {
                cliques.add(List.copyOf(clique));
                encodedArrayBytes += addedBytes;
            }
        }

        private long cliqueArrayBytes(List<String> clique) {
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

        private CliqueEnumerationResult result() {
            if (status == null) {
                status = CliqueEnumerationStatus.FAILED;
                terminationDetail = "cross_check_status_missing";
            }
            if (status == CliqueEnumerationStatus.FAILED && terminationDetail == null) {
                terminationDetail = "cross_check_failed";
            }
            return new CliqueEnumerationResult(
                    cliques,
                    status,
                    limits,
                    encodedArrayBytes,
                    terminationDetail,
                    enumeratedCount,
                    sizeDistribution,
                    participation,
                    membershipListComplete);
        }
    }
}
