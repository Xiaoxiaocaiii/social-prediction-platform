package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialNetworkGroupInsightsControllerTest {

    @Test
    void exposesReadOnlyInsightsAndBoundsPagination() throws Exception {
        SocialNetworkGroupInsightsService service = mock(SocialNetworkGroupInsightsService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkGroupInsightsController(service)).build();
        when(service.clique("dataset", 0, 20)).thenReturn(
                new SocialNetworkGroupInsightsService.CliqueView(
                        false, "complete_memberships_unavailable", "fingerprint", Map.of(), null));
        when(service.louvain("dataset")).thenReturn(
                new SocialNetworkGroupInsightsService.LouvainView(
                        true, null, "fingerprint", Map.of("a", "alice"),
                        List.of(new SocialNetworkGroupInsightCalculator.CommunityPair("C1", "C2", 3))));
        when(service.clique("dataset", 0, 101))
                .thenThrow(new IllegalArgumentException("invalid page size"));
        when(service.sharedCliques("dataset", "a", "b", 0, 20)).thenReturn(
                new SocialNetworkGroupInsightsService.SharedView(
                        true, null, "fingerprint",
                        new SocialNetworkGroupInsightCalculator.SharedCliques(
                                "a", "b", 1, 0, 20, List.of(
                                        new SocialNetworkGroupInsightCalculator.MaximumClique(
                                                4, List.of("a", "b", "c"))))));
        mvc.perform(get("/api/social-network/datasets/dataset/network/cliques/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("complete_memberships_unavailable"));
        mvc.perform(get("/api/social-network/datasets/dataset/network/louvain/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityPairs[0].tieCount").value(3))
                .andExpect(jsonPath("$.data.names.a").value("alice"));
        mvc.perform(get("/api/social-network/datasets/dataset/network/cliques/insights/shared")
                        .param("first", "a").param("second", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalCount").value(1))
                .andExpect(jsonPath("$.data.page.cliques[0].index").value(4));
        mvc.perform(get("/api/social-network/datasets/dataset/network/cliques/insights")
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest());
    }
}
