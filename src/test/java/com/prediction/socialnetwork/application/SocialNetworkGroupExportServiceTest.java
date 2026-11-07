package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.NetworkCommunity;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkGroupExportServiceTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String FINGERPRINT = "fixture-fingerprint";

    @Test
    void exportsCurrentCliquePageAndEvidenceWithTraceabilityAndCsvProtection() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        CliqueEnumerationWorkflowView view = cliqueView(
                List.of(List.of("=1+1", "node-b", "node-c")), 8, 0, 20);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20)).thenReturn(view, view);
        when(evidenceService.clique(DATASET_ID, 0, "members", 0, 20))
                .thenReturn(evidenceView());

        SocialNetworkGroupExportService service = service(
                cliqueWorkflow, louvainWorkflow, evidenceService);
        SocialNetworkGroupExportService.ExportResult result = service.export(
                DATASET_ID,
                new SocialNetworkGroupExportService.ExportRequest(
                        "clique",
                        "csv",
                        0,
                        20,
                        new SocialNetworkGroupExportService.EvidenceTarget(
                                "clique", "0", null, "members", 0, 20)));

        assertEquals("text/csv; charset=UTF-8", result.contentType());
        String csv = (String) result.result();
        assertEquals(
                csv.getBytes(StandardCharsets.UTF_8).length, result.contentLength());
        assertTrue(csv.startsWith("\uFEFFrecordType,path,value\r\n"));
        assertTrue(csv.contains("'=1+1"));
        assertTrue(csv.contains("'@source"));
        assertEquals(8, ((Map<?, ?>) result.metadata().get("scope")).get("totalCount"));
        assertEquals(true, ((Map<?, ?>) result.metadata().get("scope")).get("truncated"));
        Map<?, ?> evidenceScope = (Map<?, ?>)
                ((Map<?, ?>) result.metadata().get("scope")).get("evidence");
        assertEquals(2, evidenceScope.get("totalCount"));
        assertEquals(1, evidenceScope.get("returnedCount"));
        assertEquals(true, evidenceScope.get("truncated"));
        verify(cliqueWorkflow, never()).request(DATASET_ID, false);
        verify(louvainWorkflow, never()).request(DATASET_ID, false);
    }

    @Test
    void exportsLouvainIntegrityStateAndParametersAsJson() throws Exception {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(louvainView());

        SocialNetworkGroupExportService.ExportResult result = service(
                cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "louvain", "json", null, null, null));

        String json = (String) result.result();
        assertEquals(
                json.getBytes(StandardCharsets.UTF_8).length, result.contentLength());
        Map<?, ?> file = new ObjectMapper().readValue(json, Map.class);
        Map<?, ?> metadata = (Map<?, ?>) file.get("metadata");
        assertEquals(false, metadata.get("complete"));
        assertEquals(false, metadata.get("converged"));
        assertEquals(true, metadata.get("workerCompleted"));
        assertEquals(1.0, ((Map<?, ?>) metadata.get("algorithmParameters")).get("resolution"));
        assertEquals(1, ((Map<?, ?>) metadata.get("scope")).get("returnedCount"));
        verify(evidenceService, never()).louvain(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void exportsCliqueAsReadableReportWithoutChangingAnalysisScope() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        CliqueEnumerationWorkflowView view =
                cliqueView(List.of(List.of("node-a", "node-b", "node-c")), 8, 0, 20);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20)).thenReturn(view, view);
        when(evidenceService.clique(DATASET_ID, 0, "members", 0, 20))
                .thenReturn(evidenceView());

        SocialNetworkGroupExportService.ExportResult result = service(
                cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "clique",
                                "print",
                                0,
                                20,
                                new SocialNetworkGroupExportService.EvidenceTarget(
                                        "clique", "0", null, "members", 0, 20)));

        String report = (String) result.result();
        assertTrue(report.startsWith("社交网络分析报告\n分析类型：Clique 极大团分析"));
        assertTrue(report.contains("识别到的极大团总数：8"));
        assertTrue(report.contains("当前页：第 1 到 1 个团"));
        assertTrue(report.contains("团 1（3 个账号）"));
        assertTrue(report.contains("账号 ID：node-a"));
        assertTrue(report.contains("证据类型：成员资料来源"));
        assertTrue(report.contains("本页证据数：1 / 2"));
        assertTrue(report.contains("账号 ID：@source"));
        assertFalse(report.contains("metadata."));
        assertFalse(report.contains("analysis.cliques[0][0]"));
        assertEquals(
                report.getBytes(StandardCharsets.UTF_8).length, result.contentLength());
        verify(cliqueWorkflow, never()).request(DATASET_ID, false);
        verify(louvainWorkflow, never()).request(DATASET_ID, false);
    }

    @Test
    void exportsLouvainAsReadableCommunitySummary() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        when(louvainWorkflow.status(DATASET_ID)).thenReturn(louvainView());

        String report = (String) service(
                cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "louvain", "print", null, null, null))
                .result();

        assertTrue(report.startsWith("社交网络分析报告\n分析类型：Louvain 社群分析"));
        assertTrue(report.contains("划分出的社群数：1"));
        assertTrue(report.contains("C001：3 个账号，内部关系 2 条"));
        assertTrue(report.contains("已结束，但尚未收敛"));
        assertFalse(report.contains("analysis.communityByNodeId"));
        verify(cliqueWorkflow, never()).request(DATASET_ID, false);
        verify(louvainWorkflow, never()).request(DATASET_ID, false);
    }

    @Test
    void rejectsWholeExportWhenSerializedFileExceedsTwoMib() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        String oversizedId = "x".repeat(SocialNetworkGroupExportService.MAX_EXPORT_BYTES);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20))
                .thenReturn(cliqueView(List.of(List.of(oversizedId)), 1, 0, 20));

        assertThrows(
                SocialNetworkGroupExportService.ExportTooLargeException.class,
                () -> service(cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "clique", "json", 0, 20, null)));
    }

    @Test
    void refusesUnrequestedAnalysisInsteadOfExportingPlaceholder() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20))
                .thenReturn(cliqueView(List.of(), 0, 0, 20, "NOT_REQUESTED"));

        assertThrows(
                SocialNetworkGroupExportService.EmptyResultException.class,
                () -> service(cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "clique", "json", 0, 20, null)));
    }

    @Test
    void refusesExportWhileAnalysisIsRunning() {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        LouvainAnalysisWorkflowService louvainWorkflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkGroupEvidenceService evidenceService =
                mock(SocialNetworkGroupEvidenceService.class);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20))
                .thenReturn(cliqueView(List.of(), 0, 0, 20, "RUNNING"));

        assertThrows(
                SocialNetworkGroupExportService.AnalysisRunningException.class,
                () -> service(cliqueWorkflow, louvainWorkflow, evidenceService).export(
                        DATASET_ID,
                        new SocialNetworkGroupExportService.ExportRequest(
                                "clique", "json", 0, 20, null)));
        verify(evidenceService, never()).clique(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private static SocialNetworkGroupExportService service(
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkGroupEvidenceService evidenceService) {
        return new SocialNetworkGroupExportService(
                cliqueWorkflow,
                louvainWorkflow,
                evidenceService,
                new ObjectMapper().findAndRegisterModules());
    }

    private static CliqueEnumerationWorkflowView cliqueView(
            List<List<String>> cliques,
            int retainedCount,
            int offset,
            int pageSize) {
        return cliqueView(cliques, retainedCount, offset, pageSize, "COMPLETED");
    }

    private static CliqueEnumerationWorkflowView cliqueView(
            List<List<String>> cliques,
            int retainedCount,
            int offset,
            int pageSize,
            String state) {
        return new CliqueEnumerationWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                "COMPLETED".equals(state),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:01Z"),
                3,
                3,
                retainedCount,
                retainedCount,
                3,
                Map.of(3, retainedCount),
                Map.of("node-a", retainedCount),
                "COMPLETED".equals(state),
                offset,
                pageSize,
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
                "maximal-clique-test-v1",
                Map.of("minimumCliqueSize", 3),
                "fixture-cache-key",
                true);
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
                "iteration_limit",
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:01Z"),
                3,
                3,
                "louvain-test-v1",
                Map.of("resolution", 1.0),
                "full_normalized_network",
                FINGERPRINT,
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                "undirected_simple_any_direction",
                "quality-test-v1",
                List.of(new NetworkCommunity("C001", 3, 3, 0, 2)),
                Map.of("node-a", "C001", "node-b", "C001", "node-c", "C001"),
                0,
                List.of(),
                10,
                new LouvainQualitySummary(
                        3, 3, 3, List.of(), 2, 2, 0, Map.of("C001", 2), 0.5),
                12,
                20,
                30,
                40,
                50,
                "fixture-cache-key",
                true);
    }

    private static SocialNetworkGroupEvidenceView evidenceView() {
        return new SocialNetworkGroupEvidenceView(
                DATASET_ID,
                "clique",
                "COMPLETED",
                true,
                true,
                null,
                true,
                null,
                FINGERPRINT,
                "0",
                "members",
                0,
                20,
                2,
                List.of(new CliqueEvidenceAggregator.MemberEvidence(
                        "@source", "profile", List.of())),
                List.of());
    }
}
