package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LouvainEvidenceAggregatorTest {

    private final LouvainEvidenceAggregator aggregator = new LouvainEvidenceAggregator(
            List.of(
                    node("A", NetworkNodeType.PROFILE),
                    node("B", NetworkNodeType.PROFILE),
                    node("C", NetworkNodeType.PROFILE),
                    node("X", NetworkNodeType.EXTERNAL)),
            List.of(user("A", 10), user("B", 11), user("C", 12)),
            List.of(
                    relationship("A", "B", 20, 0, true, RelationStatus.VALID),
                    relationship("B", "A", 21, 2, true, RelationStatus.VALID),
                    relationship("A", "B", 22, 3, false, RelationStatus.DUPLICATE),
                    relationship("A", "C", 23, 1, true, RelationStatus.VALID),
                    relationship("C", "X", 24, 0, true, RelationStatus.VALID)));

    @Test
    void returnsStableMemberPagesAndProfileSourceRows() {
        LouvainEvidenceAggregator.EvidencePage page = aggregator.page(
                List.of("B", "A", "X"), "members", 1, 1);

        assertEquals(3, page.totalCount());
        assertEquals(List.of("B"),
                page.members().stream()
                        .map(CliqueEvidenceAggregator.MemberEvidence::nodeId)
                        .toList());
        assertEquals(11, page.members().get(0).profileSources().get(0).sourceRowNumber());
    }

    @Test
    void returnsUniqueInternalUndirectedTiesWithAllDirectionsAndDuplicates() {
        LouvainEvidenceAggregator.EvidencePage page = aggregator.page(
                List.of("B", "A", "X"), "ties", 0, 10);

        assertEquals(1, page.totalCount());
        CliqueEvidenceAggregator.TieEvidence tie = page.ties().get(0);
        assertEquals("A", tie.leftNodeId());
        assertEquals("B", tie.rightNodeId());
        assertEquals(1, tie.weight());
        assertEquals(List.of("A->B", "B->A"),
                tie.supportingRelationships().stream()
                        .map(source -> source.sourceId() + "->" + source.targetId())
                        .toList());
        assertEquals(List.of(22),
                tie.duplicateRelationshipRecords().stream()
                        .map(CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                ::sourceRowNumber)
                        .toList());
    }

    @Test
    void pagesCrossCommunityTiesWithDirectionsAndSources() {
        var page = aggregator.between(List.of("A", "X"), List.of("B", "C"), 0, 1);
        assertEquals(3, page.totalCount());
        assertEquals("A", page.ties().get(0).leftNodeId());
        assertEquals("B", page.ties().get(0).rightNodeId());
        assertEquals(List.of(20, 21), page.ties().get(0).supportingRelationships()
                .stream().map(CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                        ::sourceRowNumber).toList());
        var secondPage = aggregator.between(List.of("A", "X"), List.of("B", "C"), 1, 1);
        assertEquals("C", secondPage.ties().get(0).rightNodeId());
        assertThrows(IllegalArgumentException.class,
                () -> aggregator.between(List.of("A", "B"), List.of("B", "C"), 0, 20));
    }

    @Test
    void boundsPageSizeAndRejectsInvalidSections() {
        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.page(List.of("A", "B"), "members", 0, 101));
        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.page(List.of("A", "B"), "profile", 0, 10));
    }

    private static NetworkNode node(String id, NetworkNodeType type) {
        return new NetworkNode(id, type, 0, 0, 0.0, false);
    }

    private static NormalizedUser user(String id, int row) {
        return new NormalizedUser(
                "users.csv", row, id, Map.of(),
                NormalizationStatus.NORMALIZED, Set.of());
    }

    private static NormalizedRelationship relationship(
            String source,
            String target,
            int row,
            int index,
            boolean included,
            RelationStatus status) {
        return new NormalizedRelationship(
                "relationships.csv",
                row,
                index,
                source,
                target,
                AssociationStatus.LINKED,
                target.equals("X")
                        ? AssociationStatus.EXTERNAL_NODE
                        : AssociationStatus.LINKED,
                status,
                NormalizationStatus.NORMALIZED,
                included);
    }
}
