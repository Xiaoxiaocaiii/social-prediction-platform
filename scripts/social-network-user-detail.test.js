"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const createSocialNetworkState = require(
    "../src/main/resources/static/js/social-network-state.js"
);

const root = path.join(__dirname, "..");
const source = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network.js"),
    "utf8"
);
const index = fs.readFileSync(
    path.join(root, "src/main/resources/static/index.html"),
    "utf8"
);
const css = fs.readFileSync(
    path.join(root, "src/main/resources/static/css/style.css"),
    "utf8"
);

function functionBlock(name, nextName) {
    const startPattern = new RegExp(
        `^    (?:async )?function ${name}\\(`,
        "m"
    );
    const startMatch = startPattern.exec(source);
    assert.ok(startMatch, `${name} is defined`);
    const endPattern = new RegExp(
        `\\n    (?:async )?function ${nextName}\\(`,
        "g"
    );
    endPattern.lastIndex = startMatch.index + startMatch[0].length;
    const endMatch = endPattern.exec(source);
    assert.ok(endMatch, `${name} has a bounded implementation`);
    return source.slice(startMatch.index, endMatch.index);
}

const renderDetail = functionBlock("renderUserDetail", "closeSocialUserDrawer");
const loadDetailStart = source.indexOf("async function loadUserDetail(");
const loadDetailEnd = source.indexOf(
    "window.openSocialNetworkUserDetail = function", loadDetailStart
);
assert.ok(loadDetailStart >= 0 && loadDetailEnd > loadDetailStart);
const loadDetail = source.slice(loadDetailStart, loadDetailEnd);
const clearContext = functionBlock("clearContextBoundViews", "activateUploadPreview");
const renderRanking = functionBlock(
    "renderRankingTaskResult",
    "rankingReasonLabel"
);
assert.match(clearContext, /querySelectorAll\("\[data-user-detail-panel\]"\)/);
assert.doesNotMatch(clearContext, /social-network-user-drawer-body"\)[\s\S]*?\.innerHTML\s*=/);
const taskPageShownStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function (task) {"
);
const taskPageShownEnd = source.indexOf(
    "\n    };", taskPageShownStart
);
assert.ok(taskPageShownStart >= 0 && taskPageShownEnd > taskPageShownStart);
const taskPageShown = source.slice(taskPageShownStart, taskPageShownEnd);

assert.equal(
    (index.match(/role="tab"[\s\S]*?data-user-detail-tab=/g) || []).length,
    3,
    "the drawer provides three evidence categories"
);
assert.match(index, /id="social-network-user-drawer-identity"/);
assert.match(index, /id="social-network-user-drawer-context"/);
assert.match(index, /role="tabpanel"[\s\S]*?data-user-detail-panel="profile"/);
assert.match(index, /data-user-detail-panel="tweets" hidden/);
assert.match(index, /data-user-detail-panel="neighbors" hidden/);
assert.match(index, /social-network\.js\?v=20261107-overview-heading-gap-1/);

assert.match(css, /width:\s*min\(560px,\s*calc\(100vw - 48px\)\)/);
assert.match(css, /\.social-network-user-drawer \.social-network-detail-body\s*\{[^}]*overflow:\s*auto/s);
assert.match(css, /\.social-network-user-drawer \.social-network-detail-body > \[role="tabpanel"\]\[hidden\]\s*\{\s*display:\s*none;/);

assert.match(renderRanking, /查看资料/);
assert.match(renderRanking, /metric:\s*result\.metric/);
assert.match(renderRanking, /rank:\s*item\.rank/);
assert.match(renderRanking, /value:\s*item\.value/);
assert.match(renderRanking, /scope:\s*result\.scope/);
assert.doesNotMatch(renderDetail, /rankingReasons/);
assert.match(renderDetail, /validSamples > 0/);
assert.match(renderDetail, /formatDecimal\(metric\.average\)/);
assert.match(renderDetail, /raw\.available === true/);
assert.match(renderDetail, /data\.nodeType === "external"/);
assert.match(renderDetail, /data\.neighbors/);
assert.match(renderDetail, /本次返回/);

assert.match(source, /workspaceState\.invalidateRequest\("userDetail"\)/);
assert.match(loadDetail, /requireCurrentTaskRequest\(requestToken, requestParameters\)/);
assert.match(loadDetail, /socialUserDrawerUserId !== String\(userId\)/);
assert.match(clearContext, /closeSocialUserDrawer\(false\)/);
assert.match(taskPageShown, /activeSocialNetworkTask !== nextTask/);
assert.match(source, /event\.key === "Escape"/);
assert.match(source, /event\.key !== "Tab"/);
assert.match(source, /!panel\.contains\(document\.activeElement\)/);
assert.match(source, /socialUserDrawerOpener\.focus\(\)/);
assert.match(source, /event\.key === "ArrowRight"/);

const state = createSocialNetworkState({
    datasetId: "dataset-a",
    datasetVersion: null,
    sessionId: null
});
const pendingA = state.beginRequest("userDetail", { userId: "user-a" });
const pendingB = state.beginRequest("userDetail", { userId: "user-b" });
assert.equal(state.isRequestCurrent(pendingA), false,
    "switching users invalidates the previous request");
assert.equal(state.isRequestCurrent(pendingB), true);
state.invalidateRequest("userDetail");
assert.equal(state.isRequestCurrent(pendingB), false,
    "closing the drawer invalidates its in-flight request");
const pendingContext = state.beginRequest("userDetail", { userId: "user-c" });
state.setDatasetContext({
    datasetId: "dataset-b",
    datasetVersion: "v2",
    sessionId: "session-b"
});
assert.equal(state.isRequestCurrent(pendingContext), false,
    "changing dataset context invalidates the in-flight request");

console.log("social-network-user-detail: contract and request-state checks passed");
