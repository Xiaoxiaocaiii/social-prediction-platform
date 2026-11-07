"use strict";

const assert = require("node:assert/strict");
const rankingModel = require(
    "../src/main/resources/static/js/social-network-ranking-model.js"
);

const metrics = rankingModel.metrics();
assert.equal(metrics.length, 8);
assert.deepEqual(metrics.map((entry) => entry.id), [
    "indegree",
    "outdegree",
    "pagerank",
    "followers_count",
    "following_count",
    "tweet_count",
    "k_core",
    "betweenness"
]);
assert.equal(rankingModel.metric("indegree").groupLabel, "关系网络");
assert.equal(rankingModel.metric("followers_count").groupLabel, "账号资料");
assert.equal(rankingModel.metric("k_core").groupLabel, "网络位置参考");
assert.equal(rankingModel.metric("pagerank").label, "网络位置估算");
assert.match(rankingModel.metric("indegree").description, /不等于账号资料中的粉丝数/);
assert.match(rankingModel.metric("betweenness").description, /不表示它能控制信息传播/);

const regular = rankingModel.fromRankingResponse({
    data: {
        metric: "indegree",
        metricType: "network",
        sampleSize: 22,
        scope: "完整网络",
        items: [
            { rank: 1, nodeId: "known", value: 8, sampleSize: 1 },
            {
                rank: 1,
                nodeId: "external-1",
                value: 8,
                status: "external",
                sampleSize: 1
            },
            { rank: 3, nodeId: "last", value: 4, sampleSize: 1 }
        ]
    }
}, "indegree");
assert.deepEqual(regular.rows.map((row) => row.rank), [1, 1, 3]);
assert.equal(regular.rows[1].status, "external");
assert.equal(regular.rows[1].sampleSize, 1);
assert.equal(regular.totalCount, 22);
assert.equal(regular.scope, "完整网络");

const unknownRankingState = rankingModel.fromRankingResponse({
    data: {
        metric: "indegree",
        state: "future-state",
        items: [{ rank: 1, nodeId: "must-not-render", value: 5 }]
    }
}, "indegree");
assert.equal(unknownRankingState.state, "unavailable");
assert.equal(unknownRankingState.rows.length, 0);
assert.match(unknownRankingState.reason, /无法识别的状态/);

const topTwenty = rankingModel.fromRankingResponse({
    data: {
        metric: "tweet_count",
        sampleSize: 25,
        items: Array.from({ length: 25 }, (_, index) => ({
            rank: index + 1,
            nodeId: "u-" + index,
            value: 100 - index
        }))
    }
}, "tweet_count");
assert.equal(topTwenty.rows.length, 20);
assert.equal(topTwenty.truncated, true);
assert.equal(topTwenty.rows[0].rank, 1);
assert.equal(topTwenty.rows[19].rank, 20);

assert.throws(() => rankingModel.fromRankingResponse({
    data: { metric: "outdegree", items: [] }
}, "indegree"), /不一致/);
assert.equal(rankingModel.isOrdinaryExportMetric("pagerank"), true);
assert.equal(rankingModel.isOrdinaryExportMetric("followers_count"), false);
assert.equal(rankingModel.isOrdinaryExportMetric("k_core"), false);

const unavailable = rankingModel.fromStructureRolesResponse({
    data: {
        nodeCount: 2,
        undirectedTieCount: 0,
        limit: 20,
        kCore: { state: "available", items: [{ rank: 1, nodeId: "u1", value: 0 }] },
        betweenness: {
            state: "unavailable",
            reason: "节点数不足",
            items: []
        }
    }
}, "betweenness");
assert.equal(unavailable.state, "unavailable");
assert.equal(unavailable.reason, "节点数不足");
assert.equal(unavailable.nodeCount, 2);
assert.equal(unavailable.undirectedTieCount, 0);
assert.equal(unavailable.rows.length, 0);

const roleTopTwenty = rankingModel.fromStructureRolesResponse({
    data: {
        nodeCount: 626,
        undirectedTieCount: 1000,
        algorithmVersion: "node-roles-v1",
        limit: 20,
        kCore: {
            state: "available",
            items: Array.from({ length: 20 }, (_, index) => ({
                rank: index + 1,
                nodeId: "node-" + index,
                nodeType: index === 19 ? "external" : "profile",
                value: 4
            }))
        }
    }
}, "k_core");
assert.equal(roleTopTwenty.rows.length, 20);
assert.equal(roleTopTwenty.rows[19].nodeType, "external");
assert.equal(roleTopTwenty.nodeCount, 626);
assert.equal(roleTopTwenty.truncated, true);

const unknownRoleState = rankingModel.fromStructureRolesResponse({
    data: {
        nodeCount: 1,
        kCore: {
            state: "future-state",
            items: [{ rank: 1, nodeId: "must-not-render", value: 3 }]
        }
    }
}, "k_core");
assert.equal(unknownRoleState.state, "unavailable");
assert.equal(unknownRoleState.rows.length, 0);
assert.match(unknownRoleState.reason, /无法识别的状态/);

