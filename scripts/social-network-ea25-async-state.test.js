"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const networkModel = require(
    "../src/main/resources/static/js/social-network-network-structure-model.js"
);
const rankingModel = require(
    "../src/main/resources/static/js/social-network-ranking-model.js"
);

const root = path.join(__dirname, "..");
const source = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network.js"),
    "utf8"
);

function functionSource(name, nextName) {
    const start = new RegExp(
        `^    (?:async )?function ${name}\\(`,
        "m"
    ).exec(source);
    const end = new RegExp(
        `\\n    (?:async )?function ${nextName}\\(`,
        "g"
    );
    assert.ok(start, `${name} exists`);
    end.lastIndex = start.index + start[0].length;
    const endMatch = end.exec(source);
    assert.ok(endMatch, `${name} has an end boundary`);
    return source.slice(start.index, endMatch.index);
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
}

function contextKey(context) {
    return [
        context.datasetId,
        context.datasetVersion || "",
        context.sessionId || ""
    ].join("|");
}

function makeMount() {
    const retryButton = {
        handler: null,
        addEventListener(event, handler) {
            if (event === "click") {
                this.handler = handler;
            }
        }
    };
    const metric = { value: "" };
    const note = { textContent: "" };
    const count = { textContent: "" };
    const table = {
        innerHTML: "",
        querySelectorAll() {
            return [];
        }
    };
    const elements = {
        "#social-network-node-ranking-metric": metric,
        "[data-ranking-task-note]": note,
        "[data-ranking-task-count]": count,
        "[data-ranking-task-table]": table
    };
    return {
        elements,
        retryButton,
        innerHTML: "",
        querySelector(selector) {
            if (selector === "[data-network-structure-retry]") {
                return retryButton;
            }
            return elements[selector] || null;
        }
    };
}

function makeNetworkHarness(initialContext) {
    let currentContext = initialContext;
    const requests = [];
    const renders = [];
    const status = [];
    const mount = makeMount();
    const card = { classList: { add() {} } };
    const sandbox = {
        Promise,
        console: { error() {} },
        document: { querySelector() { return card; } },
        activeSocialNetworkTask: "relations",
        networkStructureModel: networkModel,
        networkStructureMount() { return mount; },
        readDatasetContext() { return currentContext; },
        datasetContextKey: contextKey,
        sameDatasetContext(left, right) {
            return contextKey(left) === contextKey(right);
        },
        buildDatasetUrl(route, params, context) {
            return `${route}?${contextKey(context)}`;
        },
        request(url) {
            const pending = deferred();
            requests.push({ url, ...pending });
            return pending.promise;
        },
        renderNetworkStructureView(summary, context) {
            renders.push({ summary, context });
        },
        setText(id, text) {
            status.push([id, text]);
        },
        isStaleDatasetRequest(error) {
            return Boolean(error && error.staleDatasetContext);
        },
        networkStructureContextKey: "",
        networkStructureView: "overview",
        networkStructureKCoreMode: "exact",
        networkStructureSelectedCore: null,
        networkStructureNodeQuery: "",
        networkStructureNodePage: 1,
        currentNetworkStructure: null,
        networkStructureLookup: null,
        networkStructureRequestSequence: 0
    };
    vm.createContext(sandbox);
    vm.runInContext(functionSource(
        "loadNetworkStructure",
        "rankingTaskMount"
    ), sandbox);

    return {
        sandbox,
        card,
        mount,
        requests,
        renders,
        status,
        load() {
            sandbox.loadNetworkStructure(card);
        },
        setContext(context) {
            currentContext = context;
        }
    };
}

