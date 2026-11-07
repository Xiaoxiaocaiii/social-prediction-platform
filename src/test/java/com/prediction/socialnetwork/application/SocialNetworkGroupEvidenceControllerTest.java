package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialNetworkGroupEvidenceControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;

    @Test
    void exposesPagedCliqueAndLouvainEvidenceRoutes() throws Exception {
        SocialNetworkGroupEvidenceService service =
                mock(SocialNetworkGroupEvidenceService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupEvidenceController(service)).build();

        when(service.clique(DATASET_ID, 2, "ties", 1, 5))
                .thenReturn(view("clique", "2", "ties", true));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques/2/evidence")
                        .param("section", "ties")
                        .param("offset", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisType").value("clique"))
                .andExpect(jsonPath("$.data.targetId").value("2"))
                .andExpect(jsonPath("$.data.section").value("ties"))
                .andExpect(jsonPath("$.data.evidenceAvailable").value(true))
                .andExpect(jsonPath("$.meta.dataFingerprint").value("fixture-fingerprint"));
        verify(service).clique(DATASET_ID, 2, "ties", 1, 5);

        when(service.louvain(DATASET_ID, "C001", "members", 0, 20))
                .thenReturn(view("louvain", "C001", "members", false));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain/communities/C001/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisType").value("louvain"))
                .andExpect(jsonPath("$.data.targetId").value("C001"))
                .andExpect(jsonPath("$.data.section").value("members"))
                .andExpect(jsonPath("$.data.evidenceAvailable").value(false));
        verify(service).louvain(DATASET_ID, "C001", "members", 0, 20);

        when(service.betweenCommunities(DATASET_ID, "C001", "C002", 0, 20))
                .thenReturn(view("louvain", "C001 / C002", "ties", true));
        mockMvc.perform(get("/api/social-network/datasets/" + DATASET_ID
                        + "/network/louvain/communities/C001/between/C002/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section").value("ties"))
                .andExpect(jsonPath("$.data.evidenceAvailable").value(true));
        verify(service).betweenCommunities(DATASET_ID, "C001", "C002", 0, 20);
    }

    @Test
    void returnsBadRequestForInvalidEvidencePagination() throws Exception {
        SocialNetworkGroupEvidenceService service =
                mock(SocialNetworkGroupEvidenceService.class);
        when(service.clique(DATASET_ID, 0, "members", -1, 20))
                .thenThrow(new IllegalArgumentException("invalid offset"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupEvidenceController(service)).build();

        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/cliques/0/evidence")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_GROUP_EVIDENCE_BAD_REQUEST"));
    }

    private static SocialNetworkGroupEvidenceView view(
            String type, String targetId, String section, boolean available) {
        return new SocialNetworkGroupEvidenceView(
                DATASET_ID,
                type,
                available ? "COMPLETED" : "NOT_REQUESTED",
                available,
                available,
                "louvain".equals(type) ? available : null,
                available,
                available ? null : "analysis_not_requested",
                "fixture-fingerprint",
                targetId,
                section,
                0,
                20,
                0,
                List.of(),
                List.of());
    }
}