const crossMetricSnapshot = {
    datasetId: "think-tank-social-network",
    datasetVersion: "v1",
    snapshotCalculatedAt: "2026-10-29T12:00:00Z",
    versions: {
        normalizationVersion: "normalization-v2",
        networkMetricVersion: "network-v2",
        pageRankVersion: "pagerank-v1",
        structuralRoleAlgorithmVersion: "roles-v1"
    }
};
const crossMetric = rankingModel.fromCrossMetricResponse({
    meta: {
        datasetId: crossMetricSnapshot.datasetId,
        datasetVersion: crossMetricSnapshot.datasetVersion
    },
    data: {
        analysisSnapshot: crossMetricSnapshot,
        crossMetric: {
            state: "limited",
            reason: "部分结果受限。",
            populationSize: 626,
            spearmanPairs: Array.from({ length: 12 }, (_, index) => ({
                metricA: "indegree",
                metricB: "outdegree",
                rho: index === 0 ? 0 : null,
                sampleSize: 626,
                tieMethod: "average_rank",
                state: index === 0 ? "available" : "unavailable",
                reason: index === 0 ? null : "无变异。"
            })),
            top20Comparisons: [{
                metricA: "kCore",
                metricB: "normalizedBetweenness",
                k: 20,
                thresholdA: 4,
                thresholdB: 0,
                setSizeA: 22,
                setSizeB: 19,
                intersectionCount: 3,
                jaccard: null,
                tieExpandedA: true,
                tieExpandedB: false,
                state: "limited",
                reason: "零阈值，不计算 Jaccard。"
            }],
            nodeEvidenceState: "available",
            nodeEvidenceReason: null,
            nodeEvidence: [{
                nodeId: "u-1",
                nodeType: "profile",
                profileState: "available",
                profile: { username: "user", displayName: "User" },
                metrics: [
                    { metricKey: "kCore", value: 0, rank: 1 },
                    { metricKey: "normalizedBetweenness", value: 0.2, rank: 2 }
                ]
            }]
        }
    }
}, {
    datasetId: crossMetricSnapshot.datasetId,
    datasetVersion: crossMetricSnapshot.datasetVersion
});
assert.equal(crossMetric.state, "limited");
assert.equal(crossMetric.populationSize, 626);
assert.equal(crossMetric.snapshot.snapshotCalculatedAt,
    "2026-10-29T12:00:00Z");
assert.equal(crossMetric.spearmanPairs.length, 10);
assert.equal(crossMetric.spearmanPairs[0].rho, 0);
assert.equal(crossMetric.spearmanPairs[0].metricALabel,
    "被关注关系数");
assert.equal(crossMetric.top20Comparisons[0].jaccard, null);
assert.equal(crossMetric.top20Comparisons[0].tieExpandedA, true);
assert.equal(crossMetric.nodeEvidence[0].metrics[0].value, 0);
assert.equal(crossMetric.nodeEvidence[0].metrics[0].rank, 1);

const unknownCrossMetricState = rankingModel.fromCrossMetricResponse({
    meta: {
        datasetId: crossMetricSnapshot.datasetId,
        datasetVersion: crossMetricSnapshot.datasetVersion
    },
    data: {
        analysisSnapshot: crossMetricSnapshot,
        crossMetric: {
            state: "future-state",
            spearmanPairs: [{
                metricA: "indegree",
                metricB: "outdegree",
                rho: 0.8,
                sampleSize: 626,
                state: "available"
            }],
            nodeEvidenceState: "available",
            nodeEvidence: [{ nodeId: "must-not-render", metrics: [] }]
        }
    }
}, {
    datasetId: crossMetricSnapshot.datasetId,
    datasetVersion: crossMetricSnapshot.datasetVersion
});
assert.equal(unknownCrossMetricState.state, "unavailable");
assert.equal(unknownCrossMetricState.spearmanPairs.length, 0);
assert.equal(unknownCrossMetricState.nodeEvidence.length, 0);
assert.match(unknownCrossMetricState.reason, /无法识别的状态/);

const builtinWithoutLoadedVersion = rankingModel.fromCrossMetricResponse({
    meta: {
        datasetId: crossMetricSnapshot.datasetId,
        datasetVersion: crossMetricSnapshot.datasetVersion
    },
    data: {
        analysisSnapshot: crossMetricSnapshot,
        crossMetric: {
            state: "available",
            populationSize: 1,
            spearmanPairs: [],
            top20Comparisons: [],
            nodeEvidenceState: "available",
            nodeEvidence: [{
                nodeId: "u-1",
                nodeType: "profile",
                profileState: "available",
                profile: { displayName: "User" },
                metrics: [{ metricKey: "indegree", value: 0, rank: 1 }]
            }]
        }
    }
}, {
    datasetId: crossMetricSnapshot.datasetId,
    datasetVersion: null,
    sessionId: null
});
assert.equal(builtinWithoutLoadedVersion.state, "available");
assert.equal(builtinWithoutLoadedVersion.nodeEvidence.length, 1);

const mismatchedCrossMetric = rankingModel.fromCrossMetricResponse({
    meta: {
        datasetId: "think-tank-social-network",
        datasetVersion: "v2"
    },
    data: {
        analysisSnapshot: crossMetricSnapshot,
        crossMetric: { state: "available", spearmanPairs: [] }
    }
}, {
    datasetId: "think-tank-social-network",
    datasetVersion: "v1"
});
assert.equal(mismatchedCrossMetric.state, "mismatch");
assert.deepEqual(mismatchedCrossMetric.spearmanPairs, []);
assert.deepEqual(mismatchedCrossMetric.nodeEvidence, []);

console.log("social-network-ranking-model: cross-metric checks passed");
