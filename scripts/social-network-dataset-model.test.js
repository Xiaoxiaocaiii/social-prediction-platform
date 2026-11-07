"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const datasetModel = require(
    "../src/main/resources/static/js/social-network-dataset-model.js"
);

let checks = 0;

function check(actual, expected, message) {
    assert.deepEqual(actual, expected, message);
    checks += 1;
}

const builtin = datasetModel.fromResponse({
    data: {
        datasetId: "think-tank-social-network",
        name: "智库社交网络数据",
        type: "builtin",
        version: "v1",
        status: "available_with_warnings",
        sourceDescription: "项目开发者整理并提供",
        timeRange: { from: "2016-05-03", to: "2026-01-21" },
        files: [{
            logicalFileName: "all_csv/users.csv",
            role: "USERS",
            format: "CSV",
            encoding: "utf8"
        }],
        counts: {
            file_count: 1,
            normalized_user_count: 0,
            user_record_count: 7,
            relationship_candidate_count: 0,
            tweet_record_count: 3
        }
    },
    meta: { datasetVersion: "v1" }
}, { datasetId: "think-tank-social-network" });

check(builtin.name, "智库社交网络数据", "use the server-provided name");
check(builtin.type, "内置数据集", "identify the built-in dataset");
check(builtin.version, "v1", "preserve the server dataset version");
check(builtin.sourceDescription, "项目开发者整理并提供",
    "show only the supplied source description");
check(builtin.timeRangeFrom, "2016-05-03", "preserve the supplied start date");
check(builtin.timeRangeTo, "2026-01-21", "preserve the supplied end date");
check(builtin.fileCount, 1, "preserve the explicit file count");
check(builtin.files[0], {
    logicalFileName: "all_csv/users.csv",
    role: "USERS",
    format: "CSV",
    encoding: "utf8"
}, "preserve actual file metadata");
check(builtin.userCount, 0, "keep a true zero user count");
check(builtin.relationshipCandidateCount, 0,
    "keep a true zero relationship count");
check(builtin.tweetRecordCount, 3, "preserve tweet record count");

const temporary = datasetModel.fromResponse({
    data: {
        datasetId: "server-dataset",
        name: "我的分析数据",
        type: "upload_session",
        version: "v9",
        timeRange: { from: "null", to: "not-a-date" },
        counts: {}
    }
}, { datasetId: "server-dataset", sessionId: "private-session-id" });

check(temporary.name, "我的分析数据",
    "use the server name instead of the session identifier");
check(temporary.type, "临时上传数据", "identify an upload session");
check(temporary.sessionId, "private-session-id",
    "keep the session identifier available for detail display");
check(temporary.timeRangeFrom, null, "treat string null as missing");
check(temporary.timeRangeTo, null, "reject invalid dates");
check(temporary.sourceDescription, null, "leave absent source information missing");
check(temporary.fileCount, null, "do not infer a missing file count");
check(temporary.files, null, "do not infer absent file details");
check(temporary.userCount, null, "leave absent counts missing");
check(datasetModel.validDate("2024-02-29"), "2024-02-29",
    "accept valid leap-day dates");
check(datasetModel.validDate("2023-02-29"), null,
    "reject impossible calendar dates");

const html = fs.readFileSync(
    "src/main/resources/static/index.html",
    "utf8"
);
const taskStart = html.indexOf('id="page-social-network-task"');
const taskEnd = html.indexOf(
    '<section id="page-social-network-graph"',
    taskStart
);
check(taskEnd > taskStart, true,
    "bound the task shell before the separate paused graph page");
const taskMarkup = html.slice(taskStart, taskEnd);
check(html.includes('id="page-social-network"'), false,
    "remove the unreachable legacy long page");
check(html.includes('id="page-social-network-graph"'), true,
    "retain the separate paused graph page");
check((html.match(/id="social-network-dataset-page"/g) || []).length,
    1, "render one dedicated current-data task");
check(taskMarkup.includes('href="#/social-network/data/upload"'), true,
    "route the upload action to the upload task");
check(html.includes('id="social-network-dataset-switch-dialog"'), true,
    "provide an explicit dataset-switch confirmation dialog");
check(html.includes('id="social-network-dataset-source-dialog"'), true,
    "provide an on-demand source details dialog");

const socialScript = fs.readFileSync(
    "src/main/resources/static/js/social-network.js",
    "utf8"
);
const pageLoaderStart = socialScript.indexOf(
    "function loadDatasetPage(scrollToReport)"
);
const pageLoaderEnd = socialScript.indexOf(
    "function initializeDatasetPageControls()",
    pageLoaderStart
);
const pageLoader = socialScript.slice(pageLoaderStart, pageLoaderEnd);
check(pageLoader.includes("loadShellDatasetSummary()"), true,
    "load the current-data view from the shared identity summary");
check(pageLoader.includes("refreshWorkspaceData()"), false,
    "do not load unrelated analysis modules on the current-data page");

console.log("social-network-dataset-model: " + checks + " checks passed");
