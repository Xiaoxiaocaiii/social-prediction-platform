package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialNetworkGroupExportControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;

    @Test
    void returnsCurrentAnalysisExportAndMetadata() throws Exception {
        SocialNetworkGroupExportService service =
                mock(SocialNetworkGroupExportService.class);
        when(service.export(
                DATASET_ID,
                new SocialNetworkGroupExportService.ExportRequest(
                        "clique", "json", 0, 20, null)))
                .thenReturn(new SocialNetworkGroupExportService.ExportResult(
                        "clique",
                        "json",
                        "application/json; charset=UTF-8",
                        128,
                        Map.of("complete", true),
                        "{\"exportType\":\"clique\",\"data\":{}}"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupExportController(service)).build();

        mockMvc.perform(post("/api/social-network/datasets/" + DATASET_ID
                        + "/network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "analysisType": "clique",
                                  "format": "json",
                                  "offset": 0,
                                  "pageSize": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.exportType").value("clique"))
                .andExpect(jsonPath("$.data.format").value("json"))
                .andExpect(jsonPath("$.data.contentLength").value(128))
                .andExpect(jsonPath("$.data.metadata.complete").value(true))
                .andExpect(jsonPath("$.data.result").value(
                        "{\"exportType\":\"clique\",\"data\":{}}"));
    }

    @Test
    void mapsOversizeToWholeExportRejection() throws Exception {
        SocialNetworkGroupExportService service =
                mock(SocialNetworkGroupExportService.class);
        when(service.export(
                DATASET_ID,
                new SocialNetworkGroupExportService.ExportRequest(
                        "louvain", "csv", 0, 20, null)))
                .thenThrow(new SocialNetworkGroupExportService.ExportTooLargeException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupExportController(service)).build();

        mockMvc.perform(post("/api/social-network/datasets/" + DATASET_ID
                        + "/network/exports")
                        .contentType("application/json")
                        .content("""
                                {"analysisType":"louvain","format":"csv",
                                 "offset":0,"pageSize":20}
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_EXPORT_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value(
                        "导出结果超过单次响应上限（2 MiB），未生成部分文件。"));
    }

    @Test
    void rejectsOversizedSerializedResultThroughControllerAndService() throws Exception {
        CliqueEnumerationWorkflowService cliqueWorkflow =
                mock(CliqueEnumerationWorkflowService.class);
        when(cliqueWorkflow.status(DATASET_ID, 0, 20))
                .thenReturn(oversizedCliqueView());
        SocialNetworkGroupExportService exportService =
                new SocialNetworkGroupExportService(
                        cliqueWorkflow,
                        mock(LouvainAnalysisWorkflowService.class),
                        mock(SocialNetworkGroupEvidenceService.class),
                        new ObjectMapper().findAndRegisterModules());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupExportController(exportService)).build();

        mockMvc.perform(post("/api/social-network/datasets/" + DATASET_ID
                        + "/network/exports")
                        .contentType("application/json")
                        .content("""
                                {"analysisType":"clique","format":"json",
                                 "offset":0,"pageSize":20}
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("SN_EXPORT_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value(
                        "导出结果超过单次响应上限（2 MiB），未生成部分文件。"));
    }

    @Test
    void mapsRunningAnalysisToConflictWithoutExportingPartialResults() throws Exception {
        SocialNetworkGroupExportService service =
                mock(SocialNetworkGroupExportService.class);
        when(service.export(
                DATASET_ID,
                new SocialNetworkGroupExportService.ExportRequest(
                        "clique", "json", 0, 20, null)))
                .thenThrow(new SocialNetworkGroupExportService.AnalysisRunningException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupExportController(service)).build();

        mockMvc.perform(post("/api/social-network/datasets/" + DATASET_ID
                        + "/network/exports")
                        .contentType("application/json")
                        .content("""
                                {"analysisType":"clique","format":"json",
                                 "offset":0,"pageSize":20}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SN_GROUP_EXPORT_NOT_READY"));
    }

    private static CliqueEnumerationWorkflowView oversizedCliqueView() {
        String oversizedId = "x".repeat(SocialNetworkGroupExportService.MAX_EXPORT_BYTES);
        return new CliqueEnumerationWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "COMPLETED",
                true,
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:01Z"),
                3,
                3,
                1,
                1,
                3,
                Map.of(3, 1),
                Map.of("node-a", 1),
                true,
                0,
                20,
                List.of(List.of(oversizedId)),
                30_000,
                100_000,
                2 * 1024 * 1024,
                "data_fingerprint",
                true,
                "undirected_simple_any_direction",
                "full_normalized_network",
                "fixture-fingerprint",
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                "maximal-clique-test-v1",
                Map.of("minimumCliqueSize", 3),
                "fixture-cache-key",
                true);
    }
}
