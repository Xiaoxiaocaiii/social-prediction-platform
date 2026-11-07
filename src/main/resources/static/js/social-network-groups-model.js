(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkGroupsModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var BUILTIN_DATASET_ID = "think-tank-social-network";
    var PAGE_SIZE = 20;
    var STATUS_LABELS = {
        NOT_REQUESTED: "尚未运行",
        RUNNING: "计算中",
        COMPLETED: "计算完成",
        COMPLETED_UNCONVERGED: "执行完成，算法未收敛",
        FAILED: "运行失败",
        MEMORY_LIMIT: "内存门禁阻止启动",
        TIME_LIMIT: "达到时间上限",
        CLIQUE_LIMIT: "达到团数量上限",
        CANCELLED: "已取消",
        BUSY: "另一个分析任务正在运行"
    };

    function isSupportedContext(context) {
        return Boolean(context &&
            context.datasetId === BUILTIN_DATASET_ID &&
            !String(context.sessionId || "").trim());
    }

    function labelForStatus(status) {
        return STATUS_LABELS[status] || status || "状态未知";
    }

    function cliqueDistribution(distribution) {
        return Object.keys(distribution || {})
            .map(function (key) {
                return {
                    size: Number(key),
                    count: Number(distribution[key])
                };
            })
            .filter(function (entry) {
                return Number.isInteger(entry.size) && entry.size >= 3 &&
                    Number.isFinite(entry.count) && entry.count >= 0;
            })
            .sort(function (first, second) {
                return first.size - second.size;
            });
    }

    function louvainDistribution(communities) {
        return (Array.isArray(communities) ? communities : [])
            .map(function (community) {
                return {
                    id: String(community && community.id || ""),
                    size: Number(community && community.nodeCount)
                };
            })
            .filter(function (entry) {
                return entry.id && Number.isFinite(entry.size) && entry.size >= 0;
            })
            .sort(function (first, second) {
                return second.size - first.size ||
                    first.id.localeCompare(second.id);
            });
    }

    function topParticipants(participation, limit) {
        return Object.keys(participation || {})
            .map(function (nodeId) {
                return { nodeId: nodeId, count: Number(participation[nodeId]) };
            })
            .filter(function (entry) {
                return Number.isFinite(entry.count) && entry.count > 0;
            })
            .sort(function (first, second) {
                return second.count - first.count ||
                    first.nodeId.localeCompare(second.nodeId);
            })
            .slice(0, Math.max(0, limit || 5));
    }

    function isRunning(status) {
        return status === "RUNNING";
    }

    return {
        BUILTIN_DATASET_ID: BUILTIN_DATASET_ID,
        PAGE_SIZE: PAGE_SIZE,
        isSupportedContext: isSupportedContext,
        labelForStatus: labelForStatus,
        cliqueDistribution: cliqueDistribution,
        louvainDistribution: louvainDistribution,
        topParticipants: topParticipants,
        isRunning: isRunning
    };
});
