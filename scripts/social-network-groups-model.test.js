"use strict";

const assert = require("node:assert/strict");
const model = require(
    "../src/main/resources/static/js/social-network-groups-model.js"
);

assert.equal(model.PAGE_SIZE, 20);
assert.equal(model.isSupportedContext({
    datasetId: "think-tank-social-network",
    datasetVersion: "v1",
    sessionId: null
}), true);
assert.equal(model.isSupportedContext({
    datasetId: "think-tank-social-network",
    datasetVersion: "upload-v1",
    sessionId: "upload-session"
}), false);
assert.equal(model.isUploadContext({
    datasetId: "upload-session-1",
    datasetVersion: "session-1",
    sessionId: "session-1"
}), true);
assert.equal(model.isSupportedContext({
    datasetId: "upload-session-1",
    datasetVersion: "session-1",
    sessionId: "session-1"
}), true);
assert.equal(model.isAnalysisSupportedContext({
    datasetId: "upload-session-1",
    datasetVersion: "session-1",
    sessionId: "session-1"
}, "louvain"), true);
assert.equal(model.isAnalysisSupportedContext({
    datasetId: "upload-session-1",
    datasetVersion: "session-1",
    sessionId: "session-1"
}, "clique"), true);
assert.equal(model.isAnalysisSupportedContext({
    datasetId: "upload-session-1",
    datasetVersion: "session-1",
    sessionId: "session-1"
}, "unknown-analysis"), false);
assert.equal(model.isSupportedContext({
    datasetId: "other-network",
    sessionId: null
}), false);

assert.equal(model.labelForStatus("NOT_REQUESTED"), "尚未运行");
assert.equal(model.labelForStatus("COMPLETED_UNCONVERGED"), "执行完成，算法未收敛");
assert.equal(model.labelForStatus("CUSTOM_STATUS"), "CUSTOM_STATUS");
assert.equal(model.isRunning("RUNNING"), true);
assert.equal(model.isRunning("COMPLETED"), false);

assert.deepEqual(model.cliqueDistribution({
    "4": 8,
    "3": 12,
    "2": 90,
    invalid: 1
}), [
    { size: 3, count: 12 },
    { size: 4, count: 8 }
]);
assert.deepEqual(model.louvainDistribution([
    { id: "C002", nodeCount: 4 },
    { id: "C001", nodeCount: 4 },
    { id: "C003", nodeCount: 2 },
    { id: "", nodeCount: 100 }
]), [
    { id: "C001", size: 4 },
    { id: "C002", size: 4 },
    { id: "C003", size: 2 }
]);
assert.deepEqual(model.topParticipants({
    b: 3,
    a: 3,
    c: 1,
    ignored: 0
}, 2), [
    { nodeId: "a", count: 3 },
    { nodeId: "b", count: 3 }
]);

console.log("social-network-groups-model: 20 checks passed");
