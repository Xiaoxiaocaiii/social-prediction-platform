package com.prediction.socialnetwork.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared metric labels and interpretation rules for page and export consumers.
 */
public final class SocialNetworkMetricCatalog {

    private static final Map<String, MetricDefinition> DEFINITIONS = definitions();

    private SocialNetworkMetricCatalog() {
    }

    public static MetricDefinition require(String metric) {
        MetricDefinition definition = DEFINITIONS.get(metric);
        if (definition == null) {
            throw new IllegalArgumentException("unsupported social network metric: " + metric);
        }
        return definition;
    }

    public static Map<String, MetricDefinition> all() {
        return DEFINITIONS;
    }

    private static Map<String, MetricDefinition> definitions() {
        Map<String, MetricDefinition> values = new LinkedHashMap<>();
        add(values, "indegree", "入度", "network",
                "全量规范化关系网络中的唯一有效入边数量");
        add(values, "outdegree", "出度", "network",
                "全量规范化关系网络中的唯一有效出边数量");
        add(values, "pagerank", "PageRank", "network",
                "当前有向关系网络中的结构位置指标，不等同于社会影响力");
        add(values, "density", "网络密度", "network",
                "当前节点范围内实际有向边占理论最大有向边数的比例");
        add(values, "followers_count", "粉丝数（原始账户字段）", "raw_account_field",
                "用户资料中的原始账户字段，不表示增长量");
        add(values, "following_count", "关注数（原始账户字段）", "raw_account_field",
                "用户资料中的原始账户字段，不替代计算出度");
        add(values, "tweet_count", "推文总数（原始账户字段）", "raw_account_field",
                "用户资料中的原始账户字段，不等同于当前采集样本数");
        add(values, "like_count", "点赞数", "interaction",
                "去重且纳入分析的推文样本；未归属来源保留在全局统计");
        add(values, "reply_count", "评论数", "interaction",
                "去重且纳入分析的推文样本；未归属来源保留在全局统计");
        add(values, "repost_count", "转发数", "interaction",
                "去重且纳入分析的推文样本；未归属来源保留在全局统计");
        add(values, "term_frequency", "词元频次", "text",
                "去重且纳入分析的有效文本推文");
        add(values, "document_frequency", "推文覆盖数", "text",
                "包含该词元的去重且有效文本推文数");
        add(values, "tfidf", "TF-IDF 区分度", "text",
                "一条去重且有效文本推文为一个文档单位");
        return Map.copyOf(values);
    }

    private static void add(
            Map<String, MetricDefinition> values,
            String key,
            String label,
            String type,
            String scope) {
        values.put(key, new MetricDefinition(
                key,
                label,
                type,
                scope,
                "missing 表示未采集或无法关联",
                "valid_zero 表示明确采集到的 0",
                "invalid 不进入该指标的有效样本"));
    }

    public record MetricDefinition(
            String key,
            String label,
            String type,
            String scope,
            String missingValueRule,
            String zeroValueRule,
            String invalidValueRule) {
    }
}
