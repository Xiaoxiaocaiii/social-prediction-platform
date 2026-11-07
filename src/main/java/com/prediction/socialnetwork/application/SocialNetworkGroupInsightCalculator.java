package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.NetworkEdge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives bounded summaries from existing results without running an algorithm. */
public final class SocialNetworkGroupInsightCalculator {

    private SocialNetworkGroupInsightCalculator() {
    }

    public static CliqueInsights clique(CliqueEnumerationResult result, int offset, int pageSize) {
        if (!result.isComplete() || !result.membershipListComplete()) {
            throw new IllegalArgumentException("Complete clique memberships are required");
        }
        if (offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid maximum clique page");
        }
        List<Participant> participants = result.nodeParticipationCounts().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(entry -> new Participant(entry.getKey(), entry.getValue()))
                .toList();
        String first = participants.isEmpty() ? null : participants.get(0).nodeId();
        String second = participants.size() < 2 ? null : participants.get(1).nodeId();
        int maxSize = result.largestObservedCliqueSize();
        int maximumCount = 0;
        int pairCount = 0;
        Set<String> common = null;
        List<MaximumClique> page = new ArrayList<>();
        for (int index = 0; index < result.cliques().size(); index++) {
            List<String> members = result.cliques().get(index);
            if (first != null && second != null
                    && members.contains(first) && members.contains(second)) {
                pairCount++;
            }
            if (members.size() != maxSize) {
                continue;
            }
            if (common == null) {
                common = new HashSet<>(members);
            } else {
                common.retainAll(members);
            }
            if (maximumCount >= offset && page.size() < pageSize) {
                page.add(new MaximumClique(index, members));
            }
            maximumCount++;
        }
        List<String> commonMembers = common == null ? List.of() : common.stream().sorted().toList();
        return new CliqueInsights(
                result.enumeratedCliqueCount(), participants, first, second, pairCount,
                maxSize, maximumCount, commonMembers, offset, pageSize, page);
    }

    public static List<CommunityPair> communityPairs(
            List<NetworkEdge> edges, Map<String, String> membership) {
        Set<NodePair> unique = new HashSet<>();
        Map<CommunityKey, Integer> counts = new HashMap<>();
        for (NetworkEdge edge : edges) {
            String firstCommunity = membership.get(edge.source());
            String secondCommunity = membership.get(edge.target());
            if (edge.source().equals(edge.target()) || firstCommunity == null
                    || secondCommunity == null || firstCommunity.equals(secondCommunity)) {
                continue;
            }
            NodePair pair = NodePair.of(edge.source(), edge.target());
            if (unique.add(pair)) {
                counts.merge(CommunityKey.of(firstCommunity, secondCommunity), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new CommunityPair(
                        entry.getKey().first(), entry.getKey().second(), entry.getValue()))
                .sorted(Comparator.comparingInt(CommunityPair::tieCount).reversed()
                        .thenComparing(CommunityPair::first)
                        .thenComparing(CommunityPair::second))
                .toList();
    }

    public static SharedCliques sharedCliques(
            CliqueEnumerationResult result, String first, String second, int offset, int pageSize) {
        if (!result.isComplete() || !result.membershipListComplete()) {
            throw new IllegalArgumentException("Complete clique memberships are required");
        }
        if (first == null || first.isBlank() || second == null || second.isBlank()
                || first.equals(second) || offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid shared clique query");
        }
        int total = 0;
        List<MaximumClique> page = new ArrayList<>();
        for (int index = 0; index < result.cliques().size(); index++) {
            List<String> members = result.cliques().get(index);
            if (!members.contains(first) || !members.contains(second)) {
                continue;
            }
            if (total >= offset && page.size() < pageSize) {
                page.add(new MaximumClique(index, members));
            }
            total++;
        }
        return new SharedCliques(first, second, total, offset, pageSize, page);
    }

    private record NodePair(String first, String second) {
        private static NodePair of(String first, String second) {
            return first.compareTo(second) <= 0
                    ? new NodePair(first, second) : new NodePair(second, first);
        }
    }

    private record CommunityKey(String first, String second) {
        private static CommunityKey of(String first, String second) {
            return first.compareTo(second) <= 0
                    ? new CommunityKey(first, second) : new CommunityKey(second, first);
        }
    }

    public record Participant(String nodeId, int count) {
    }

    public record MaximumClique(int index, List<String> members) {
        public MaximumClique {
            members = List.copyOf(members);
        }
    }

    public record CliqueInsights(
            long totalCount, List<Participant> participants, String firstNodeId,
            String secondNodeId, int commonCliqueCount, int maximumSize, int maximumCount,
            List<String> commonMembers, int offset, int pageSize, List<MaximumClique> maximumCliques) {
        public CliqueInsights {
            participants = List.copyOf(participants);
            commonMembers = List.copyOf(commonMembers);
            maximumCliques = List.copyOf(maximumCliques);
        }
    }

    public record CommunityPair(String first, String second, int tieCount) {
    }

    public record SharedCliques(
            String first, String second, int totalCount, int offset,
            int pageSize, List<MaximumClique> cliques) {
        public SharedCliques {
            cliques = List.copyOf(cliques);
        }
    }
}
