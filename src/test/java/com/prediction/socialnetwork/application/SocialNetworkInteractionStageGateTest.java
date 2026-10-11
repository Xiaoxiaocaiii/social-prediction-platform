package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prediction.socialnetwork.capability.CapabilityStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkInteractionStageGateTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void interactionBusinessLoopUsesOneConsistentEvidenceScope() throws Exception {
        SocialNetworkWorkspaceSnapshot snapshot =
                new SocialNetworkWorkspaceService(new SocialNetworkDatasetRegistry()).current();

        assertTrue(snapshot.interactions().available());
        assertEquals(3, snapshot.interactions().metrics().size());
        assertTrue(snapshot.interactions().metrics().values().stream()
                .allMatch(metric -> metric.validSampleCount() > 0
                        && metric.median() >= 0));
        assertEquals(53, snapshot.interactions().unassignedSourceSampleCount());
        assertTrue(snapshot.interactions().linkedAuthorSampleCount() > 0);
        assertEquals(CapabilityStatus.LIMITED, snapshot.capabilities().items().stream()
                .filter(item -> item.capabilityId().equals("tweet-interaction"))
                .findFirst()
                .orElseThrow()
                .status());
        assertEquals(CapabilityStatus.DISABLED, snapshot.capabilities().items().stream()
                .filter(item -> item.capabilityId().equals("bookmark-analysis"))
                .findFirst()
                .orElseThrow()
                .status());
    }

    @Test
    void interactionEndpointsAndB06PageRemainBounded() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String script = Files.readString(Path.of(
                "src/main/resources/static/js/social-network.js"));

        assertTrue(html.contains("social-network-interaction-analysis"));
        assertTrue(html.contains("social-network-interaction-metric"));
        assertTrue(script.contains("/interactions/"));
        assertTrue(script.contains("\"summary\""));
        assertTrue(script.contains("\"tweets\""));
        assertTrue(script.contains("\"users\""));
        assertTrue(script.contains("\"timeseries\""));
        assertTrue(script.contains("data-social-user-id"));
        assertTrue(script.contains("loadUserDetail"));
        assertFalse(html.contains("综合互动分"));

        for (String endpoint : new String[] {
                "summary",
                "tweets?metric=like_count&limit=3",
                "users?metric=like_count&limit=3",
                "timeseries?metric=like_count"
        }) {
            mockMvc.perform(get(
                            "/api/social-network/datasets/think-tank-social-network/interactions/"
                                    + endpoint))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.data").exists());
        }
    }

    @Test
    void interactionStageDoesNotCrossPredictionBoundary() throws Exception {
        String predictionController = Files.readString(Path.of(
                "src/main/java/com/prediction/api/PredictController.java"));
        assertFalse(predictionController.contains("social-network"));

        mockMvc.perform(get("/api/social-network/datasets/think-tank-social-network/"
                        + "interactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scale.unassignedSourceSampleCount")
                        .value(53))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.metrics.like_count.median").isNumber());
    }
}
