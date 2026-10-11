package com.prediction.socialnetwork.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkPrintableSummaryGeneratorTest {

    @Test
    void includesSharedMetadataScopeCountsAndBoundaryNotes() {
        SocialNetworkExportMetadata metadata = new SocialNetworkExportMetadata(
                "think-tank-social-network",
                "dataset-v1.0",
                "normalization-v1.0",
                "network-metrics-v1.0",
                "text-processing-v1.0",
                "explanation-v1.0",
                Map.of("metric", "like_count"),
                Map.of("name", "去重推文样本"),
                Map.of("resultCount", 3),
                "当前数据由项目开发者整理并提供。",
                Instant.parse("2026-10-05T00:00:00Z"),
                List.of("PageRank 表示网络结构位置，不等同于社会影响力。"));

        String summary = new SocialNetworkPrintableSummaryGenerator()
                .generate(metadata, "interactions");

        assertEquals(true, summary.contains("数据集: think-tank-social-network"));
        assertEquals(true, summary.contains("指标版本: network-metrics-v1.0"));
        assertEquals(true, summary.contains("查询条件: metric=like_count"));
        assertEquals(true, summary.contains("样本计数: resultCount=3"));
        assertEquals(true, summary.contains("- PageRank 表示网络结构位置，不等同于社会影响力。"));
    }

    @Test
    void rejectsMissingExportType() {
        SocialNetworkExportMetadata metadata = new SocialNetworkExportMetadata(
                "dataset", "version", "normalization", "metric", "text",
                "explanation", Map.of(), Map.of(), Map.of(), "source",
                Instant.now(), List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new SocialNetworkPrintableSummaryGenerator().generate(metadata, ""));
    }
}
