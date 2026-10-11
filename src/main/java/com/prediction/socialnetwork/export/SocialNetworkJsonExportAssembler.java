package com.prediction.socialnetwork.export;

import com.prediction.socialnetwork.application.SocialNetworkWorkspaceSnapshot;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds traceable JSON-ready data without writing files or changing statistics.
 */
public final class SocialNetworkJsonExportAssembler {

    public Map<String, Object> assemble(
            SocialNetworkWorkspaceSnapshot snapshot,
            String exportType,
            Map<String, Object> query,
            Map<String, Object> scope) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        String normalizedType = requireText(exportType, "exportType").toLowerCase();
        Map<String, Object> normalizedQuery = query == null ? Map.of() : Map.copyOf(query);
        Map<String, Object> normalizedScope = scope == null ? Map.of() : Map.copyOf(scope);

        Object data = switch (normalizedType) {
            case "network" -> network(snapshot);
            case "users" -> users(snapshot);
            case "tweets" -> tweets(snapshot);
            case "interactions" -> interactions(snapshot);
            case "topic" -> topic(snapshot, normalizedQuery);
            case "quality" -> quality(snapshot);
            default -> throw new IllegalArgumentException("unsupported export type: " + exportType);
        };
        int sampleCount = sampleCount(normalizedType, data);
        if (sampleCount == 0) {
            throw new IllegalStateException("SN_EXPORT_EMPTY_RESULT");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportType", normalizedType);
        result.put("metadata", metadata(snapshot, normalizedQuery, normalizedScope, sampleCount));
        result.put("data", data);
        return result;
    }

    private static Map<String, Object> network(SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", snapshot.network().nodes());
        data.put("edges", snapshot.network().edges());
        data.put("metrics", snapshot.network().metrics());
        return data;
    }

    private static List<Map<String, Object>> users(SocialNetworkWorkspaceSnapshot snapshot) {
        return snapshot.users().stream().map(user -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", user.userId());
            item.put("fields", user.fields());
            item.put("status", user.normalizationStatus().name().toLowerCase());
            return item;
        }).toList();
    }

    private static List<Map<String, Object>> tweets(SocialNetworkWorkspaceSnapshot snapshot) {
        return snapshot.tweets().stream()
                .filter(NormalizedTweet::analysisIncluded)
                .map(tweet -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tweetId", tweet.tweetId());
                    item.put("authorUserId", tweet.authorUserId());
                    item.put("authorStatus", tweet.authorStatus().name().toLowerCase());
                    item.put("fields", tweet.fields());
                    return item;
                }).toList();
    }

    private static Map<String, Object> interactions(SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", snapshot.interactions().metrics());
        data.put("analysisSampleCount", snapshot.interactions().analysisSampleCount());
        data.put("linkedAuthorSampleCount", snapshot.interactions().linkedAuthorSampleCount());
        data.put("unassignedSourceSampleCount",
                snapshot.interactions().unassignedSourceSampleCount());
        data.put("unlinkedAuthorSampleCount",
                snapshot.interactions().unlinkedAuthorSampleCount());
        return data;
    }

    private static List<Map<String, Object>> topic(
            SocialNetworkWorkspaceSnapshot snapshot,
            Map<String, Object> query) {
        String term = query.get("term") == null ? "" : query.get("term").toString().strip().toLowerCase();
        if (term.isBlank()) {
            throw new IllegalArgumentException("topic query term must not be blank");
        }
        return snapshot.textAnalysis().documents().stream()
                .filter(document -> document.tokens().stream().anyMatch(term::equals))
                .map(document -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tweetId", document.tweetId());
                    item.put("authorUserId", document.authorUserId());
                    item.put("textOriginal", document.textOriginal());
                    item.put("matchedTerm", term);
                    return item;
                }).toList();
    }

    private static Map<String, Object> quality(SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetStatus", snapshot.quality().datasetStatus().name().toLowerCase());
        data.put("summary", snapshot.quality().summary());
        data.put("coverage", snapshot.quality().coverage());
        data.put("issues", snapshot.quality().issues());
        return data;
    }

    private static SocialNetworkExportMetadata metadata(
            SocialNetworkWorkspaceSnapshot snapshot,
            Map<String, Object> query,
            Map<String, Object> scope,
            int sampleCount) {
        var dataset = snapshot.dataset();
        Map<String, Object> sampleCounts = new LinkedHashMap<>();
        sampleCounts.put("resultCount", sampleCount);
        sampleCounts.put("userCount", snapshot.users().size());
        sampleCounts.put("tweetCount", snapshot.tweets().size());
        sampleCounts.put("edgeCount", snapshot.network().edges().size());
        List<String> boundaryNotes = new ArrayList<>();
        boundaryNotes.add("导出仅包含当前数据集和筛选范围内已有真实证据。");
        boundaryNotes.add("PageRank 表示网络结构位置，不等同于社会影响力。");
        boundaryNotes.add("点赞、评论和转发保持独立统计，不生成综合互动分。");
        return new SocialNetworkExportMetadata(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                dataset.metricVersion(),
                dataset.textProcessingVersion(),
                dataset.explanationVersion(),
                query,
                scope,
                sampleCounts,
                dataset.sourceDescription(),
                Instant.now(),
                boundaryNotes);
    }

    private static int sampleCount(String exportType, Object data) {
        if (data instanceof List<?> values) {
            return values.size();
        }
        if (data instanceof Map<?, ?> values) {
            Object resultCount = values.get("resultCount");
            if (resultCount instanceof Number number) {
                return number.intValue();
            }
            return switch (exportType) {
                case "network" -> {
                    Object nodes = values.get("nodes");
                    yield nodes instanceof List<?> list ? list.size() : 0;
                }
                case "interactions" -> {
                    Object analysisSampleCount = values.get("analysisSampleCount");
                    yield analysisSampleCount instanceof Number number
                            ? number.intValue() : 0;
                }
                case "quality" -> 1;
                default -> 0;
            };
        }
        return 0;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
