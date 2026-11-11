"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const createState = require(
    "../src/main/resources/static/js/social-network-state.js"
);
const routes = require(
    "../src/main/resources/static/js/social-network-routes.js"
);

const root = path.join(__dirname, "..");
const source = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network.js"),
    "utf8"
);
const stateSource = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network-state.js"),
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
const router = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/router.js"),
    "utf8"
);

function functionBlock(name, nextName) {
    const startMatch = new RegExp(
        `^    (?:async )?function ${name}\\(`,
        "m"
    ).exec(source);
    assert.ok(startMatch, `${name} is defined`);
    const endMatch = new RegExp(
        `\\n    (?:async )?function ${nextName}\\(`,
        "g"
    );
    endMatch.lastIndex = startMatch.index + startMatch[0].length;
    const nextMatch = endMatch.exec(source);
    assert.ok(nextMatch, `${name} has a bounded implementation`);
    return source.slice(startMatch.index, nextMatch.index);
}

const contextRecovery = functionBlock(
    "recoverExpiredDatasetContext",
    "request"
);
const request = functionBlock("request", "currentExportQuery");
const contextChanged = functionBlock(
    "handleSocialNetworkDatasetContextChanged",
    "setTaskUploadStatus"
);
const uploadRenderer = functionBlock(
    "renderUploadTask",
    "renderTaskUploadIssueList"
);
const poll = functionBlock("pollUploadTask", "submitUpload");
const pageShownStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function (task) {"
);
const pageShownEnd = source.indexOf("\n    };", pageShownStart);
assert.ok(pageShownStart >= 0 && pageShownEnd > pageShownStart);
const pageShown = source.slice(pageShownStart, pageShownEnd);

