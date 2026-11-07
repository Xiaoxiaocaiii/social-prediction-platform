(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkNetworkStructureModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var categories = [
        {
            id: "scale-connectivity",
            label: "规模与连通",
            metricKeys: [
                "density",
                "isolatedNodeCount",
                "weaklyConnectedComponentCount",
                "largestWeaklyConnectedComponentRatio"
            ]
        },
        {
            id: "closure-reciprocity",
            label: "闭合与互惠",
            metricKeys: [
                "averageClusteringCoefficient",
                "reciprocityRate"
            ]
        },
        {
            id: "structural-distance",
            label: "结构距离",
            metricKeys: [
                "largestWeaklyConnectedComponentAveragePathLength"
            ]
        }
    ];

    var metrics = {
        density: {
            label: "网络密度",
            format: "density",
            meaning: "把实际存在的关注关系，与网络中理论上可能出现的全部关系位置相比。数值越高，整体关系越密，但不代表用户彼此更亲近。",
            description: "有效有向关系数 ÷ 节点数 ×（节点数 − 1）；表示可用有向关系位置的占比，不代表关系亲密度。"
        },
        isolatedNodeCount: {
            label: "孤立节点",
            format: "count",
            meaning: "没有关注任何人、也没有被任何人关注的账号数。它表示网络中暂时没有连接的账号数量，不代表这些账号不活跃。",
            description: "在完整规范化网络中没有有效入边或出边的节点数；有效资料用户即使没有关系也计入节点范围。"
        },
        weaklyConnectedComponentCount: {
            label: "弱连通分量",
            format: "count",
            meaning: "先忽略关注方向，再把能通过关系链连起来的账号看作一组。组数越多，表示网络被分成越多互不相连的部分；单个孤立账号也单独算一组。",
            description: "忽略边方向后互相可达的节点集合数量；每个孤立节点单独计为一个分量。空网络的分量数为真实的 0。"
        },
        largestWeaklyConnectedComponentRatio: {
            label: "最大弱连通分量占比",
            format: "ratio",
            meaning: "最大那组可通过关系链连起来的账号，占全体账号的比例。数值越高，表示更多账号处在同一个大网络中；不代表组内人人都有直接关系。",
            description: "最大弱连通分量节点数 ÷ 完整网络节点数；分量大小和全网节点数同时提供分子、分母。"
        },
        averageClusteringCoefficient: {
            label: "平均聚类系数",
            format: "decimal",
            meaning: "观察一个账号关注的对象之间，彼此也存在关系的程度。平均值越高，局部越容易形成小圈子；不表示整个网络都更紧密。",
            description: "按无向简单投影计算节点局部闭合程度的平均值；不表示关注关系具有方向闭合。"
        },
        reciprocityRate: {
            label: "关系互惠率",
            format: "ratio",
            meaning: "已经存在的关注关系中，同时存在反向关注的比例。数值越高，互相关注越常见；不代表互动频率或关系亲密度。",
            description: "按完整范围内的有效有向关系统计双向互惠；无有效边时不可计算，不以 0 替代。"
        },
        largestWeaklyConnectedComponentAveragePathLength: {
            label: "最大分量平均路径长度",
            format: "decimal",
            meaning: "在最大的关系群体中，两名账号之间平均要经过多少条关系才能连起来。数值越小，平均连接链越短；计算忽略关注方向。",
            description: "仅在最大弱连通分量内计算节点对最短距离；忽略方向，不等于有向传播路径。"
        }
    };

    function fromResponse(body, qualityBody, structureBody) {
        var data = body && body.data && typeof body.data === "object"
            ? body.data
            : {};
        var meta = body && body.meta && typeof body.meta === "object"
            ? body.meta
            : {};
        var qualityMeta = qualityBody && qualityBody.meta &&
            typeof qualityBody.meta === "object"
            ? qualityBody.meta
            : {};
        var qualityData = qualityBody && qualityBody.data &&
            typeof qualityBody.data === "object"
            ? qualityBody.data
            : null;
        var qualityMatchesSnapshot = Boolean(
            qualityData &&
            meta.datasetId &&
            meta.datasetVersion &&
            qualityMeta.datasetId === meta.datasetId &&
            qualityMeta.datasetVersion === meta.datasetVersion
        );
        var quality = qualityMatchesSnapshot
            ? qualityData.qualitySummary || qualityData
            : null;
        var qualityWarnings = qualityMatchesSnapshot &&
            Array.isArray(qualityBody.warnings)
            ? qualityBody.warnings
            : [];
        var structureMeta = structureBody && structureBody.meta &&
            typeof structureBody.meta === "object"
            ? structureBody.meta
            : {};
        var structureData = structureBody && structureBody.data &&
            typeof structureBody.data === "object"
            ? structureBody.data
            : null;
        var structureMatchesSnapshot = Boolean(
            structureData &&
            meta.datasetId &&
            meta.datasetVersion &&
            structureMeta.datasetId === meta.datasetId &&
            structureMeta.datasetVersion === meta.datasetVersion
        );
        var coreDistribution = structureMatchesSnapshot
            ? structureData.kCoreDistribution
            : null;
        var coreValidation = validateKCoreDistribution(
            coreDistribution,
            data.nodeCount
        );
        var coreLoadState = !structureBody
            ? "unavailable"
            : !structureMatchesSnapshot ? "mismatch"
                : coreValidation.available ? "available" : "unavailable";
        return {
            data: data,
            datasetId: meta.datasetId == null
                ? null
                : String(meta.datasetId),
            datasetVersion: meta.datasetVersion == null
                ? null
                : String(meta.datasetVersion),
            normalizationVersion: meta.normalizationVersion == null
                ? null
                : String(meta.normalizationVersion),
            qualityStatus: quality && (
                quality.datasetStatus || quality.state
            ) || meta.qualityStatus || null,
            qualityLoadState: !qualityBody
                ? "unavailable"
                : qualityMatchesSnapshot ? "available" : "mismatch",
            quality: quality,
            qualityWarnings: qualityWarnings,
            kCoreLoadState: coreLoadState,
            kCoreDistribution: coreValidation.available
                ? coreDistribution
                : {
                    state: coreLoadState,
                    reason: coreLoadState === "mismatch"
                        ? "核心层分布与当前网络数据版本不一致，已忽略该响应。"
                        : !structureBody
                            ? "暂时无法读取核心层完整分布。"
                            : coreValidation.reason,
                    sampleSize: null,
                    shells: []
                },
            categories: categories.map(function (item) {
                return {
                    id: item.id,
                    label: item.label,
                    metricKeys: item.metricKeys.slice()
                };
            }),
            state: data.state == null
                ? null
                : String(data.state).toLowerCase(),
            metricVersion: data.metricVersion == null
                ? null
                : String(data.metricVersion),
            pagerankVersion: data.pagerankVersion == null
                ? null
                : String(data.pagerankVersion),
            pageRank: data.pageRank && typeof data.pageRank === "object"
                ? data.pageRank
                : null
        };
    }

    function validateKCoreDistribution(distribution, networkNodeCount) {
        if (!distribution || typeof distribution !== "object" ||
                distribution.complete !== true ||
                distribution.truncated !== false ||
                !Array.isArray(distribution.shells)) {
            return {
                available: false,
                reason: "服务端未提供完整、未截断的核心层分布。"
            };
        }
        var sampleSize = distribution.sampleSize;
        if (!Number.isInteger(sampleSize) || sampleSize < 0 ||
                !Number.isInteger(networkNodeCount) ||
                sampleSize !== networkNodeCount) {
            return {
                available: false,
                reason: "核心层分布的总体数与网络节点总数不一致。"
            };
        }
        var shells = distribution.shells;
        if (sampleSize === 0) {
            return shells.length === 0 &&
                distribution.maxCoreNumber == null &&
                distribution.highestCoreCount == null
                ? { available: true }
                : {
                    available: false,
                    reason: "空网络的核心层分布字段不完整。"
                };
        }
        var exactTotal = 0;
        var previousCumulativeCount = sampleSize;
        var validShells = shells.every(function (shell, index) {
            if (!shell || shell.coreNumber !== index ||
                    !Number.isInteger(shell.exactCount) ||
                    shell.exactCount < 0 ||
                    !Number.isInteger(shell.cumulativeCount) ||
                    shell.cumulativeCount < 0 ||
                    shell.cumulativeCount > sampleSize ||
                    shell.cumulativeCount < shell.exactCount ||
                    shell.cumulativeCount > previousCumulativeCount ||
                    !Number.isFinite(shell.exactRatio) ||
                    shell.exactRatio < 0 || shell.exactRatio > 1 ||
                    !Number.isFinite(shell.cumulativeRatio) ||
                    shell.cumulativeRatio < 0 ||
                    shell.cumulativeRatio > 1 ||
                    Math.abs(shell.exactRatio -
                        shell.exactCount / sampleSize) > 1e-9 ||
                    Math.abs(shell.cumulativeRatio -
                        shell.cumulativeCount / sampleSize) > 1e-9 ||
                    !Array.isArray(shell.nodes) ||
                    shell.nodes.length !== shell.exactCount) {
                return false;
            }
            exactTotal += shell.exactCount;
            previousCumulativeCount = shell.cumulativeCount;
            return true;
        });
        if (!validShells || shells.length === 0 ||
                shells[0].cumulativeCount !== sampleSize ||
                shells[0].coreNumber !== 0 ||
                shells[shells.length - 1].coreNumber !==
                    distribution.maxCoreNumber ||
                shells[shells.length - 1].cumulativeCount !==
                    distribution.highestCoreCount ||
                exactTotal !== sampleSize ||
                !Number.isInteger(distribution.highestCoreCount) ||
                distribution.highestCoreCount !==
                    shells[shells.length - 1].exactCount) {
            return {
                available: false,
                reason: "核心层分布缺层、节点证据不完整或总数不一致。"
            };
        }
        return { available: true };
    }

    function kCoreRows(summary, mode) {
        var distribution = summary && summary.kCoreDistribution;
        if (!distribution || summary.kCoreLoadState !== "available" ||
                !Array.isArray(distribution.shells)) {
            return [];
        }
        var cumulative = mode === "cumulative";
        return distribution.shells.map(function (shell) {
            return {
                coreNumber: shell.coreNumber,
                label: cumulative
                    ? "core≥" + shell.coreNumber
                    : "core=" + shell.coreNumber,
                count: cumulative
                    ? shell.cumulativeCount
                    : shell.exactCount,
                ratio: cumulative
                    ? shell.cumulativeRatio
                    : shell.exactRatio,
                sampleSize: distribution.sampleSize,
                exactCount: shell.exactCount,
                cumulativeCount: shell.cumulativeCount,
                nodes: shell.nodes
            };
        });
    }

    function kCoreEvidenceNodes(summary, coreNumber, mode) {
        var rows = kCoreRows(summary, mode);
        var selected = rows.find(function (row) {
            return row.coreNumber === coreNumber;
        });
        if (!selected) {
            return [];
        }
        var nodes = [];
        (summary.kCoreDistribution.shells || []).forEach(function (shell) {
            if (mode === "cumulative"
                    ? shell.coreNumber >= coreNumber
                    : shell.coreNumber === coreNumber) {
                shell.nodes.forEach(function (node) {
                    nodes.push(Object.assign({
                        coreNumber: shell.coreNumber
                    }, node));
                });
            }
        });
        return nodes.sort(function (left, right) {
            return String(left.nodeId).localeCompare(String(right.nodeId));
        });
    }

    function networkStateLabel(state) {
        var labels = {
            available: "可分析",
            empty: "当前没有网络节点",
            limited: "部分可用",
            unavailable: "暂不可用",
            failed: "读取失败"
        };
        var key = state == null ? "" : String(state).toLowerCase();
        return labels[key] || (state == null ? "状态未提供" : String(state));
    }

    function qualityStatusLabel(state) {
        var labels = {
            healthy: "正常",
            available_with_warnings: "有质量提示",
            partial: "部分数据",
            limited: "部分数据",
            unavailable: "不可用"
        };
        var key = state == null ? "" : String(state).toLowerCase();
        return labels[key] || (state == null ? "状态未提供" : String(state));
    }

    function qualityImpactItems(summary) {
        if (!summary || summary.qualityLoadState !== "available") {
            return [{
                key: "quality-unavailable",
                label: "质量影响暂无法确认",
                text: summary && summary.qualityLoadState === "mismatch"
                    ? "质量摘要与当前数据版本不一致，已忽略该响应；网络指标仍绑定当前网络摘要。"
                    : "质量摘要暂时无法读取；网络指标仍按当前网络摘要展示，暂不能核对账号 ID 和关系记录是否影响总体。"
            }];
        }

        var quality = summary.quality || {};
        var userQuality = quality.userQuality || {};
        var relationshipQuality = quality.relationshipQuality || {};
        var items = [];
        var duplicateUserIds = Number(userQuality.duplicate_user_id_count);
        var missingUserIds = Number(userQuality.missing_user_id_count);
        var invalidRelationships = Number(
            relationshipQuality.invalid_relationship_count
        );
        if (Number.isFinite(duplicateUserIds) && duplicateUserIds > 0) {
            items.push({
                key: "duplicate-user-id",
                label: "重复账号 ID：" + duplicateUserIds + " 条重复记录",
                text: "网络按账号 ID 合并节点，不会因重复资料行重复增加节点数；若同一 ID 的资料字段冲突，账号资料展示可能存在歧义，但不会把它们拆成多个网络节点。"
            });
        }
        if (Number.isFinite(missingUserIds) && missingUserIds > 0) {
            items.push({
                key: "missing-user-id",
                label: "账号资料缺少 ID：" + missingUserIds + " 条",
                text: "这些记录不能计入资料账号数；若其账号仍出现在有效关系端点中，会按外部节点纳入。资料账号与外部节点的数量及划分受影响，不能据此补回未知账号。"
            });
        }
        if (Number.isFinite(invalidRelationships) &&
                invalidRelationships > 0) {
            items.push({
                key: "invalid-relationships",
                label: "无效关注关系：" + invalidRelationships + " 条",
                text: "这些关系未进入有效边集合。关系数、度数、连通结构、PageRank、核心层和介数等依赖关系的结果，只描述保留下来的有效关系网络。"
            });
        }
        (summary.qualityWarnings || []).forEach(function (warning) {
            if (warning && warning.code === "INVALID_FOLLOWING_LIST") {
                var count = Number(warning.count);
                items.push({
                    key: "invalid-following-list",
                    label: "无法读取的关注列表：" +
                        (Number.isFinite(count) ? count : "未提供") + " 条",
                    text: "这些列表没有展开为关系边，因此会影响有效关系数及所有依赖关系结构的指标。"
                });
            }
        });
        if (items.length) {
            return items;
        }
        if (String(summary.qualityStatus || "").toLowerCase() ===
                "unavailable") {
            return [{
                key: "dataset-unavailable",
                label: "数据质量状态：不可用",
                text: "服务端将当前数据标记为不可用，但没有提供可对应到具体网络指标的原因；不要把已有数值外推为完整数据结论。"
            }];
        }
        return [{
            key: "no-network-limitation",
            label: "未发现账号 ID 或关系端点缺陷",
            text: "质量摘要未报告会改变网络节点范围或有效关系集合的账号 ID、关系端点问题。推文、互动和文本质量提示不用于解释本页网络指标。"
        }];
    }

    function valueState(source, key) {
        if (!source || !Object.prototype.hasOwnProperty.call(source, key)) {
            return "not-provided";
        }
        var value = source[key];
        if (value === null || value === undefined ||
                (typeof value === "number" && !Number.isFinite(value))) {
            return "unavailable";
        }
        return "value";
    }

    function metricEntries(summary, categoryId) {
        var category = categories.find(function (item) {
            return item.id === categoryId;
        });
        if (!category) {
            return [];
        }
        var data = summary && summary.data || {};
        return category.metricKeys.map(function (key) {
            var definition = metrics[key];
            return {
                key: key,
                label: definition.label,
                format: definition.format,
                meaning: definition.meaning,
                description: definition.description,
                value: data[key],
                state: valueState(data, key)
            };
        });
    }

    function pageRankState(summary) {
        var pageRank = summary && summary.pageRank;
        if (!pageRank || !Object.prototype.hasOwnProperty.call(
                pageRank, "converged")) {
            return "not-provided";
        }
        if (pageRank.converged === true) {
            return "converged";
        }
        if (pageRank.converged === false) {
            return "not-converged";
        }
        return "not-provided";
    }

    function pageRankValueState(summary, key) {
        return valueState(summary && summary.pageRank, key);
    }

    function largestComponent(summary) {
        var data = summary && summary.data || {};
        var sizeState = valueState(
            data,
            "largestWeaklyConnectedComponentNodeCount"
        );
        var totalState = valueState(data, "nodeCount");
        var ratioState = valueState(
            data,
            "largestWeaklyConnectedComponentRatio"
        );
        return {
            sizeState: sizeState,
            size: data.largestWeaklyConnectedComponentNodeCount,
            totalState: totalState,
            total: data.nodeCount,
            ratioState: ratioState,
            ratio: data.largestWeaklyConnectedComponentRatio
        };
    }

    return {
        categories: function () {
            return categories.map(function (item) {
                return { id: item.id, label: item.label };
            });
        },
        fromResponse: fromResponse,
        kCoreRows: kCoreRows,
        kCoreEvidenceNodes: kCoreEvidenceNodes,
        networkStateLabel: networkStateLabel,
        qualityStatusLabel: qualityStatusLabel,
        qualityImpactItems: qualityImpactItems,
        valueState: valueState,
        metricEntries: metricEntries,
        pageRankState: pageRankState,
        pageRankValueState: pageRankValueState,
        largestComponent: largestComponent
    };
});
