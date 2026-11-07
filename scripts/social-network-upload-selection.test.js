"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const scriptPath = path.join(
    root,
    "src/main/resources/static/js/social-network.js"
);
const htmlPath = path.join(root, "src/main/resources/static/index.html");
const source = fs.readFileSync(scriptPath, "utf8");
const html = fs.readFileSync(htmlPath, "utf8");
const blockStart = source.indexOf("    function setTaskUploadStatus(");
const blockEnd = source.indexOf(
    "\n    window.onSocialNetworkTaskPageShown",
    blockStart
);

assert.notEqual(blockStart, -1, "UI08 upload-selection renderer exists");
assert.notEqual(blockEnd, -1, "UI08 renderer has a bounded task-page entry");

const selectionBlock = source.slice(blockStart, blockEnd);
const taskIds = [
    "social-network-task-upload-content",
    ...Array.from(selectionBlock.matchAll(/\sid="([^"]+)"/g), match => match[1])
];
const legacyUploadIds = Array.from(
    html.matchAll(/\bid="(social-network-upload-[^"]+)"/g),
    match => match[1]
);

assert.ok(taskIds.every(id => id.startsWith("social-network-task-upload-")));
assert.equal(new Set(taskIds).size, taskIds.length, "UI08 IDs are unique");
assert.ok(
    taskIds.every(id => !legacyUploadIds.includes(id)),
    "UI08 controls do not reuse legacy upload IDs"
);
assert.match(selectionBlock, /type="file" multiple/);
assert.match(selectionBlock, /accept="\.csv,\.jsonl/);
assert.match(selectionBlock, /单文件 128 MiB、单次请求 160 MiB/);
assert.match(selectionBlock, /social-network-task-upload-submit/);
assert.match(selectionBlock, /social-network-task-upload-cancel/);
assert.match(selectionBlock, /social-network-task-upload-retry/);
assert.match(selectionBlock, /type="button" disabled/);
assert.match(selectionBlock, /获得任务编号后才能取消检查/);
assert.match(selectionBlock, /uploadState\.uploadDraft\.sourceDescription/);
assert.match(selectionBlock, /workspaceState\.replaceUploadFiles/);
assert.match(selectionBlock, /addEventListener\("click", submitUpload\)/);
assert.match(
    source.slice(blockEnd),
    /if \(task === "upload"\)[\s\S]*?renderTaskUploadSelection\(taskCard\)/
);

console.log("social-network-upload-selection: 16 checks passed");
