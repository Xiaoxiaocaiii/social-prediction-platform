package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CliqueEvidenceAggregatorTest {

    private final List<NetworkNode> nodes = List.of(
            node("A", NetworkNodeType.PROFILE),
            node("B", NetworkNodeType.PROFILE),
            node("C", NetworkNodeType.PROFILE),
            node("D", NetworkNodeType.EXTERNAL));
    private final List<NormalizedUser> users = List.of(
            user("A", 10), user("B", 11), user("C", 12));
    private final List<NormalizedRelationship> relationships = List.of(
            relationship("A", "B", 20, 0, true, RelationStatus.VALID),
            relationship("B", "A", 21, 2, true, RelationStatus.VALID),
            relationship("A", "C", 22, 1, true, RelationStatus.VALID),
            relationship("B", "C", 23, 0, true, RelationStatus.VALID),
            relationship("B", "D", 24, 0, true, RelationStatus.VALID),
            relationship("C", "D", 25, 0, true, RelationStatus.VALID),
            relationship("A", "B", 26, 3, false, RelationStatus.DUPLICATE));

    @Test
    void streamsMemberProfileAndEverySupportingDirectionForCompleteCliques() {
        CliqueEvidenceAggregator aggregator =
                new CliqueEvidenceAggregator(nodes, users, relationships);
        List<CliqueEvidenceAggregator.CliqueEvidence> evidence = new ArrayList<>();

        aggregator.forEachCompleteResult(result(), evidence::add);

        assertEquals(2, evidence.size());
        CliqueEvidenceAggregator.CliqueEvidence first = evidence.get(0);
        assertEquals(0, first.cliqueIndex());
        assertEquals(List.of("A", "B", "C"),
                first.members().stream()
                        .map(CliqueEvidenceAggregator.MemberEvidence::nodeId)
                        .toList());
        assertEquals(10, first.members().get(0).profileSources().get(0).sourceRowNumber());
        assertEquals(3, first.ties().size());

        CliqueEvidenceAggregator.TieEvidence reciprocalTie = first.ties().get(0);
        assertEquals("A", reciprocalTie.leftNodeId());
        assertEquals("B", reciprocalTie.rightNodeId());
        assertEquals(1, reciprocalTie.weight());
        assertEquals(
                List.of("A->B", "B->A"),
                reciprocalTie.supportingRelationships().stream()
                        .map(reference -> reference.sourceId() + "->" + reference.targetId())
                        .toList());
        assertEquals(List.of(20, 21),
                reciprocalTie.supportingRelationships().stream()
                        .map(CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                ::sourceRowNumber)
                        .toList());
        assertEquals(
                List.of(26),
                reciprocalTie.duplicateRelationshipRecords().stream()
                        .map(CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                ::sourceRowNumber)
                        .toList());

        CliqueEvidenceAggregator.CliqueEvidence second = evidence.get(1);
        assertEquals(1, second.cliqueIndex());
        CliqueEvidenceAggregator.MemberEvidence externalMember =
                second.members().stream()
                        .filter(member -> member.nodeId().equals("D"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("external", externalMember.nodeType());
        assertTrue(externalMember.profileSources().isEmpty());
        assertTrue(second.ties().stream()
                .flatMap(tie -> tie.supportingRelationships().stream())
                .noneMatch(reference -> reference.sourceRowNumber() == 26));
    }

    @Test
    void returnsEvidenceForARequestedPageWithAbsoluteCliqueIndexes() {
        CliqueEvidenceAggregator aggregator =
                new CliqueEvidenceAggregator(nodes, users, relationships);

        List<CliqueEvidenceAggregator.CliqueEvidence> page =
                aggregator.aggregatePage(result(), 1, 1);

        assertEquals(1, page.size());
        assertEquals(1, page.get(0).cliqueIndex());
        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.aggregatePage(result(), 0, 101));
    }

    @Test
    void pagesKnownCliqueEvidenceWithoutBuildingTheFullEvidencePayload() {
        CliqueEvidenceAggregator aggregator =
                new CliqueEvidenceAggregator(nodes, users, relationships);

        CliqueEvidenceAggregator.EvidencePage memberPage =
                aggregator.aggregateKnownCliqueSection(
                        0, List.of("C", "B", "A"), "members", 1, 1);
        CliqueEvidenceAggregator.EvidencePage tiePage =
                aggregator.aggregateKnownCliqueSection(
                        0, List.of("C", "B", "A"), "ties", 1, 1);

        assertEquals(3, memberPage.totalCount());
        assertEquals(List.of("B"),
                memberPage.members().stream()
                        .map(CliqueEvidenceAggregator.MemberEvidence::nodeId)
                        .toList());
        assertEquals(3, tiePage.totalCount());
        assertEquals(1, tiePage.ties().size());
        assertEquals("A", tiePage.ties().get(0).leftNodeId());
        assertEquals("C", tiePage.ties().get(0).rightNodeId());
    }

    @Test
    void refusesIncompleteMembershipResults() {
        CliqueEvidenceAggregator aggregator =
                new CliqueEvidenceAggregator(nodes, users, relationships);
        CliqueEnumerationResult incomplete = CliqueEnumerationResult.externalTermination(
                CliqueEnumerationStatus.TIME_LIMIT,
                CliqueEnumerationLimits.DEFAULT,
                "time_limit");

        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.aggregatePage(incomplete, 0, 1));
    }

    @Test
    void refusesAClaimedCliqueWithoutEverySourceTie() {
        CliqueEvidenceAggregator aggregator =
                new CliqueEvidenceAggregator(nodes, users, relationships);
        CliqueEnumerationResult missingTie = new CliqueEnumerationResult(
                List.of(List.of("A", "B", "D")),
                CliqueEnumerationStatus.COMPLETED,
                CliqueEnumerationLimits.DEFAULT,
                2,
                null);

        assertThrows(
                IllegalStateException.class,
                () -> aggregator.aggregatePage(missingTie, 0, 1));
    }

    private static CliqueEnumerationResult result() {
        return new CliqueEnumerationResult(
                List.of(List.of("C", "B", "A"), List.of("D", "C", "B")),
                CliqueEnumerationStatus.COMPLETED,
                CliqueEnumerationLimits.DEFAULT,
                2,
                null);
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
                source.equals("D")
                        ? AssociationStatus.EXTERNAL_NODE
                        : AssociationStatus.LINKED,
                target.equals("D")
                        ? AssociationStatus.EXTERNAL_NODE
                        : AssociationStatus.LINKED,
                status,
                status == RelationStatus.VALID
                        ? NormalizationStatus.NORMALIZED
                        : NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                included);
    }
}
