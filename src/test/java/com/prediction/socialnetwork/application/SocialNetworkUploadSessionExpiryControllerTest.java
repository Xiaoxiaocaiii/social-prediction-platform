package com.prediction.socialnetwork.application;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialNetworkUploadSessionExpiryControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void naturallyExpiredUploadContextReturnsGoneOverHttp() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(clock);
        var uploaded = registry.createUploadSession(List.of(new DatasetFileDescriptor(
                "users.jsonl", DatasetFileRole.USERS, SourceFormat.JSONL, "utf8")));
        clock.advance(Duration.ofHours(2));

        mockMvc(registry).perform(get(datasetPath(uploaded.datasetId()))
                        .param("datasetVersion", uploaded.datasetVersion())
                        .param("sessionId", uploaded.sessionId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_DATASET_SESSION_EXPIRED"));
    }

    @Test
    void uploadContextFromBeforeServiceRestartReturnsGoneOverHttp() throws Exception {
        SocialNetworkDatasetRegistry beforeRestart =
                new SocialNetworkDatasetRegistry(new MutableClock(NOW));
        var uploaded = beforeRestart.createUploadSession(List.of(new DatasetFileDescriptor(
                "users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8")));

        SocialNetworkDatasetRegistry afterRestart =
                new SocialNetworkDatasetRegistry(new MutableClock(NOW));
        MockMvc mvc = mockMvc(afterRestart);

        mvc.perform(get(datasetPath(uploaded.datasetId()))
                        .param("datasetVersion", uploaded.datasetVersion())
                        .param("sessionId", uploaded.sessionId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_DATASET_SESSION_EXPIRED"));
    }

    private static String datasetPath(String datasetId) {
        return "/api/social-network/datasets/" + datasetId + "/network/summary";
    }

    private static MockMvc mockMvc(SocialNetworkDatasetRegistry registry) {
        SocialNetworkController controller = new SocialNetworkController(
                registry,
                new SocialNetworkWorkspaceService(registry),
                mock(CliqueEnumerationWorkflowService.class),
                mock(LouvainAnalysisWorkflowService.class),
                mock(SocialNetworkEvidenceFactSummaryService.class),
                mock(SocialNetworkBridgeCandidateExportService.class),
                new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
