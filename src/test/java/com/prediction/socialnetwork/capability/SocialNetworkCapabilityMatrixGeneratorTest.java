package com.prediction.socialnetwork.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SocialNetworkState;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReportGenerator;
import com.prediction.socialnetwork.reader.FileReadReport;
import com.prediction.socialnetwork.reader.FileReadStatus;
import com.prediction.socialnetwork.reader.HeaderStatus;
import com.prediction.socialnetwork.reader.RawRecord;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SocialNetworkCapabilityMatrixGeneratorTest {

    @Test
    void relationshipsOnlyOpensNetworkAndLimitsProfileAndTweetCapabilities() {
        var rawRelationship = new RawRecord(
                "relationships.csv",
                2,
                Map.of("source_user_id", "1", "target_user_id", "2"),
                FileReadStatus.READ_OK);
        var normalized = new SocialNetworkNormalizer().normalize(
                List.of(), List.of(rawRelationship), List.of());
        var report = report(normalized, "relationships.csv");

        var matrix = new SocialNetworkCapabilityMatrixGenerator().generate(
                dataset(DatasetFileRole.RELATIONSHIPS), report);
        var byId = byId(matrix);

        assertEquals(CapabilityStatus.AVAILABLE, byId.get("network-overview").status());
        assertEquals(SocialNetworkState.AVAILABLE, byId.get("network-overview").state());
        assertEquals(CapabilityStatus.AVAILABLE, byId.get("following-network").status());
        assertEquals(CapabilityStatus.LIMITED, byId.get("important-nodes").status());
        assertEquals(SocialNetworkState.LIMITED, byId.get("important-nodes").state());
        assertEquals(CapabilityStatus.UNAVAILABLE, byId.get("tweet-interaction").status());
        assertEquals(SocialNetworkState.UNAVAILABLE, byId.get("tweet-interaction").state());
        assertEquals(CapabilityStatus.UNAVAILABLE, byId.get("topic-analysis").status());
    }

    @Test
    void completeDataDisablesAllZeroBookmarksAndKeepsMainCapabilitiesAvailable() {
        var user = new RawRecord(
                "users.csv", 2, Map.of("user_id", "1", "username", "u"), FileReadStatus.READ_OK);
        var relationship = new RawRecord(
                "relationships.csv",
                2,
                Map.of("source_user_id", "1", "target_user_id", "2"),
                FileReadStatus.READ_OK);
        var tweet = new RawRecord(
                "tweets.csv",
                2,
                Map.of(
                        "tweet_id", "10",
                        "user_id", "1",
                        "created_at", "2025-01-01",
                        "text", "hello",
                        "like_count", "1",
                        "reply_count", "0",
                        "repost_count", "0",
                        "bookmark_count", "0"),
                FileReadStatus.READ_OK);
        var normalized = new SocialNetworkNormalizer().normalize(
                List.of(user), List.of(relationship), List.of(tweet));
        var report = report(normalized, "users.csv", "relationships.csv", "tweets.csv");

        var matrix = new SocialNetworkCapabilityMatrixGenerator().generate(
                dataset(DatasetFileRole.USERS, DatasetFileRole.RELATIONSHIPS, DatasetFileRole.TWEETS),
                report);
        var byId = byId(matrix);

        assertEquals("capability-v1.0", matrix.capabilityVersion());
        assertEquals(CapabilityStatus.AVAILABLE, byId.get("network-overview").status());
        assertEquals(CapabilityStatus.AVAILABLE, byId.get("tweet-interaction").status());
        assertEquals(CapabilityStatus.AVAILABLE, byId.get("topic-analysis").status());
        assertEquals(CapabilityStatus.DISABLED, byId.get("bookmark-analysis").status());
        assertEquals("BOOKMARK_ALL_ZERO", byId.get("bookmark-analysis").reasonCode());
    }

    @Test
    void missingRelationshipsDisablesOnlyTopicNetworkLinkage() {
        var user = new RawRecord(
                "users.csv", 2, Map.of("user_id", "1"), FileReadStatus.READ_OK);
        var tweet = new RawRecord(
                "tweets.csv",
                2,
                Map.of(
                        "tweet_id", "10",
                        "user_id", "1",
                        "created_at", "2025-01-01",
                        "text", "hello"),
                FileReadStatus.READ_OK);
        var normalized = new SocialNetworkNormalizer().normalize(
                List.of(user), List.of(), List.of(tweet));
        var report = report(normalized, "users.csv", "tweets.csv");

        var matrix = new SocialNetworkCapabilityMatrixGenerator().generate(
                dataset(DatasetFileRole.USERS, DatasetFileRole.TWEETS), report);
        var byId = byId(matrix);

        assertEquals(CapabilityStatus.AVAILABLE, byId.get("topic-analysis").status());
        assertEquals(CapabilityStatus.DISABLED, byId.get("topic-network-linkage").status());
        assertEquals(CapabilityStatus.UNAVAILABLE, byId.get("network-overview").status());
    }

    @Test
    void lowAuthorCoverageAndLowTextCoverageProduceLimitedStates() {
        var user = new RawRecord(
                "users.csv", 2, Map.of("user_id", "1"), FileReadStatus.READ_OK);
        var linkedTweet = new RawRecord(
                "tweets.csv",
                2,
                Map.of("tweet_id", "10", "user_id", "1", "text", "hello"),
                FileReadStatus.READ_OK);
        var unlinkedTweet = new RawRecord(
                "tweets.csv",
                3,
                Map.of("tweet_id", "11", "user_id", "9", "text", ""),
                FileReadStatus.READ_OK);
        var normalized = new SocialNetworkNormalizer().normalize(
                List.of(user), List.of(), List.of(linkedTweet, unlinkedTweet));
        var report = report(normalized, "users.csv", "tweets.csv");

        var matrix = new SocialNetworkCapabilityMatrixGenerator().generate(
                dataset(DatasetFileRole.USERS, DatasetFileRole.TWEETS), report);
        var byId = byId(matrix);

        assertEquals(CapabilityStatus.LIMITED, byId.get("tweet-interaction").status());
        assertEquals(CapabilityStatus.LIMITED, byId.get("topic-analysis").status());
        assertTrue(byId.get("tweet-interaction").reasonCode().contains("AUTHOR"));
        assertEquals(CapabilityStatus.DISABLED, byId.get("topic-network-linkage").status());
    }

    private static Map<String, CapabilityItem> byId(SocialNetworkCapabilityMatrix matrix) {
        return matrix.items().stream()
                .collect(java.util.stream.Collectors.toMap(CapabilityItem::capabilityId, Function.identity()));
    }

    private static com.prediction.socialnetwork.quality.SocialNetworkQualityReport report(
            com.prediction.socialnetwork.normalization.SocialNetworkNormalizationResult normalized,
            String... files) {
        List<FileReadReport> reports = java.util.Arrays.stream(files)
                .map(file -> new FileReadReport(
                        file,
                        SourceFormat.CSV,
                        "utf8",
                        HeaderStatus.VALID,
                        1,
                        1,
                        0,
                        0,
                        0,
                        List.of()))
                .toList();
        return new SocialNetworkQualityReportGenerator().generate(
                "dataset", "v1", DatasetType.UPLOAD_SESSION, reports, normalized);
    }

    private static SocialNetworkDataset dataset(DatasetFileRole... roles) {
        List<DatasetFileDescriptor> files = java.util.Arrays.stream(roles)
                .map(role -> new DatasetFileDescriptor(
                        role.name().toLowerCase() + ".csv", role, SourceFormat.CSV, "utf8"))
                .toList();
        return new SocialNetworkDataset(
                "dataset",
                "测试数据集",
                DatasetType.UPLOAD_SESSION,
                "v1",
                DatasetStatus.AVAILABLE_WITH_WARNINGS,
                "description",
                "source",
                null,
                null,
                files,
                "session",
                "normalization-v1.0",
                "network-metrics-v1.0",
                "text-processing-v1.0",
                "explanation-v1.0");
    }
}
