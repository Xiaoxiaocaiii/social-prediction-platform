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
        String socialRoutes = Files.readString(Path.of(
                "src/main/resources/static/js/social-network-routes.js"));
        String socialScript = Files.readString(Path.of(
                "src/main/resources/static/js/social-network.js"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/prediction/socialnetwork/application/SocialNetworkController.java"));
        String predictionController = Files.readString(Path.of(
                "src/main/java/com/prediction/api/PredictController.java"));

        assertTrue(html.contains("href=\"#/social-network\""));
        assertTrue(html.contains("id=\"page-social-network-task\""));
        assertTrue(html.contains("js/social-network-routes.js"));
        assertTrue(router.contains("\"/social-network/network\": \"social-network-graph\""));
        assertTrue(socialRoutes.contains("\"/social-network\": \"overview\""));
        assertFalse(socialScript.contains("location.hash === \"#/social-network\""));
        assertTrue(controller.contains("@RequestMapping(\"/api/social-network\")"));
        assertTrue(controller.contains("SocialNetworkApiResponse"));
        assertTrue(predictionController.contains("@PostMapping(value = \"/predict\""));
        assertFalse(predictionController.contains("social-network"));
    }

    @Test
    void socialNetworkShellHasAccessibleNavigationAndDesktopWidths() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));
        String app = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertTrue(html.contains("id=\"social-network-sidebar\""));
        assertTrue(html.contains("id=\"social-network-sidebar-toggle\""));
        assertTrue(html.contains("aria-controls=\"social-network-sidebar\""));
        assertTrue(html.contains("id=\"social-network-shell-main\""));
        assertTrue(html.contains("data-social-network-group=\"overview\""));
        assertTrue(html.contains("href=\"#/social-network/data\""));
        assertTrue(html.contains("href=\"#/social-network/relations\""));
        assertTrue(html.contains("href=\"#/social-network/content\""));
        assertTrue(html.contains("aria-labelledby=\"social-network-dataset-name\""));
        assertTrue(html.contains("id=\"social-network-dataset-name\""));
        assertFalse(html.contains("id=\"social-network-shell-dataset-name\""));
        assertTrue(css.contains("grid-template-columns: 224px minmax(0, 1fr)"));
        assertTrue(css.contains("grid-template-columns: 72px minmax(0, 1fr)"));
        assertTrue(app.contains("social-network-shell-active"));
        assertTrue(app.contains("aria-current"));
        assertTrue(app.contains("socialSidebarToggle.addEventListener"));
    }
}
