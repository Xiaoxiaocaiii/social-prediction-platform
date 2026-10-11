package com.prediction.socialnetwork.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.reader.FileReadReport;
import com.prediction.socialnetwork.reader.FileReadStatus;
import com.prediction.socialnetwork.reader.HeaderStatus;
import com.prediction.socialnetwork.reader.RawRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkQualityReportGeneratorTest {

    @Test
    void summarizesWarningsCoverageAndDisabledBookmarks() {
        var rawUser = new RawRecord(
                "users.csv", 2, Map.of("user_id", "1", "username", "u"), FileReadStatus.READ_OK);
        var rawTweet = new RawRecord(
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
                List.of(rawUser), List.of(), List.of(rawTweet));
        var fileReport = new FileReadReport(
                "users.csv",
                SourceFormat.CSV,
                "utf8",
                HeaderStatus.VALID,
                1,
                1,
                0,
                0,
                0,
                List.of());

        var report = new SocialNetworkQualityReportGenerator().generate(
                "dataset", "v1", DatasetType.UPLOAD_SESSION, List.of(fileReport), normalized);

        assertEquals("available_with_warnings", report.datasetStatus().name().toLowerCase());
        assertEquals(1, report.textQuality().get("valid_text_count"));
        assertEquals(1, report.interactionQuality().get("bookmark_all_zero"));
        assertEquals(1.0, report.coverage().get("tweet_author_link_rate"));
        assertTrue(report.capabilityImpact().containsKey("bookmark_analysis"));
    }
}