function makeRankingHarness(initialContext) {
    let currentContext = initialContext;
    const requests = [];
    const renders = [];
    const status = [];
    const mount = makeMount();
    const card = {};
    const condition = { metric: "indegree" };
    const sandbox = {
        Promise,
        console: { error() {} },
        document: {
            querySelector() { return card; },
            getElementById() { return mount; }
        },
        activeSocialNetworkTask: "ranking",
        socialNetworkRankingModel: rankingModel,
        workspaceState: {
            getCondition() { return condition; },
            setCondition(_name, update) {
                Object.assign(condition, update);
            }
        },
        readDatasetContext() { return currentContext; },
        datasetContextKey: contextKey,
        buildDatasetUrl(route, params, context) {
            const metric = params && params.metric
                ? `&metric=${params.metric}`
                : "";
            return `${route}?${contextKey(context)}${metric}`;
        },
        request(url) {
            const pending = deferred();
            requests.push({ url, ...pending });
            return pending.promise;
        },
        ensureRankingTaskView() { return mount; },
        setRankingTaskViewVisibility() {},
        renderCrossMetricLoading() {},
        renderCrossMetricFailure(_mount, message) {
            renders.push({ type: "failure", message });
        },
        renderRankingCrossMetricResult(_mount, result) {
            renders.push({ type: "comparison", result });
        },
        renderRankingTaskLoading: null,
        renderRankingTaskResult(result) {
            renders.push({ type: "single", result });
            const note = mount.elements["[data-ranking-task-note]"];
            const count = mount.elements["[data-ranking-task-count]"];
            const table = mount.elements["[data-ranking-task-table]"];
            note.textContent = result.definition.description;
            if (result.state === "unavailable") {
                table.innerHTML = `<p role="status">${result.reason}</p>`;
            } else if (result.state === "empty" || !result.rows.length) {
                table.innerHTML =
                    `<p role="status">${result.reason || "当前指标没有可排序样本。"}</p>`;
            } else {
                table.innerHTML = result.rows.map((row) => row.nodeId).join(",");
            }
            count.textContent = String(result.totalCount || "");
            status.push(result.state);
        },
        formatNumber: String,
        formatDecimal: String,
        escapeHtml(value) { return String(value); },
        setText(id, text) {
            status.push([id, text]);
        },
        rankingTaskContextKey: "",
        rankingTaskView: "single",
        rankingTaskRolesCache: null,
        rankingTaskRolesLookup: null,
        rankingTaskOrdinaryLookup: null,
        rankingTaskRequestSequence: 0
    };
    vm.createContext(sandbox);
    vm.runInContext([
        functionSource("rankingTaskIsCurrent", "renderRankingTaskLoading"),
        functionSource("renderRankingTaskLoading", "renderRankingTaskResult"),
        functionSource("renderRankingTaskResult", "loadRankingTask"),
        functionSource("loadRankingTask", "userRelationsTaskMount")
    ].join("\n"), sandbox);
    const renderSingleResult = sandbox.renderRankingTaskResult;
    sandbox.renderRankingTaskResult = function (result) {
        renders.push({ type: "single", result });
        return renderSingleResult(result);
    };

    return {
        sandbox,
        mount,
        requests,
        renders,
        condition,
        status,
        setContext(context) {
            currentContext = context;
        },
        async load() {
            return sandbox.loadRankingTask(card);
        }
    };
}

function networkResponse(context, state, nodeCount) {
    return {
        meta: {
            datasetId: context.datasetId,
            datasetVersion: context.datasetVersion,
            normalizationVersion: "normalization-v1"
        },
        data: {
            state,
            nodeCount,
            metricVersion: "network-v1",
            pageRank: {}
        }
    };
}

function resolveNetworkBatch(harness, start, context, state, nodeCount) {
    const responses = [
        networkResponse(context, state, nodeCount),
        null,
        null
    ];
    responses.forEach((response, index) => {
        harness.requests[start + index].resolve(response);
    });
}

function rankingResponse(metric, state, nodeId) {
    return {
        data: {
            metric,
            state,
            metricType: "network",
            sampleSize: nodeId ? 1 : 0,
            items: nodeId
                ? [{ rank: 1, nodeId, username: nodeId, value: 1 }]
                : []
        }
    };
}

async function flushPromises() {
    await new Promise((resolve) => setImmediate(resolve));
}

async function testNetworkLoadingEmptyUnknownAndFailure() {
    const context = {
        datasetId: "network-a",
        datasetVersion: "v1",
        sessionId: null
    };
    const harness = makeNetworkHarness(context);
    harness.load();
    assert.match(harness.mount.innerHTML, /正在读取/);
    resolveNetworkBatch(harness, 0, context, "empty", 0);
    await flushPromises();
    assert.equal(harness.renders.length, 1);
    assert.equal(harness.renders[0].summary.state, "empty");
    assert.equal(harness.renders[0].summary.kCoreLoadState, "unavailable");
    assert.equal(
        harness.renders[0].summary.qualityLoadState,
        "unavailable"
    );

    const unknownContext = {
        datasetId: "network-unknown",
        datasetVersion: "v1",
        sessionId: null
    };
    harness.setContext(unknownContext);
    harness.load();
    resolveNetworkBatch(harness, 3, unknownContext, "future-state", 3);
    await flushPromises();
    assert.equal(harness.renders[1].summary.state, "future-state");
    assert.equal(
        networkModel.networkStateLabel(harness.renders[1].summary.state),
        "future-state"
    );

    const failed = makeNetworkHarness(context);
    failed.load();
    const requestError = new Error("network request failed");
    failed.requests[0].reject(requestError);
    await flushPromises();
    assert.match(failed.mount.innerHTML, /role="alert"/);
    assert.equal(typeof failed.mount.retryButton.handler, "function");
    failed.mount.retryButton.handler();
    assert.match(failed.mount.innerHTML, /正在读取/);
    assert.equal(failed.requests.length, 6);
}

