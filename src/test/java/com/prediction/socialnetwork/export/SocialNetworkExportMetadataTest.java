package com.prediction.socialnetwork.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkExportMetadataTest {

    @Test
    void preservesTraceabilityFieldsAndDefensivelyCopiesCollections() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("metric", "like_count");
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("userId", "1001");
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("tweetCount", 3);

        SocialNetworkExportMetadata metadata = new SocialNetworkExportMetadata(
                "think-tank-social-network",
                "dataset-v1.0",
                "normalization-v1.0",
                "network-metrics-v1.0",
                "text-processing-v1.0",
                "explanation-v1.0",
                query,
                scope,
                samples,
                "当前数据由项目开发者整理并提供。",
                Instant.parse("2026-10-05T00:00:00Z"),
                List.of("PageRank 表示网络结构位置，不等同于社会影响力。"));

        query.put("metric", "reply_count");
        assertEquals("like_count", metadata.query().get("metric"));
        assertEquals("1001", metadata.scope().get("userId"));
        assertEquals(3, metadata.sampleCounts().get("tweetCount"));
        assertEquals(1, metadata.boundaryNotes().size());
    }

    @Test
    void rejectsMissingTraceabilityFields() {
        assertThrows(IllegalArgumentException.class, () -> new SocialNetworkExportMetadata(
                "",
                "dataset-v1.0",
                "normalization-v1.0",
                "network-metrics-v1.0",
                "text-processing-v1.0",
                "explanation-v1.0",
                Map.of(),
                Map.of(),
                Map.of(),
                "source",
                Instant.now(),
                List.of()));
    }
}
