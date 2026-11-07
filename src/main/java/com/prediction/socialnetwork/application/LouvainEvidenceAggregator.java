package com.prediction.socialnetwork.application;

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
import java.util.Set;
import java.util.TreeSet;

public final class LouvainEvidenceAggregator {

    public static final int MAX_PAGE_SIZE = CliqueEvidenceAggregator.MAX_PAGE_SIZE;

    private static final Comparator<
            CliqueEvidenceAggregator.DirectedRelationshipSourceReference>
            SOURCE_ORDER = Comparator
                    .comparing(
                            CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                    ::sourceId)
                    .thenComparing(
                            CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                    ::targetId)
                    .thenComparing(
                            CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                    ::sourceFileName)
                    .thenComparingInt(
                            CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                    ::sourceRowNumber)
                    .thenComparingInt(
                            CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                    ::sourceArrayIndex);

    private final Map<String, NetworkNodeType> nodeTypes;
    private final Map<String, List<CliqueEvidenceAggregator.ProfileSourceReference>>
            profileSourcesById;
    private final Map<TieKey,
            List<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>>
            sourcesByTie;
    private final Map<TieKey,
            List<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>>
            duplicatesByTie;

    public LouvainEvidenceAggregator(
            List<NetworkNode> nodes,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(relationships, "relationships");

        Map<String, NetworkNodeType> types = new HashMap<>();
        for (NetworkNode node : nodes) {
            Objects.requireNonNull(node, "node");
            if (types.putIfAbsent(node.id(), node.nodeType()) != null) {
                throw new IllegalArgumentException("Duplicate network node: " + node.id());
            }
        }
        nodeTypes = Map.copyOf(types);

        Map<String, List<CliqueEvidenceAggregator.ProfileSourceReference>> profileSources =
                new HashMap<>();
        for (NormalizedUser user : users) {
            Objects.requireNonNull(user, "user");
            if (user.userId() == null || user.userId().isBlank()) {
                continue;
            }
            requireSource(user.sourceFileName(), user.sourceRowNumber(), "profile");
            profileSources.computeIfAbsent(user.userId(), ignored -> new ArrayList<>())
                    .add(new CliqueEvidenceAggregator.ProfileSourceReference(
                            user.sourceFileName(),
                            user.sourceRowNumber(),
                            user.normalizationStatus()));
        }
        profileSources.values().forEach(sources -> sources.sort(
                Comparator.comparing(
                                CliqueEvidenceAggregator.ProfileSourceReference::sourceFileName)
                        .thenComparingInt(
                                CliqueEvidenceAggregator.ProfileSourceReference
                                        ::sourceRowNumber)));
        profileSourcesById = immutableLists(profileSources);

        Map<TieKey,
                List<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>> valid =
                        new HashMap<>();
        Map<TieKey,
                List<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>> duplicates =
                        new HashMap<>();
        for (NormalizedRelationship relationship : relationships) {
            Objects.requireNonNull(relationship, "relationship");
            if (relationship.relationStatus() == RelationStatus.DUPLICATE
                    && !relationship.analysisIncluded()) {
                validateRelationship(relationship);
                duplicates.computeIfAbsent(
                        TieKey.of(relationship.sourceUserId(), relationship.targetUserId()),
                        ignored -> new ArrayList<>())
                        .add(sourceReference(relationship));
            } else if (relationship.analysisIncluded()) {
                if (relationship.relationStatus() != RelationStatus.VALID) {
                    throw new IllegalArgumentException(
                            "Included relationship is not valid at its source");
                }
                validateRelationship(relationship);
                valid.computeIfAbsent(
                        TieKey.of(relationship.sourceUserId(), relationship.targetUserId()),
                        ignored -> new ArrayList<>())
                        .add(sourceReference(relationship));
            }
        }
        valid.values().forEach(sources -> sources.sort(SOURCE_ORDER));
        duplicates.values().forEach(sources -> sources.sort(SOURCE_ORDER));
        sourcesByTie = immutableLists(valid);
        duplicatesByTie = immutableLists(duplicates);
    }

    public EvidencePage page(
            List<String> communityMemberIds,
            String section,
            int offset,
            int pageSize) {
        validatePage(section, offset, pageSize);
        List<String> memberIds = orderedMembers(communityMemberIds);
        if ("members".equals(section)) {
            int end = (int) Math.min(memberIds.size(), (long) offset + pageSize);
            List<CliqueEvidenceAggregator.MemberEvidence> members = memberIds
                    .subList(Math.min(offset, memberIds.size()), end)
                    .stream()
                    .map(this::memberEvidence)
                    .toList();
            return new EvidencePage(section, offset, pageSize, memberIds.size(), members, List.of());
        }

        Set<String> selectedIds = Set.copyOf(memberIds);
        TreeSet<TieKey> internalTies = new TreeSet<>(
                Comparator.comparing(TieKey::leftId).thenComparing(TieKey::rightId));
        sourcesByTie.keySet().stream()
                .filter(tie -> selectedIds.contains(tie.leftId())
                        && selectedIds.contains(tie.rightId()))
                .forEach(internalTies::add);
        List<TieKey> ties = List.copyOf(internalTies);
        int end = (int) Math.min(ties.size(), (long) offset + pageSize);
        List<CliqueEvidenceAggregator.TieEvidence> tiePage = ties
                .subList(Math.min(offset, ties.size()), end)
                .stream()
                .map(this::tieEvidence)
                .toList();
        return new EvidencePage(section, offset, pageSize, ties.size(), List.of(), tiePage);
    }

