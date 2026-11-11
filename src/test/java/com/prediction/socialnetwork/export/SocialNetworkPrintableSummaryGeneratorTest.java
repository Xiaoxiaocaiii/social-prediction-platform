package com.prediction.socialnetwork.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                Map.of("pageRank", "pagerank-v1.0"),
                Map.of("metric", "like_count"),
                Map.of("name", "去重推文样本"),
                Map.of("resultCount", 3),
                "当前数据由项目开发者整理并提供。",
                Instant.parse("2026-10-05T00:00:00Z"),
                List.of("PageRank 表示网络结构位置，不等同于社会影响力。"));

        String summary = new SocialNetworkPrintableSummaryGenerator()
                .generate(metadata, "interactions");

        assertTrue(summary.contains("数据集：think-tank-social-network"));
        assertTrue(summary.contains("点赞数"));
        assertTrue(summary.contains("本次导出记录数: 3"));
        assertTrue(summary.contains(
                "边界说明：PageRank 表示网络结构位置，不等同于社会影响力。"));
        assertFalse(summary.contains("network-metrics-v1.0"));
        assertFalse(summary.contains("pagerank-v1.0"));
        assertFalse(summary.contains("text-processing-v1.0"));
        assertFalse(summary.contains("explanation-v1.0"));
        assertFalse(summary.contains("data."));
        assertFalse(summary.contains("like_count"));
    }

    @Test
    void writesQualityReportAsReadableChineseAnalysisWithoutChangingMachineFormats() {
        SocialNetworkExportMetadata metadata = new SocialNetworkExportMetadata(
                "think-tank-social-network",
                "dataset-v1.0",
                "normalization-v1.0",
                "network-metrics-v1.0",
                "text-processing-v1.0",
                "explanation-v1.0",
                Map.of(),
                Map.of(),
                Map.of("range", "当前数据集质量摘要"),
                Map.of("resultCount", 1),
                "当前数据由项目开发者整理并提供。",
                Instant.parse("2026-11-06T00:00:00Z"),
                List.of());
        Map<String, Object> quality = Map.of(
                "datasetStatus", "available_with_warnings",
                "summary", Map.of(
                        "file_count", 3,
                        "user_record_count", 2,
                        "relationship_record_count", 4,
                        "relationship_candidate_count", 3,
                        "tweet_record_count", 2,
                        "quality_issue_count", 1,
                        "capability_impact_count", 1),
                "userQuality", Map.of(
                        "normalized_user_count", 2,
                        "raw_user_count", 2),
                "relationshipQuality", Map.of(
                        "normalized_relationship_count", 2,
                        "candidate_relationship_count", 3),
                "tweetQuality", Map.of(
                        "linked_author_count", 1,
                        "raw_tweet_count", 2),
                "textQuality", Map.of("valid_text_count", 1),
                "coverage", Map.of(
                        "user_id_coverage", 1.0,
                        "relationship_inclusion_rate", 2.0 / 3.0,
                        "tweet_author_link_rate", 0.5,
                        "valid_text_rate", 0.5),
                "issues", List.of(Map.of(
                        "severity", "warning",
                        "message", "用户资料不完整",
                        "sourceFileName", "users.csv",
                        "sourceRowNumbers", List.of(2))),
                "warnings", List.of(Map.of(
                        "severity", "warning",
                        "message", "部分用户资料缺失",
                        "affectedScope", "users",
                        "count", 1)));

        String report = new SocialNetworkPrintableSummaryGenerator()
                .generate(metadata, "quality", quality);

        assertTrue(report.contains("一、结论摘要"));
        assertTrue(report.contains("二、指标口径"));
        assertTrue(report.contains("三、结果范围"));
        assertTrue(report.contains("四、质量证据"));
        assertTrue(report.contains("五、复核信息"));
        assertTrue(report.contains("2 / 2"));
        assertTrue(report.contains("归并的问题项数：1"));
        assertTrue(report.contains("不受页面分类和本地分页筛选影响"));
        assertTrue(report.contains("users.csv"));
        assertFalse(report.contains("quality_issue_count"));
        assertFalse(report.contains("raw_user_count"));
    }

    @Test
    void writesNetworkReportForOrdinaryReadersWithoutMachineFieldPaths() {
        SocialNetworkExportMetadata metadata = new SocialNetworkExportMetadata(
                "think-tank-social-network",
                "dataset-v2",
                "normalization-v3",
                "network-metrics-v4",
                "text-v2",
                "explanation-v1",
                Map.of("structuralRoles", "roles-v2"),
                Map.of(),
                Map.of(
                        "range", "当前模块结果；按页面展示限制截取",
                        "truncated", false),
                Map.of("resultCount", 1),
                "当前数据由项目开发者整理并提供。",
                Instant.parse("2026-11-06T00:00:00Z"),
                List.of());
        Map<String, Object> network = Map.ofEntries(
                Map.entry("nodeCount", 10),
                Map.entry("edgeCount", 14),
                Map.entry("profileUserCount", 8),
                Map.entry("externalNodeCount", 2),
                Map.entry("isolatedNodeCount", 1),
                Map.entry("weaklyConnectedComponentCount", 3),
                Map.entry("largestWeaklyConnectedComponentNodeCount", 7),
                Map.entry("largestWeaklyConnectedComponentRatio", 0.7),
                Map.entry("density", 14.0 / 90.0),
                Map.entry("reciprocityRate", 0.25),
                Map.entry("averageClusteringCoefficient", 0.4),
                Map.entry("largestWeaklyConnectedComponentAveragePathLength", 2.1),
                Map.entry("metricVersion", "network-metrics-v4"),
                Map.entry("pagerankVersion", "pagerank-v2"),
                Map.entry("includeExternalNodes", true),
                Map.entry("excludeSelfLoops", true),
                Map.entry("pageRank", Map.of(
                        "converged", true,
                        "iterations", 18,
                        "finalDelta", 0.00000001,
                        "sum", 1.0)),
                Map.entry("evidenceSummary", Map.of(
                        "facts", List.of(Map.of(
                                "text", "最大连接组包含 7/10 个账号。",
                                "state", "available",
                                "reason", "",
                                "evidenceReferences", List.of(
                                        "GET /api/social-network/datasets/demo/network/summary#/data",
                                        "GET /api/social-network/datasets/demo/network/evidence/nodes#/data/items"))))));

        String report = new SocialNetworkPrintableSummaryGenerator()
                .generate(metadata, "network", network);

        assertTrue(report.contains("一、结论摘要"));
        assertTrue(report.contains("二、指标口径"));
        assertTrue(report.contains("三、结果范围"));
        assertTrue(report.contains("四、证据"));
        assertTrue(report.contains("五、复核信息"));
        assertTrue(report.contains("最大连接组包含 7 个账号，占全网 70%"));
        assertTrue(report.contains("网络位置估算已稳定"));
        assertTrue(report.contains("不等同于真实互动、社会影响力"));
        assertTrue(report.contains("最大连接组包含 7/10 个账号。"));
        assertTrue(report.contains("网络汇总指标；全网节点证据"));
        assertTrue(report.contains("资料外账号：纳入统计"));
        assertFalse(report.contains("network-metrics-v4"));
        assertFalse(report.contains("pagerank-v2"));
        assertFalse(report.contains("roles-v2"));
        assertFalse(report.contains("weaklyConnectedComponentCount"));
        assertFalse(report.contains("evidenceReferences"));
        assertFalse(report.contains("GET /api/"));
    }

    @Test
    void explainsWhenNetworkReportHasNoRowLevelEvidence() {
        String report = new SocialNetworkPrintableSummaryGenerator().generate(
                metadata(Map.of()),
                "network",
                Map.of(
                        "nodeCount", 5,
                        "edgeCount", 8,
                        "evidenceSummary", Map.of("facts", List.of())));

        assertTrue(report.contains(
                "本报告未附逐账号或逐关系的原始记录证据；以上数值是按当前数据集计算的网络汇总指标。"));
        assertFalse(report.contains("未提供全网事实摘要"));
        assertFalse(report.contains("请通过 JSON/CSV"));
    }

    @Test
    void makesUsersInteractionsTopicsAndTweetsReportsSpecificToTheirResults() {
        SocialNetworkPrintableSummaryGenerator generator =
                new SocialNetworkPrintableSummaryGenerator();
        SocialNetworkExportMetadata usersMetadata = metadata(
                Map.of("metric", "pagerank"));
        String users = generator.generate(usersMetadata, "users", Map.of(
                "ranking", Map.of(
                        "metric", "pagerank",
                        "sampleSize", 2,
                        "truncated", false,
                        "items", List.of(Map.of(
                                "rank", 1,
                                "nodeId", "user-a",
                                "displayName", "甲账号",
                                "value", 0.42))),
                "structuralRoles", Map.of()));

        SocialNetworkExportMetadata interactionsMetadata = metadata(
                Map.of("metric", "reply_count"));
        String interactions = generator.generate(interactionsMetadata, "interactions",
                Map.of(
                        "metric", "reply_count",
                        "summary", Map.of("metrics", Map.of("reply_count", Map.of(
                                "total", 9,
                                "average", 3.0,
                                "median", 2.0,
                                "validSampleCount", 3,
                                "missingCount", 1,
                                "zeroCount", 1))),
                        "tweets", Map.of("items", List.of(Map.of(
                                "tweetId", "tweet-r",
                                "value", 7))),
                        "users", Map.of("items", List.of(Map.of(
                                "userId", "user-r",
                                "total", 9,
                                "sampleCount", 3))),
                        "timeseries", Map.of("items", List.of(Map.of(
                                "period", "2026-01-01",
                                "total", 9)))));

        SocialNetworkExportMetadata topicMetadata = metadata(Map.of("term", "climate"));
        String topic = generator.generate(topicMetadata, "topic", Map.of(
                "query", "climate",
                "matchedTweetCount", 2,
                "relatedUserCount", 1,
                "tweets", List.of(Map.of(
                        "tweetId", "tweet-topic",
                        "authorUserId", "user-topic",
                        "textOriginal", "Climate policy discussion")),
                "users", List.of(Map.of(
                        "userId", "user-topic",
                        "matchedTweetCount", 2)),
                "network", Map.of("nodeCount", 1, "totalEdgeCount", 0)));

        SocialNetworkExportMetadata tweetsMetadata = metadata(Map.of());
        String tweets = generator.generate(tweetsMetadata, "tweets", List.of(Map.of(
                "tweetId", "tweet-raw",
                "authorUserId", "user-raw",
                "fields", Map.of("text", "Original post content"))));

        assertTrue(users.contains("网络位置估算"));
        assertTrue(users.contains("甲账号"));
        assertFalse(users.contains("评论数"));
        assertTrue(interactions.contains("评论数"));
        assertTrue(interactions.contains("合计 9"));
        assertFalse(interactions.contains("点赞数"));
        assertTrue(topic.contains("精确词元“climate”"));
        assertTrue(topic.contains("Climate policy discussion"));
        assertTrue(tweets.contains("推文样本导出报告"));
        assertTrue(tweets.contains("Original post content"));
        assertNotEquals(users, interactions);
        assertNotEquals(interactions, topic);
        assertNotEquals(topic, tweets);
    }

    @Test
    void writesBridgeCandidateScopeAndSelectedRelationshipSourcesAsReadableReport() {
        SocialNetworkExportMetadata metadata = metadata(Map.of());
        Map<String, Object> result = Map.of(
                "question", "哪些资料账号连接了其他社群？",
                "conclusion", "完整网络中共有 21 个跨社群连接候选。",
                "methodology", Map.of(
                        "candidateDefinition", "有资料且已归属社群的账号。",
                        "relationshipCounting", "按有效有向关系计数。",
                        "excludedRelationships", List.of("自环", "同社群关系"),
                        "interpretationLimit", "不代表影响力或传播控制能力。"),
                "scope", Map.of(
                        "calculationScope", "完整网络和当前 Louvain 结果",
                        "canvasFiltersApplied", false),
                "candidatePage", Map.of(
                        "offset", 0,
                        "returnedCount", 1,
                        "totalCount", 21,
                        "truncated", true),
                "analysisIdentity", Map.of(
                        "analysisResultId", "sha256:bridge-report-test",
                        "dataFingerprint", "bridge-report-fingerprint"),
                "candidates", List.of(Map.of(
                        "rank", 1,
                        "accountId", "account-1",
                        "displayName", "示例账号",
                        "communityId", "C001",
                        "connectedCommunityCount", 1,
                        "distinctOtherAccountCount", 2,
                        "outgoingRelationshipCount", 2,
                        "incomingRelationshipCount", 1,
                        "communities", List.of())),
                "evidencePage", Map.of(
                        "state", "available",
                        "accountId", "account-1",
                        "communityId", "C002",
                        "direction", "outgoing",
                        "offset", 0,
                        "pageSize", 20,
                        "returnedCount", 1,
                        "totalCount", 21,
                        "truncated", true,
                        "items", List.of(Map.of(
                                "sourceId", "account-1",
                                "targetId", "external-2",
                                "sourceCommunityId", "C001",
                                "targetCommunityId", "C002",
                                "direction", "outgoing",
                                "sources", List.of(Map.of(
                                        "sourceFileName", "relationships.csv",
                                        "sourceRowNumber", 18,
                                        "sourceArrayIndex", 0,
                                        "sourceNodeStatus", "LINKED",
                                        "targetNodeStatus", "EXTERNAL_NODE",
                                        "relationStatus", "VALID",
                                        "normalizationStatus", "NORMALIZED_WITH_WARNINGS",
                                        "analysisIncluded", true,
                                        "duplicate", false))))));

        String report = new SocialNetworkPrintableSummaryGenerator()
                .generate(metadata, "bridge-candidates", result);

        assertTrue(report.contains("跨社群连接候选核对报告"));
        assertTrue(report.contains("完整候选共 21 个"));
        assertTrue(report.contains("第 1 至 1 项"));
        assertTrue(report.contains("本文件只包含这一页候选"));
        assertFalse(report.contains("0 起"));
        assertTrue(report.contains("当前筛选条件下共 21 条"));
        assertTrue(report.contains("不能视为完整关系证据"));
        assertTrue(report.contains("主动关系是候选关注其他社群账号"));
        assertTrue(report.contains("不同账号计数"));
        assertTrue(report.contains("依据本报告所附来源文件和行号"));
        assertTrue(report.contains("ID account-1"));
        assertTrue(report.contains("relationships.csv 第 18 行"));
        assertTrue(report.contains("关注列表索引 0"));
        assertTrue(report.contains("有效关系，计入关系统计"));
        assertTrue(report.contains("账号资料已采集 → 关系端点，资料未采集"));
        assertTrue(report.contains("规范化完成但有提示"));
        assertTrue(report.contains("分析结果身份：sha256:bridge-report-test"));
        assertTrue(report.contains("不代表影响力或传播控制能力。"));
        assertFalse(report.contains("\"candidatePage\""));
    }

    @Test
    void omitsArrayIndexForRelationshipRowsWithoutAnArraySource() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("sourceFileName", "relationships.csv");
        source.put("sourceRowNumber", 9);
        source.put("sourceArrayIndex", null);
        source.put("sourceNodeStatus", "LINKED");
        source.put("targetNodeStatus", "EXTERNAL_NODE");
        source.put("relationStatus", "VALID");
        source.put("normalizationStatus", "NORMALIZED_WITH_WARNINGS");
        source.put("analysisIncluded", true);
        source.put("duplicate", false);
        String report = new SocialNetworkPrintableSummaryGenerator().generate(
                metadata(Map.of()),
                "bridge-candidates",
                Map.of(
                        "scope", Map.of(),
                        "candidatePage", Map.of(
                                "offset", 0,
                                "returnedCount", 1,
                                "totalCount", 1),
                        "analysisIdentity", Map.of(),
                        "question", "问题",
                        "conclusion", "结论",
                        "methodology", Map.of(),
                        "candidates", List.of(Map.of(
                                "rank", 1,
                                "accountId", "account-a",
                                "communities", List.of())),
                        "evidencePage", Map.of(
                                "state", "available",
                                "accountId", "account-a",
                                "direction", "outgoing",
                                "offset", 0,
                                "totalCount", 1,
                                "truncated", false,
                                "items", List.of(Map.of(
                                        "sourceId", "account-a",
                                        "targetId", "account-b",
                                        "sources", List.of(source))))));

        assertTrue(report.contains("relationships.csv 第 9 行"));
        assertTrue(report.contains("有效关系，计入关系统计"));
        assertFalse(report.contains("数组索引"));
        assertFalse(report.contains("数组索引 -1"));
    }

    @Test
    void distinguishesValidZeroCandidatesFromAnOutOfRangeCandidatePage() {
        SocialNetworkPrintableSummaryGenerator generator =
                new SocialNetworkPrintableSummaryGenerator();
        SocialNetworkExportMetadata metadata = metadata(Map.of());
        Map<String, Object> shared = Map.of(
                "question", "哪些资料账号连接了其他社群？",
                "conclusion", "当前结果说明。",
                "methodology", Map.of(),
                "scope", Map.of("canvasFiltersApplied", false),
                "analysisIdentity", Map.of(
                        "analysisResultId", "sha256:bridge-empty-test",
                        "dataFingerprint", "bridge-empty-fingerprint"),
                "candidates", List.of(),
                "evidencePage", Map.of("state", "not_selected", "items", List.of()));
        Map<String, Object> zeroResult = new java.util.LinkedHashMap<>(shared);
        zeroResult.put("candidatePage", Map.of(
                "offset", 0, "returnedCount", 0, "totalCount", 0, "truncated", false));
        String zeroReport = generator.generate(
                metadata, "bridge-candidates", zeroResult);
        assertTrue(zeroReport.contains("有效零结果，不代表分析不可用"));
        assertTrue(zeroReport.contains("完整网络中没有符合条件的候选"));
        assertFalse(zeroReport.contains("0 起"));
        assertTrue(zeroReport.contains("请在平台打开目标账号的关系证据页后再导出"));
        assertFalse(zeroReport.contains("按文件中的来源行号"));

        Map<String, Object> outOfRangeResult =
                new java.util.LinkedHashMap<>(shared);
        outOfRangeResult.put("candidatePage", Map.of(
                "offset", 20, "returnedCount", 0, "totalCount", 21, "truncated", true));
        String outOfRangeReport = generator.generate(
                metadata, "bridge-candidates", outOfRangeResult);
        assertTrue(outOfRangeReport.contains("完整候选总数不为 0"));
        assertTrue(outOfRangeReport.contains("请刷新候选页后重试"));
        assertTrue(outOfRangeReport.contains("当前页未返回账号"));
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

    private static SocialNetworkExportMetadata metadata(Map<String, Object> query) {
        return new SocialNetworkExportMetadata(
                "demo-dataset",
                "dataset-v1",
                "normalization-v1",
                "network-metrics-v1",
                "text-v1",
                "explanation-v1",
                Map.of(),
                query,
                Map.of("range", "当前模块的服务端结果"),
                Map.of("resultCount", 1, "tweetCount", 1),
                "项目整理的数据",
                Instant.parse("2026-11-06T00:00:00Z"),
                List.of());
    }
}
