"use strict";

const assert = require("node:assert/strict");
const model = require(
    "../src/main/resources/static/js/social-network-network-structure-model.js"
);

const responseMeta = {
    datasetId: "think-tank-social-network",
    datasetVersion: "v1",
    normalizationVersion: "normalization-v2",
    qualityStatus: "available_with_warnings"
};

assert.deepEqual(
    model.categories().map(item => item.id),
    ["scale-connectivity", "closure-reciprocity", "structural-distance"]
);

const summary = model.fromResponse({
    meta: responseMeta,
    data: {
        nodeCount: 0,
        edgeCount: 0,
        profileUserCount: 0,
        externalNodeCount: 0,
        isolatedNodeCount: 0,
        weaklyConnectedComponentCount: 0,
        largestWeaklyConnectedComponentNodeCount: 0,
        largestWeaklyConnectedComponentRatio: null,
        density: null,
        averageClusteringCoefficient: null,
        reciprocityRate: null,
        largestWeaklyConnectedComponentAveragePathLength: null,
        metricVersion: "network-metrics-v2.0",
        pagerankVersion: "pagerank-v1.0",
        pageRank: {
            converged: false,
            iterations: 100,
            finalDelta: 0.0001,
            sum: 1
        }
    }
});

assert.equal(summary.datasetId, "think-tank-social-network");
assert.equal(summary.datasetVersion, "v1");
assert.equal(summary.normalizationVersion, "normalization-v2");
assert.equal(summary.qualityLoadState, "unavailable");
assert.equal(summary.state, null);
assert.equal(summary.metricVersion, "network-metrics-v2.0");
assert.equal(summary.pagerankVersion, "pagerank-v1.0");
assert.deepEqual(model.qualityImpactItems(summary).map(item => item.key),
    ["quality-unavailable"]);
const allMetricEntries = model.categories().flatMap(category =>
    model.metricEntries(summary, category.id)
);
assert.equal(allMetricEntries.length, 7);
assert.ok(allMetricEntries.every(entry =>
    typeof entry.meaning === "string" && entry.meaning.length > 0
));
assert.match(allMetricEntries[0].meaning, /不代表用户彼此更亲近/);
assert.deepEqual(
    model.metricEntries(summary, "scale-connectivity")
        .map(item => [item.key, item.state]),
    [
        ["density", "unavailable"],
        ["isolatedNodeCount", "value"],
        ["weaklyConnectedComponentCount", "value"],
        ["largestWeaklyConnectedComponentRatio", "unavailable"]
    ]
);
assert.equal(
    model.metricEntries(summary, "scale-connectivity")[2].value,
    0
);
assert.equal(model.metricEntries(summary, "closure-reciprocity")
    .find(item => item.key === "averageClusteringCoefficient").state,
"unavailable");
assert.equal(model.metricEntries(summary, "structural-distance")[0].state,
    "unavailable");
assert.deepEqual(model.largestComponent(summary), {
    sizeState: "value",
    size: 0,
    totalState: "value",
    total: 0,
    ratioState: "unavailable",
    ratio: null
});
assert.equal(model.pageRankState(summary), "not-converged");
assert.equal(model.pageRankValueState(summary, "iterations"), "value");
assert.equal(model.pageRankValueState(summary, "sum"), "value");
assert.equal(model.networkStateLabel("empty"), "当前没有网络节点");
assert.equal(model.networkStateLabel("unavailable"), "暂不可用");
assert.equal(model.networkStateLabel("failed"), "读取失败");
assert.equal(model.networkStateLabel(null), "状态未提供");
assert.equal(model.qualityStatusLabel("available_with_warnings"), "有质量提示");

const missing = model.fromResponse({
    data: {
        nodeCount: 1,
        density: 0,
        averageClusteringCoefficient: 0,
        pageRank: {}
    }
});
assert.equal(model.valueState(missing.data, "nodeCount"), "value");
assert.equal(model.valueState(missing.data, "density"), "value");
assert.equal(model.valueState(missing.data, "reciprocityRate"),
    "not-provided");