    public EvidencePage between(
            List<String> firstMembers, List<String> secondMembers, int offset, int pageSize) {
        validatePage("ties", offset, pageSize);
        Set<String> first = Set.copyOf(orderedMembers(firstMembers));
        Set<String> second = Set.copyOf(orderedMembers(secondMembers));
        if (first.stream().anyMatch(second::contains)) {
            throw new IllegalArgumentException("Community memberships must be disjoint");
        }
        List<TieKey> ties = sourcesByTie.keySet().stream()
                .filter(tie -> (first.contains(tie.leftId()) && second.contains(tie.rightId()))
                        || (second.contains(tie.leftId()) && first.contains(tie.rightId())))
                .sorted(Comparator.comparing(TieKey::leftId).thenComparing(TieKey::rightId))
                .toList();
        int end = (int) Math.min(ties.size(), (long) offset + pageSize);
        List<CliqueEvidenceAggregator.TieEvidence> tiePage = ties
                .subList(Math.min(offset, ties.size()), end)
                .stream().map(this::tieEvidence).toList();
        return new EvidencePage("ties", offset, pageSize, ties.size(), List.of(), tiePage);
    }

    private List<String> orderedMembers(List<String> communityMemberIds) {
        Objects.requireNonNull(communityMemberIds, "communityMemberIds");
        if (communityMemberIds.stream().anyMatch(id -> id == null || id.isBlank())
                || communityMemberIds.stream().distinct().count()
                        != communityMemberIds.size()) {
            throw new IllegalArgumentException("Community members are invalid");
        }
        List<String> ids = communityMemberIds.stream().sorted().toList();
        for (String id : ids) {
            if (!nodeTypes.containsKey(id)) {
                throw new IllegalStateException(
                        "Community member is absent from the input network: " + id);
            }
        }
        return ids;
    }

    private CliqueEvidenceAggregator.MemberEvidence memberEvidence(String nodeId) {
        NetworkNodeType nodeType = nodeTypes.get(nodeId);
        List<CliqueEvidenceAggregator.ProfileSourceReference> sources =
                profileSourcesById.getOrDefault(nodeId, List.of());
        if (nodeType == NetworkNodeType.PROFILE && sources.isEmpty()) {
            throw new IllegalStateException(
                    "Profile node has no source record: " + nodeId);
        }
        return new CliqueEvidenceAggregator.MemberEvidence(
                nodeId, nodeType.value(), sources);
    }

    private CliqueEvidenceAggregator.TieEvidence tieEvidence(TieKey tie) {
        List<CliqueEvidenceAggregator.DirectedRelationshipSourceReference> sources =
                sourcesByTie.get(tie);
        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException(
                    "Community tie has no source relationship evidence");
        }
        return new CliqueEvidenceAggregator.TieEvidence(
                tie.leftId(),
                tie.rightId(),
                1,
                sources,
                duplicatesByTie.getOrDefault(tie, List.of()));
    }

    private void validateRelationship(NormalizedRelationship relationship) {
        requireSource(
                relationship.sourceFileName(),
                relationship.sourceRowNumber(),
                "relationship");
        if (relationship.sourceUserId() == null
                || relationship.sourceUserId().isBlank()
                || relationship.targetUserId() == null
                || relationship.targetUserId().isBlank()
                || relationship.sourceUserId().equals(relationship.targetUserId())
                || !nodeTypes.containsKey(relationship.sourceUserId())
                || !nodeTypes.containsKey(relationship.targetUserId())
                || relationship.sourceArrayIndex() < -1) {
            throw new IllegalArgumentException(
                    "Relationship evidence has invalid coordinates or endpoints");
        }
    }

    private static CliqueEvidenceAggregator.DirectedRelationshipSourceReference sourceReference(
            NormalizedRelationship relationship) {
        return new CliqueEvidenceAggregator.DirectedRelationshipSourceReference(
                relationship.sourceFileName(),
                relationship.sourceRowNumber(),
                relationship.sourceArrayIndex(),
                relationship.sourceUserId(),
                relationship.targetUserId());
    }

    private static void validatePage(String section, int offset, int pageSize) {
        if (!"members".equals(section) && !"ties".equals(section)) {
            throw new IllegalArgumentException("section must be members or ties");
        }
        if (offset < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "offset must be non-negative and pageSize must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
    }

    private static <K, V> Map<K, List<V>> immutableLists(
            Map<K, List<V>> source) {
        Map<K, List<V>> immutable = new LinkedHashMap<>();
        source.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private static void requireSource(String fileName, int rowNumber, String label) {
        if (fileName == null || fileName.isBlank() || rowNumber < 1) {
            throw new IllegalArgumentException(
                    label + " source file and positive row number are required");
        }
    }

    private record TieKey(String leftId, String rightId) {

        private static TieKey of(String first, String second) {
            return first.compareTo(second) < 0
                    ? new TieKey(first, second)
                    : new TieKey(second, first);
        }
    }

    public record EvidencePage(
            String section,
            int offset,
            int pageSize,
            int totalCount,
            List<CliqueEvidenceAggregator.MemberEvidence> members,
            List<CliqueEvidenceAggregator.TieEvidence> ties) {

        public EvidencePage {
            members = List.copyOf(members);
            ties = List.copyOf(ties);
        }
    }
}
