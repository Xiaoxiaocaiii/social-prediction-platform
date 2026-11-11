package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
class SocialNetworkBridgeCandidateExportContractTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String RESULT_ID = "sha256:bridge-export-contract";
    private static final String FORMULA_NAME =
            "=HYPERLINK(\"https://example.test\",\"open\")";

    @Autowired
    private SocialNetworkDatasetRegistry registry;

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    @Autowired
    private CliqueEnumerationWorkflowService cliqueWorkflow;

    @Autowired
    private LouvainAnalysisWorkflowService louvainWorkflow;

    @Autowired
    private SocialNetworkEvidenceFactSummaryService evidenceSummaryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void emitsStructuredJsonSafeCsvAndReadableBridgeReport() throws Exception {
        SocialNetworkBridgeCandidateService candidateService =
                candidateServiceWithPage();
        SocialNetworkController controller = controller(candidateService);

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> jsonResponse =
                controller.export(DATASET_ID, request("json", Map.of()));
        assertEquals(201, jsonResponse.getStatusCode().value());
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsBytes(jsonResponse.getBody()));
        assertEquals("bridge-candidates", json.at("/data/exportType").asText());
        assertEquals(FORMULA_NAME, json.at("/data/result/candidates/0/displayName").asText());
        assertEquals(1, json.at("/data/result/candidatePage/returnedCount").asInt());

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> csvResponse =
                controller.export(DATASET_ID, request("csv", Map.of()));
        assertEquals(201, csvResponse.getStatusCode().value());
        String csv = String.valueOf(csvResponse.getBody().data().get("result"));
        assertTrue(csv.startsWith("\uFEFFrecordType,"));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://example.test\"\",\"\"open\"\")\""));
        assertTrue(csv.contains("'  +command"));

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> printResponse =
                controller.export(DATASET_ID, request("print", Map.of()));
        assertEquals(201, printResponse.getStatusCode().value());
        String report = String.valueOf(printResponse.getBody().data().get("result"));
        assertTrue(report.contains("跨社群连接候选核对报告"));
        assertTrue(report.contains("第 1 至 1 项"));
        assertTrue(report.contains("不代表影响力或传播控制能力"));
    }

    @Test
    void enforcesTheTwentyItemLimitForCandidateAndEvidencePages() {
        SocialNetworkBridgeCandidateService candidateService = candidateServiceWithPage();
        SocialNetworkController controller = controller(candidateService);

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> candidateLimit =
                controller.export(DATASET_ID, request("json", Map.of(
                        "offset", 0,
                        "pageSize", 21)));
        assertEquals(400, candidateLimit.getStatusCode().value());
        assertEquals("SN_BAD_REQUEST", candidateLimit.getBody().code());

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> evidenceLimit =
                controller.export(DATASET_ID, request("json", Map.of(
                        "offset", 0,
                        "pageSize", 20,
                        "evidence", Map.of(
                                "accountId", "account-1",
                                "pageSize", 21))));
        assertEquals(400, evidenceLimit.getStatusCode().value());
        assertEquals("SN_BAD_REQUEST", evidenceLimit.getBody().code());
    }

    @Test
    void returnsConflictInsteadOfMixingCandidateAndChangedEvidenceResults() {
        SocialNetworkBridgeCandidateService candidateService = candidateServiceWithPage();
        when(candidateService.evidence(
                DATASET_ID, "account-1", null, "all", RESULT_ID, 0, 20))
                .thenReturn(new SocialNetworkBridgeCandidateService.EvidenceView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        "account-1",
                        null,
                        "all",
                        identity("sha256:changed-during-export"),
                        0,
                        20,
                        1,
                        List.of()));

        ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> response =
                controller(candidateService).export(
                        DATASET_ID,
                        request("json", Map.of(
                                "analysisResultId", RESULT_ID,
                                "offset", 0,
                                "pageSize", 20,
                                "evidence", Map.of("accountId", "account-1"))));

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("SN_EXPORT_RESULT_CHANGED", response.getBody().code());
    }

    private SocialNetworkController controller(
            SocialNetworkBridgeCandidateService candidateService) {
        SocialNetworkBridgeCandidateExportService exportService =
                new SocialNetworkBridgeCandidateExportService(
                        candidateService, objectMapper);
        return new SocialNetworkController(
                registry,
                workspace,
                cliqueWorkflow,
                louvainWorkflow,
                evidenceSummaryService,
                exportService,
                objectMapper);
    }

    private static SocialNetworkBridgeCandidateService candidateServiceWithPage() {
        SocialNetworkBridgeCandidateService candidateService =
                mock(SocialNetworkBridgeCandidateService.class);
        when(candidateService.candidates(DATASET_ID, 0, 20)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(RESULT_ID),
                        0,
                        20,
                        1,
                        List.of(new SocialNetworkBridgeCandidateService.CandidateItem(
                                1,
                                "account-1",
                                "  +command",
                                FORMULA_NAME,
                                "complete",
                                "C001",
                                1,
                                2,
                                2,
                                1,
                                3,
                                List.of()))));
        return candidateService;
    }

    private static Map<String, Object> request(
            String format,
            Map<String, Object> queryOverrides) {
        java.util.LinkedHashMap<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("analysisResultId", RESULT_ID);
        query.put("offset", 0);
        query.put("pageSize", 20);
        query.putAll(queryOverrides);
        return Map.of(
                "exportType", "bridge-candidates",
                "format", format,
                "query", query);
    }

    private static SocialNetworkBridgeCandidateService.AnalysisIdentity identity(
            String resultId) {
        return new SocialNetworkBridgeCandidateService.AnalysisIdentity(
                resultId,
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "contract-fingerprint",
                "contract-cache-key",
                "louvain-v1",
                Map.of("resolution", 1.0));
    }
}