assert.equal(routes.paths.length, 10);
for (const routePath of routes.paths) {
    const route = routes.resolve(routePath);
    assert.equal(route.page, "social-network-task");
    assert.ok(route.task);
}
assert.match(router, /addEventListener\("hashchange", showRoute\)/);
assert.doesNotMatch(router, /addEventListener\("load", showRoute/);
assert.match(router, /showPage\("social-network-task", "unknown"\)/);

assert.doesNotMatch(contextRecovery, /refreshWorkspaceData/);
assert.match(contextRecovery, /sameDatasetContext\(context, readDatasetContext\(\)\)/);
assert.match(contextRecovery, /resetDatasetContext\(\)/);
assert.match(request, /recoverExpiredDatasetContext\(context\)/);
assert.doesNotMatch(request, /refreshWorkspaceData/);
assert.match(contextChanged,
    /activeSocialNetworkTask === "overview"\)\s*\{\s*loadOverviewSummary\(\)/);
assert.match(contextChanged,
    /else if \(isDataQualityTask\(activeSocialNetworkTask\)\)\s*\{\s*loadDatasetPage/);
assert.match(contextChanged, /else if \(activeSocialNetworkTask === "relations"\)/);
assert.match(contextChanged, /else if \(activeSocialNetworkTask === "ranking"\)/);
assert.match(contextChanged, /else if \(activeSocialNetworkTask === "interaction"\)/);
assert.match(contextChanged, /else if \(activeSocialNetworkTask === "topic"\)/);
assert.match(contextChanged, /else if \(activeSocialNetworkTask === "user-relations"\)/);
assert.doesNotMatch(contextChanged, /refreshWorkspaceData/);

let renderCount = 0;
const detachedUploadState = {};
const renderDetachedUpload = new Function(
    "document",
    "uploadState",
    "persistUploadTaskId",
    "renderTaskUploadResult",
    "renderTaskUploadState",
    "updateUploadControls",
    `${uploadRenderer}; return renderUploadTask;`
)(
    { getElementById: () => null },
    detachedUploadState,
    () => {},
    () => { renderCount += 1; },
    () => { renderCount += 1; },
    () => { renderCount += 1; }
);
const processingView = { status: "processing" };
renderDetachedUpload(processingView);
assert.equal(detachedUploadState.uploadTaskView, processingView);
assert.equal(renderCount, 0);
assert.match(poll, /renderUploadTask\(body\.data\)/);
assert.match(poll, /scheduleUploadTaskPoll\(body\.data\.status === "queued" \? 700 : 1000\)/);
assert.match(pageShown, /scheduleUploadTaskPoll\(0\)/);
assert.doesNotMatch(pageShown, /submitUpload\(/);
assert.doesNotMatch(pageShown, /clearUploadTaskState\(\)/);
assert.doesNotMatch(pageShown, /invalidateRequest\("uploadPoll"\)/);

assert.match(stateSource, /draft: "", submittedTerm: null/);
const state = createState({
    datasetId: "builtin",
    datasetVersion: "v1",
    sessionId: null
});
state.setCondition("topic", {
    draft: "second draft",
    submittedTerm: "first success"
});
state.setCondition("interaction", { metric: "comment_count" });
assert.deepEqual(state.getCondition("topic"), {
    draft: "second draft",
    submittedTerm: "first success",
    category: "tweets"
});
assert.deepEqual(state.getCondition("interaction"), {
    metric: "comment_count",
    category: "tweets"
});

assert.doesNotMatch(index, /id="page-social-network"/);
assert.match(index, /id="page-social-network-task"/);
assert.match(index, /id="page-social-network-graph"/);
assert.doesNotMatch(index, /social-network-upload-(?:input|files|source)"/);
assert.doesNotMatch(
    index + source,
    /后续获授权 UI|预览激活将在 UI10 接入/
);
assert.match(
    index,
    /上传数据支持[^。]*Clique 团体分析与成员来源核对及当前团列表页导出/
);
assert.doesNotMatch(index, /Clique 导出尚未开放/);
assert.match(index, /social-network\.js\?v=20261108-global-graph-entry-1/);
assert.match(index, /style\.css\?v=20261111-ranking-bridge-spacing-1/);
assert.doesNotMatch(index, /id="social-network-data-tabs"/);
assert.equal((index.match(/class="social-network-overview-task-eyebrow"/g) || []).length, 3);
assert.match(index, /href="#\/social-network\/data">[\s\S]*?DATA &amp; QUALITY[\s\S]*?数据与质量/);
assert.match(index, /id="social-network-dataset-quality"/);
assert.match(index, /id="social-network-quality-report"/);
assert.equal(routes.resolve("/social-network/data").title,
    routes.resolve("/social-network/data/quality").title);
assert.match(index, /<nav id="social-network-content-tabs"[\s\S]*?href="#\/social-network\/content\/topic">主题分析<\/a>/);
assert.equal((index.match(/id="social-network-content-tabs"/g) || []).length, 1);
assert.match(index, /<nav id="social-network-relations-tabs"[\s\S]*?href="#\/social-network\/relations\/user">用户关系<\/a>/);
assert.equal((index.match(/id="social-network-relations-tabs"/g) || []).length, 1);
assert.match(index, /<header class="social-network-task-heading">[\s\S]*?<nav id="social-network-relations-tabs"/);
assert.ok(
    index.indexOf('id="social-network-relations-tabs"') <
        index.indexOf('class="social-network-task-card"')
);
assert.match(pageShown, /loadDatasetPage\(task === "quality"\)/);
assert.doesNotMatch(pageShown, /dataTabs/);
assert.match(pageShown, /var contentTabs = document\.getElementById\(\s*"social-network-content-tabs"/);
assert.match(pageShown, /contentTabs\.hidden = !currentContentPath/);
assert.match(pageShown, /contentPathByTask\s*=\s*\{[\s\S]*?topic: "#\/social-network\/content\/topic"/);
assert.match(pageShown, /var relationsTabs = document\.getElementById\(\s*"social-network-relations-tabs"/);
assert.match(pageShown, /relationsTabs\.hidden = !currentRelationsPath/);
assert.match(pageShown, /relationsPathByTask\s*=\s*\{[\s\S]*?"user-relations": "#\/social-network\/relations\/user"/);
assert.match(pageShown, /link\.setAttribute\("aria-current", "page"\)/);
assert.doesNotMatch(
    source,
    /social-network-(?:network-structure|node-ranking|user-relations)-nav/
);
assert.doesNotMatch(source, /class="social-network-content-task-nav"/);
assert.match(css, /#social-network-data-tabs\[hidden\]\s*\{\s*display:\s*none;\s*\}/);
assert.match(css, /body\.social-network-shell-active > \.footer\s*\{\s*display:\s*none;\s*\}/);
assert.match(
    css,
    /body\.social-network-shell-active #social-network-dataset-source-open,[\s\S]*?\[data-interaction-task-refresh\]\s*\{/
);
assert.match(css, /body\.social-network-shell-active \[data-interaction-task-refresh\]:focus-visible/);
assert.match(css, /body\.social-network-shell-active \[data-interaction-task-refresh\]:disabled/);
assert.doesNotMatch(index, /social-network-context-bar|social-network-shell-dataset-(?:name|type|version)|social-network-context-switch/);
assert.doesNotMatch(css, /social-network-context-(?:bar|inner|switch)/);
assert.match(index, /id="social-network-dataset-switch-heading">切换数据/);
assert.doesNotMatch(index, /social-network-dataset-export-tool|social-network-central-export/);
assert.doesNotMatch(source, /centralExport|central-export|导出工具/);
assert.doesNotMatch(css, /dataset-export-tool|central-export/);
assert.match(
    css,
    /\.social-network-dataset-switch\s*\{\s*padding:\s*20px 22px;\s*border:\s*1px solid var\(--border\);\s*border-radius:\s*10px;\s*background:\s*var\(--surface\);/
);
assert.match(
    css,
    /body\.social-network-shell-active \.social-network-dataset-switch\s*\{\s*padding:\s*var\(--sn-space-2\);/
);
assert.match(css, /body\.social-network-shell-active #social-network-dataset-page\s+\.social-network-dataset-switch-row\s*\{\s*gap:\s*var\(--sn-space-2\);\s*padding:\s*var\(--sn-space-2\);\s*border:\s*1px solid var\(--border\);\s*border-radius:\s*8px;\s*background:\s*var\(--surface-soft\);\s*box-shadow:\s*none;/);
assert.match(css, /body\.social-network-shell-active \.social-network-dataset-switch\s*\{\s*margin-top:\s*var\(--sn-space-3\);/);
assert.match(css, /body\.social-network-shell-active \.social-network-shell-layout\s*\{\s*grid-template-columns:\s*184px/);
assert.match(css, /body\.social-network-shell-active \.social-network-sidebar-link\s*\{\s*min-height:\s*40px/);
assert.match(css, /body\.social-network-shell-active \.social-network-task-content\s*\{\s*padding:\s*26px 38px 32px/);
assert.match(css, /#page-social-network-task\s+\.social-network-task-card \.button,[\s\S]*?border-radius:\s*var\(--social-control-radius\)/);
assert.match(css, /\.social-network-task-card \.button-primary,[\s\S]*?background:\s*var\(--primary-weak\)/);
assert.match(css, /\.social-network-interaction-category-nav button\s*\{[\s\S]*?appearance:\s*none;[\s\S]*?border-bottom:\s*2px solid transparent/);
assert.match(css, /\.social-network-task-card:has\([\s\S]*?> #social-network-task-status,[\s\S]*?clip-path:\s*inset\(50%\)/);
assert.match(css, /body\.social-network-shell-active > \.topbar\s*\{\s*flex:\s*0 0 auto;\s*\}/);
assert.match(css, /social-network-interaction-task-header h2,[\s\S]*?social-network-topic-task-header h2\)\s*\{\s*font-size:\s*20px/);
assert.match(css, /social-network-task-upload-categories button,[\s\S]*?min-height:\s*36px[\s\S]*?border-radius:\s*var\(--social-control-radius\)/);
assert.match(css, /social-network-topic-task-categories button\):is\([\s\S]*?background:\s*var\(--primary-weak\)/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\.social-network-shell-layout,[\s\S]*?dialog\[class\*="social-network-"\]\)\s*\{[\s\S]*?--font-data:\s*Inter,[\s\S]*?"Noto Sans SC"/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+h1\s*\{\s*font-family:\s*var\(--font-serif\);\s*font-size:\s*24px/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+h2\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*20px/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+h3\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*16px/);
assert.match(css, /body\.social-network-shell-active #page-social-network-task :is\([\s\S]*?\.social-network-dataset-counts dd,[\s\S]*?\.social-network-network-structure-scope strong,[\s\S]*?\.social-network-network-structure-metric > strong,[\s\S]*?\.social-network-quality-metric strong,[\s\S]*?\)\s*\{\s*font: 600 20px\/1\.2 var\(--font-data\);\s*font-variant-numeric: tabular-nums;/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+:is\(\s*button, \.button, \[role="button"\]\)\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*12px/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+:is\(input, select, textarea\)\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*13px/);
assert.match(css, /social-network-quality-report-eyebrow,[\s\S]*?social-network-network-structure-eyebrow\)\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*10px/);
assert.match(css, /\.social-network-graph-page h1\s*\{\s*font-family:\s*var\(--font-serif\);\s*font-size:\s*24px/);
assert.match(css, /\.social-network-graph-page :is\(button, \.button, \[role="button"\]\)\s*\{\s*font-family:\s*var\(--font-sans\);\s*font-size:\s*12px/);
assert.match(css, /--sn-space-1:\s*8px;[\s\S]*?--sn-space-section:\s*72px;/);
assert.match(css, /body\.social-network-shell-active \.social-network-task-content\s*\{\s*padding:\s*var\(--sn-space-4\)\s+var\(--sn-space-5\)\s+40px;/);
assert.match(css, /@media \(max-width: 1560px\)\s*\{[\s\S]*?social-network-task-content,[\s\S]*?padding-right:\s*var\(--sn-space-4\);[\s\S]*?padding-left:\s*var\(--sn-space-4\);/);
assert.match(css, /@media \(max-width: 1320px\)\s*\{[\s\S]*?social-network-task-content,[\s\S]*?padding-right:\s*var\(--sn-space-3\);[\s\S]*?padding-left:\s*var\(--sn-space-3\);/);
assert.match(css, /#page-social-network-task\s+\.social-network-task-card,[\s\S]*?#page-social-network-task\s+\.social-network-dataset-identity\s*\{\s*padding:\s*var\(--sn-space-3\);/);
assert.match(css, /body\.social-network-shell-active \.social-network-task-heading\s*\{\s*margin:\s*0\s+0\s+var\(--sn-space-4\);/);
assert.match(css, /:is\(\.social-network-data-eyebrow,[\s\S]*?\.social-network-quality-report-eyebrow\)\s*\{\s*margin:\s*0\s+0\s+var\(--sn-space-1\);/);
assert.match(css, /#page-social-network-task\s+:is\(\.social-network-dataset-identity-head,[\s\S]*?\.social-network-quality-report-header\)\s*\{\s*align-items:\s*flex-start;/);
assert.match(css, /social-network-dataset-counts\s*\{\s*gap:\s*var\(--sn-space-3\);\s*margin-top:\s*var\(--sn-space-4\);/);
assert.match(css, /\.social-network-task-card :is\([\s\S]*?\.social-network-network-structure,[\s\S]*?\.social-network-topic-task\)\s*\{\s*gap:\s*var\(--sn-space-2\);/);
assert.match(css, /\.social-network-task-card :is\([\s\S]*?\.social-network-network-structure-pagerank\)\s*\{\s*gap:\s*var\(--sn-space-2\);\s*padding:\s*var\(--sn-space-2\);/);
assert.match(css, /social-network-dataset-switch\s*\{\s*margin-top:\s*var\(--sn-space-3\);/);
assert.match(css, /\.social-network-graph-workspace\s*\{\s*gap:\s*var\(--sn-space-3\);/);
assert.match(css, /\.social-network-graph-side-card\s*\{\s*padding:\s*var\(--sn-space-2\);/);
assert.match(css, /body\.social-network-shell-active #page-social-network-task\s+:is\([\s\S]*?\.social-network-relations-tabs a,[\s\S]*?\.social-network-data-tabs a,[\s\S]*?\.social-network-content-task-nav a,[\s\S]*?\)\s*\{\s*min-height:\s*36px;\s*padding:\s*7px 12px;[\s\S]*?border-radius:\s*var\(--social-control-radius\);/);
assert.match(css, /:is\(\.social-network-relations-tabs a,[\s\S]*?\.social-network-data-tabs a,[\s\S]*?\.social-network-content-task-nav a\)\s*\{\s*min-width:\s*var\(--social-tab-min-width\);\s*text-align:\s*center;/);
assert.match(css, /:is\(\.social-network-relations-tabs a\[aria-current="page"\],[\s\S]*?\.social-network-data-tabs a\[aria-current="page"\],[\s\S]*?\.social-network-content-task-nav a\[aria-current="page"\]\)\s*\{\s*border-color:\s*rgba\(200, 107, 67, 0\.38\);[\s\S]*?font-weight:\s*650;/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\.social-network-content-task-nav\):not\(\[hidden\]\)\s*\{\s*display:\s*flex;[\s\S]*?flex:\s*0 0 auto;[\s\S]*?gap:\s*var\(--sn-space-2\);/);
assert.match(css, /body\.social-network-shell-active:has\([\s\S]*?#social-network-relations-tabs:not\(\[hidden\]\),[\s\S]*?#social-network-data-tabs:not\(\[hidden\]\),[\s\S]*?#social-network-content-tabs:not\(\[hidden\]\)\)\s*\.social-network-task-heading\s*\{\s*display:\s*flex;[\s\S]*?align-items:\s*flex-end;[\s\S]*?gap:\s*var\(--sn-space-3\);/);
assert.doesNotMatch(css, /\.social-network-network-structure-categories/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?dialog\[class\*="social-network-"\]\)\s+:is\(h1, h2, h3, h4\)\s*\{\s*margin:\s*0;/);
assert.match(css, /body\.social-network-shell-active :is\([\s\S]*?\)\s+h2\s*\{\s*margin-bottom:\s*var\(--sn-space-1\);/);
assert.match(css, /body\.social-network-shell-active dialog\[class\*="social-network-"\]\[open\]\s*\{\s*position:\s*fixed;\s*inset:\s*50% auto auto 50%;\s*transform:\s*translate\(-50%, -50%\);\s*margin:\s*0;/);

console.log("social-network-ui22: 119 checks passed");
