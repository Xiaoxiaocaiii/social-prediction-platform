package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Independently checks retained clique memberships against the directed input.
 *
 * <p>This verifier checks validity and maximality of every retained clique and
 * recomputes all summaries. It does not prove that an enumerator omitted no
 * cliques; that guarantee still depends on a completed enumeration.</p>
 */
public final class CliqueEnumerationIndependentVerifier {

    private static final int MINIMUM_CLIQUE_SIZE = 3;

    public VerificationReport verify(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationResult result) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(directedEdges, "directedEdges");
        Objects.requireNonNull(result, "result");
        if (!result.isComplete() || !result.membershipListComplete()) {
            throw new IllegalArgumentException(
                    "Independent verification requires complete enumeration and memberships");
        }
        if (result.enumeratedCliqueCount() != result.cliques().size()) {
            throw new IllegalArgumentException(
                    "Complete membership count does not match the enumeration total");
        }

        Map<String, Integer> nodeIndexes = new HashMap<>();
        for (NetworkNode node : nodes) {
            Objects.requireNonNull(node, "node");
            if (nodeIndexes.putIfAbsent(node.id(), nodeIndexes.size()) != null) {
                throw new IllegalArgumentException("Duplicate input node id");
            }
        }

        BitSet[] adjacency = new BitSet[nodeIndexes.size()];
        for (int index = 0; index < adjacency.length; index++) {
            adjacency[index] = new BitSet(adjacency.length);
        }
        int projectedEdgeCount = 0;
        int ignoredEdgeCount = 0;
        for (NetworkEdge edge : directedEdges) {
            Objects.requireNonNull(edge, "directed edge");
            Integer source = nodeIndexes.get(edge.source());
            Integer target = nodeIndexes.get(edge.target());
            if (source == null || target == null || source.equals(target)) {
                ignoredEdgeCount++;
                continue;
            }
            if (!adjacency[source].get(target)) {
                projectedEdgeCount++;
                adjacency[source].set(target);
                adjacency[target].set(source);
            }
        }

        BitSet allNodes = new BitSet(adjacency.length);
        allNodes.set(0, adjacency.length);
        Set<List<String>> uniqueCliques = new HashSet<>();
        Map<Integer, Integer> recomputedSizeDistribution = new TreeMap<>();
        Map<String, Integer> recomputedParticipation = new TreeMap<>();
        nodeIndexes.keySet().forEach(id -> recomputedParticipation.put(id, 0));
        long checkedMemberPairs = 0;
        long memberIncidences = 0;
        int minimumSize = Integer.MAX_VALUE;
        int maximumSize = 0;

        for (int cliqueIndex = 0; cliqueIndex < result.cliques().size(); cliqueIndex++) {
            List<String> clique = result.cliques().get(cliqueIndex);
            if (clique.size() < MINIMUM_CLIQUE_SIZE
                    || new HashSet<>(clique).size() != clique.size()) {
                throw new IllegalStateException(
                        "Clique " + cliqueIndex + " has an invalid size or repeated member");
            }
            List<String> canonicalClique = clique.stream().sorted().toList();
            if (!uniqueCliques.add(canonicalClique)) {
                throw new IllegalStateException("Duplicate clique at index " + cliqueIndex);
            }

            List<Integer> indexes = new ArrayList<>(clique.size());
            for (String member : clique) {
                Integer index = nodeIndexes.get(member);
                if (index == null) {
                    throw new IllegalStateException(
                            "Clique " + cliqueIndex + " contains an unknown node");
                }
                indexes.add(index);
            }
            for (int left = 0; left < indexes.size(); left++) {
                for (int right = left + 1; right < indexes.size(); right++) {
                    checkedMemberPairs++;
                    if (!adjacency[indexes.get(left)].get(indexes.get(right))) {
                        throw new IllegalStateException(
                                "Clique " + cliqueIndex + " contains a missing tie");
                    }
                }
            }

            BitSet commonNeighbors = (BitSet) allNodes.clone();
            for (int index : indexes) {
                commonNeighbors.and(adjacency[index]);
            }
            for (int index : indexes) {
                commonNeighbors.clear(index);
            }
            if (!commonNeighbors.isEmpty()) {
                throw new IllegalStateException(
                        "Clique " + cliqueIndex + " is not maximal");
            }

            recomputedSizeDistribution.merge(clique.size(), 1, Integer::sum);
            for (String member : clique) {
                recomputedParticipation.merge(member, 1, Integer::sum);
            }
            memberIncidences += clique.size();
            minimumSize = Math.min(minimumSize, clique.size());
            maximumSize = Math.max(maximumSize, clique.size());
        }

        long participationTotal = recomputedParticipation.values().stream()
                .mapToLong(Integer::longValue)
                .sum();
        long distributionMembershipTotal = recomputedSizeDistribution.entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey() * entry.getValue())
                .sum();
        if (!recomputedSizeDistribution.equals(result.cliqueSizeDistribution())) {
            throw new IllegalStateException("Clique size distribution does not match memberships");
        }
        if (!recomputedParticipation.equals(result.nodeParticipationCounts())) {
            throw new IllegalStateException("Node participation summary does not match memberships");
        }
        if (participationTotal != distributionMembershipTotal
                || participationTotal != memberIncidences) {
            throw new IllegalStateException("Clique and node participation totals do not match");
        }

        return new VerificationReport(
                nodeIndexes.size(),
                projectedEdgeCount,
                ignoredEdgeCount,
                result.cliques().size(),
                memberIncidences,
                checkedMemberPairs,
                result.cliques().size(),
                minimumSize == Integer.MAX_VALUE ? 0 : minimumSize,
                maximumSize);
    }

    public record VerificationReport(
            int inputNodeCount,
            int projectedUndirectedEdgeCount,
            int ignoredDirectedEdgeCount,
            int verifiedCliqueCount,
            long verifiedMemberIncidences,
            long checkedMemberPairs,
            int checkedMaximalityCount,
            int minimumCliqueSize,
            int maximumCliqueSize) {
    }
}
