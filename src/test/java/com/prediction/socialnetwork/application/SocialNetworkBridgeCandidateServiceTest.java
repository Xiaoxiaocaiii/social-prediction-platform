package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SocialNetworkBridgeCandidateServiceTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String FINGERPRINT = "bridge-fixture-fingerprint";
    private static final String CACHE_KEY = "bridge-fixture-cache";
    private final SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
    private final SocialNetworkWorkspaceService workspace =
            mock(SocialNetworkWorkspaceService.class);
    private final LouvainAnalysisWorkflowService louvainWorkflow =
            mock(LouvainAnalysisWorkflowService.class);
    private final SocialNetworkDataFingerprintService fingerprintService =
            mock(SocialNetworkDataFingerprintService.class);
    private final SocialNetworkBridgeCandidateService service =
            new SocialNetworkBridgeCandidateService(
                    registry, workspace, louvainWorkflow, fingerprintService);
    private final SocialNetworkWorkspaceService.GroupEvidenceInput snapshot =
            mock(SocialNetworkWorkspaceService.GroupEvidenceInput.class);
    private final LouvainAnalysisWorkflowView completed = analysis(CACHE_KEY);

    @BeforeEach
    void setUp() throws Exception {
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(completed);
        when(fingerprintService.fingerprint(registry.findDataset(DATASET_ID)))
                .thenReturn(fingerprint(FINGERPRINT));
        when(workspace.getGroupEvidenceInput(DATASET_ID, FINGERPRINT)).thenReturn(snapshot);
        when(snapshot.network()).thenReturn(new SocialNetworkGraphResult(
                List.of(
                        node("A", NetworkNodeType.PROFILE),
                        node("B", NetworkNodeType.PROFILE),
                        node("X", NetworkNodeType.EXTERNAL)),
                List.of(
                        new NetworkEdge("A", "X", "following"),
                        new NetworkEdge("A", "B", "following"),
                        new NetworkEdge("X", "A", "following")),
                null,
                null,
                List.of(),
                null,
                null,
                null));
        when(snapshot.users()).thenReturn(List.of(
                new NormalizedUser(
                        "users.csv", 4, "A",
                        Map.of("username", "account_a", "display_name", "Account A"),
                        NormalizationStatus.NORMALIZED,
                        Set.of()),
                new NormalizedUser(
                        "users.csv", 5, "B", Map.of(),
                        NormalizationStatus.NORMALIZED,
                        Set.of())));
        when(snapshot.relationships()).thenReturn(List.of(
                relationship("A", "X", 10, 0, true, RelationStatus.VALID),
                relationship("A", "X", 11, 1, false, RelationStatus.DUPLICATE),
                relationship("A", "B", 12, 2, true, RelationStatus.VALID),
                relationship("X", "A", 13, -1, true, RelationStatus.VALID)));
    }

    @Test
    void returnsPagedCandidateWithIdentityAndProfileDetailsWithoutRecomputing() {
        SocialNetworkBridgeCandidateService.CandidateListView view =
                service.candidates(DATASET_ID, 0, 10);

        assertEquals("available", view.state());
        assertEquals("full_normalized_network", view.scope());
        assertTrue(view.analysisIdentity().analysisResultId().startsWith("sha256:"));
        assertEquals(2, view.totalCount());
        assertEquals(2, view.items().size());
        assertEquals(1, view.items().get(0).rank());
        assertEquals("A", view.items().get(0).accountId());
        assertEquals("account_a", view.items().get(0).username());
        assertEquals("Account A", view.items().get(0).displayName());
        assertEquals(1, view.items().get(0).connectedCommunityCount());
        assertEquals(2, view.items().get(0).distinctOtherAccountCount());
        assertEquals(2, view.items().get(0).outgoingRelationshipCount());
        assertEquals(1, view.items().get(0).incomingRelationshipCount());
        assertEquals(3, view.items().get(0).directedRelationshipCount());
        verify(louvainWorkflow, times(2)).status(DATASET_ID);
    }

    @Test
    void refusesCandidatePageWhenAnalysisChangesDuringTheQuery() {
        LouvainAnalysisWorkflowView changed = analysis("changed-cache-key");
        when(louvainWorkflow.status(DATASET_ID))
                .thenReturn(completed, changed);

        SocialNetworkBridgeCandidateService.CandidateListView view =
                service.candidates(DATASET_ID, 0, 10);

        assertEquals("unavailable", view.state());
        assertEquals("analysis_result_changed", view.reason());
        assertTrue(view.items().isEmpty());
    }

    @Test
    void pagesDirectionAndCommunityEvidenceAndKeepsDuplicateSources() {
        SocialNetworkBridgeCandidateService.EvidenceView outgoing =
                service.evidence(
                        DATASET_ID, "A", "C2", "outgoing", resultId(), 0, 10);

        assertEquals("available", outgoing.state());
        assertEquals(2, outgoing.totalCount());
        assertEquals(List.of("B", "X"), outgoing.items().stream()
                .map(SocialNetworkBridgeEvidenceAggregator.RelationshipEvidence::targetId)
                .sorted()
                .toList());
        SocialNetworkBridgeEvidenceAggregator.RelationshipEvidence aToX =
                outgoing.items().stream()
                        .filter(item -> item.targetId().equals("X"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("profile", aToX.sourceNodeType());
        assertEquals("external", aToX.targetNodeType());
        assertEquals(List.of(false, true), aToX.sources().stream()
                .map(SocialNetworkBridgeEvidenceAggregator.RelationshipSource::duplicate)
                .toList());
        assertEquals(List.of(10, 11), aToX.sources().stream()
                .map(SocialNetworkBridgeEvidenceAggregator.RelationshipSource::sourceRowNumber)
                .toList());
        var validSource = aToX.sources().get(0);
        assertEquals(AssociationStatus.LINKED, validSource.sourceNodeStatus());
        assertEquals(AssociationStatus.EXTERNAL_NODE, validSource.targetNodeStatus());
        assertEquals(RelationStatus.VALID, validSource.relationStatus());
        assertEquals(NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                validSource.normalizationStatus());
        assertTrue(validSource.analysisIncluded());
        var duplicateSource = aToX.sources().get(1);
        assertEquals(RelationStatus.DUPLICATE, duplicateSource.relationStatus());
        assertEquals(NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                duplicateSource.normalizationStatus());
        assertTrue(duplicateSource.duplicate());
        assertFalse(duplicateSource.analysisIncluded());

        SocialNetworkBridgeCandidateService.EvidenceView incoming =
                service.evidence(
                        DATASET_ID, "A", "C2", "incoming", resultId(), 0, 10);
        assertEquals(1, incoming.totalCount());
        assertEquals("X", incoming.items().get(0).sourceId());
        assertEquals("A", incoming.items().get(0).targetId());
        assertEquals("incoming", incoming.items().get(0).direction());
        assertNull(incoming.items().get(0).sources().get(0).sourceArrayIndex());
        assertEquals(RelationStatus.VALID,
                incoming.items().get(0).sources().get(0).relationStatus());

        SocialNetworkBridgeCandidateService.EvidenceView noOtherCommunity =
                service.evidence(
                        DATASET_ID, "A", "C1", "all", resultId(), 0, 10);
        assertEquals("empty", noOtherCommunity.state());
        assertEquals(0, noOtherCommunity.totalCount());

        SocialNetworkBridgeCandidateService.EvidenceView beyondEnd =
                service.evidence(
                        DATASET_ID, "A", "C2", "outgoing", resultId(), 10, 10);
        assertEquals("available", beyondEnd.state());
        assertEquals(2, beyondEnd.totalCount());
        assertTrue(beyondEnd.items().isEmpty());
    }

    @Test
    void returnsEmptyCandidateListForAValidPartitionWithNoCrossCommunityLinks() {
        when(completed.communityByNodeId()).thenReturn(
                Map.of("A", "C1", "B", "C1", "X", "C1"));

        SocialNetworkBridgeCandidateService.CandidateListView view =
                service.candidates(DATASET_ID, 0, 20);

        assertEquals("empty", view.state());
        assertEquals(0, view.totalCount());
        assertTrue(view.items().isEmpty());
        assertNotNull(view.analysisIdentity());
    }

    @Test
    void refusesCandidatesWhenAQualifiedProfileRecordIsMissing() {
        when(snapshot.users()).thenReturn(List.of(
                new NormalizedUser(
                        "users.csv", 4, "A",
                        Map.of("username", "account_a"),
                        NormalizationStatus.NORMALIZED,
                        Set.of())));

        SocialNetworkBridgeCandidateService.CandidateListView view =
                service.candidates(DATASET_ID, 0, 20);

        assertEquals("unavailable", view.state());
        assertEquals("profile_records_incomplete", view.reason());
        assertEquals(0, view.totalCount());
        assertTrue(view.items().isEmpty());
    }

    @Test
    void refusesEvidenceForAnAccountThatIsNotAProfileCandidate() {
        SocialNetworkBridgeCandidateService.EvidenceView view =
                service.evidence(
                        DATASET_ID, "X", null, "all", resultId(), 0, 20);

        assertEquals("unavailable", view.state());
        assertEquals("candidate_not_found", view.reason());
    }

    @Test
    void rejectsStaleAnalysisIdentityBeforeReadingEvidenceSnapshot() {
        SocialNetworkBridgeCandidateService.EvidenceView view =
                service.evidence(
                        DATASET_ID, "A", null, "all", "sha256:stale", 0, 20);

        assertEquals("unavailable", view.state());
        assertEquals("analysis_result_changed", view.reason());
        assertNotNull(view.analysisIdentity());
        verify(workspace, never()).getGroupEvidenceInput(DATASET_ID, FINGERPRINT);
    }

    @Test
    void refusesEvidenceWhenAnalysisChangesDuringTheQuery() {
        String resultId = resultId();
        LouvainAnalysisWorkflowView changed = analysis("changed-cache-key");
        when(louvainWorkflow.status(DATASET_ID))
                .thenReturn(completed, changed);

        SocialNetworkBridgeCandidateService.EvidenceView view =
                service.evidence(
                        DATASET_ID, "A", null, "all", resultId, 0, 20);

        assertEquals("unavailable", view.state());
        assertEquals("analysis_result_changed", view.reason());
    }

    @Test
    void refusesCandidatesWhenInputFingerprintChangesDuringTheQuery() throws Exception {
        when(fingerprintService.fingerprint(registry.findDataset(DATASET_ID)))
                .thenReturn(fingerprint(FINGERPRINT), fingerprint("changed-fingerprint"));

        SocialNetworkBridgeCandidateService.CandidateListView view =
                service.candidates(DATASET_ID, 0, 10);

        assertEquals("unavailable", view.state());
        assertEquals("input_changed_during_query", view.reason());
    }

    @Test
    void refusesEvidenceWhenInputFingerprintChangesBeforeTheQuery() throws Exception {
        String expectedResultId = resultId();
        when(fingerprintService.fingerprint(registry.findDataset(DATASET_ID)))
                .thenReturn(fingerprint("changed-fingerprint"));

        SocialNetworkBridgeCandidateService.EvidenceView view =
                service.evidence(
                        DATASET_ID, "A", null, "all", expectedResultId, 0, 20);

        assertEquals("unavailable", view.state());
        assertEquals("input_changed_before_query", view.reason());
    }

    @Test
    void distinguishesNotRequestedAnalysisAndRejectsInvalidQueries() {
        LouvainAnalysisWorkflowView notRequested = analysis(CACHE_KEY);
        when(notRequested.complete()).thenReturn(false);
        when(notRequested.state()).thenReturn("NOT_REQUESTED");
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(notRequested);

        SocialNetworkBridgeCandidateService.CandidateListView unavailable =
                service.candidates(DATASET_ID, 0, 20);
        assertEquals("unavailable", unavailable.state());
        assertEquals("analysis_not_requested", unavailable.reason());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.candidates(DATASET_ID, -1, 20));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.evidence(
                        DATASET_ID, "A", null, "sideways", "id", 0, 20));
    }

    private String resultId() {
        return service.candidates(DATASET_ID, 0, 10)
                .analysisIdentity().analysisResultId();
    }

    private static LouvainAnalysisWorkflowView analysis(String cacheKey) {
        LouvainAnalysisWorkflowView view = mock(LouvainAnalysisWorkflowView.class);
        when(view.datasetId()).thenReturn(DATASET_ID);
        when(view.datasetVersion()).thenReturn(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION);
        when(view.state()).thenReturn("COMPLETED");
        when(view.complete()).thenReturn(true);
        when(view.converged()).thenReturn(true);
        when(view.analysisScope()).thenReturn("full_normalized_network");
        when(view.dataFingerprint()).thenReturn(FINGERPRINT);
        when(view.cacheKey()).thenReturn(cacheKey);
        when(view.algorithmVersion()).thenReturn("louvain-v1");
        when(view.algorithmParameters()).thenReturn(Map.of("resolution", 1.0));
        when(view.communityByNodeId()).thenReturn(
                Map.of("A", "C1", "B", "C2", "X", "C2"));
        return view;
    }

    private static NetworkNode node(String id, NetworkNodeType type) {
        return new NetworkNode(id, type, 0, 0, 0.0, false);
    }

    private static NormalizedRelationship relationship(
            String source,
            String target,
            int row,
            int index,
            boolean included,
            RelationStatus status) {
        AssociationStatus sourceStatus = "X".equals(source)
                ? AssociationStatus.EXTERNAL_NODE
                : AssociationStatus.LINKED;
        AssociationStatus targetStatus = "X".equals(target)
                ? AssociationStatus.EXTERNAL_NODE
                : AssociationStatus.LINKED;
        NormalizationStatus normalizationStatus =
                status == RelationStatus.DUPLICATE
                                || sourceStatus == AssociationStatus.EXTERNAL_NODE
                                || targetStatus == AssociationStatus.EXTERNAL_NODE
                        ? NormalizationStatus.NORMALIZED_WITH_WARNINGS
                        : NormalizationStatus.NORMALIZED;
        return new NormalizedRelationship(
                "relationships.csv",
                row,
                index,
                source,
                target,
                sourceStatus,
                targetStatus,
                status,
                normalizationStatus,
                included);
    }

    private static SocialNetworkDataFingerprintService.Fingerprint fingerprint(
            String dataFingerprint) {
        return new SocialNetworkDataFingerprintService.Fingerprint(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "normalization-v1",
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                List.of(),
                dataFingerprint);
    }
}
