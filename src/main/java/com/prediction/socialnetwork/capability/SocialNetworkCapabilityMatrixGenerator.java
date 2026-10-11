package com.prediction.socialnetwork.capability;

import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SocialNetworkStateCatalog;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SocialNetworkCapabilityMatrixGenerator {

    public static final String CAPABILITY_VERSION = "capability-v1.0";

    public SocialNetworkCapabilityMatrix generate(
            SocialNetworkDataset dataset,
            SocialNetworkQualityReport quality) {
        Inputs inputs = Inputs.from(dataset);
        List<CapabilityItem> items = new ArrayList<>();
        items.add(datasetEntry(dataset, quality));
        items.add(networkOverview(inputs, quality));
        items.add(followingNetwork(inputs, quality));
        items.add(importantNodes(inputs, quality));
        items.add(userDetail(inputs, quality));
        items.add(tweetInteraction(inputs, quality));
        items.add(topicAnalysis(inputs, quality));
        items.add(topicNetworkLinkage(inputs, quality));
        items.add(bookmarkAnalysis(inputs, quality));
        return new SocialNetworkCapabilityMatrix(
                dataset.datasetId(),
                dataset.datasetVersion(),
                CAPABILITY_VERSION,
                Instant.now(),
                items);
    }

    private CapabilityItem datasetEntry(
            SocialNetworkDataset dataset, SocialNetworkQualityReport quality) {
        if (quality.datasetStatus() == DatasetStatus.UNAVAILABLE) {
            return item("dataset-entry", "B01", CapabilityStatus.UNAVAILABLE,
                    "DATASET_UNAVAILABLE", "数据集存在阻断性质量问题，暂不可分析。",
                    List.of(), List.of(), "修复文件读取或必填字段问题后重新检查。");
        }
        if (quality.datasetStatus() == DatasetStatus.PARTIAL) {
            return item("dataset-entry", "B01", CapabilityStatus.LIMITED,
                    "DATASET_PARTIAL", "数据集可以查看，但部分分析能力不可用。",
                    List.of(), List.of(), "查看质量报告并使用当前可用的数据范围。");
        }
        return item("dataset-entry", "B01", CapabilityStatus.AVAILABLE,
                "DATASET_READY", "数据集入口可用，具体分析能力按质量报告分别判定。",
                List.of(), List.of(), "查看质量报告和能力明细。");
    }

    private CapabilityItem networkOverview(Inputs inputs, SocialNetworkQualityReport quality) {
        int candidates = value(quality.relationshipQuality(), "candidate_relationship_count");
        if (!inputs.relationships() || candidates == 0) {
            return item("network-overview", "B02", CapabilityStatus.UNAVAILABLE,
                    "MISSING_VALID_RELATIONSHIPS", "缺少可用于网络概览的有效关系。",
                    List.of("relationships"), inputs.names(),
                    "上传或选择包含有效 relationships 的数据集。");
        }
        if (!inputs.users()) {
            return item("network-overview", "B02", CapabilityStatus.AVAILABLE,
                    "NETWORK_WITHOUT_PROFILES", "网络结构摘要可用，但节点资料未采集。",
                    List.of("relationships"), inputs.names(),
                    "查看结构指标；补充 users 后可显示节点资料。");
        }
        return item("network-overview", "B02", CapabilityStatus.AVAILABLE,
                "NETWORK_INPUTS_READY", "网络规模和关系摘要可用。",
                List.of("relationships"), inputs.names(), "查看网络质量提示和结构范围。");
    }

    private CapabilityItem followingNetwork(Inputs inputs, SocialNetworkQualityReport quality) {
        int candidates = value(quality.relationshipQuality(), "candidate_relationship_count");
        if (!inputs.relationships() || candidates == 0) {
            return item("following-network", "B03", CapabilityStatus.UNAVAILABLE,
                    "MISSING_VALID_RELATIONSHIPS", "缺少可绘制的有效关注关系。",
                    List.of("relationships"), inputs.names(),
                    "上传包含 source_user_id 和 target_user_id 的关系文件。");
        }
        if (quality.relationshipQuality().getOrDefault("invalid_relationship_count", 0) > 0) {
            return item("following-network", "B03", CapabilityStatus.LIMITED,
                    "RELATIONSHIP_QUALITY_LIMITED", "关注网络可用，但部分关系因质量问题被排除。",
                    List.of("relationships"), inputs.names(),
                    "仅查看纳入分析的有效关系，并展开质量详情。");
        }
        return item("following-network", "B03", CapabilityStatus.AVAILABLE,
                "RELATIONSHIPS_READY", "用户关注网络可用。",
                List.of("relationships"), inputs.names(), "默认按局部范围绘制网络。");
    }

    private CapabilityItem importantNodes(Inputs inputs, SocialNetworkQualityReport quality) {
        int candidates = value(quality.relationshipQuality(), "candidate_relationship_count");
        if (!inputs.relationships() || candidates == 0) {
            return item("important-nodes", "B04", CapabilityStatus.UNAVAILABLE,
                    "MISSING_NETWORK_STRUCTURE", "缺少入度、出度和 PageRank 所需的网络结构。",
                    List.of("relationships"), inputs.names(),
                    "补充有效关系后重新生成节点排行。");
        }
        if (!inputs.users()) {
            return item("important-nodes", "B04", CapabilityStatus.LIMITED,
                    "PROFILE_RANKING_UNAVAILABLE", "网络结构排行可用，但用户资料字段排行不可用。",
                    List.of("relationships"), inputs.names(),
                    "仅查看网络指标排行；补充 users 后开放账户字段排行。");
        }
        return item("important-nodes", "B04", CapabilityStatus.AVAILABLE,
                "NETWORK_RANKINGS_READY", "网络指标和用户原始字段排行可用。",
                List.of("relationships"), inputs.names(),
                "网络指标与原始账户字段分开查看。");
    }

    private CapabilityItem userDetail(Inputs inputs, SocialNetworkQualityReport quality) {
        int users = value(quality.userQuality(), "normalized_user_count");
        if (!inputs.users() || users == 0) {
            if (inputs.tweets() && value(quality.tweetQuality(), "linked_author_count") > 0) {
                return item("user-detail", "B05", CapabilityStatus.LIMITED,
                        "MISSING_USER_PROFILE", "存在可关联作者，但缺少 users 资料字段。",
                        List.of("users"), inputs.names(),
                        "仅展示作者 ID、推文样本和可计算摘要。");
            }
            return item("user-detail", "B05", CapabilityStatus.UNAVAILABLE,
                    "MISSING_USERS", "缺少可用于用户详情的用户资料。",
                    List.of("users"), inputs.names(),
                    "上传包含 user_id 的 users 文件。");
        }
        if (!inputs.relationships() || !inputs.tweets()) {
            return item("user-detail", "B05", CapabilityStatus.LIMITED,
                    "PARTIAL_USER_CONTEXT", "用户资料可用，但网络或推文区块缺少对应输入。",
                    List.of("users"), inputs.names(),
                    "当前仅展示可用资料；补充 relationships 或 tweets 后扩展详情。");
        }
        return item("user-detail", "B05", CapabilityStatus.AVAILABLE,
                "USER_DETAIL_READY", "用户资料、网络和推文详情可按数据质量展示。",
                List.of("users"), inputs.names(), "未采集字段保持为空并显示覆盖提示。");
    }

    private CapabilityItem tweetInteraction(Inputs inputs, SocialNetworkQualityReport quality) {
        int tweets = value(quality.tweetQuality(), "unique_tweet_id_count");
        if (!inputs.tweets() || tweets == 0) {
            return item("tweet-interaction", "B06", CapabilityStatus.UNAVAILABLE,
                    "MISSING_VALID_TWEETS", "缺少可用于互动统计的有效推文样本。",
                    List.of("tweets"), inputs.names(),
                    "上传包含有效 tweet_id 的 tweets 文件。");
        }
        double linkRate = quality.coverage().getOrDefault("tweet_author_link_rate", 0.0);
        if (linkRate < 1.0 || value(quality.tweetQuality(), "unassigned_source_count") > 0) {
            return item("tweet-interaction", "B06", CapabilityStatus.LIMITED,
                    "AUTHOR_LINKAGE_LIMITED", "推文互动可统计，但部分作者未能关联到用户。",
                    List.of("tweets"), inputs.names(),
                    "全局推文结果可用；用户聚合排除未关联作者。");
        }
        return item("tweet-interaction", "B06", CapabilityStatus.AVAILABLE,
                "TWEETS_READY", "点赞、评论和转发可以分别统计。",
                List.of("tweets"), inputs.names(), "互动指标分别展示，不生成综合分。");
    }

    private CapabilityItem topicAnalysis(Inputs inputs, SocialNetworkQualityReport quality) {
        int validText = value(quality.textQuality(), "valid_text_count");
        if (!inputs.tweets() || validText == 0) {
            return item("topic-analysis", "B07", CapabilityStatus.UNAVAILABLE,
                    "NO_VALID_TEXT", "缺少可用于关键词和 TF-IDF 的有效文本样本。",
                    List.of("tweets", "text"), inputs.names(),
                    "补充包含非空 text 的推文样本。");
        }
        int uniqueTweets = value(quality.tweetQuality(), "unique_tweet_id_count");
        if (uniqueTweets == 0 || validText * 1.0 / uniqueTweets < 0.8) {
            return item("topic-analysis", "B07", CapabilityStatus.LIMITED,
                    "TEXT_SAMPLE_LIMITED", "文本样本不足，主题结果仅作有限样本分析。",
                    List.of("tweets", "text"), inputs.names(),
                    "显示样本量和覆盖率，不扩展为总体结论。");
        }
        return item("topic-analysis", "B07", CapabilityStatus.AVAILABLE,
                "TEXT_READY", "关键词和 TF-IDF 分析可用。",
                List.of("tweets", "text"), inputs.names(),
                "结果回溯到匹配推文，不生成因果或影响力结论。");
    }

    private CapabilityItem topicNetworkLinkage(
            Inputs inputs, SocialNetworkQualityReport quality) {
        CapabilityStatus topicStatus = topicAnalysis(inputs, quality).status();
        if (topicStatus == CapabilityStatus.UNAVAILABLE) {
            return item("topic-network-linkage", "B07", CapabilityStatus.UNAVAILABLE,
                    "TOPIC_ANALYSIS_UNAVAILABLE", "主题文本样本不可用，无法生成主题关联用户。",
                    List.of("tweets", "relationships"), inputs.names(),
                    "先满足主题分析的有效文本条件。");
        }
        int candidates = value(quality.relationshipQuality(), "candidate_relationship_count");
        if (!inputs.relationships() || candidates == 0) {
            return item("topic-network-linkage", "B07", CapabilityStatus.DISABLED,
                    "NO_RELATIONSHIP_LINKAGE", "主题分析可用，但没有真实关注关系可用于联动子图。",
                    List.of("tweets", "relationships"), inputs.names(),
                    "仅查看主题推文和相关用户，不绘制主题网络边。");
        }
        if (topicStatus == CapabilityStatus.LIMITED
                || quality.coverage().getOrDefault("tweet_author_link_rate", 0.0) < 1.0) {
            return item("topic-network-linkage", "B07", CapabilityStatus.LIMITED,
                    "TOPIC_NETWORK_LIMITED", "主题结果可联动真实关系，但样本或作者关联覆盖有限。",
                    List.of("tweets", "relationships"), inputs.names(),
                    "仅绘制已关联作者之间的真实关注关系，并标注样本范围。");
        }
        return item("topic-network-linkage", "B07", CapabilityStatus.AVAILABLE,
                "TOPIC_NETWORK_READY", "主题相关用户可以联动真实关注关系。",
                List.of("tweets", "relationships"), inputs.names(),
                "主题网络只表示真实关注关系，不表示传播路径。");
    }

    private CapabilityItem bookmarkAnalysis(Inputs inputs, SocialNetworkQualityReport quality) {
        if (!inputs.tweets() || value(quality.tweetQuality(), "unique_tweet_id_count") == 0) {
            return item("bookmark-analysis", "B06", CapabilityStatus.UNAVAILABLE,
                    "MISSING_TWEETS", "缺少书签字段所属的有效推文样本。",
                    List.of("tweets", "bookmark_count"), inputs.names(),
                    "补充有效推文和 bookmark_count 字段。");
        }
        if (value(quality.interactionQuality(), "bookmark_all_zero") == 1) {
            return item("bookmark-analysis", "B06", CapabilityStatus.DISABLED,
                    "BOOKMARK_ALL_ZERO", "当前书签字段全为零，暂不开放书签分析。",
                    List.of("tweets", "bookmark_count"), inputs.names(),
                    "保留字段质量证据，待出现有效差异后重新开放。");
        }
        return item("bookmark-analysis", "B06", CapabilityStatus.AVAILABLE,
                "BOOKMARK_READY", "书签字段存在有效差异，可以单独统计。",
                List.of("tweets", "bookmark_count"), inputs.names(),
                "书签与点赞、评论、转发分开统计。");
    }

    private CapabilityItem item(
            String capabilityId,
            String moduleId,
            CapabilityStatus status,
            String reasonCode,
            String reasonMessage,
            List<String> requiredInputs,
            List<String> availableInputs,
            String fallback) {
        return new CapabilityItem(
                capabilityId, moduleId, status, SocialNetworkStateCatalog.fromCapability(status),
                reasonCode, reasonMessage, requiredInputs,
                availableInputs, fallback);
    }

    private static int value(Map<String, Integer> values, String key) {
        return values.getOrDefault(key, 0);
    }

    private record Inputs(boolean users, boolean relationships, boolean tweets, List<String> names) {
        private static Inputs from(SocialNetworkDataset dataset) {
            boolean users = dataset.files().stream().anyMatch(file -> file.role() == DatasetFileRole.USERS);
            boolean relationships = dataset.files().stream()
                    .anyMatch(file -> file.role() == DatasetFileRole.RELATIONSHIPS);
            boolean tweets = dataset.files().stream().anyMatch(file -> file.role() == DatasetFileRole.TWEETS);
            List<String> names = dataset.files().stream()
                    .map(file -> file.role().name().toLowerCase())
                    .distinct()
                    .sorted()
                    .toList();
            return new Inputs(users, relationships, tweets, names);
        }
    }
}
