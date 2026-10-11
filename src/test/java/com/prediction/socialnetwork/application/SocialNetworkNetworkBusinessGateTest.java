package com.prediction.socialnetwork.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkNetworkBusinessGateTest {

    private static final String DATASET =
            "/api/social-network/datasets/think-tank-social-network";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void closesB01ToB05BusinessChainWithRealData() throws Exception {
        mockMvc.perform(get(DATASET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetId").value("think-tank-social-network"));

        mockMvc.perform(get(DATASET + "/network/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(626))
                .andExpect(jsonPath("$.data.edgeCount").value(12963));

        mockMvc.perform(get(DATASET + "/users/search").param("query", "niwcrso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId")
                        .value("1002259603537702912"));

        mockMvc.perform(get(DATASET + "/network/local")
                        .param("centerNodeId", "1002259603537702912")
                        .param("depth", "1")
                        .param("topK", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("中心节点局部关系网络"))
                .andExpect(jsonPath("$.data.edges").isArray());

        mockMvc.perform(get(DATASET + "/rankings").param("metric", "pagerank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricType").value("network"))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get(DATASET + "/users/1002259603537702912"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.networkPosition.pageRank").isNumber())
                .andExpect(jsonPath("$.data.tweetEvidence.scope")
                        .value("当前采集且成功关联的推文样本"));
    }

    @Test
    void pageContainsFiveBusinessSectionsAndDesktopLayoutHooks() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));
        String script = Files.readString(Path.of("src/main/resources/static/js/social-network.js"));

        assertTrue(html.contains("social-network-summary"));
        assertTrue(html.contains("social-network-overview"));
        assertTrue(html.contains("social-network-local"));
        assertTrue(html.contains("social-network-ranking"));
        assertTrue(html.contains("social-network-detail"));
        assertTrue(html.contains("SOCIAL NETWORK"));
        assertTrue(css.contains("social-network-local-chart"));
        assertTrue(css.contains("social-network-detail-grid"));
        assertTrue(script.contains("/network/local"));
        assertTrue(script.contains("/rankings"));
        assertTrue(script.contains("/users/"));
    }
}
