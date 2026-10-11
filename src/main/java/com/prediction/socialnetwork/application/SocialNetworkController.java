package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SocialNetworkStateCatalog;
import com.prediction.socialnetwork.export.SocialNetworkJsonExportAssembler;
import com.prediction.socialnetwork.export.SocialNetworkExportMetadata;
import com.prediction.socialnetwork.export.SocialNetworkCsvSerializer;
import com.prediction.socialnetwork.export.SocialNetworkPrintableSummaryGenerator;
import com.prediction.socialnetwork.export.SocialNetworkExportMessages;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.quality.QualityIssue;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import com.prediction.socialnetwork.network.NetworkGraphView;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social-network")
public class SocialNetworkController {

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final ObjectMapper objectMapper;
    private final SocialNetworkJsonExportAssembler jsonExportAssembler =
            new SocialNetworkJsonExportAssembler();
    private final SocialNetworkCsvSerializer csvSerializer =
            new SocialNetworkCsvSerializer();
    private final SocialNetworkPrintableSummaryGenerator printableSummaryGenerator =
            new SocialNetworkPrintableSummaryGenerator();

    public SocialNetworkController(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/datasets")
    public SocialNetworkApiResponse<Map<String, Object>> datasets() {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.current();
        Map<String, Object> item = datasetSummary(snapshot.dataset(), snapshot.quality());
        return success(Map.of("items", List.of(item)), snapshot);
    }

    @GetMapping("/datasets/{datasetId}")
    public SocialNetworkApiResponse<Map<String, Object>> dataset(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        Map<String, Object> data = datasetSummary(snapshot.dataset(), snapshot.quality());
        data.put("qualitySummary", qualitySummary(snapshot.quality()));
        data.put("capabilitySummary", Map.of(
                "items", snapshot.capabilities().items(),
                "capabilityVersion", snapshot.capabilities().capabilityVersion()));
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/capabilities")
    public SocialNetworkApiResponse<Map<String, Object>> capabilities(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        return success(Map.of(
                "capabilityVersion", snapshot.capabilities().capabilityVersion(),
                "items", snapshot.capabilities().items()), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/quality/summary")
    public SocialNetworkApiResponse<Map<String, Object>> quality(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        return success(qualitySummary(snapshot.quality()), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/summary")
    public SocialNetworkApiResponse<Map<String, Object>> networkSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        return success(networkSummary(snapshot), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/users/search")
    public SocialNetworkApiResponse<Map<String, Object>> searchUsers(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        List<Map<String, Object>> items = snapshot.users().stream()
                .filter(user -> isSearchableUser(user, normalizedQuery))
                .map(user -> userSummary(user, snapshot))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query == null ? "" : query);
        data.put("limit", boundedLimit);
        data.put("items", items);
        data.put("matchedCount", items.size());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("searchScope", List.of("user_id", "username", "display_name"));
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/users/{userId}")
    public SocialNetworkApiResponse<Map<String, Object>> userDetail(
            @PathVariable String datasetId,
            @PathVariable String userId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        Map<String, Object> data = userDetailData(snapshot, userId);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/local")
    public SocialNetworkApiResponse<Map<String, Object>> localNetwork(
            @PathVariable String datasetId,
            @RequestParam String centerNodeId,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "30") int topK,
            @RequestParam(defaultValue = "true") boolean includeExternalNodes) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        NetworkGraphView view = new com.prediction.socialnetwork.network.SocialNetworkCalculator()
                .localView(snapshot.network(), centerNodeId, depth, topK, includeExternalNodes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerNodeId", view.centerNodeId());
        data.put("depth", view.depth());
        data.put("topK", view.topK());
        data.put("includeExternalNodes", view.includeExternalNodes());
        data.put("truncated", view.truncated());
        data.put("truncationReason", view.truncationReason());
        data.put("nodes", view.nodes().stream()
                .map(node -> nodeSummary(node, snapshot))
                .toList());
        data.put("edges", view.edges());
        data.put("state", view.nodes().isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : view.truncated()
                    ? com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED
                    : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("scope", "中心节点局部关系网络");
        data.put("limits", Map.of(
                "maxNodes", com.prediction.socialnetwork.network.SocialNetworkCalculator.MAX_GRAPH_NODES,
                "maxEdges", com.prediction.socialnetwork.network.SocialNetworkCalculator.MAX_GRAPH_EDGES));
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/rankings")
    public SocialNetworkApiResponse<Map<String, Object>> rankings(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "indegree") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String normalizedMetric = metric == null ? "indegree" : metric.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<RankingValue> values = rankingValues(snapshot, normalizedMetric);
        SocialNetworkMetricCatalog.MetricDefinition definition =
                SocialNetworkMetricCatalog.require(normalizedMetric);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        double previous = Double.NaN;
        int rank = 0;
        for (int index = 0; index < Math.min(boundedLimit, values.size()); index++) {
            RankingValue value = values.get(index);
            if (Double.isNaN(previous) || Double.compare(previous, value.value()) != 0) {
                rank = index + 1;
                previous = value.value();
            }
            items.add(rankingItem(value, rank, normalizedMetric, snapshot));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", normalizedMetric);
        data.put("metricType", definition.type());
        data.put("label", definition.label());
        data.put("sampleSize", values.size());
        data.put("scope", definition.scope());
        data.put("metricDefinition", metricDefinition(definition));
        data.put("items", items);
        data.put("available", !values.isEmpty());
        data.put("state", values.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", values.isEmpty() ? "当前指标没有可用样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/summary")
    public SocialNetworkApiResponse<Map<String, Object>> interactionSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scale", snapshot.interactions());
        data.put("scope", "去重且纳入分析的推文样本；未归属来源保留在全局统计中");
        data.put("metrics", snapshot.interactions().metrics());
        data.put("metricDefinitions", metricDefinitions(
                List.of("like_count", "reply_count", "repost_count")));
        data.put("state", snapshot.interactions().available()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/tweets")
    public SocialNetworkApiResponse<Map<String, Object>> interactionTweets(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String field = interactionMetric(metric);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> items = snapshot.tweets().stream()
                .filter(NormalizedTweet::analysisIncluded)
                .map(tweet -> interactionValue(tweet, field))
                .filter(value -> value != null)
                .sorted((left, right) -> Long.compare(
                        (Long) right.get("value"), (Long) left.get("value")))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("limit", boundedLimit);
        data.put("scope", "去重且纳入分析的推文样本");
        data.put("items", items);
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用推文样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/users")
    public SocialNetworkApiResponse<Map<String, Object>> interactionUsers(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String field = interactionMetric(metric);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        snapshot.tweets().stream()
                .filter(tweet -> tweet.analysisIncluded()
                        && tweet.authorStatus() == AssociationStatus.LINKED
                        && tweet.authorUserId() != null)
                .forEach(tweet -> {
                    Long value = parseLong(tweet.fields().get(field));
                    if (value != null && value >= 0) {
                        grouped.computeIfAbsent(tweet.authorUserId(),
                                ignored -> new java.util.ArrayList<>()).add(value);
                    }
                });
        List<Map<String, Object>> items = grouped.entrySet().stream()
                .map(entry -> {
                    List<Long> values = entry.getValue();
                    long total = values.stream().mapToLong(Long::longValue).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", entry.getKey());
                    item.put("total", total);
                    item.put("average", total * 1.0 / values.size());
                    item.put("median", median(values));
                    item.put("sampleCount", values.size());
                    return item;
                })
                .sorted((left, right) -> Double.compare(
                        ((Number) right.get("total")).doubleValue(),
                        ((Number) left.get("total")).doubleValue()))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("limit", boundedLimit);
        data.put("scope", "已关联用户的去重推文样本");
        data.put("items", items);
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用用户样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/timeseries")
    public SocialNetworkApiResponse<Map<String, Object>> interactionTimeseries(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String field = interactionMetric(metric);
        Map<String, List<Long>> grouped = new java.util.TreeMap<>();
        int missingDateCount = 0;
        for (NormalizedTweet tweet : snapshot.tweets()) {
            if (!tweet.analysisIncluded()) {
                continue;
            }
            Long value = parseLong(tweet.fields().get(field));
            String date = tweet.fields().get("created_at");
            if (value == null || value < 0 || date == null || date.isBlank()) {
                if (date == null || date.isBlank()) {
                    missingDateCount++;
                }
                continue;
            }
            grouped.computeIfAbsent(date.strip(), ignored -> new java.util.ArrayList<>())
                    .add(value);
        }
        List<Map<String, Object>> items = grouped.entrySet().stream()
                .map(entry -> {
                    long total = entry.getValue().stream().mapToLong(Long::longValue).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("period", entry.getKey());
                    item.put("total", total);
                    item.put("average", total * 1.0 / entry.getValue().size());
                    item.put("sampleCount", entry.getValue().size());
                    return item;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("items", items);
        data.put("missingDateCount", missingDateCount);
        data.put("scope", "按 created_at 聚合的去重推文样本");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用时间样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/summary")
    public SocialNetworkApiResponse<Map<String, Object>> textSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        SocialNetworkTextAnalysis.Result result = snapshot.textAnalysis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("languageCounts", result.languageCounts());
        data.put("analysisSampleCount", result.analysisSampleCount());
        data.put("validTextSampleCount", result.validTextSampleCount());
        data.put("emptyTextSampleCount", result.emptyTextSampleCount());
        data.put("textProcessingVersion", result.textProcessingVersion());
        data.put("stopwordsVersion", result.stopwordsVersion());
        data.put("tokenizerVersion", result.tokenizerVersion());
        data.put("tfidfVersion", result.tfidfVersion());
        data.put("originalTextPreserved", true);
        data.put("available", result.validTextSampleCount() > 0);
        data.put("state", result.validTextSampleCount() > 0
                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/keywords")
    public SocialNetworkApiResponse<Map<String, Object>> textKeywords(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "all") String language,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<SocialNetworkTextAnalysis.Keyword> items = snapshot.textAnalysis().keywords().stream()
                .filter(keyword -> "all".equalsIgnoreCase(language)
                        || keyword.language().equalsIgnoreCase(language))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("language", language.toLowerCase());
        data.put("limit", boundedLimit);
        data.put("items", items);
        data.put("scope", "去重且纳入分析的有效文本推文");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前语言范围没有有效关键词。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/tfidf")
    public SocialNetworkApiResponse<Map<String, Object>> textTfidf(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", boundedLimit);
        data.put("items", snapshot.textAnalysis().tfidf().stream()
                .limit(boundedLimit).toList());
        data.put("formula", "tfidf=(1+log(raw_tf))*[log((N+1)/(df+1))+1]");
        data.put("scope", "文档单位为一条去重且有效文本推文");
        data.put("available", !snapshot.textAnalysis().tfidf().isEmpty());
        data.put("state", snapshot.textAnalysis().tfidf().isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/search")
    public SocialNetworkApiResponse<Map<String, Object>> textSearch(
            @PathVariable String datasetId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> items = snapshot.textAnalysis().documents().stream()
                .filter(document -> document.tokens().stream()
                        .anyMatch(token -> token.equals(normalizedQuery)))
                .limit(boundedLimit)
                .map(document -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tweetId", document.tweetId());
                    item.put("authorUserId", document.authorUserId());
                    item.put("language", document.language());
                    item.put("textOriginal", document.textOriginal());
                    item.put("textNormalized", document.textNormalized());
                    item.put("status", document.status());
                    return item;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("normalizedQuery", normalizedQuery);
        data.put("items", items);
        data.put("matchedCount", items.size());
        data.put("matchMode", "exact-token");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "没有精确词元匹配的推文。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/topic")
    public SocialNetworkApiResponse<Map<String, Object>> textTopic(
            @PathVariable String datasetId,
            @RequestParam String query,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Set<String> matchedTweetIds = snapshot.textAnalysis().documents().stream()
                .filter(document -> document.tokens().stream()
                        .anyMatch(token -> token.equals(normalizedQuery)))
                .map(SocialNetworkTextAnalysis.Document::tweetId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, com.prediction.socialnetwork.normalization.NormalizedTweet> tweets =
                snapshot.tweets().stream().collect(java.util.stream.Collectors.toMap(
                        com.prediction.socialnetwork.normalization.NormalizedTweet::tweetId,
                        tweet -> tweet,
                        (left, right) -> left));
        Map<String, Integer> userMatches = new LinkedHashMap<>();
        Map<String, long[]> userInteractions = new LinkedHashMap<>();
        List<Map<String, Object>> matchedTweets = new ArrayList<>();
        for (String tweetId : matchedTweetIds) {
            var tweet = tweets.get(tweetId);
            if (tweet == null) {
                continue;
            }
            if (tweet.authorStatus() == AssociationStatus.LINKED
                    && tweet.authorUserId() != null) {
                userMatches.merge(tweet.authorUserId(), 1, Integer::sum);
            }
            long[] interaction = userInteractions.computeIfAbsent(
                    tweet.authorUserId() == null ? "" : tweet.authorUserId(),
                    ignored -> new long[3]);
            interaction[0] += parseLongOrZero(tweet.fields().get("like_count"));
            interaction[1] += parseLongOrZero(tweet.fields().get("reply_count"));
            interaction[2] += parseLongOrZero(tweet.fields().get("repost_count"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tweetId", tweet.tweetId());
            item.put("authorUserId", tweet.authorUserId());
            item.put("authorStatus", tweet.authorStatus().name().toLowerCase());
            item.put("textOriginal", tweet.fields().getOrDefault("text", ""));
            item.put("createdAt", tweet.fields().get("created_at"));
            item.put("likeCount", parseLongOrZero(tweet.fields().get("like_count")));
            item.put("replyCount", parseLongOrZero(tweet.fields().get("reply_count")));
            item.put("repostCount", parseLongOrZero(tweet.fields().get("repost_count")));
            matchedTweets.add(item);
        }
        matchedTweets.sort((left, right) -> Long.compare(
                ((Number) right.get("likeCount")).longValue(),
                ((Number) left.get("likeCount")).longValue()));
        Set<String> relatedUsers = userMatches.keySet();
        List<Map<String, Object>> users = userMatches.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(boundedLimit)
                .map(entry -> {
                    long[] values = userInteractions.get(entry.getKey());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", entry.getKey());
                    item.put("matchedTweetCount", entry.getValue());
                    item.put("likeTotal", values[0]);
                    item.put("replyTotal", values[1]);
                    item.put("repostTotal", values[2]);
                    return item;
                }).toList();
        List<Map<String, Object>> edges = snapshot.relationships().stream()
                .filter(relation -> relation.analysisIncluded()
                        && relatedUsers.contains(relation.sourceUserId())
                        && relatedUsers.contains(relation.targetUserId()))
                .limit(500)
                .map(relation -> {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", relation.sourceUserId());
                    edge.put("target", relation.targetUserId());
                    edge.put("relationType", "following");
                    return edge;
                }).toList();
        Map<String, Object> network = new LinkedHashMap<>();
        network.put("nodeCount", relatedUsers.size());
        network.put("edgeCount", edges.size());
        network.put("edges", edges);
        network.put("scope", "主题相关已关联用户之间的真实关注关系");
        network.put("externalNodesIncluded", false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("normalizedQuery", normalizedQuery);
        data.put("matchedTweetCount", matchedTweets.size());
        data.put("relatedUserCount", relatedUsers.size());
        data.put("tweets", matchedTweets.stream().limit(boundedLimit).toList());
        data.put("users", users);
        data.put("network", network);
        data.put("textProcessingVersion", snapshot.textAnalysis().textProcessingVersion());
        data.put("matchMode", "exact-token");
        data.put("available", !matchedTweets.isEmpty());
        data.put("state", matchedTweets.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : relatedUsers.isEmpty()
                    ? com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED
                    : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", matchedTweets.isEmpty() ? "没有精确词元匹配的推文。" : null);
        return success(data, snapshot);
    }

    @PostMapping("/datasets/{datasetId}/exports")
    public ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> export(
            @PathVariable String datasetId,
            @RequestBody Map<String, Object> request) {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(datasetId);
        String format = textValue(request, "format", "json").toLowerCase();
        String exportType = textValue(request, "exportType", "");
        if (!List.of("json", "csv", "print").contains(format) || exportType.isBlank()) {
            return badRequest(SocialNetworkExportMessages.BAD_REQUEST_CODE,
                    SocialNetworkExportMessages.BAD_REQUEST_MESSAGE);
        }
        Map<String, Object> query = mapValue(request.get("query"));
        Map<String, Object> scope = mapValue(request.get("scope"));
        try {
            Map<String, Object> result = jsonExportAssembler.assemble(
                    snapshot, exportType, query, scope);
            SocialNetworkExportMetadata metadata =
                    (SocialNetworkExportMetadata) result.get("metadata");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exportId", UUID.randomUUID().toString());
            data.put("status", SocialNetworkExportMessages.STATUS_COMPLETED);
            data.put("statusLabel", SocialNetworkExportMessages.STATUS_COMPLETED_LABEL);
            data.put("expiresAt", null);
            data.put("downloadAvailable", false);
            data.put("format", format);
            data.put("metadata", metadata);
            data.put("resultState", "available");
            if ("csv".equals(format)) {
                data.put("contentType", "text/csv; charset=UTF-8");
                data.put("result", csvResult(exportType, result.get("data")));
            } else if ("print".equals(format)) {
                data.put("contentType", "text/plain; charset=UTF-8");
                data.put("result", printableSummaryGenerator.generate(metadata, exportType));
            } else {
                data.put("contentType", "application/json; charset=UTF-8");
                data.put("result", result.get("data"));
            }
            data.put("contentLength", contentLength(format, data.get("result")));
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    success(data, snapshot));
        } catch (IllegalArgumentException exception) {
            return badRequest("SN_BAD_REQUEST", exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.unprocessableEntity().body(
                    SocialNetworkApiResponse.error(
                            UUID.randomUUID().toString(),
                            SocialNetworkExportMessages.EMPTY_RESULT_CODE,
                            SocialNetworkExportMessages.EMPTY_RESULT_MESSAGE,
                            meta(snapshot)));
        }
    }

    private static long parseLongOrZero(String value) {
        Long parsed = parseLong(value);
        return parsed == null || parsed < 0 ? 0L : parsed;
    }

    private static String textValue(
            Map<String, Object> request,
            String key,
            String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null ? defaultValue : value.toString().strip();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("export query and scope must be objects");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int contentLength(String format, Object result) {
        try {
            return "json".equals(format)
                    ? objectMapper.writeValueAsBytes(result).length
                    : String.valueOf(result).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to serialize export result", exception);
        }
    }

    private String csvResult(String exportType, Object result) {
        List<Map<String, ?>> rows = new ArrayList<>();
        List<String> columns;
        if ("network".equalsIgnoreCase(exportType) && result instanceof Map<?, ?> network) {
            columns = List.of("recordType", "nodeId", "nodeType", "inDegree",
                    "outDegree", "pageRank", "source", "target", "relationType");
            Object nodes = network.get("nodes");
            if (nodes instanceof List<?> nodeList) {
                for (Object node : nodeList) {
                    if (node instanceof NetworkNode networkNode) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("nodeId", networkNode.id());
                        row.put("nodeType", networkNode.nodeType().value());
                        row.put("inDegree", networkNode.inDegree());
                        row.put("outDegree", networkNode.outDegree());
                        row.put("pageRank", networkNode.pageRank());
                        rows.add(withType("node", row));
                    } else if (node instanceof Map<?, ?> map) {
                        rows.add(withType("node", map));
                    }
                }
            }
            Object edges = network.get("edges");
            if (edges instanceof List<?> edgeList) {
                for (Object edge : edgeList) {
                    if (edge instanceof NetworkEdge networkEdge) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("source", networkEdge.source());
                        row.put("target", networkEdge.target());
                        row.put("relationType", networkEdge.relationType());
                        rows.add(withType("edge", row));
                    } else if (edge instanceof Map<?, ?> map) {
                        rows.add(withType("edge", map));
                    }
                }
            }
        } else if (result instanceof List<?> list) {
            columns = List.of("recordType", "userId", "tweetId", "authorUserId",
                    "authorStatus", "textOriginal", "createdAt", "value", "status");
            list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .forEach(item -> rows.add(withType(exportType, item)));
        } else if (result instanceof Map<?, ?> map) {
            columns = List.of("recordType", "metric", "total", "average",
                    "median", "validSampleCount", "missingCount", "zeroCount", "status");
            map.forEach((key, value) -> {
                if (value instanceof Map<?, ?> metric) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recordType", exportType);
                    row.put("metric", key);
                    metric.forEach((metricKey, metricValue) ->
                            row.put(String.valueOf(metricKey), metricValue));
                    rows.add(row);
                }
            });
        } else {
            throw new IllegalArgumentException("CSV export result is not tabular");
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("SN_EXPORT_EMPTY_RESULT");
        }
        return csvSerializer.serialize(columns, rows);
    }

    private static Map<String, Object> withType(String type, Map<?, ?> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordType", type);
        source.forEach((key, value) -> row.put(String.valueOf(key), value));
        return row;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> notFound(IllegalArgumentException exception) {
        return error(HttpStatus.NOT_FOUND, "SN_DATASET_NOT_FOUND", "未找到指定数据集");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> unavailable(IllegalStateException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SN_DATASET_UNAVAILABLE", "当前社交网络数据暂不可用");
    }

    private <T> SocialNetworkApiResponse<T> success(
            T data, SocialNetworkWorkspaceSnapshot snapshot) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                data,
                warnings(snapshot.quality()),
                meta(snapshot));
    }

    private static Map<String, Object> datasetSummary(
            SocialNetworkDataset dataset, SocialNetworkQualityReport quality) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetId", dataset.datasetId());
        data.put("name", dataset.name());
        data.put("type", dataset.type().name().toLowerCase());
        data.put("version", dataset.datasetVersion());
        data.put("status", quality.datasetStatus().name().toLowerCase());
        data.put("state", SocialNetworkStateCatalog.fromDataset(quality.datasetStatus()));
        data.put("sourceDescription", dataset.sourceDescription());
        data.put("timeRange", Map.of(
                "from", String.valueOf(dataset.timeRangeFrom()),
                "to", String.valueOf(dataset.timeRangeTo())));
        data.put("files", dataset.files());
        data.put("counts", quality.summary());
        return data;
    }

    private static Map<String, Object> qualitySummary(SocialNetworkQualityReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetStatus", report.datasetStatus().name().toLowerCase());
        data.put("state", SocialNetworkStateCatalog.fromDataset(report.datasetStatus()));
        data.put("summary", report.summary());
        data.put("userQuality", report.userQuality());
        data.put("relationshipQuality", report.relationshipQuality());
        data.put("tweetQuality", report.tweetQuality());
        data.put("interactionQuality", report.interactionQuality());
        data.put("textQuality", report.textQuality());
        data.put("coverage", report.coverage());
        return data;
    }

    private static Map<String, Object> networkSummary(
            SocialNetworkWorkspaceSnapshot snapshot) {
        var metrics = snapshot.network().metrics();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodeCount", metrics.nodeCount());
        data.put("edgeCount", metrics.edgeCount());
        data.put("profileUserCount", metrics.profileUserCount());
        data.put("externalNodeCount", metrics.externalNodeCount());
        data.put("isolatedNodeCount", metrics.isolatedNodeCount());
        data.put("density", metrics.density());
        data.put("metricVersion", metrics.metricVersion());
        data.put("pagerankVersion", metrics.pagerankVersion());
        data.put("includeExternalNodes", metrics.includeExternalNodes());
        data.put("excludeSelfLoops", metrics.excludeSelfLoops());
        data.put("scope", "全量规范化关系网络");
        data.put("state", metrics.nodeCount() == 0
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("pageRank", Map.of(
                "converged", snapshot.network().pageRank().converged(),
                "iterations", snapshot.network().pageRank().iterations(),
                "finalDelta", snapshot.network().pageRank().finalDelta(),
                "sum", snapshot.network().pageRank().sum()));
        data.put("interactionScale", snapshot.interactions());
        return data;
    }

    private static boolean isSearchableUser(NormalizedUser user, String query) {
        if (user.userId() == null
                || user.userId().isBlank()
                || user.normalizationStatus() == NormalizationStatus.REJECTED_REQUIRED_FIELD
                || user.normalizationStatus() == NormalizationStatus.REJECTED_INVALID_FORMAT) {
            return false;
        }
        if (query.isBlank()) {
            return true;
        }
        return List.of(user.userId(), user.fields().get("username"), user.fields().get("display_name"))
                .stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase())
                .anyMatch(value -> value.contains(query));
    }

    private static Map<String, Object> userSummary(
            NormalizedUser user, SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.userId());
        data.put("username", user.fields().getOrDefault("username", ""));
        data.put("displayName", user.fields().getOrDefault("display_name", ""));
        data.put("nodeType", snapshot.network().nodes().stream()
                .filter(node -> node.id().equals(user.userId()))
                .map(node -> node.nodeType().value())
                .findFirst()
                .orElse("profile"));
        return data;
    }

    private static Map<String, Object> userDetailData(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        NetworkNode node = snapshot.network().nodes().stream()
                .filter(item -> item.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user node not found"));
        NormalizedUser profile = snapshot.users().stream()
                .filter(user -> userId.equals(user.userId()))
                .findFirst()
                .orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nodeType", node.nodeType().value());
        data.put("profileStatus", profile == null ? "unavailable" : "available");
        data.put("profileState", profile == null
                ? com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("datasetVersion", snapshot.dataset().datasetVersion());
        data.put("scope", "当前规范化关系网络与采集样本");
        if (profile == null || node.nodeType() == NetworkNodeType.EXTERNAL) {
            data.put("profile", Map.of(
                    "username", "",
                    "displayName", "",
                    "bio", "",
                    "status", "资料未采集"));
        } else {
            data.put("profile", profileFields(profile));
        }
        data.put("networkPosition", Map.of(
                "inDegree", node.inDegree(),
                "outDegree", node.outDegree(),
                "pageRank", node.pageRank(),
                "isolated", node.isolated()));
        data.put("rawAccountFields", rawAccountFields(profile));
        data.put("rankingReasons", rankingReasons(snapshot, userId));
        data.put("tweetEvidence", tweetEvidence(snapshot, userId));
        data.put("neighbors", neighbors(snapshot, userId));
        data.put("textSummary", Map.of(
                "available", false,
                "state", com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE,
                "reason", "关键词和主题分析将在文本业务任务中接入。"));
        data.put("links", Map.of(
                "localNetwork", "/api/social-network/datasets/" + snapshot.dataset().datasetId()
                        + "/network/local?centerNodeId=" + userId));
        return data;
    }

    private static Map<String, Object> profileFields(NormalizedUser profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", profile.fields().getOrDefault("username", ""));
        data.put("displayName", profile.fields().getOrDefault("display_name", ""));
        data.put("bio", profile.fields().getOrDefault("bio", ""));
        data.put("location", profile.fields().getOrDefault("location", ""));
        data.put("createdAt", profile.fields().getOrDefault("created_at", ""));
        data.put("status", "已采集");
        return data;
    }

    private static Map<String, Object> rawAccountFields(NormalizedUser profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (profile == null) {
            data.put("available", false);
            data.put("state", com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE);
            data.put("reason", "外部节点未采集用户资料。");
            return data;
        }
        data.put("available", true);
        data.put("state", com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("followersCount", parseLong(profile.fields().get("followers_count")));
        data.put("followingCount", parseLong(profile.fields().get("following_count")));
        data.put("declaredTweetCount", parseLong(profile.fields().get("tweet_count")));
        data.put("source", "用户资料原始字段");
        return data;
    }

    private static Map<String, Object> rankingReasons(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String metric : List.of("indegree", "outdegree", "pagerank",
                "followers_count", "following_count", "tweet_count")) {
            List<RankingValue> values = rankingValues(snapshot, metric);
            int position = -1;
            double value = Double.NaN;
            for (int index = 0; index < values.size(); index++) {
                if (userId.equals(values.get(index).nodeId())) {
                    position = index + 1;
                    value = values.get(index).value();
                    break;
                }
            }
            if (position > 0) {
                data.put(metric, Map.of(
                        "rank", position,
                        "value", value,
                        "reason", "按" + metricLabel(metric) + "降序排列"));
            }
        }
        return data;
    }

    private static Map<String, Object> tweetEvidence(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        List<NormalizedTweet> tweets = snapshot.tweets().stream()
                .filter(tweet -> userId.equals(tweet.authorUserId()) && tweet.analysisIncluded())
                .toList();
        Map<String, InteractionMetricSummary> metrics = new LinkedHashMap<>();
        for (String field : List.of("like_count", "reply_count", "repost_count")) {
            List<Long> values = tweets.stream()
                    .map(tweet -> parseLong(tweet.fields().get(field)))
                    .filter(value -> value != null && value >= 0)
                    .toList();
            long total = values.stream().mapToLong(Long::longValue).sum();
            metrics.put(field, new InteractionMetricSummary(
                    field, total, values.isEmpty() ? 0.0 : total * 1.0 / values.size(),
                    median(values), values.size(), tweets.size() - values.size(),
                    (int) values.stream().filter(value -> value == 0).count()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", !tweets.isEmpty());
        data.put("state", tweets.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("sampleCount", tweets.size());
        data.put("scope", "当前采集且成功关联的推文样本");
        data.put("interactions", metrics);
        data.put("reason", tweets.isEmpty() ? "当前用户没有可展示的关联推文样本。" : null);
        return data;
    }

    private static double median(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new java.util.ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static Map<String, Object> neighbors(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        List<Map<String, Object>> items = snapshot.network().edges().stream()
                .filter(edge -> userId.equals(edge.source()) || userId.equals(edge.target()))
                .map(edge -> {
                    String neighborId = userId.equals(edge.source()) ? edge.target() : edge.source();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", neighborId);
                    item.put("direction", userId.equals(edge.source()) ? "outgoing" : "incoming");
                    item.put("relationType", edge.relationType());
                    item.put("external", snapshot.network().nodes().stream()
                            .filter(node -> neighborId.equals(node.id()))
                            .anyMatch(node -> node.nodeType() == NetworkNodeType.EXTERNAL));
                    return item;
                })
                .limit(50)
                .toList();
        return Map.of("items", items, "sampleLimit", 50, "totalAvailable", items.size());
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String interactionMetric(String metric) {
        String normalized = metric == null ? "like_count" : metric.strip().toLowerCase();
        if (!List.of("like_count", "reply_count", "repost_count").contains(normalized)) {
            throw new IllegalArgumentException("unsupported interaction metric");
        }
        return normalized;
    }

    private static Map<String, Object> interactionValue(
            NormalizedTweet tweet, String metric) {
        Long value = parseLong(tweet.fields().get(metric));
        if (value == null || value < 0) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tweetId", tweet.tweetId());
        item.put("authorUserId", tweet.authorUserId());
        item.put("authorStatus", tweet.authorStatus().name().toLowerCase());
        item.put("sourceFolder", tweet.sourceFolder());
        item.put("createdAt", tweet.fields().get("created_at"));
        item.put("value", value);
        item.put("qualityFlags", tweet.qualityFlags());
        return item;
    }

    private static Map<String, Object> nodeSummary(
            NetworkNode node, SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", node.id());
        data.put("nodeType", node.nodeType().value());
        data.put("inDegree", node.inDegree());
        data.put("outDegree", node.outDegree());
        data.put("pageRank", node.pageRank());
        data.put("isolated", node.isolated());
        if (node.nodeType() == NetworkNodeType.PROFILE) {
            snapshot.users().stream()
                    .filter(user -> node.id().equals(user.userId()))
                    .findFirst()
                    .ifPresent(user -> {
                        data.put("username", user.fields().getOrDefault("username", ""));
                        data.put("displayName", user.fields().getOrDefault("display_name", ""));
                    });
        }
        return data;
    }

    private static List<RankingValue> rankingValues(
            SocialNetworkWorkspaceSnapshot snapshot, String metric) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (Set.of("indegree", "outdegree", "pagerank").contains(metric)) {
            snapshot.network().nodes().forEach(node -> values.put(node.id(),
                    switch (metric) {
                        case "indegree" -> (double) node.inDegree();
                        case "outdegree" -> (double) node.outDegree();
                        default -> node.pageRank();
                    }));
        } else {
            Set<String> seen = new LinkedHashSet<>();
            snapshot.users().forEach(user -> {
                if (user.userId() == null || !seen.add(user.userId())) {
                    return;
                }
                String raw = user.fields().get(fieldForMetric(metric));
                if (raw == null || raw.isBlank()) {
                    return;
                }
                try {
                    long parsed = Long.parseLong(raw);
                    if (parsed >= 0) {
                        values.put(user.userId(), (double) parsed);
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid raw counters remain excluded and are represented by coverage.
                }
            });
        }
        return values.entrySet().stream()
                .map(entry -> new RankingValue(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(RankingValue::value).reversed()
                        .thenComparing(RankingValue::nodeId))
                .toList();
    }

    private static Map<String, Object> rankingItem(
            RankingValue value,
            int rank,
            String metric,
            SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("rank", rank);
        item.put("nodeId", value.nodeId());
        item.put("value", value.value());
        item.put("status", snapshot.network().nodes().stream()
                .filter(node -> node.id().equals(value.nodeId()))
                .map(node -> node.nodeType() == NetworkNodeType.EXTERNAL ? "external" : "available")
                .findFirst()
                .orElse("available"));
        item.put("sampleSize", 1);
        item.put("reason", "按" + metricLabel(metric) + "降序排列");
        snapshot.users().stream()
                .filter(user -> value.nodeId().equals(user.userId()))
                .findFirst()
                .ifPresent(user -> {
                    item.put("username", user.fields().getOrDefault("username", ""));
                    item.put("displayName", user.fields().getOrDefault("display_name", ""));
                });
        return item;
    }

    private static String fieldForMetric(String metric) {
        return switch (metric) {
            case "followers_count" -> "followers_count";
            case "following_count" -> "following_count";
            case "tweet_count" -> "tweet_count";
            default -> throw new IllegalArgumentException("unsupported ranking metric");
        };
    }

    private static String metricType(String metric) {
        return SocialNetworkMetricCatalog.require(metric).type();
    }

    private static String metricLabel(String metric) {
        return SocialNetworkMetricCatalog.require(metric).label();
    }

    private static Map<String, Object> metricDefinition(
            SocialNetworkMetricCatalog.MetricDefinition definition) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", definition.key());
        data.put("label", definition.label());
        data.put("type", definition.type());
        data.put("scope", definition.scope());
        data.put("missingValueRule", definition.missingValueRule());
        data.put("zeroValueRule", definition.zeroValueRule());
        data.put("invalidValueRule", definition.invalidValueRule());
        return data;
    }

    private static Map<String, Object> metricDefinitions(List<String> metrics) {
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (String metric : metrics) {
            SocialNetworkMetricCatalog.MetricDefinition definition =
                    SocialNetworkMetricCatalog.require(metric);
            definitions.put(metric, metricDefinition(definition));
        }
        return definitions;
    }

    private record RankingValue(String nodeId, double value) {
    }

    private static List<Map<String, Object>> warnings(SocialNetworkQualityReport report) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (QualityIssue issue : report.issues()) {
            if (!issue.severity().name().equals("WARNING")
                    && !issue.severity().name().equals("INFO")) {
                continue;
            }
            String key = issue.severity() + "|" + issue.code() + "|"
                    + issue.dimension() + "|" + issue.message();
            Map<String, Object> item = grouped.computeIfAbsent(key, ignored -> warning(issue));
            item.put("count", ((Integer) item.getOrDefault("count", 0)) + 1);
        }
        return List.copyOf(grouped.values());
    }

    private static Map<String, Object> warning(QualityIssue issue) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", issue.code());
        warning.put("message", issue.message());
        warning.put("severity", issue.severity().name().toLowerCase());
        warning.put("affectedScope", issue.dimension());
        warning.put("count", 0);
        return warning;
    }

    private static Map<String, Object> meta(SocialNetworkWorkspaceSnapshot snapshot) {
        SocialNetworkDataset dataset = snapshot.dataset();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.datasetId());
        meta.put("datasetVersion", dataset.datasetVersion());
        meta.put("normalizationVersion", dataset.normalizationVersion());
        meta.put("metricVersion", dataset.metricVersion());
        meta.put("textProcessingVersion", dataset.textProcessingVersion());
        meta.put("explanationVersion", dataset.explanationVersion());
        meta.put("qualityStatus", snapshot.quality().datasetStatus().name().toLowerCase());
        meta.put("capabilityVersion", snapshot.capabilities().capabilityVersion());
        meta.put("scope", Map.of());
        meta.put("calculatedAt", Instant.now().toString());
        return meta;
    }

    private static ResponseEntity<SocialNetworkApiResponse<Void>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", Map.of())));
    }

    private static ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> badRequest(
            String code,
            String message) {
        return ResponseEntity.badRequest().body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", Map.of())));
    }
}
