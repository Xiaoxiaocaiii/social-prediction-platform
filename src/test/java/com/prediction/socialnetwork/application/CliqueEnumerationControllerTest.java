package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CliqueEnumerationControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;

    @Test
    void exposesExplicitStartAndPagedStatusRoutes() throws Exception {
        CliqueEnumerationWorkflowService workflow =
                mock(CliqueEnumerationWorkflowService.class);
        SocialNetworkController controller = new SocialNetworkController(
                new SocialNetworkDatasetRegistry(),
                mock(SocialNetworkWorkspaceService.class),
                workflow,
                mock(LouvainAnalysisWorkflowService.class),
                mock(SocialNetworkEvidenceFactSummaryService.class),
                new ObjectMapper());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(workflow.request(DATASET_ID, false)).thenReturn(view("RUNNING", 0, 20));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.inputIdentityMode").value("data_fingerprint"))
                .andExpect(jsonPath("$.meta.dataFingerprint").value("fixture-fingerprint"));
        verify(workflow).request(DATASET_ID, false);

        when(workflow.request(DATASET_ID, true)).thenReturn(view("RUNNING", 0, 20));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques")
                        .param("force", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        verify(workflow).request(DATASET_ID, true);

        when(workflow.status(DATASET_ID, 2, 7)).thenReturn(view("COMPLETED", 2, 7));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques")
                        .param("offset", "2")
                        .param("pageSize", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.offset").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(7))
                .andExpect(jsonPath("$.data.enumeratedCliqueCount").value(0))
                .andExpect(jsonPath("$.data.membershipListComplete").value(true));
        verify(workflow).status(DATASET_ID, 2, 7);

        when(workflow.status(DATASET_ID, 0, 20))
                .thenReturn(view("FAILED", 0, 20, null));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("FAILED"))
                .andExpect(jsonPath("$.data.inputFingerprintAvailable").value(false));
        verify(workflow).status(DATASET_ID, 0, 20);
    }

    private static CliqueEnumerationWorkflowView view(
            String state, int offset, int pageSize) {
        return view(state, offset, pageSize, "fixture-fingerprint");
    }

    private static CliqueEnumerationWorkflowView view(
            String state, int offset, int pageSize, String fingerprint) {
        return new CliqueEnumerationWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                "COMPLETED".equals(state),
                null,
                null,
                null,
                3,
                3,
                0,
                0,
                0,
                java.util.Map.of(),
                java.util.Map.of(),
                true,
                offset,
                pageSize,
                List.of(),
                30_000,
                100_000,
                2 * 1024 * 1024,
                "data_fingerprint",
                fingerprint != null,
                "undirected_simple_any_direction",
                "full_normalized_network",
                fingerprint,
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                "maximal-clique-bron-kerbosch-pivot-v1",
                java.util.Map.of("minimumCliqueSize", 3),
                "fixture-cache-key",
                false);
    }
}
