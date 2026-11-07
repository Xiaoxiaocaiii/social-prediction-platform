(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkUploadPreviewModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var ISSUE_LIMIT = 40;
    var RESULT_CATEGORIES = [
        { id: "files", label: "文件读取" },
        { id: "quality", label: "质量统计" },
        { id: "issues", label: "问题定位" },
        { id: "capabilities", label: "可开展的分析" }
    ];
    var QUALITY_DIMENSIONS = [
        { id: "summary", label: "整体" },
        { id: "userQuality", label: "用户" },
        { id: "relationshipQuality", label: "关注关系" },
        { id: "tweetQuality", label: "推文" },
        { id: "interactionQuality", label: "互动" },
        { id: "textQuality", label: "文本" }
    ];
    var METRIC_LABELS = {
        file_count: "文件数",
        user_record_count: "用户记录数",
        relationship_record_count: "关注关系记录数",
        relationship_candidate_count: "关注关系候选数",
        tweet_record_count: "推文记录数",
        quality_issue_count: "质量问题项数",
        capability_impact_count: "受影响能力数",
        raw_user_count: "原始用户记录数",
        normalized_user_count: "规范化用户数",
        unique_user_id_count: "唯一用户 ID 数",
        duplicate_user_id_count: "重复用户 ID 数",
        missing_user_id_count: "缺失用户 ID 数",
        missing_username_count: "缺失用户名数",
        missing_display_name_count: "缺失显示名称数",
        missing_bio_count: "缺失简介数",
        invalid_counter_count: "无效计数值数",
        profile_missing_count: "缺失用户资料数",
        raw_relationship_count: "原始关系记录数",
        candidate_relationship_count: "关注关系候选数",
        normalized_relationship_count: "纳入规范化的关系数",
        unique_relationship_count: "唯一关系数",
        duplicate_relationship_count: "重复关系数",
        self_loop_count: "自环关系数",
        missing_source_count: "缺失来源用户数",
        missing_target_count: "缺失目标用户数",
        external_source_node_count: "外部来源节点数",
        external_target_node_count: "外部目标节点数",
        invalid_relationship_count: "无效关系数",
        raw_tweet_count: "原始推文记录数",
        unique_tweet_id_count: "唯一推文 ID 数",
        duplicate_tweet_count: "重复推文数",
        conflict_duplicate_count: "冲突重复推文数",
        missing_tweet_id_count: "缺失推文 ID 数",
        missing_author_count: "缺失作者数",
        linked_author_count: "已关联作者数",
        unlinked_author_count: "未关联作者数",
        unassigned_source_count: "未分配来源数",
        empty_text_count: "空文本数",
        time_parse_failed_count: "时间解析失败数",
        like_missing_count: "缺失点赞值数",
        like_invalid_count: "无效点赞值数",
        reply_missing_count: "缺失评论值数",
        reply_invalid_count: "无效评论值数",
        repost_missing_count: "缺失转发值数",
        repost_invalid_count: "无效转发值数",
        bookmark_missing_count: "缺失书签值数",
        bookmark_invalid_count: "无效书签值数",
        bookmark_all_zero: "书签字段是否全为零",
        valid_text_count: "有效文本数",
        language_detected_count: "已识别语言数",
        language_unknown_count: "未知语言数",
        mixed_language_count: "混合语言数",
        text_analysis_available: "文本分析是否可用"
    };
    var MODULE_LABELS = {
        "dataset-entry": "数据集入口",
        "network-overview": "网络结构",
        "following-network": "用户关系",
        "important-nodes": "节点排行",
        "user-detail": "用户详情",
        "tweet-interaction": "互动分析",
        "topic-analysis": "主题分析",
        "topic-network-linkage": "主题与关系联动",
        "bookmark-analysis": "书签字段分析"
    };
    var STATUS_LABELS = {
        available: "可用",
        available_with_warnings: "可用，存在质量提示",
        partial: "部分可用",
        limited: "受限",
        unavailable: "不可用",
        disabled: "未开放"
    };
    var HEADER_LABELS = {
        valid: "字段检查通过",
        missing: "缺少必需字段",
        invalid: "字段格式异常",
        unreadable: "无法读取"
    };
    var ROLE_LABELS = {
        users: "用户",
        relationships: "关注关系",
        tweets: "推文"
    };
    var SEVERITY_LABELS = {
        blocking: "阻断",
        error: "错误",
        warning: "警告",
        info: "提示"
    };

    function list(value) {
        return Array.isArray(value) ? value : [];
    }

    function categories() {
        return RESULT_CATEGORIES.map(function (item) {
            return { id: item.id, label: item.label };
        });
    }

    function dimensions() {
        return QUALITY_DIMENSIONS.map(function (item) {
            return { id: item.id, label: item.label };
        });
    }

    function issueSummary(issues, limit) {
        var returned = list(issues);
        var maximum = Number.isInteger(limit) && limit >= 0 ? limit : ISSUE_LIMIT;
        return {
            returnedCount: returned.length,
            displayed: returned.slice(0, maximum),
            displayedCount: Math.min(returned.length, maximum),
            omittedCount: Math.max(0, returned.length - maximum),
            limit: maximum
        };
    }

    function fileResults(preview) {
        var data = preview || {};
        var files = list(data.files);
        var reports = list((data.qualitySummary || {}).fileReports);
        var reportByName = new Map();
        reports.forEach(function (report) {
            if (report && report.logicalFileName) {
                reportByName.set(report.logicalFileName, report);
            }
        });
        if (!files.length) {
            files = reports.map(function (report) {
                return {
                    fileName: report.logicalFileName,
                    format: report.format,
                    role: ""
                };
            });
        }
        return files.map(function (file, index) {
            var report = reportByName.get(file.fileName) || reports[index] || {};
            var header = String(report.headerStatus || "").toLowerCase();
            return {
                fileName: file.fileName || report.logicalFileName || "文件名未提供",
                role: ROLE_LABELS[String(file.role || "").toLowerCase()] ||
                    file.role || "未提供",
                format: String(file.format || report.format || "未提供")
                    .toLowerCase(),
                encoding: report.encoding || "未提供",
                headerStatus: HEADER_LABELS[header] ||
                    report.headerStatus || "未提供",
                rowCount: file.rowCount === undefined ? report.rowCount : file.rowCount,
                readOkCount: file.readOkCount === undefined
                    ? report.readOkCount
                    : file.readOkCount,
                readWarningCount: report.readWarningCount,
                readSkippedCount: file.readSkippedCount === undefined
                    ? report.readSkippedCount
                    : file.readSkippedCount,
                readErrorCount: file.readErrorCount === undefined
                    ? report.readErrorCount
                    : file.readErrorCount,
                qualityStatus: file.qualityStatus || "未提供"
            };
        });
    }

    function metricEntries(quality, dimensionId) {
        var dimension = QUALITY_DIMENSIONS.find(function (item) {
            return item.id === dimensionId;
        });
        var values = dimension && quality ? quality[dimension.id] : null;
        if (!values || typeof values !== "object" || Array.isArray(values)) {
            return [];
        }
        return Object.keys(values).map(function (key) {
            return {
                key: key,
                label: METRIC_LABELS[key] || key.replace(/_/g, " "),
                value: values[key]
            };
        });
    }

    function capabilityItems(capabilities) {
        return list(capabilities && capabilities.items).map(function (item) {
            var status = String(item.status || item.state || "unavailable")
                .toLowerCase();
            var capabilityId = item.capabilityId || "";
            return {
                id: capabilityId || item.moduleId || "capability",
                label: MODULE_LABELS[capabilityId] ||
                    item.moduleId || "模块状态",
                status: status,
                statusLabel: STATUS_LABELS[status] || status,
                reasonMessage: item.reasonMessage || "",
                fallback: item.fallback || ""
            };
        });
    }

    function datasetStatusLabel(status) {
        var key = String(status || "").toLowerCase();
        return STATUS_LABELS[key] || "状态未提供";
    }

    function severityLabel(severity) {
        var key = String(severity || "").toLowerCase();
        return SEVERITY_LABELS[key] || severity || "级别未提供";
    }

    return {
        ISSUE_LIMIT: ISSUE_LIMIT,
        categories: categories,
        dimensions: dimensions,
        issueSummary: issueSummary,
        fileResults: fileResults,
        metricEntries: metricEntries,
        capabilityItems: capabilityItems,
        datasetStatusLabel: datasetStatusLabel,
        severityLabel: severityLabel
    };
});
