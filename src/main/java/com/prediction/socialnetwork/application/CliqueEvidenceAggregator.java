package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class CliqueEvidenceAggregator {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Comparator<ProfileSourceReference> PROFILE_SOURCE_ORDER =
            Comparator.comparing(ProfileSourceReference::sourceFileName)
                    .thenComparingInt(ProfileSourceReference::sourceRowNumber);
    private static final Comparator<DirectedRelationshipSourceReference>
            RELATIONSHIP_SOURCE_ORDER =
                    Comparator.comparing(DirectedRelationshipSourceReference::sourceId)
                            .thenComparing(DirectedRelationshipSourceReference::targetId)
                            .thenComparing(
                                    DirectedRelationshipSourceReference::sourceFileName)
                            .thenComparingInt(
                                    DirectedRelationshipSourceReference::sourceRowNumber)
                            .thenComparingInt(
                                    DirectedRelationshipSourceReference::sourceArrayIndex);

    private final Map<String, NodeEvidenceSource> nodesById;
    private final Map<TieKey, List<DirectedRelationshipSourceReference>> evidenceByTie;
    private final Map<TieKey, List<DirectedRelationshipSourceReference>>
            duplicateEvidenceByTie;

    public CliqueEvidenceAggregator(
            List<NetworkNode> nodes,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(relationships, "relationships");

        Map<String, List<ProfileSourceReference>> profileSourcesById = new HashMap<>();
        for (NormalizedUser user : users) {
            if (user.userId() == null || user.userId().isBlank()) {
                continue;
            }
            requireSource(user.sourceFileName(), user.sourceRowNumber(), "profile");
            profileSourcesById.computeIfAbsent(user.userId(), ignored -> new ArrayList<>())
                    .add(new ProfileSourceReference(
                            user.sourceFileName(),
                            user.sourceRowNumber(),
                            user.normalizationStatus()));
        }
        profileSourcesById.values().forEach(sources -> sources.sort(PROFILE_SOURCE_ORDER));

        Map<String, NodeEvidenceSource> nodeSources = new LinkedHashMap<>();
        for (NetworkNode node : nodes.stream().sorted(Comparator.comparing(NetworkNode::id))
                .toList()) {
            Objects.requireNonNull(node, "node");
            List<ProfileSourceReference> profileSources =
                    profileSourcesById.getOrDefault(node.id(), List.of());
            if (node.nodeType() == NetworkNodeType.PROFILE && profileSources.isEmpty()) {
                throw new IllegalArgumentException(
                        "Profile node has no source record: " + node.id());
            }
            if (nodeSources.putIfAbsent(
                    node.id(),
                    new NodeEvidenceSource(node.nodeType(), List.copyOf(profileSources))) != null) {
                throw new IllegalArgumentException("Duplicate network node: " + node.id());
            }
        }
        nodesById = Map.copyOf(nodeSources);

        Map<TieKey, List<DirectedRelationshipSourceReference>> tieSources = new HashMap<>();
        Map<TieKey, List<DirectedRelationshipSourceReference>> duplicateSources =
                new HashMap<>();
        for (NormalizedRelationship relationship : relationships) {
            Objects.requireNonNull(relationship, "relationship");
            if (relationship.relationStatus() == RelationStatus.DUPLICATE
                    && !relationship.analysisIncluded()) {
                validateRelationshipEvidence(relationship);
                TieKey tie = TieKey.of(
                        relationship.sourceUserId(), relationship.targetUserId());
                duplicateSources.computeIfAbsent(tie, ignored -> new ArrayList<>())
                        .add(sourceReference(relationship));
                continue;
            }
            if (!relationship.analysisIncluded()) {
                continue;
            }
            if (relationship.relationStatus() != RelationStatus.VALID) {
                throw new IllegalArgumentException(
                        "Included relationship is not valid at its source");
            }
            validateRelationshipEvidence(relationship);
            TieKey tie = TieKey.of(
                    relationship.sourceUserId(), relationship.targetUserId());
            tieSources.computeIfAbsent(tie, ignored -> new ArrayList<>())
                    .add(sourceReference(relationship));
        }
        tieSources.values().forEach(sources -> sources.sort(RELATIONSHIP_SOURCE_ORDER));
        duplicateSources.values().forEach(sources -> sources.sort(RELATIONSHIP_SOURCE_ORDER));
        Map<TieKey, List<DirectedRelationshipSourceReference>> immutableTieSources =
                new HashMap<>();
        tieSources.forEach((tie, sources) ->
                immutableTieSources.put(tie, List.copyOf(sources)));
        evidenceByTie = Map.copyOf(immutableTieSources);
        Map<TieKey, List<DirectedRelationshipSourceReference>> immutableDuplicateSources =
                new HashMap<>();
        duplicateSources.forEach((tie, sources) ->
                immutableDuplicateSources.put(tie, List.copyOf(sources)));
        duplicateEvidenceByTie = Map.copyOf(immutableDuplicateSources);
    }

    public List<CliqueEvidence> aggregatePage(
            CliqueEnumerationResult result, int offset, int pageSize) {
        requireCompleteMemberships(result);
        if (offset < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "offset must be non-negative and pageSize must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
        List<List<String>> members = result.page(offset, pageSize);
        List<CliqueEvidence> evidence = new ArrayList<>(members.size());
        for (int index = 0; index < members.size(); index++) {
            evidence.add(aggregateClique(offset + index, members.get(index)));
        }
        return List.copyOf(evidence);
    }

    public void forEachCompleteResult(
            CliqueEnumerationResult result, Consumer<CliqueEvidence> consumer) {
        requireCompleteMemberships(result);
        Objects.requireNonNull(consumer, "consumer");
        for (int index = 0; index < result.cliques().size(); index++) {
            consumer.accept(aggregateClique(index, result.cliques().get(index)));
        }
    }

    public EvidencePage aggregateKnownCliqueSection(
            int cliqueIndex,
            List<String> cliqueMembers,
            String section,
            int offset,
            int pageSize) {
        if (cliqueIndex < 0 || offset < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid clique evidence page");
        }
        if (!"members".equals(section) && !"ties".equals(section)) {
            throw new IllegalArgumentException("section must be members or ties");
        }
        List<String> memberIds = orderedCliqueMembers(cliqueMembers);
        int totalCount = "members".equals(section)
                ? memberIds.size()
                : Math.toIntExact((long) memberIds.size() * (memberIds.size() - 1) / 2);
        int endExclusive = (int) Math.min(totalCount, (long) offset + pageSize);
        List<MemberEvidence> memberPage = new ArrayList<>();
        List<TieEvidence> tiePage = new ArrayList<>();
        if ("members".equals(section)) {
            for (int index = Math.min(offset, totalCount); index < endExclusive; index++) {
                memberPage.add(memberEvidence(memberIds.get(index)));
            }
        } else if (offset < endExclusive) {
            int ordinal = 0;
            for (int leftIndex = 0;
                    leftIndex < memberIds.size() && ordinal < endExclusive;
                    leftIndex++) {
                for (int rightIndex = leftIndex + 1;
                        rightIndex < memberIds.size() && ordinal < endExclusive;
                        rightIndex++, ordinal++) {
                    if (ordinal >= offset) {
                        tiePage.add(tieEvidence(
                                memberIds.get(leftIndex), memberIds.get(rightIndex)));
                    }
                }
            }
        }
        return new EvidencePage(section, offset, pageSize, totalCount, memberPage, tiePage);
    }

    private CliqueEvidence aggregateClique(int cliqueIndex, List<String> cliqueMembers) {
        List<String> memberIds = orderedCliqueMembers(cliqueMembers);
        List<MemberEvidence> members = memberIds.stream()
                .map(this::memberEvidence)
                .toList();
        List<TieEvidence> ties = new ArrayList<>(
                Math.toIntExact((long) memberIds.size() * (memberIds.size() - 1) / 2));
        for (int leftIndex = 0; leftIndex < memberIds.size(); leftIndex++) {
            String left = memberIds.get(leftIndex);
            for (int rightIndex = leftIndex + 1;
                    rightIndex < memberIds.size();
                    rightIndex++) {
                ties.add(tieEvidence(left, memberIds.get(rightIndex)));
            }
        }
        return new CliqueEvidence(cliqueIndex, members, ties);
    }

    private List<String> orderedCliqueMembers(List<String> cliqueMembers) {
        Objects.requireNonNull(cliqueMembers, "cliqueMembers");
        List<String> memberIds = cliqueMembers.stream().sorted().toList();
        if (memberIds.size() < 3
                || memberIds.stream().anyMatch(id -> id == null || id.isBlank())
                || memberIds.stream().distinct().count() != memberIds.size()) {
            throw new IllegalArgumentException("Clique members are invalid");
        }
        memberIds.forEach(this::requireMember);
        return memberIds;
    }

    private MemberEvidence memberEvidence(String memberId) {
        NodeEvidenceSource source = requireMember(memberId);
        return new MemberEvidence(memberId, source.nodeType().value(), source.profileSources());
    }

    private NodeEvidenceSource requireMember(String memberId) {
        NodeEvidenceSource source = nodesById.get(memberId);
        if (source == null) {
            throw new IllegalStateException(
                    "Clique member is absent from the input network: " + memberId);
        }
        return source;
    }

    private TieEvidence tieEvidence(String left, String right) {
        TieKey tie = new TieKey(left, right);
        List<DirectedRelationshipSourceReference> supporting = evidenceByTie.get(tie);
        if (supporting == null || supporting.isEmpty()) {
            throw new IllegalStateException(
                    "Clique member pair has no source relationship evidence: "
                            + left + " -- " + right);
        }
        return new TieEvidence(
                left,
                right,
                1,
                supporting,
                duplicateEvidenceByTie.getOrDefault(tie, List.of()));
    }

    private void validateRelationshipEvidence(NormalizedRelationship relationship) {
        if (relationship.sourceFileName() == null
                || relationship.sourceFileName().isBlank()
                || relationship.sourceRowNumber() < 1
                || relationship.sourceUserId() == null
                || relationship.sourceUserId().isBlank()
                || relationship.targetUserId() == null
                || relationship.targetUserId().isBlank()
                || relationship.sourceUserId().equals(relationship.targetUserId())) {
            throw new IllegalArgumentException(
                    "Relationship evidence has invalid source coordinates or endpoints");
        }
        if (!nodesById.containsKey(relationship.sourceUserId())
                || !nodesById.containsKey(relationship.targetUserId())) {
            throw new IllegalArgumentException(
                    "Relationship evidence endpoint is absent from the network");
        }
        if (relationship.sourceArrayIndex() < -1) {
            throw new IllegalArgumentException(
                    "Relationship evidence has an invalid source array index");
        }
    }

    private static DirectedRelationshipSourceReference sourceReference(
            NormalizedRelationship relationship) {
        return new DirectedRelationshipSourceReference(
                relationship.sourceFileName(),
                relationship.sourceRowNumber(),
                relationship.sourceArrayIndex(),
                relationship.sourceUserId(),
                relationship.targetUserId());
    }

    private static void requireCompleteMemberships(CliqueEnumerationResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.isComplete() || !result.membershipListComplete()
                || result.enumeratedCliqueCount() != result.cliques().size()) {
            throw new IllegalArgumentException(
                    "Evidence aggregation requires a complete enumeration and member lists");
        }
    }

    private static void requireSource(String fileName, int rowNumber, String label) {
        if (fileName == null || fileName.isBlank() || rowNumber < 1) {
            throw new IllegalArgumentException(
                    label + " source file and positive row number are required");
        }
    }

    private record NodeEvidenceSource(
            NetworkNodeType nodeType,
            List<ProfileSourceReference> profileSources) {
    }

    private record TieKey(String leftId, String rightId) {

        private static TieKey of(String leftId, String rightId) {
            return leftId.compareTo(rightId) < 0
                    ? new TieKey(leftId, rightId)
                    : new TieKey(rightId, leftId);
        }
    }

    public record ProfileSourceReference(
            String sourceFileName,
            int sourceRowNumber,
            com.prediction.socialnetwork.normalization.NormalizationStatus normalizationStatus) {
    }

    public record DirectedRelationshipSourceReference(
            String sourceFileName,
            int sourceRowNumber,
            int sourceArrayIndex,
            String sourceId,
            String targetId) {

        public DirectedRelationshipSourceReference {
            if (sourceArrayIndex < -1) {
                throw new IllegalArgumentException("sourceArrayIndex must be at least -1");
            }
        }
    }

    public record MemberEvidence(
            String nodeId,
            String nodeType,
            List<ProfileSourceReference> profileSources) {

        public MemberEvidence {
            profileSources = List.copyOf(profileSources);
        }
    }

    public record TieEvidence(
            String leftNodeId,
            String rightNodeId,
            int weight,
            List<DirectedRelationshipSourceReference> supportingRelationships,
            List<DirectedRelationshipSourceReference> duplicateRelationshipRecords) {

        public TieEvidence {
            supportingRelationships = List.copyOf(supportingRelationships);
            duplicateRelationshipRecords = List.copyOf(duplicateRelationshipRecords);
        }
    }

    public record CliqueEvidence(
            int cliqueIndex,
            List<MemberEvidence> members,
            List<TieEvidence> ties) {

        public CliqueEvidence {
            members = List.copyOf(members);
            ties = List.copyOf(ties);
        }
    }

    public record EvidencePage(
            String section,
            int offset,
            int pageSize,
            int totalCount,
            List<MemberEvidence> members,
            List<TieEvidence> ties) {

        public EvidencePage {
            members = List.copyOf(members);
            ties = List.copyOf(ties);
        }
    }
}