assert.equal(model.valueState({ reciprocityRate: null }, "reciprocityRate"),
    "unavailable");
assert.equal(model.metricEntries(missing, "closure-reciprocity")
    .find(item => item.key === "averageClusteringCoefficient").value,
0);
assert.equal(model.pageRankState(missing), "not-provided");

const converged = model.fromResponse({
    data: { pageRank: { converged: true, iterations: 17 } }
});
assert.equal(model.pageRankState(converged), "converged");
assert.deepEqual(model.metricEntries(summary, "unknown"), []);

const coreSnapshotMeta = {
    datasetId: "think-tank-social-network",
    datasetVersion: "v1"
};
const coreDistributionResponse = {
    meta: coreSnapshotMeta,
    data: {
        kCoreDistribution: {
            state: "available",
            algorithmVersion: "kcore-v1",
            sampleSize: 5,
            maxCoreNumber: 2,
            highestCoreCount: 1,
            complete: true,
            truncated: false,
            shells: [
                {
                    coreNumber: 0,
                    exactCount: 2,
                    exactRatio: 0.4,
                    cumulativeCount: 5,
                    cumulativeRatio: 1,
                    nodes: [
                        { nodeId: "z", nodeType: "profile", profile: {} },
                        { nodeId: "y", nodeType: "profile", profile: {} }
                    ]
                },
                {
                    coreNumber: 1,
                    exactCount: 2,
                    exactRatio: 0.4,
                    cumulativeCount: 3,
                    cumulativeRatio: 0.6,
                    nodes: [
                        { nodeId: "d", nodeType: "profile", profile: {} },
                        { nodeId: "c", nodeType: "external", profile: {} }
                    ]
                },
                {
                    coreNumber: 2,
                    exactCount: 1,
                    exactRatio: 0.2,
                    cumulativeCount: 1,
                    cumulativeRatio: 0.2,
                    nodes: [
                        { nodeId: "a", nodeType: "profile", profile: {} }
                    ]
                }
            ]
        }
    }
};
const coreSummary = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 5 }
}, null, coreDistributionResponse);
assert.equal(coreSummary.kCoreLoadState, "available");
assert.equal(coreSummary.kCoreDistribution.algorithmVersion, "kcore-v1");
assert.deepEqual(
    model.kCoreRows(coreSummary, "exact").map(row => [
        row.label, row.count, row.sampleSize
    ]),
    [["core=0", 2, 5], ["core=1", 2, 5], ["core=2", 1, 5]]
);
assert.deepEqual(
    model.kCoreRows(coreSummary, "cumulative").map(row => [
        row.label, row.count, row.ratio
    ]),
    [["core≥0", 5, 1], ["core≥1", 3, 0.6], ["core≥2", 1, 0.2]]
);
assert.deepEqual(
    model.kCoreEvidenceNodes(coreSummary, 1, "exact")
        .map(node => [node.nodeId, node.coreNumber]),
    [["c", 1], ["d", 1]]
);
assert.deepEqual(
    model.kCoreEvidenceNodes(coreSummary, 1, "cumulative")
        .map(node => node.nodeId),
    ["a", "c", "d"]
);

const mismatchedCoreSummary = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 5 }
}, null, {
    meta: {
        datasetId: "think-tank-social-network",
        datasetVersion: "v2"
    },
    data: coreDistributionResponse.data
});
assert.equal(mismatchedCoreSummary.kCoreLoadState, "mismatch");
assert.match(mismatchedCoreSummary.kCoreDistribution.reason, /版本不一致/);

const incompleteCoreSummary = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 5 }
}, null, {
    meta: coreSnapshotMeta,
    data: {
        kCoreDistribution: Object.assign(
            {},
            coreDistributionResponse.data.kCoreDistribution,
            { truncated: true }
        )
    }
});
assert.equal(incompleteCoreSummary.kCoreLoadState, "unavailable");
assert.match(incompleteCoreSummary.kCoreDistribution.reason, /未截断/);

