package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Summarizes cross-community connections from a complete graph and an existing partition. */
public final class SocialNetworkBridgeCandidateCalculator {

    private SocialNetworkBridgeCandidateCalculator() {
    }

    public static List<Candidate> calculate(
            SocialNetworkGraphResult graph, Map<String, String> communityByNodeId) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(communityByNodeId, "communityByNodeId");

        Set<String> profileIds = graph.nodes().stream()
                .filter(node -> node.nodeType() == NetworkNodeType.PROFILE)
                .map(NetworkNode::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, CandidateAccumulator> candidates = new HashMap<>();
        Set<DirectedPair> uniqueRelationships = new HashSet<>();

        for (NetworkEdge edge : graph.edges()) {
            if (edge.source().equals(edge.target())
                    || !uniqueRelationships.add(new DirectedPair(edge.source(), edge.target()))) {
                continue;
            }
            String sourceCommunity = assignedCommunity(communityByNodeId, edge.source());
            String targetCommunity = assignedCommunity(communityByNodeId, edge.target());
            if (sourceCommunity == null || targetCommunity == null
                    || sourceCommunity.equals(targetCommunity)) {
                continue;
            }

            if (profileIds.contains(edge.source())) {
                candidates.computeIfAbsent(
                                edge.source(),
                                id -> new CandidateAccumulator(id, sourceCommunity))
                        .add(targetCommunity, edge.target(), true);
            }
            if (profileIds.contains(edge.target())) {
                candidates.computeIfAbsent(
                                edge.target(),
                                id -> new CandidateAccumulator(id, targetCommunity))
                        .add(sourceCommunity, edge.source(), false);
            }
        }

        return candidates.values().stream()
                .map(CandidateAccumulator::toCandidate)
                .sorted(Comparator
                        .comparingInt(Candidate::connectedCommunityCount).reversed()
                        .thenComparing(Comparator.comparingInt(
                                Candidate::directedRelationshipCount).reversed())
                        .thenComparing(Candidate::accountId))
                .toList();
    }

    private static String assignedCommunity(
            Map<String, String> communityByNodeId, String nodeId) {
        String communityId = communityByNodeId.get(nodeId);
        return communityId == null || communityId.isBlank() ? null : communityId;
    }

    private record DirectedPair(String source, String target) {
    }

    private static final class CandidateAccumulator {

        private final String accountId;
        private final String communityId;
        private final Set<String> otherAccountIds = new HashSet<>();
        private final Map<String, CommunityAccumulator> communities = new HashMap<>();

        private CandidateAccumulator(String accountId, String communityId) {
            this.accountId = accountId;
            this.communityId = communityId;
        }

        private void add(String otherCommunityId, String otherAccountId, boolean outgoing) {
            otherAccountIds.add(otherAccountId);
            communities.computeIfAbsent(otherCommunityId, CommunityAccumulator::new)
                    .add(otherAccountId, outgoing);
        }

        private Candidate toCandidate() {
            List<CommunitySummary> communitySummaries = communities.values().stream()
                    .map(CommunityAccumulator::toSummary)
                    .sorted(Comparator.comparing(CommunitySummary::communityId))
                    .toList();
            int outgoing = communitySummaries.stream()
                    .mapToInt(CommunitySummary::outgoingRelationshipCount)
                    .sum();
            int incoming = communitySummaries.stream()
                    .mapToInt(CommunitySummary::incomingRelationshipCount)
                    .sum();
            return new Candidate(
                    accountId,
                    communityId,
                    communitySummaries.size(),
                    otherAccountIds.size(),
                    outgoing,
                    incoming,
                    outgoing + incoming,
                    communitySummaries);
        }
    }

    private static final class CommunityAccumulator {

        private final String communityId;
        private final Set<String> otherAccountIds = new HashSet<>();
        private int outgoingRelationshipCount;
        private int incomingRelationshipCount;

        private CommunityAccumulator(String communityId) {
            this.communityId = communityId;
        }

        private void add(String otherAccountId, boolean outgoing) {
            otherAccountIds.add(otherAccountId);
            if (outgoing) {
                outgoingRelationshipCount++;
            } else {
                incomingRelationshipCount++;
            }
        }

        private CommunitySummary toSummary() {
            return new CommunitySummary(
                    communityId,
                    otherAccountIds.size(),
                    outgoingRelationshipCount,
                    incomingRelationshipCount,
                    outgoingRelationshipCount + incomingRelationshipCount);
        }
    }

    public record Candidate(
            String accountId,
            String communityId,
            int connectedCommunityCount,
            int distinctOtherAccountCount,
            int outgoingRelationshipCount,
            int incomingRelationshipCount,
            int directedRelationshipCount,
            List<CommunitySummary> communities) {
        public Candidate {
            communities = List.copyOf(communities);
        }
    }

    public record CommunitySummary(
            String communityId,
            int distinctOtherAccountCount,
            int outgoingRelationshipCount,
            int incomingRelationshipCount,
            int directedRelationshipCount) {
    }
}
