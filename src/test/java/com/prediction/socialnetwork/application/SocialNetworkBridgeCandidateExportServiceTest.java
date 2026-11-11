package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkBridgeCandidateExportServiceTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String RESULT_ID = "sha256:export-fixture-result";
    private static final String FINGERPRINT = "export-fixture-fingerprint";

    private final SocialNetworkBridgeCandidateService candidateService =
            mock(SocialNetworkBridgeCandidateService.class);
    private final SocialNetworkBridgeCandidateExportService exportService =
            new SocialNetworkBridgeCandidateExportService(
                    candidateService, new ObjectMapper());

    @Test
    void preservesDetailedRelationshipSourceFieldsInExportData() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(candidate())));
        var source = new SocialNetworkBridgeEvidenceAggregator.RelationshipSource(
                "relationships.csv",
                18,
                null,
                AssociationStatus.LINKED,
                AssociationStatus.EXTERNAL_NODE,
                RelationStatus.VALID,
                NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                true,
                false);
        when(candidateService.evidence(
                DATASET_ID, "account-21", null, "all", RESULT_ID, 0, 20))
                .thenReturn(new SocialNetworkBridgeCandidateService.EvidenceView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        "account-21",
                        null,
                        "all",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(new SocialNetworkBridgeEvidenceAggregator.RelationshipEvidence(
                                "account-21",
                                "external-2",
                                "C001",
                                "C002",
                                "outgoing",
                                "profile",
                                "external",
                                List.of(source)))));

        SocialNetworkBridgeCandidateExportService.ExportData exported =
                exportService.assemble(DATASET_ID, Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 20,
                        "evidence", Map.of("accountId", "account-21")));

        Map<?, ?> evidencePage = (Map<?, ?>) exported.result().get("evidencePage");
        Map<?, ?> evidence = (Map<?, ?>) ((List<?>) evidencePage.get("items")).get(0);
        Map<?, ?> exportedSource = (Map<?, ?>) ((List<?>) evidence.get("sources")).get(0);
        assertNull(exportedSource.get("sourceArrayIndex"));
        assertEquals("LINKED", exportedSource.get("sourceNodeStatus"));
        assertEquals("EXTERNAL_NODE", exportedSource.get("targetNodeStatus"));
        assertEquals("VALID", exportedSource.get("relationStatus"));
        assertEquals("NORMALIZED_WITH_WARNINGS", exportedSource.get("normalizationStatus"));
        assertEquals(true, exportedSource.get("analysisIncluded"));
    }

    @Test
    void exportsTheRequestedCandidatePageAndOnlyTheExplicitEvidencePage() {
        when(candidateService.candidates(DATASET_ID, 20, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        20,
                        20,
                        21,
                        List.of(candidate())));
        when(candidateService.evidence(
                DATASET_ID, "account-21", "C002", "incoming",
                RESULT_ID, 20, 20)).thenReturn(
                        new SocialNetworkBridgeCandidateService.EvidenceView(
                                DATASET_ID,
                                "empty",
                                null,
                                "full_normalized_network",
                                "account-21",
                                "C002",
                                "incoming",
                                identity(),
                                20,
                                20,
                                20,
                                List.of()));

        SocialNetworkBridgeCandidateExportService.ExportData exported =
                exportService.assemble(DATASET_ID, Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 20,
                        "pageSize", 20,
                        "evidence", Map.of(
                                "accountId", "account-21",
                                "communityId", "C002",
                                "direction", "incoming",
                                "offset", 20,
                                "pageSize", 20)));

        assertEquals(1, exported.returnedCount());
        assertEquals(21, exported.totalCount());
        assertTrue(exported.truncated());
        assertEquals(
                "完整网络中共有 21 个跨社群连接候选。候选仅供核对，不作影响力结论。",
                exported.result().get("conclusion"));
        assertEquals(20, exported.result().get("candidatePage") instanceof Map<?, ?> page
                ? page.get("offset") : -1);
        assertEquals("account-21", ((Map<?, ?>) exported.result().get("evidencePage"))
                .get("accountId"));
        assertEquals(20, ((Map<?, ?>) exported.result().get("evidencePage"))
                .get("totalCount"));
        assertEquals(0, ((Map<?, ?>) exported.result().get("evidencePage"))
                .get("returnedCount"));
        assertEquals(true, ((Map<?, ?>) exported.result().get("evidencePage"))
                .get("truncated"));
        assertTrue(Boolean.TRUE.equals(((Map<?, ?>) exported.result().get("scope"))
                .get("selectedEvidencePageIncluded")));
        verify(candidateService).evidence(
                DATASET_ID, "account-21", "C002", "incoming", RESULT_ID, 20, 20);
    }

    @Test
    void rejectsAnAnalysisIdentityThatChangedBeforeExport() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(candidate())));

        SocialNetworkBridgeCandidateExportService.ExportUnavailableException exception =
                assertThrows(
                        SocialNetworkBridgeCandidateExportService
                                .ExportUnavailableException.class,
                        () -> exportService.assemble(DATASET_ID, Map.of(
                                "analysisResultId", "sha256:stale-result",
                                "offset", 0,
                                "pageSize", 20)));

        assertEquals("analysis_result_changed", exception.reason());
        verify(candidateService, never()).evidence(
                DATASET_ID, "account-21", null, "all", "sha256:stale-result", 0, 20);
    }

    @Test
    void readsUploadedCandidateAndEvidencePagesWithTheExactSessionContext() {
        String datasetId = "upload-export-session";
        String datasetVersion = "session-export-session";
        String sessionId = "export-session";
        SocialNetworkBridgeCandidateService.AnalysisIdentity uploadIdentity =
                identity(RESULT_ID, datasetId, datasetVersion);
        when(candidateService.candidates(datasetId, datasetVersion, sessionId, 0, 20))
                .thenReturn(new SocialNetworkBridgeCandidateService.CandidateListView(
                        datasetId,
                        "available",
                        null,
                        "full_normalized_network",
                        uploadIdentity,
                        0,
                        20,
                        1,
                        List.of(candidate())));
        when(candidateService.evidence(
                datasetId, datasetVersion, sessionId,
                "account-21", null, "all", RESULT_ID, 0, 20))
                .thenReturn(new SocialNetworkBridgeCandidateService.EvidenceView(
                        datasetId,
                        "available",
                        null,
                        "full_normalized_network",
                        "account-21",
                        null,
                        "all",
                        uploadIdentity,
                        0,
                        20,
                        1,
                        List.of()));

        SocialNetworkBridgeCandidateExportService.ExportData exported =
                exportService.assemble(datasetId, datasetVersion, sessionId, Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 20,
                        "evidence", Map.of("accountId", "account-21")));

        Map<?, ?> exportedIdentity =
                (Map<?, ?>) exported.result().get("analysisIdentity");
        assertEquals(datasetId, exportedIdentity.get("datasetId"));
        assertEquals(datasetVersion, exportedIdentity.get("datasetVersion"));
        verify(candidateService).candidates(datasetId, datasetVersion, sessionId, 0, 20);
        verify(candidateService).evidence(
                datasetId, datasetVersion, sessionId,
                "account-21", null, "all", RESULT_ID, 0, 20);
    }

    @Test
    void rejectsCandidateAndEvidencePagesFromDifferentAnalysisResults() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(candidate())));
        when(candidateService.evidence(
                DATASET_ID, "account-21", null, "all", RESULT_ID, 0, 20)).thenReturn(
                        new SocialNetworkBridgeCandidateService.EvidenceView(
                                DATASET_ID,
                                "available",
                                null,
                                "full_normalized_network",
                                "account-21",
                                null,
                                "all",
                                identity("sha256:changed-result"),
                                0,
                                20,
                                1,
                                List.of()));

        SocialNetworkBridgeCandidateExportService.ExportUnavailableException exception =
                assertThrows(
                        SocialNetworkBridgeCandidateExportService
                                .ExportUnavailableException.class,
                        () -> exportService.assemble(DATASET_ID, Map.of(
                                "analysisResultId", RESULT_ID,
                                "offset", 0,
                                "pageSize", 20,
                                "evidence", Map.of("accountId", "account-21"))));

        assertEquals("analysis_result_changed", exception.reason());
    }

    @Test
    void rejectsCandidatePageSizeAboveExportLimitBeforeReadingCandidates() {
        assertThrows(IllegalArgumentException.class, () -> exportService.assemble(
                DATASET_ID,
                Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 21)));
        verify(candidateService, never()).candidates(DATASET_ID, 0, 21);
    }

    @Test
    void rejectsEvidencePageSizeAboveExportLimitBeforeReadingEvidence() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(candidate())));

        assertThrows(IllegalArgumentException.class, () -> exportService.assemble(
                DATASET_ID,
                Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 20,
                        "evidence", Map.of(
                                "accountId", "account-21",
                                "pageSize", 21))));
        verify(candidateService, never()).evidence(
                DATASET_ID, "account-21", null, "all", RESULT_ID, 0, 21);
    }

    @Test
    void keepsAValidZeroCandidateResultDistinctFromUnavailable() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "empty",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        0,
                        List.of()));

        SocialNetworkBridgeCandidateExportService.ExportData exported =
                exportService.assemble(DATASET_ID, Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 20));

        assertEquals("empty", exported.result().get("state"));
        assertEquals(0, exported.returnedCount());
        assertEquals(0, exported.totalCount());
        assertFalse(exported.truncated());
        assertEquals("not_selected", ((Map<?, ?>) exported.result().get("evidencePage"))
                .get("state"));
    }

    @Test
    void doesNotAttachEvidenceForAnAccountOutsideTheExportedPage() {
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        0,
                        20,
                        1,
                        List.of(candidate())));

        assertThrows(IllegalArgumentException.class, () -> exportService.assemble(
                DATASET_ID,
                Map.of(
                        "analysisResultId", RESULT_ID,
                        "offset", 0,
                        "pageSize", 20,
                        "evidence", Map.of("accountId", "not-in-page"))));
        verify(candidateService, never()).evidence(
                DATASET_ID, "not-in-page", null, "all", RESULT_ID, 0, 20);
    }

    private static SocialNetworkBridgeCandidateService.AnalysisIdentity identity() {
        return identity(RESULT_ID);
    }

    private static SocialNetworkBridgeCandidateService.AnalysisIdentity identity(
            String resultId) {
        return identity(
                resultId, DATASET_ID, SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION);
    }

    private static SocialNetworkBridgeCandidateService.AnalysisIdentity identity(
            String resultId, String datasetId, String datasetVersion) {
        return new SocialNetworkBridgeCandidateService.AnalysisIdentity(
                resultId,
                datasetId,
                datasetVersion,
                FINGERPRINT,
                "fixture-cache-key",
                "louvain-v1",
                Map.of());
    }

    private static SocialNetworkBridgeCandidateService.CandidateItem candidate() {
        return new SocialNetworkBridgeCandidateService.CandidateItem(
                21,
                "account-21",
                "user21",
                "Account 21",
                "complete",
                "C001",
                1,
                3,
                2,
                1,
                3,
                List.of());
    }
}
