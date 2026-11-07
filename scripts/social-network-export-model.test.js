"use strict";

const assert = require("node:assert/strict");
const exportModel = require(
    "../src/main/resources/static/js/social-network-export-model.js"
);

const sourceContext = {
    datasetId: "builtin",
    datasetVersion: "v1",
    sessionId: null
};
const sourceQuery = { metric: "pagerank", filters: { includeExternal: true } };
const snapshot = exportModel.createSnapshot(
    "network",
    sourceContext,
    sourceQuery
);

sourceContext.datasetId = "changed";
sourceQuery.metric = "indegree";
sourceQuery.filters.includeExternal = false;
assert.equal(snapshot.exportType, "network");
assert.deepEqual(snapshot.datasetContext, {
    datasetId: "builtin",
    datasetVersion: "v1",
    sessionId: null
});
assert.deepEqual(snapshot.query, {
    metric: "pagerank",
    filters: { includeExternal: true }
});
assert.equal(Object.isFrozen(snapshot), true);
assert.equal(Object.isFrozen(snapshot.datasetContext), true);
assert.equal(Object.isFrozen(snapshot.query), true);
assert.equal(Object.isFrozen(snapshot.query.filters), true);

assert.equal(exportModel.matchesDataset(snapshot, {
    datasetId: "builtin",
    datasetVersion: "v1",
    sessionId: null
}), true);
assert.equal(exportModel.matchesDataset(snapshot, {
    datasetId: "builtin",
    datasetVersion: "v2",
    sessionId: null
}), false);
assert.equal(exportModel.matchesDataset(snapshot, {
    datasetId: "upload",
    datasetVersion: "v1",
    sessionId: "session-1"
}), false);
assert.equal(exportModel.matchesDataset(null, sourceContext), false);
assert.throws(
    () => exportModel.createSnapshot("network", null, {}),
    /缺少数据集身份/
);

console.log("social-network-export-model: checks passed");
