(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkQualityModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var dimensions = [
        { id: "users", label: "用户", qualityKey: "userQuality" },
        {
            id: "relationships",
            label: "关注关系",
            qualityKey: "relationshipQuality"
        },
        { id: "tweets", label: "推文", qualityKey: "tweetQuality" },
        {
            id: "interactions",
            label: "互动",
            qualityKey: "interactionQuality"
        },
        { id: "text", label: "文本", qualityKey: "textQuality" },
        { id: "other", label: "其他提示", qualityKey: null }
    ];

    var summaryLabels = {
        file_count: "文件数",
        user_record_count: "原始用户记录",
        relationship_record_count: "原始关注关系记录",
        relationship_candidate_count: "关注关系候选记录",
        tweet_record_count: "原始推文记录",
        quality_issue_count: "质量问题项",
        capability_impact_count: "受影响能力项"
    };

    var metricLabels = {
        userQuality: {
            raw_user_count: "原始用户记录",
            normalized_user_count: "已识别用户",
            unique_user_id_count: "唯一用户 ID",
            duplicate_user_id_count: "重复用户 ID",
            missing_user_id_count: "缺少用户 ID",
            missing_username_count: "缺少用户名",
            missing_display_name_count: "缺少显示名称",
            missing_bio_count: "缺少简介",
            invalid_counter_count: "无效账号计数",
            profile_missing_count: "缺少用户资料"
        },
        relationshipQuality: {
            raw_relationship_count: "原始关注关系记录",
            candidate_relationship_count: "关注关系候选记录",
            normalized_relationship_count: "规范化有效关注关系",
            unique_relationship_count: "唯一有效关注关系",
            duplicate_relationship_count: "重复关注关系",
            self_loop_count: "自环关系",
            missing_source_count: "缺少来源用户 ID",
            missing_target_count: "缺少目标用户 ID",
            external_source_node_count: "外部来源节点",
            external_target_node_count: "外部目标节点",
            invalid_relationship_count: "无效关注关系"
        },
        tweetQuality: {
            raw_tweet_count: "原始推文记录",
            unique_tweet_id_count: "唯一推文 ID",
            duplicate_tweet_count: "重复推文记录",
            conflict_duplicate_count: "冲突推文记录",
            missing_tweet_id_count: "缺少推文 ID",
            missing_author_count: "缺少作者 ID",
            linked_author_count: "已关联作者推文",
            unlinked_author_count: "未关联作者推文",
            unassigned_source_count: "未归属来源推文",
            empty_text_count: "空文本推文",
            time_parse_failed_count: "时间解析失败记录"
        },
        interactionQuality: {
            like_missing_count: "缺少点赞数",
            like_invalid_count: "无效点赞数",
            reply_missing_count: "缺少评论数",
            reply_invalid_count: "无效评论数",
            repost_missing_count: "缺少转发数",
            repost_invalid_count: "无效转发数",
            bookmark_missing_count: "缺少书签数",
            bookmark_invalid_count: "无效书签数",
            bookmark_all_zero: "书签数是否全为零（1=是）"
        },
        textQuality: {
            valid_text_count: "非空文本记录",
            empty_text_count: "空文本记录",
            language_detected_count: "已识别语言记录",
            language_unknown_count: "语言未知记录",
            mixed_language_count: "混合语言记录",
            text_analysis_available: "文本分析是否可用（1=是）"
        }
    };

    var coverageLabels = {
        user_id_coverage: "用户 ID 覆盖率",
        relationship_inclusion_rate: "关注关系纳入率",
        tweet_author_link_rate: "推文作者关联率",
        valid_text_rate: "有效文本率"
    };

    var scopeAliases = {
        user: "users",
        users: "users",
        profile: "users",
        profiles: "users",
        relationship: "relationships",
        relationships: "relationships",
        relation: "relationships",
        tweet: "tweets",
        tweets: "tweets",
        interaction: "interactions",
        interactions: "interactions",
        text: "text",
        file: "other",
        files: "other",
        summary: "other",
        capability: "other",
        other: "other"
    };

    function categories() {
        return dimensions.map(function (item) {
            return {
                id: item.id,
                label: item.label
            };
        });
    }

    function reportFromResponse(body) {
        var data = body && body.data || {};
        var meta = body && body.meta || {};
        var quality = data.qualitySummary || data;
        return {
            identity: {
                datasetId: data.datasetId || meta.datasetId || null,
                datasetVersion: data.version || meta.datasetVersion || null,
                datasetName: data.name || data.datasetName ||
                    data.datasetId || meta.datasetId || null,
                datasetType: data.type || null,
                status: data.status || quality.datasetStatus ||
                    quality.state || null
            },
            quality: quality,
            warnings: Array.isArray(body && body.warnings)
                ? body.warnings
                : []
        };
    }

    function metricEntries(quality, dimensionId) {
        var dimension = dimensions.find(function (item) {
            return item.id === dimensionId;
        });
        if (!dimension || !dimension.qualityKey) {
            return [];
        }
        var values = quality && quality[dimension.qualityKey];
        if (!values || typeof values !== "object" || Array.isArray(values)) {
            return [];
        }
        var knownLabels = metricLabels[dimension.qualityKey] || {};
        return Object.keys(values).map(function (key) {
            return {
                key: key,
                label: knownLabels[key] || key,
                known: Object.prototype.hasOwnProperty.call(knownLabels, key),
                value: values[key]
            };
        });
    }

    function summaryEntries(quality) {
        var values = quality && quality.summary;
        if (!values || typeof values !== "object" || Array.isArray(values)) {
            return [];
        }
        return Object.keys(values).map(function (key) {
            return {
                key: key,
                label: summaryLabels[key] || key,
                known: Object.prototype.hasOwnProperty.call(summaryLabels, key),
                value: values[key]
            };
        });
    }

    function coverageEntries(quality) {
        var values = quality && quality.coverage;
        if (!values || typeof values !== "object" || Array.isArray(values)) {
            return [];
        }
        return Object.keys(values).map(function (key) {
            return {
                key: key,
                label: coverageLabels[key] || key,
                known: Object.prototype.hasOwnProperty.call(coverageLabels, key),
                value: values[key]
            };
        });
    }

    function warningDimension(warning) {
        var scope = warning && warning.affectedScope;
        var normalized = scope == null
            ? ""
            : String(scope).trim().toLowerCase();
        return scopeAliases[normalized] || "other";
    }

    function warningsForDimension(warnings, dimensionId) {
        if (!Array.isArray(warnings)) {
            return [];
        }
        return warnings.filter(function (warning) {
            return warningDimension(warning) === dimensionId;
        });
    }

    function paginateWarnings(warnings, page, pageSize) {
        var items = Array.isArray(warnings) ? warnings : [];
        var size = Number.isInteger(pageSize) && pageSize > 0 ? pageSize : 10;
        var pageCount = Math.max(1, Math.ceil(items.length / size));
        var selectedPage = Number.isInteger(page) ? page : 1;
        selectedPage = Math.max(1, Math.min(pageCount, selectedPage));
        var startIndex = (selectedPage - 1) * size;
        var pageItems = items.slice(startIndex, startIndex + size);
        return {
            page: selectedPage,
            pageSize: size,
            pageCount: pageCount,
            returnedCount: items.length,
            displayedCount: pageItems.length,
            start: pageItems.length ? startIndex + 1 : 0,
            end: pageItems.length ? startIndex + pageItems.length : 0,
            items: pageItems
        };
    }

    function severityLabel(value) {
        var labels = {
            blocking: "阻断",
            error: "错误",
            warning: "警告",
            info: "提示"
        };
        var key = value == null ? "" : String(value).toLowerCase();
        return labels[key] || (value == null ? "级别未提供" : String(value));
    }

    function statusLabel(value) {
        var labels = {
            healthy: "健康",
            available: "可用",
            available_with_warnings: "可用（含质量警告）",
            partial: "部分可用",
            limited: "部分可用",
            unavailable: "不可用",
            empty: "暂无数据",
            failed: "检查失败"
        };
        var key = value == null ? "" : String(value).toLowerCase();
        return labels[key] || (value == null ? "未提供" : String(value));
    }

    return {
        categories: categories,
        reportFromResponse: reportFromResponse,
        metricEntries: metricEntries,
        summaryEntries: summaryEntries,
        coverageEntries: coverageEntries,
        warningDimension: warningDimension,
        warningsForDimension: warningsForDimension,
        paginateWarnings: paginateWarnings,
        severityLabel: severityLabel,
        statusLabel: statusLabel
    };
});