async function testNetworkDatasetSwitchRejectsExpiredResponse() {
    const contextA = {
        datasetId: "network-a",
        datasetVersion: "v1",
        sessionId: "session-a"
    };
    const contextB = {
        datasetId: "network-b",
        datasetVersion: "v2",
        sessionId: "session-b"
    };
    const harness = makeNetworkHarness(contextA);
    harness.load();
    harness.setContext(contextB);
    harness.load();
    resolveNetworkBatch(harness, 3, contextB, "available", 6);
    await flushPromises();
    resolveNetworkBatch(harness, 0, contextA, "available", 2);
    await flushPromises();
    assert.equal(harness.renders.length, 1);
    assert.equal(harness.renders[0].context.datasetId, "network-b");
    assert.equal(harness.renders[0].summary.data.nodeCount, 6);
}

async function testRankingEmptyUnknownAndFastMetricSwitch() {
    const context = {
        datasetId: "ranking-a",
        datasetVersion: "v1",
        sessionId: null
    };
    const states = makeRankingHarness(context);
    const emptyLoad = states.load();
    assert.match(states.mount.elements["[data-ranking-task-table]"].innerHTML,
        /正在(?:读取|更新)/);
    states.requests[0].resolve(rankingResponse("indegree", "empty", null));
    await emptyLoad;
    assert.match(states.mount.elements["[data-ranking-task-table]"].innerHTML,
        /没有可参与排序的对象/);

    states.condition.metric = "outdegree";
    const unknownLoad = states.load();
    states.requests[1].resolve(rankingResponse(
        "outdegree",
        "future-state",
        "must-not-render"
    ));
    await unknownLoad;
    assert.equal(states.renders[1].result.state, "unavailable");
    assert.equal(states.renders[1].result.rows.length, 0);
    assert.match(states.mount.elements["[data-ranking-task-table]"].innerHTML,
        /当前数据暂不满足这项排序的计算条件/);
    assert.doesNotMatch(
        states.mount.elements["[data-ranking-task-table]"].innerHTML,
        /无法识别的状态/
    );

    const failed = makeRankingHarness(context);
    const failedLoad = failed.load();
    failed.requests[0].reject(new Error("ranking request failed"));
    await failedLoad;
    assert.match(
        failed.mount.elements["[data-ranking-task-table]"].innerHTML,
        /role="alert"/
    );

    const switching = makeRankingHarness(context);
    const oldLoad = switching.load();
    switching.condition.metric = "outdegree";
    const newLoad = switching.load();
    switching.requests[1].resolve(
        rankingResponse("outdegree", "available", "new-metric-node")
    );
    await newLoad;
    switching.requests[0].resolve(
        rankingResponse("indegree", "available", "old-metric-node")
    );
    await oldLoad;
    assert.match(switching.requests[0].url, /metric=indegree/);
    assert.match(switching.requests[1].url, /metric=outdegree/);
    assert.deepEqual(
        switching.renders.map((entry) => entry.result.metric),
        ["outdegree"]
    );
    assert.match(
        switching.mount.elements["[data-ranking-task-table]"].innerHTML,
        /new-metric-node/
    );
    assert.doesNotMatch(
        switching.mount.elements["[data-ranking-task-table]"].innerHTML,
        /old-metric-node/
    );
}

async function testRankingViewAndDatasetSwitchRejectOldResponse() {
    const contextA = {
        datasetId: "ranking-a",
        datasetVersion: "v1",
        sessionId: "session-a"
    };
    const contextB = {
        datasetId: "ranking-b",
        datasetVersion: "v2",
        sessionId: "session-b"
    };
    const viewSwitch = makeRankingHarness(contextA);
    viewSwitch.sandbox.rankingTaskView = "comparison";
    const comparisonLoad = viewSwitch.load();
    viewSwitch.sandbox.rankingTaskView = "single";
    viewSwitch.condition.metric = "followers_count";
    const singleLoad = viewSwitch.load();
    viewSwitch.requests[1].resolve(
        rankingResponse("followers_count", "available", "current-view-node")
    );
    await singleLoad;
    viewSwitch.requests[0].resolve({ data: {} });
    await comparisonLoad;
    assert.deepEqual(
        viewSwitch.renders.map((entry) => entry.type),
        ["single"]
    );
    assert.equal(viewSwitch.renders[0].result.metric, "followers_count");

    const datasetSwitch = makeRankingHarness(contextA);
    const oldLoad = datasetSwitch.load();
    datasetSwitch.setContext(contextB);
    const newLoad = datasetSwitch.load();
    datasetSwitch.requests[1].resolve(
        rankingResponse("indegree", "available", "new-dataset-node")
    );
    await newLoad;
    const expired = new Error("session expired");
    expired.staleDatasetContext = true;
    datasetSwitch.requests[0].reject(expired);
    await oldLoad;
    assert.deepEqual(
        datasetSwitch.renders.map((entry) => entry.result.rows[0].nodeId),
        ["new-dataset-node"]
    );
}

(async function main() {
    await testNetworkLoadingEmptyUnknownAndFailure();
    await testNetworkDatasetSwitchRejectsExpiredResponse();
    await testRankingEmptyUnknownAndFastMetricSwitch();
    await testRankingViewAndDatasetSwitchRejectOldResponse();
    console.log("social-network-ea25-async-state: all checks passed");
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
