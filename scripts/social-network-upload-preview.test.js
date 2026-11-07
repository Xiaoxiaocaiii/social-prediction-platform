"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const model = require("../src/main/resources/static/js/social-network-upload-preview-model.js");

const source = fs.readFileSync(
    path.join(__dirname, "../src/main/resources/static/js/social-network.js"),
    "utf8"
);

function functionBlock(name, nextName) {
    const startMatch = new RegExp(
        `^    (?:async )?function ${name}\\(`,
        "m"
    ).exec(source);
    assert.ok(startMatch, `${name} is defined`);
    const start = startMatch.index;
    const endMatch = new RegExp(
        `\\n    (?:async )?function ${nextName}\\(`,
        "g"
    );
    endMatch.lastIndex = start + startMatch[0].length;
    const nextMatch = endMatch.exec(source);
    assert.ok(nextMatch, `${name} has a bounded implementation`);
    return source.slice(start, nextMatch.index);
}

const result = functionBlock("renderTaskUploadResult", "renderTaskUploadState");
const issues = functionBlock("renderTaskUploadIssueList", "renderTaskUploadResultCategory");
const category = functionBlock(
    "renderTaskUploadResultCategory",
    "updateTaskUploadResultControls"
);
const controls = functionBlock(
    "updateTaskUploadResultControls",
    "renderTaskUploadResult"
);
const activate = functionBlock("activateUploadPreview", "editUploadSelection");
const edit = functionBlock("editUploadSelection", "switchToBuiltinDataset");
const state = functionBlock("renderTaskUploadState", "scheduleUploadTaskPoll");

assert.match(result, /uploadPreviewModel\.categories\(\)/);
assert.deepEqual(
    model.categories().map(item => item.id),
    ["files", "quality", "issues", "capabilities"]
);
assert.match(result, /view\.status === "failed"/);
assert.match(result, /不可激活/);
assert.match(result, /预览不会自动切换分析数据/);
assert.match(result, /showModal\(\)/);
assert.match(result, /预览将作废并关闭/);
assert.match(result, /保留/);
assert.match(issues, /issueSummary\(issues\)/);
assert.match(issues, /未提供服务端完整问题总数/);
assert.match(issues, /summary\.omittedCount/);
assert.match(issues, /summary\.limit/);
assert.match(issues, /项未展示/);
assert.match(category, /renderTaskUploadIssueList\(quality\.issues\)/);
assert.match(category, /metricEntries\(quality, selectedDimension\)/);
assert.match(category, /capabilityItems\(/);
assert.match(controls, /view\.status === "ready"/);
assert.match(controls, /view\.preview\.sessionId/);
assert.match(controls, /view\.preview\.datasetId/);
assert.match(controls, /view\.preview\.datasetVersion/);
assert.match(controls, /!== "unavailable"/);
assert.match(activate, /workspaceContextApi\.setDatasetContext\(next\)/);
assert.match(activate, /await loadShellDatasetSummary\(\)/);
assert.match(activate, /sameDatasetContext\(readDatasetContext\(\), next\)/);
assert.match(activate, /setDatasetContext\(previous\)/);
assert.match(activate, /预览仍保留，可重试/);
assert.match(activate, /activeUploadContext = next/);
assert.match(activate, /"#\/social-network\/data"/);
assert.match(activate, /旧临时会话未能立即关闭/);
assert.match(edit, /clearUploadTaskState\(\)/);
assert.match(edit, /closeUploadSession\(/);
assert.match(edit, /renderTaskUploadSelection\(taskCard\)/);
assert.match(state, /结果预览已就绪/);
assert.doesNotMatch(state, /预览激活将在 UI10 接入/);

console.log("social-network-upload-preview: contract checks passed");
