(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkRankingModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var METRICS = [
        {
            id: "indegree",
            group: "network",
            groupLabel: "关系网络",
            label: "被关注关系数",
            description: "回答当前数据中有多少个账号关注它。数值越高，说明这份数据里指向它的关注关系越多；不等于账号资料中的粉丝数。"
        },
        {
            id: "outdegree",
            group: "network",
            groupLabel: "关系网络",
            label: "主动关注关系数",
            description: "回答它在当前数据中关注了多少个账号。数值越高，说明主动连接的账号越多；不代表它更受欢迎。"
        },
        {
            id: "pagerank",
            group: "network",
            groupLabel: "关系网络",
            label: "网络位置估算",
            description: "根据关注关系估算账号在网络中的相对位置；被关注者自身的位置也会影响结果。这不是粉丝量、内容热度或现实影响力。"
        },
        {
            id: "followers_count",
            group: "profile",
            groupLabel: "账号资料",
            label: "资料中的粉丝数",
            description: "来自账号资料，表示资料记录的粉丝数；不保证与本次采集到的关注关系完全一致。"
        },
        {
            id: "following_count",
            group: "profile",
            groupLabel: "账号资料",
            label: "资料中的关注数",
            description: "来自账号资料，表示资料记录的关注数；不等于本次分析实际纳入的关系数。"
        },
        {
            id: "tweet_count",
            group: "profile",
            groupLabel: "账号资料",
            label: "资料中的发帖总数",
            description: "来自账号资料，表示资料记录的发帖总量；不等于本次数据集中已采集的帖子数。"
        },
        {
            id: "k_core",
            group: "structure",
            groupLabel: "网络位置参考",
            label: "紧密圈层位置",
            description: "观察账号是否处在彼此连接较密集的圈层中；数值越高，所在圈层通常越紧密。这描述网络结构，不代表人气或影响力。"
        },
        {
            id: "betweenness",
            group: "structure",
            groupLabel: "网络位置参考",
            label: "群体间连接位置",
            description: "观察账号是否位于不同账号群体之间的连接路径上，可帮助发现可能连接群体的账号；不表示它能控制信息传播。"
        }
    ];
    var ORDINARY_EXPORT_METRICS = ["indegree", "outdegree", "pagerank"];
    var MAX_ROWS = 20;
    var RESULT_STATES = ["available", "empty", "limited", "unavailable"];

    function normalizeResultState(state, itemCount, reason) {
        if (state == null || String(state).trim() === "") {
            return {
                state: itemCount > 0 ? "available" : "empty",
                reason: reason || "",
                recognized: true
            };
        }
        var normalized = String(state).toLowerCase();
        if (normalized === "failed" || normalized === "disabled" ||
                normalized === "loading" || normalized === "idle") {
            return {
                state: "unavailable",
                reason: reason ||
                    "服务端状态为“" + normalized + "”，结果暂不可用。",
                recognized: true
            };
        }
        if (RESULT_STATES.indexOf(normalized) >= 0) {
            return {
                state: normalized,
                reason: reason || "",
                recognized: true
            };
        }
        return {
            state: "unavailable",
            reason: reason ||
                "服务端返回无法识别的状态；结果暂不展示，请重试或检查数据版本。",
            recognized: false
        };
    }

    function metric(metricId) {
        return METRICS.find(function (entry) {
            return entry.id === metricId;
        }) || null;
    }

    function isOrdinaryExportMetric(metricId) {
        return ORDINARY_EXPORT_METRICS.indexOf(metricId) >= 0;
    }

    function metrics() {
        return METRICS.map(function (entry) {
            return Object.assign({}, entry);
        });
    }

    function fromRankingResponse(body, requestedMetric) {
        var data = body && body.data || {};
        var responseMetric = data.metric || requestedMetric;
        if (responseMetric !== requestedMetric) {
            throw new Error("排行接口返回的指标与当前选择不一致。");
        }
        var definition = metric(responseMetric);
        if (!definition || definition.group === "structure") {
            throw new Error("排行接口返回了不支持的普通指标。");
        }
        var items = Array.isArray(data.items) ? data.items : [];
        var resultState = normalizeResultState(
            data.state,
            items.length,
            data.reason
        );
        return {
            metric: responseMetric,
            definition: definition,
            state: resultState.state,
            reason: resultState.reason,
            rows: resultState.recognized ? items.slice(0, MAX_ROWS) : [],
            totalCount: data.sampleSize,
            scope: data.scope || "",
            metricType: data.metricType || "",
            truncated: items.length > MAX_ROWS ||
                (Number.isFinite(Number(data.sampleSize)) &&
                    Number(data.sampleSize) > Math.min(items.length, MAX_ROWS))
        };
    }

    function fromStructureRolesResponse(body, metricId) {
        var definition = metric(metricId);
        if (!definition || definition.group !== "structure") {
            throw new Error("请选择有效的结构角色指标。");
        }
        var data = body && body.data || {};
        var blockName = metricId === "k_core" ? "kCore" : "betweenness";
        var block = data[blockName] || {};
        var items = Array.isArray(block.items) ? block.items : [];
        var resultState = normalizeResultState(
            block.state,
            items.length,
            block.reason
        );
        return {
            metric: metricId,
            definition: definition,
            state: resultState.state,
            reason: resultState.reason,
            rows: resultState.recognized ? items.slice(0, MAX_ROWS) : [],
            nodeCount: data.nodeCount,
            undirectedTieCount: data.undirectedTieCount,
            limit: data.limit,
            scope: data.scope || "",
            algorithmVersion: data.algorithmVersion || "",
            truncated: items.length > MAX_ROWS ||
                (items.length >= MAX_ROWS &&
                    Number.isFinite(Number(data.nodeCount)) &&
                    Number(data.nodeCount) > MAX_ROWS)
        };
    }

    function crossMetricMetricLabel(metricId) {
        var labels = {
            indegree: "被关注关系数",
            outdegree: "主动关注关系数",
            pageRank: "网络位置估算",
            kCore: "紧密圈层位置",
            normalizedBetweenness: "群体间连接位置"
        };
        return labels[metricId] || metricId || "未提供指标";
    }

    function fromCrossMetricResponse(body, expectedContext) {
        var data = body && body.data || {};
        var meta = body && body.meta || {};
        var snapshot = data.analysisSnapshot || {};
        var context = expectedContext || {};
        var responseDatasetId = meta.datasetId;
        var responseDatasetVersion = meta.datasetVersion;
        var snapshotDatasetId = snapshot.datasetId;
        var snapshotDatasetVersion = snapshot.datasetVersion;
        var expectedDatasetVersion = context.datasetVersion;
        var matchesSnapshot = Boolean(
            context.datasetId &&
            responseDatasetId != null &&
            responseDatasetVersion != null &&
            snapshotDatasetId != null &&
            snapshotDatasetVersion != null &&
            String(responseDatasetId) === String(context.datasetId) &&
            String(snapshotDatasetId) === String(context.datasetId) &&
            String(responseDatasetVersion) ===
                String(snapshotDatasetVersion) &&
            (!expectedDatasetVersion ||
                (String(responseDatasetVersion) ===
                    String(expectedDatasetVersion) &&
                    String(snapshotDatasetVersion) ===
                        String(expectedDatasetVersion))) &&
            (!context.sessionId || expectedDatasetVersion != null)
        );
        if (!matchesSnapshot) {
            return {
                state: "mismatch",
                reason: "跨指标结果与当前数据集版本不一致，已忽略该响应。",
                populationSize: null,
                spearmanPairs: [],
                top20Comparisons: [],
                nodeEvidenceState: "unavailable",
                nodeEvidenceReason: "没有匹配当前数据快照的节点证据。",
                nodeEvidence: []
            };
        }
        var crossMetric = data.crossMetric || {};
        var spearmanPairs = Array.isArray(crossMetric.spearmanPairs)
            ? crossMetric.spearmanPairs.slice(0, 10)
            : [];
        var top20Comparisons = Array.isArray(crossMetric.top20Comparisons)
            ? crossMetric.top20Comparisons.slice(0, 10)
            : [];
        var nodeEvidence = Array.isArray(crossMetric.nodeEvidence)
            ? crossMetric.nodeEvidence.map(function (node) {
                return {
                    nodeId: node.nodeId == null ? "" : String(node.nodeId),
                    nodeType: node.nodeType || "",
                    profileState: node.profileState || "unavailable",
                    profileReason: node.profileReason || "",
                    profile: node.profile || {},
                    metrics: Array.isArray(node.metrics)
                        ? node.metrics.map(function (item) {
                            return {
                                metricKey: item.metricKey || "",
                                metricLabel: crossMetricMetricLabel(
                                    item.metricKey
                                ),
                                value: item.value,
                                rank: item.rank
                            };
                        })
                        : []
                };
            })
            : [];
        var crossMetricState = normalizeResultState(
            crossMetric.state == null ? "unavailable" : crossMetric.state,
            spearmanPairs.length + top20Comparisons.length,
            crossMetric.reason
        );
        if (!crossMetricState.recognized ||
                crossMetricState.state === "unavailable" ||
                crossMetricState.state === "empty") {
            spearmanPairs = [];
            top20Comparisons = [];
            nodeEvidence = [];
        }
        return {
            state: crossMetricState.state,
            reason: crossMetricState.reason,
            populationSize: crossMetric.populationSize,
            snapshot: snapshot,
            spearmanPairs: spearmanPairs.map(function (pair) {
                return Object.assign({}, pair, {
                    metricALabel: crossMetricMetricLabel(pair.metricA),
                    metricBLabel: crossMetricMetricLabel(pair.metricB),
                    state: normalizeResultState(
                        pair.state || "unavailable",
                        0
                    ).state
                });
            }),
            top20Comparisons: top20Comparisons.map(function (comparison) {
                return Object.assign({}, comparison, {
                    metricALabel: crossMetricMetricLabel(comparison.metricA),
                    metricBLabel: crossMetricMetricLabel(comparison.metricB),
                    state: normalizeResultState(
                        comparison.state || "unavailable",
                        0
                    ).state
                });
            }),
            nodeEvidenceState: String(
                crossMetric.nodeEvidenceState || "unavailable"
            ).toLowerCase(),
            nodeEvidenceReason: crossMetric.nodeEvidenceReason || "",
            nodeEvidence: nodeEvidence
        };
    }

    return {
        maxRows: MAX_ROWS,
        metrics: metrics,
        metric: metric,
        isOrdinaryExportMetric: isOrdinaryExportMetric,
        fromRankingResponse: fromRankingResponse,
        fromStructureRolesResponse: fromStructureRolesResponse,
        fromCrossMetricResponse: fromCrossMetricResponse
    };
});
