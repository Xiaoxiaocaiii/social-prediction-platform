package com.prediction.socialnetwork.application;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialNetworkBridgeCandidateControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;
    private static final String RESULT_ID = "sha256:fixture-result-id";

    @Test
    void exposesPagedCandidateAndAccountEvidenceRoutes() throws Exception {
        SocialNetworkBridgeCandidateService service =
                mock(SocialNetworkBridgeCandidateService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkBridgeCandidateController(service)).build();

        when(service.candidates(DATASET_ID, 2, 5)).thenReturn(
                new SocialNetworkBridgeCandidateService.CandidateListView(
                        DATASET_ID,
                        "available",
                        null,
                        "full_normalized_network",
                        identity(),
                        2,
                        5,
                        3,
                        List.of()));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/bridge-candidates")
                        .param("offset", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.analysisIdentity.analysisResultId")
                        .value(RESULT_ID))
                .andExpect(jsonPath("$.meta.scope").value("full_normalized_network"));
        verify(service).candidates(DATASET_ID, 2, 5);

        when(service.evidence(DATASET_ID, "account-a", "C002", "outgoing",
                RESULT_ID, 1, 3)).thenReturn(
                        new SocialNetworkBridgeCandidateService.EvidenceView(
                                DATASET_ID,
                                "available",
                                null,
                                "full_normalized_network",
                                "account-a",
                                "C002",
                                "outgoing",
                                identity(),
                                1,
                                3,
                                1,
                                List.of(new SocialNetworkBridgeEvidenceAggregator.RelationshipEvidence(
                                        "account-a",
                                        "external-b",
                                        "C001",
                                        "C002",
                                        "outgoing",
                                        "profile",
                                        "external",
                                        List.of(new SocialNetworkBridgeEvidenceAggregator
                                                .RelationshipSource(
                                                        "relationships.csv",
                                                        18,
                                                        null,
                                                        AssociationStatus.LINKED,
                                                        AssociationStatus.EXTERNAL_NODE,
                                                        RelationStatus.VALID,
                                                        NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                                                        true,
                                                        false))))));
        mockMvc.perform(get("/api/social-network/datasets/" + DATASET_ID
                        + "/network/bridge-candidates/account-a/evidence")
                        .param("communityId", "C002")
                        .param("direction", "outgoing")
                        .param("expectedAnalysisResultId", RESULT_ID)
                        .param("offset", "1")
                        .param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("account-a"))
                .andExpect(jsonPath("$.data.communityId").value("C002"))
                .andExpect(jsonPath("$.data.direction").value("outgoing"))
                .andExpect(jsonPath("$.data.items[0].sources[0].sourceArrayIndex")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].sources[0].sourceNodeStatus")
                        .value("LINKED"))
                .andExpect(jsonPath("$.data.items[0].sources[0].targetNodeStatus")
                        .value("EXTERNAL_NODE"))
                .andExpect(jsonPath("$.data.items[0].sources[0].relationStatus")
                        .value("VALID"))
                .andExpect(jsonPath("$.data.items[0].sources[0].normalizationStatus")
                        .value("NORMALIZED_WITH_WARNINGS"))
                .andExpect(jsonPath("$.data.items[0].sources[0].analysisIncluded")
                        .value(true))
                .andExpect(jsonPath("$.data.analysisIdentity.analysisResultId")
                        .value(RESULT_ID));
        verify(service).evidence(
                DATASET_ID, "account-a", "C002", "outgoing", RESULT_ID, 1, 3);
    }

    @Test
    void reportsInvalidPaginationAsBadRequest() throws Exception {
        SocialNetworkBridgeCandidateService service =
                mock(SocialNetworkBridgeCandidateService.class);
        when(service.candidates(DATASET_ID, -1, 20))
                .thenThrow(new IllegalArgumentException("invalid offset"));
        when(service.evidence(DATASET_ID, "account-a", null, "all", "", 0, 20))
                .thenThrow(new IllegalArgumentException("missing analysis identity"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkBridgeCandidateController(service)).build();

        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/bridge-candidates")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("SN_BRIDGE_CANDIDATE_BAD_REQUEST"));

        mockMvc.perform(get("/api/social-network/datasets/" + DATASET_ID
                        + "/network/bridge-candidates/account-a/evidence"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("SN_BRIDGE_CANDIDATE_BAD_REQUEST"));
    }

    private static SocialNetworkBridgeCandidateService.AnalysisIdentity identity() {
        return new SocialNetworkBridgeCandidateService.AnalysisIdentity(
                RESULT_ID,
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "fixture-fingerprint",
                "fixture-cache-key",
                "louvain-v1",
                Map.of("resolution", 1.0));
    }
}
