package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialNetworkDatasetRegistryTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void registersBuiltinDatasetWithStableIdentityAndManifest() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(fixedClock());

        var dataset = registry.currentDataset();

        assertEquals("think-tank-social-network", dataset.datasetId());
        assertEquals("v1", dataset.datasetVersion());
        assertEquals("智库社交网络数据", dataset.name());
        assertEquals(DatasetType.BUILTIN, dataset.type());
        assertEquals("network-metrics-v2.0", dataset.metricVersion());
        assertEquals(3, dataset.files().size());
        assertEquals("all_csv/merged_dataset/all_tweets_raw.csv",
                dataset.files().get(2).logicalFileName());
    }

    @Test
    void uploadSessionDoesNotChangeCurrentDatasetAndReturnsToBuiltinAfterClose() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(fixedClock());
        var uploaded = registry.createUploadSession(List.of(
                new DatasetFileDescriptor("tweets.jsonl", DatasetFileRole.TWEETS, SourceFormat.JSONL, "utf8_bom")));

        assertEquals(DatasetType.UPLOAD_SESSION, uploaded.type());
        assertEquals("network-metrics-v2.0", uploaded.metricVersion());
        assertNotEquals("think-tank-social-network", uploaded.datasetId());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());

        registry.selectDataset(uploaded.datasetId());
        assertEquals(uploaded.datasetId(), registry.currentDataset().datasetId());

        registry.closeUploadSession(uploaded.sessionId());

        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertThrows(IllegalArgumentException.class, () -> registry.findDataset(uploaded.datasetId()));
        assertThrows(IllegalStateException.class, () -> registry.resolveDatasetContext(
                uploaded.datasetId(), uploaded.datasetVersion(), uploaded.sessionId()));
    }

    @Test
    void preparedUploadDatasetIsNotRegisteredUntilSessionRegistration() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(fixedClock());
        var candidate = registry.prepareUploadDataset(List.of(
                new DatasetFileDescriptor(
                        "users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8")), null);

        assertThrows(IllegalArgumentException.class, () -> registry.findDataset(candidate.datasetId()));
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());

        registry.registerUploadSession(candidate);

        assertEquals(candidate.datasetId(), registry.findDataset(candidate.datasetId()).datasetId());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
    }

    @Test
    void expiringAnActiveUploadSessionRestoresBuiltinDatasetAndRejectsItsContext() {
        MutableClock clock = new MutableClock(NOW);
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(clock);
        var uploaded = registry.createUploadSession(List.of(
                new DatasetFileDescriptor(
                        "users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8")));
        registry.selectDataset(uploaded.datasetId());

        clock.advance(Duration.ofHours(2));

        assertEquals(1, registry.expireSessions());
        assertEquals(DatasetType.BUILTIN, registry.currentDataset().type());
        assertThrows(SocialNetworkDatasetSessionExpiredException.class,
                () -> registry.resolveDatasetContext(
                        uploaded.datasetId(), uploaded.datasetVersion(), uploaded.sessionId()));
    }

    @Test
    void rejectsSelectingUnknownDataset() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(fixedClock());

        assertThrows(IllegalArgumentException.class, () -> registry.selectDataset("missing"));
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
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