const inconsistentCoreData = JSON.parse(JSON.stringify(
    coreDistributionResponse.data
));
inconsistentCoreData.kCoreDistribution.shells[0].exactRatio = 0.41;
const inconsistentCoreSummary = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 5 }
}, null, {
    meta: coreSnapshotMeta,
    data: inconsistentCoreData
});
assert.equal(inconsistentCoreSummary.kCoreLoadState, "unavailable");
assert.match(inconsistentCoreSummary.kCoreDistribution.reason,
    /节点证据不完整或总数不一致/);

const emptyCoreSummary = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 0 }
}, null, {
    meta: coreSnapshotMeta,
    data: {
        kCoreDistribution: {
            state: "empty",
            sampleSize: 0,
            maxCoreNumber: null,
            highestCoreCount: null,
            complete: true,
            truncated: false,
            shells: []
        }
    }
});
assert.equal(emptyCoreSummary.kCoreLoadState, "available");
assert.equal(emptyCoreSummary.kCoreDistribution.state, "empty");
assert.deepEqual(model.kCoreRows(emptyCoreSummary, "exact"), []);

const matchedQuality = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 626 }
}, {
    meta: {
        datasetId: "think-tank-social-network",
        datasetVersion: "v1"
    },
    data: {
        datasetStatus: "available_with_warnings",
        userQuality: {
            duplicate_user_id_count: 1,
            missing_user_id_count: 2
        },
        relationshipQuality: { invalid_relationship_count: 3 },
        tweetQuality: { invalid_tweet_count: 7 }
    },
    warnings: [
        { code: "INVALID_FOLLOWING_LIST", count: 4, affectedScope: "relationship" },
        { code: "INVALID_TWEET", count: 7, affectedScope: "tweet" }
    ]
});
assert.equal(matchedQuality.qualityLoadState, "available");
assert.equal(matchedQuality.qualityStatus, "available_with_warnings");
assert.deepEqual(
    model.qualityImpactItems(matchedQuality).map(item => item.key),
    [
        "duplicate-user-id",
        "missing-user-id",
        "invalid-relationships",
        "invalid-following-list"
    ]
);
assert.match(model.qualityImpactItems(matchedQuality)[0].text,
    /不会因重复资料行重复增加节点数/);
assert.match(model.qualityImpactItems(matchedQuality)[2].text, /PageRank/);
assert.doesNotMatch(
    model.qualityImpactItems(matchedQuality).map(item => item.text).join(" "),
    /推文.*影响|互动.*影响/
);

const mismatchedQuality = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 626 }
}, {
    meta: {
        datasetId: "think-tank-social-network",
        datasetVersion: "v2"
    },
    data: {
        datasetStatus: "healthy",
        userQuality: { missing_user_id_count: 0 }
    },
    warnings: [
        { code: "INVALID_FOLLOWING_LIST", count: 99 }
    ]
});
assert.equal(mismatchedQuality.qualityLoadState, "mismatch");
assert.equal(mismatchedQuality.quality, null);
assert.deepEqual(mismatchedQuality.qualityWarnings, []);
assert.equal(model.qualityImpactItems(mismatchedQuality)[0].key,
    "quality-unavailable");
assert.match(model.qualityImpactItems(mismatchedQuality)[0].text,
    /版本不一致/);

const cleanQuality = model.fromResponse({
    meta: responseMeta,
    data: { nodeCount: 626 }
}, {
    meta: {
        datasetId: "think-tank-social-network",
        datasetVersion: "v1"
    },
    data: {
        datasetStatus: "healthy",
        userQuality: { missing_user_id_count: 0 },
        relationshipQuality: { invalid_relationship_count: 0 }
    },
    warnings: [
        { code: "INVALID_TWEET", count: 12, affectedScope: "tweet" }
    ]
});
assert.deepEqual(
    model.qualityImpactItems(cleanQuality).map(item => item.key),
    ["no-network-limitation"]
);

console.log("social-network-network-structure-model: checks passed");
