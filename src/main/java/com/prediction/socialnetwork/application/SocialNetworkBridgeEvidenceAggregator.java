package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SocialNetworkBridgeEvidenceAggregator {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Comparator<RelationshipSource> SOURCE_ORDER =
            Comparator.comparing(RelationshipSource::sourceFileName)
                    .thenComparingInt(RelationshipSource::sourceRowNumber)
                    .thenComparing(
                            RelationshipSource::sourceArrayIndex,
                            Comparator.nullsFirst(Integer::compareTo))
                    .thenComparing(RelationshipSource::duplicate);

    private SocialNetworkBridgeEvidenceAggregator() {
    }

    public static Page page(
            List<NetworkNode> nodes,
            List<NormalizedRelationship> relationships,
            Map<String, String> communityByNodeId,
            String accountId,
            String communityId,
            String direction,
            int offset,
            int pageSize) {
        validateQuery(accountId, communityId, direction, offset, pageSize);
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(relationships, "relationships");
        Objects.requireNonNull(communityByNodeId, "communityByNodeId");

        Map<String, NetworkNodeType> nodeTypes = new HashMap<>();
        for (NetworkNode node : nodes) {
            nodeTypes.put(node.id(), node.nodeType());
        }
        String accountCommunity = communityByNodeId.get(accountId);
        if (accountCommunity == null || accountCommunity.isBlank()
                || nodeTypes.get(accountId) != NetworkNodeType.PROFILE) {
            throw new IllegalArgumentException("account is not a classified profile candidate");
        }

        Map<DirectedPair, RelationshipSources> sourcesByRelationship = new HashMap<>();
        for (NormalizedRelationship relationship : relationships) {
            if (relationship.analysisIncluded()) {
                if (relationship.relationStatus() != RelationStatus.VALID) {
                    throw new IllegalStateException(
                            "Included relationship is not valid at its source");
                }
                DirectedPair pair = pair(relationship);
                sourcesByRelationship.computeIfAbsent(pair, ignored -> new RelationshipSources())
                        .validSources.add(source(relationship));
            } else if (relationship.relationStatus() == RelationStatus.DUPLICATE) {
                DirectedPair pair = pair(relationship);
                sourcesByRelationship.computeIfAbsent(pair, ignored -> new RelationshipSources())
                        .sources.add(source(relationship));
            }
        }

        List<RelationshipEvidence> all = new ArrayList<>();
        sourcesByRelationship.forEach((pair, sources) -> {
            if (sources.validSources.isEmpty()) {
                return;
            }
            String sourceCommunity = communityByNodeId.get(pair.sourceId());
            String targetCommunity = communityByNodeId.get(pair.targetId());
            if (sourceCommunity == null || targetCommunity == null
                    || sourceCommunity.isBlank() || targetCommunity.isBlank()
                    || sourceCommunity.equals(targetCommunity)) {
                return;
            }

            boolean outgoing;
            if (accountId.equals(pair.sourceId())) {
                outgoing = true;
            } else if (accountId.equals(pair.targetId())) {
                outgoing = false;
            } else {
                return;
            }
            String otherCommunity = outgoing ? targetCommunity : sourceCommunity;
            if (communityId != null && !communityId.equals(otherCommunity)) {
                return;
            }
            String relationshipDirection = outgoing ? "outgoing" : "incoming";
            if (!"all".equals(direction) && !direction.equals(relationshipDirection)) {
                return;
            }

            List<RelationshipSource> rowSources = new ArrayList<>(sources.validSources);
            rowSources.addAll(sources.sources);
            rowSources.sort(SOURCE_ORDER);
            all.add(new RelationshipEvidence(
                    pair.sourceId(),
                    pair.targetId(),
                    sourceCommunity,
                    targetCommunity,
                    relationshipDirection,
                    nodeType(nodeTypes, pair.sourceId()),
                    nodeType(nodeTypes, pair.targetId()),
                    rowSources));
        });

        List<RelationshipEvidence> ordered = all.stream()
                .sorted(Comparator.comparing(RelationshipEvidence::sourceId)
                        .thenComparing(RelationshipEvidence::targetId))
                .toList();
        int end = (int) Math.min(ordered.size(), (long) offset + pageSize);
        List<RelationshipEvidence> page = ordered.subList(
                Math.min(offset, ordered.size()), end);
        return new Page(offset, pageSize, ordered.size(), page);
    }

    private static DirectedPair pair(NormalizedRelationship relationship) {
        if (relationship.sourceUserId() == null || relationship.sourceUserId().isBlank()
                || relationship.targetUserId() == null || relationship.targetUserId().isBlank()
                || relationship.sourceUserId().equals(relationship.targetUserId())
                || relationship.sourceFileName() == null
                || relationship.sourceFileName().isBlank()
                || relationship.sourceRowNumber() < 1
                || relationship.sourceArrayIndex() < -1) {
            throw new IllegalStateException("Relationship evidence has invalid source details");
        }
        return new DirectedPair(relationship.sourceUserId(), relationship.targetUserId());
    }

    private static RelationshipSource source(NormalizedRelationship relationship) {
        if (relationship.sourceNodeStatus() == null
                || relationship.targetNodeStatus() == null
                || relationship.relationStatus() == null
                || relationship.normalizationStatus() == null) {
            throw new IllegalStateException("Relationship evidence is missing status details");
        }
        return new RelationshipSource(
                relationship.sourceFileName(),
                relationship.sourceRowNumber(),
                relationship.sourceArrayIndex() < 0 ? null : relationship.sourceArrayIndex(),
                relationship.sourceNodeStatus(),
                relationship.targetNodeStatus(),
                relationship.relationStatus(),
                relationship.normalizationStatus(),
                relationship.analysisIncluded(),
                relationship.relationStatus() == RelationStatus.DUPLICATE);
    }

    private static String nodeType(
            Map<String, NetworkNodeType> nodeTypes, String nodeId) {
        NetworkNodeType type = nodeTypes.get(nodeId);
        if (type == null) {
            throw new IllegalStateException("Relationship endpoint is absent from the graph");
        }
        return type.value();
    }

    private static void validateQuery(
            String accountId,
            String communityId,
            String direction,
            int offset,
            int pageSize) {
        if (accountId == null || accountId.isBlank()
                || (communityId != null && communityId.isBlank())
                || !("all".equals(direction)
                        || "outgoing".equals(direction)
                        || "incoming".equals(direction))
                || offset < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid bridge evidence query");
        }
    }

    private record DirectedPair(String sourceId, String targetId) {
    }

    private static final class RelationshipSources {

        private final List<RelationshipSource> validSources = new ArrayList<>();
        private final List<RelationshipSource> sources = new ArrayList<>();
    }

    public record RelationshipSource(
            String sourceFileName,
            int sourceRowNumber,
            Integer sourceArrayIndex,
            AssociationStatus sourceNodeStatus,
            AssociationStatus targetNodeStatus,
            RelationStatus relationStatus,
            NormalizationStatus normalizationStatus,
            boolean analysisIncluded,
            boolean duplicate) {
    }

    public record RelationshipEvidence(
            String sourceId,
            String targetId,
            String sourceCommunityId,
            String targetCommunityId,
            String direction,
            String sourceNodeType,
            String targetNodeType,
            List<RelationshipSource> sources) {
        public RelationshipEvidence {
            sources = List.copyOf(sources);
        }
    }

    public record Page(
            int offset,
            int pageSize,
            int totalCount,
            List<RelationshipEvidence> items) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
