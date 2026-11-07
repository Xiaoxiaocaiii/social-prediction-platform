"use strict";

const assert = require("node:assert/strict");
const createSocialNetworkState = require(
    "../src/main/resources/static/js/social-network-state.js"
);

const state = createSocialNetworkState({
    datasetId: "builtin",
    datasetVersion: null,
    sessionId: null
});

assert.deepEqual(state.getCondition("ranking"), { metric: "indegree" });
assert.deepEqual(state.getCondition("interaction"), {
    metric: "like_count",
    category: "tweets"
});
assert.deepEqual(state.getCondition("topic"), {
    draft: "",
    submittedTerm: null,
    category: "tweets"
});

state.setCondition("ranking", { metric: "pagerank" });
assert.equal(state.getCondition("ranking").metric, "pagerank");
assert.equal(state.getCondition("interaction").metric, "like_count");

const first = state.beginRequest("ranking", {
    metric: "pagerank",
    limit: 20
});
const second = state.beginRequest("ranking", {
    limit: 20,
    metric: "pagerank"
});
assert.equal(state.isRequestCurrent(first), false);
assert.equal(state.isRequestCurrent(second, {
    metric: "pagerank",
    limit: 20
}), true);
assert.equal(state.isRequestCurrent(second, {
    metric: "indegree",
    limit: 20
}), false);

const pendingTopic = state.beginRequest("topic", { term: "research" });
state.setCondition("topic", { category: "users" }, false);
assert.equal(state.isRequestCurrent(pendingTopic), true);
assert.equal(state.getCondition("topic").category, "users");
state.setCondition("topic", { draft: "policy" });
assert.equal(state.isRequestCurrent(pendingTopic), false);

const pendingInteraction = state.beginRequest("interaction", {
    metric: "like_count"
});
const initialRevision = state.getDatasetRevision();
assert.equal(state.setDatasetContext({
    datasetId: "upload",
    datasetVersion: "v2",
    sessionId: "session-1"
}), true);
assert.equal(state.getDatasetRevision(), initialRevision + 1);
assert.equal(state.isRequestCurrent(pendingInteraction), false);
assert.equal(state.getCondition("topic").submittedTerm, null);
assert.equal(state.setDatasetContext({
    datasetId: "upload",
    datasetVersion: "v2",
    sessionId: "session-1"
}), false);

const file = { name: "users.csv" };
state.state.uploadDraft.files.push({ partId: "part-1", file, role: "users" });
state.state.uploadDraft.sourceDescription = "手工整理";
state.state.uploadTaskId = "task-1";
state.state.uploadTaskView = { status: "processing" };
state.replaceUploadFiles([
    { partId: "part-2", file, role: "users" }
]);
assert.equal(state.state.uploadDraft.files[0].partId, "part-2");
assert.equal(state.state.uploadDraft.sourceDescription, "手工整理");
assert.equal(state.state.uploadTaskId, "task-1");
assert.equal(state.state.uploadTaskView.status, "processing");

const freshTabState = createSocialNetworkState({
    datasetId: "builtin",
    datasetVersion: null,
    sessionId: null
});
assert.equal(freshTabState.state.uploadDraft.files.length, 0);
assert.equal(freshTabState.state.uploadTaskId, null);

console.log("social-network-state: 10 checks passed");
