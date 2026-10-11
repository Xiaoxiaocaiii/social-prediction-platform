package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.capability.CapabilityStatus;
import com.prediction.socialnetwork.domain.DatasetStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SocialNetworkFoundationGateTest {

    @Test
    void builtinDataClosesTheFoundationLoop() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceSnapshot snapshot =
                new SocialNetworkWorkspaceService(registry).current();

        assertEquals("think-tank-social-network", snapshot.dataset().datasetId());
        assertEquals("v1", snapshot.dataset().datasetVersion());
        assertEquals(DatasetStatus.AVAILABLE_WITH_WARNINGS, snapshot.quality().datasetStatus());
        assertEquals(626, snapshot.quality().summary().get("user_record_count"));
        assertEquals(12_963, snapshot.quality().summary().get("relationship_candidate_count"));
        assertEquals(237_840, snapshot.quality().summary().get("tweet_record_count"));

        assertNotNull(snapshot.quality().generatedAt());
        assertFalse(snapshot.capabilities().items().isEmpty());
        assertEquals("capability-v1.0", snapshot.capabilities().capabilityVersion());
        assertTrue(snapshot.capabilities().items().stream()
                .anyMatch(item -> item.capabilityId().equals("network-overview")
                        && item.status() == CapabilityStatus.AVAILABLE));
        assertTrue(snapshot.capabilities().items().stream()
                .anyMatch(item -> item.capabilityId().equals("bookmark-analysis")
                        && item.status() == CapabilityStatus.DISABLED));
    }

    @Test
    void pageAndPredictionBoundariesRemainIndependent() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String router = Files.readString(Path.of("src/main/resources/static/js/router.js"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/prediction/socialnetwork/application/SocialNetworkController.java"));
        String predictionController = Files.readString(Path.of(
                "src/main/java/com/prediction/api/PredictController.java"));

        assertTrue(html.contains("href=\"#/social-network\""));
        assertTrue(router.contains("\"/social-network\": \"social-network\""));
        assertTrue(controller.contains("@RequestMapping(\"/api/social-network\")"));
        assertTrue(controller.contains("SocialNetworkApiResponse"));
        assertTrue(predictionController.contains("@PostMapping(value = \"/predict\""));
        assertFalse(predictionController.contains("social-network"));
    }
}
