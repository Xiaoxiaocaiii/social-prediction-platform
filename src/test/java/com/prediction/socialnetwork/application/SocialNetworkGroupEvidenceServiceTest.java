package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
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

class SocialNetworkGroupEvidenceServiceTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String FINGERPRINT = "fixture-data-fingerprint";

    private final SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
    private final SocialNetworkWorkspaceService workspace =
            mock(SocialNetworkWorkspaceService.class);
    private final CliqueEnumerationWorkflowService cliqueWorkflow =
            mock(CliqueEnumerationWorkflowService.class);
    private final LouvainAnalysisWorkflowService louvainWorkflow =
            mock(LouvainAnalysisWorkflowService.class);
    private final SocialNetworkDataFingerprintService fingerprintService =
            mock(SocialNetworkDataFingerprintService.class);
    private final SocialNetworkWorkspaceService.GroupEvidenceInput snapshot =
            mock(SocialNetworkWorkspaceService.GroupEvidenceInput.class);
    private final SocialNetworkGroupEvidenceService service =
            new SocialNetworkGroupEvidenceService(
                    registry, workspace, cliqueWorkflow, louvainWorkflow, fingerprintService);

    @BeforeEach
    void setUp() throws Exception {
        SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                new SocialNetworkDataFingerprintService.Fingerprint(
                        DATASET_ID,
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                        "normalization-v1",
                        SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                        SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                        List.of(),
                        FINGERPRINT);
        when(fingerprintService.fingerprint(registry.findDataset(DATASET_ID)))
                .thenReturn(fingerprint);

        when(workspace.getGroupEvidenceInput(DATASET_ID, FINGERPRINT)).thenReturn(snapshot);
        when(snapshot.network()).thenReturn(new SocialNetworkGraphResult(
                List.of(
                        node("A", NetworkNodeType.PROFILE),
                        node("B", NetworkNodeType.PROFILE),
                        node("C", NetworkNodeType.PROFILE)),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null));
        when(snapshot.users()).thenReturn(List.of(user("A", 10), user("B", 11), user("C", 12)));
        when(snapshot.relationships()).thenReturn(List.of(
                relationship("A", "B", 20, 0, true, RelationStatus.VALID),
                relationship("B", "A", 21, 1, true, RelationStatus.VALID),
                relationship("A", "C", 22, 2, true, RelationStatus.VALID)));
    }

    @Test
    void returnsPagedCliqueEvidenceAndKeepsEnumerationCompletenessSeparate() {
        when(cliqueWorkflow.status(DATASET_ID, 0, 1))
                .thenReturn(cliqueView("TIME_LIMIT", false, List.of(List.of("C", "B", "A"))));

        SocialNetworkGroupEvidenceView view =
                service.clique(DATASET_ID, 0, "ties", 1, 1);

        assertTrue(view.evidenceAvailable());
        assertFalse(view.analysisComplete());
        assertFalse(view.membershipListComplete());
        assertNull(view.converged());
        assertEquals("TIME_LIMIT", view.state());
        assertEquals(3, view.totalCount());
        assertEquals(1, view.ties().size());
        assertEquals("A", view.ties().get(0).leftNodeId());
        assertEquals("C", view.ties().get(0).rightNodeId());
        assertEquals(FINGERPRINT, view.dataFingerprint());
    }

    @Test
    void returnsLouvainEvidenceForACompletedButUnconvergedPartition() {
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(louvainView());

        SocialNetworkGroupEvidenceView view =
                service.louvain(DATASET_ID, "C001", "ties", 0, 10);

        assertTrue(view.evidenceAvailable());
        assertFalse(view.analysisComplete());
        assertTrue(view.membershipListComplete());
        assertEquals(false, view.converged());
        assertEquals("COMPLETED_UNCONVERGED", view.state());
        assertEquals(1, view.totalCount());
        assertEquals(List.of(20, 21),
                view.ties().get(0).supportingRelationships().stream()
                        .map(CliqueEvidenceAggregator.DirectedRelationshipSourceReference
                                ::sourceRowNumber)
                        .toList());
    }

    @Test
    void returnsCrossCommunityDirectionsFromLightweightEvidenceInput() {
        LouvainAnalysisWorkflowView completed = mock(LouvainAnalysisWorkflowView.class);
        when(completed.complete()).thenReturn(true);
        when(completed.converged()).thenReturn(true);
        when(completed.state()).thenReturn("COMPLETED");
        when(completed.dataFingerprint()).thenReturn(FINGERPRINT);
        when(completed.communityByNodeId()).thenReturn(
                Map.of("A", "C001", "B", "C001", "C", "C002"));
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(completed);
        SocialNetworkGroupEvidenceView view =
                service.betweenCommunities(DATASET_ID, "C001", "C002", 0, 20);
        assertTrue(view.evidenceAvailable());
        assertEquals(1, view.totalCount());
        assertEquals("A", view.ties().get(0).leftNodeId());
        assertEquals("C", view.ties().get(0).rightNodeId());
        assertEquals(22, view.ties().get(0).supportingRelationships().get(0).sourceRowNumber());
        verify(workspace, never()).getForAnalysis(any(), any());
    }

    @Test
    void returnsExplicitUnavailableStateWithoutLoadingInputWhenAnalysisHasNotRun() {
        CliqueEnumerationWorkflowView notRequested =
                cliqueView("NOT_REQUESTED", false, List.of());
        when(cliqueWorkflow.status(DATASET_ID, 0, 1)).thenReturn(notRequested);

        SocialNetworkGroupEvidenceView view =
                service.clique(DATASET_ID, 0, "members", 0, 20);

        assertFalse(view.evidenceAvailable());
        assertEquals("NOT_REQUESTED", view.state());
        verify(workspace, never()).getForAnalysis(any(), any());
        verify(workspace, never()).getGroupEvidenceInput(any(), any());
    }

    @Test
    void refusesEvidenceWhenInputFingerprintChangesDuringTheQuery() throws Exception {
        SocialNetworkDataFingerprintService.Fingerprint changed =
                new SocialNetworkDataFingerprintService.Fingerprint(
                        DATASET_ID,
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                        "normalization-v1",
                        SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                        SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                        List.of(),
                        "changed-fingerprint");
        when(fingerprintService.fingerprint(registry.findDataset(DATASET_ID)))
                .thenReturn(
                        new SocialNetworkDataFingerprintService.Fingerprint(
                                DATASET_ID,
                                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                                "normalization-v1",
                                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                                List.of(),
                                FINGERPRINT),
                        changed);
        when(cliqueWorkflow.status(DATASET_ID, 0, 1))
                .thenReturn(cliqueView(
                        "COMPLETED", true, List.of(List.of("A", "B", "C"))));

        SocialNetworkGroupEvidenceView view =
                service.clique(DATASET_ID, 0, "members", 0, 20);

        assertFalse(view.evidenceAvailable());
        assertEquals("FAILED", view.state());
        assertEquals("input_changed_during_evidence_query", view.unavailableReason());
    }

    @Test
    void refusesUnboundedOrInvalidEvidenceQueries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.clique(DATASET_ID, 0, "members", 0, 101));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.louvain(DATASET_ID, "C001", "unknown", 0, 20));
    }

    private static CliqueEnumerationWorkflowView cliqueView(
            String state, boolean complete, List<List<String>> cliques) {
        return new CliqueEnumerationWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                complete,
                complete ? null : "time_limit",
                null,
                null,
                3,
                3,
                cliques.size(),
                cliques.size(),
                3,
                Map.of(3, cliques.size()),
                Map.of(),
                complete,
                0,
                1,
                cliques,
                30_000,
                100_000,
                2 * 1024 * 1024,
                "data_fingerprint",
                true,
                "undirected_simple_any_direction",
                "full_normalized_network",
                FINGERPRINT,
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                "maximal-clique-bron-kerbosch-pivot-v1",
                Map.of("minimumCliqueSize", 3),
                "fixture-cache-key",
                false);
    }

    private static LouvainAnalysisWorkflowView louvainView() {
        return new LouvainAnalysisWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "COMPLETED_UNCONVERGED",
                false,
                true,
                false,
                "COMPLETED",
                null,
                null,
                null,
                3,
                3,
                LouvainCommunityDetector.ALGORITHM_VERSION,
                Map.of("resolution", LouvainAnalysisParameters.DEFAULT.resolution()),
                "full_normalized_network",
                FINGERPRINT,
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                LouvainAnalysisParameters.DEFAULT.relationProjection().resultName(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                List.of(),
                Map.of("A", "C001", "B", "C001", "C", "C002"),
                0,
                List.of(),
                1,
                new LouvainQualitySummary(
                        3, 3, 3, List.of(), 2, 1, 1, Map.of("C001", 1), 0.5),
                1,
                1,
                1,
                1,
                1,
                "fixture-cache-key",
                false);
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
                AssociationStatus.LINKED,
                status,
                NormalizationStatus.NORMALIZED,
                included);
    }
}
