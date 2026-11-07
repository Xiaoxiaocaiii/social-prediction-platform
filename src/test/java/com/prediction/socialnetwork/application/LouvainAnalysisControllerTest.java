package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LouvainAnalysisControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;

    @Test
    void exposesExplicitLouvainStartAndStatusRoutes() throws Exception {
        LouvainAnalysisWorkflowService workflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkController controller = new SocialNetworkController(
                new SocialNetworkDatasetRegistry(),
                mock(SocialNetworkWorkspaceService.class),
                mock(CliqueEnumerationWorkflowService.class),
                workflow,
                mock(SocialNetworkEvidenceFactSummaryService.class),
                new ObjectMapper());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(workflow.request(DATASET_ID, false)).thenReturn(view("RUNNING", false));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.meta.dataFingerprint").value("fixture-fingerprint"));
        verify(workflow).request(DATASET_ID, false);

        when(workflow.request(DATASET_ID, true)).thenReturn(view("RUNNING", false));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain")
                        .param("force", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        verify(workflow).request(DATASET_ID, true);

        when(workflow.status(DATASET_ID)).thenReturn(view("COMPLETED", true));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.algorithmParameters.resolution").value(1.0))
                .andExpect(jsonPath("$.data.qualityContractVersion")
                        .value(LouvainQualityCalculator.QUALITY_CONTRACT_VERSION));
        verify(workflow).status(DATASET_ID);
    }

    private static LouvainAnalysisWorkflowView view(String state, boolean complete) {
        return new LouvainAnalysisWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                complete,
                complete,
                complete,
                complete ? "COMPLETED" : state,
                null,
                null,
                null,
                626,
                12_963,
                LouvainCommunityDetector.ALGORITHM_VERSION,
                Map.of(
                        "resolution",
                        LouvainAnalysisParameters.DEFAULT.resolution()),
                "full_normalized_network",
                "fixture-fingerprint",
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                LouvainAnalysisParameters.DEFAULT.relationProjection().resultName(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                List.of(),
                Map.of(),
                0,
                List.of(),
                complete ? 12 : 0,
                null,
                10,
                20,
                30,
                40,
                50,
                "fixture-cache-key",
                false);
    }
}
