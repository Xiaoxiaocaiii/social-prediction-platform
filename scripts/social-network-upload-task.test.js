"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

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

const submit = functionBlock("submitUpload", "cancelUploadTask");
const cancel = functionBlock("cancelUploadTask", "clearUploadTaskState");
const poll = functionBlock("pollUploadTask", "submitUpload");
const renderUploadTask = functionBlock("renderUploadTask", "renderTaskUploadIssueList");
const taskRenderer = functionBlock("renderTaskUploadState", "scheduleUploadTaskPoll");
const controls = functionBlock("updateTaskUploadControls", "uploadCheckIsActive");
const readPersistedTaskId = functionBlock(
    "readPersistedUploadTaskId",
    "persistUploadTaskId"
);
const persistTaskId = functionBlock(
    "persistUploadTaskId",
    "restoreUploadTaskState"
);
const restoreTaskState = functionBlock(
    "restoreUploadTaskState",
    "uploadTaskIsBusy"
);
const clearTaskState = functionBlock("clearUploadTaskState", "closeUploadSession");
const pageHandlerStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function"
);
const pageHandler = source.slice(
    pageHandlerStart,
    source.indexOf("\n    };", pageHandlerStart)
);

assert.match(submit, /uploadSelectionIssue\(\)/);
assert.match(submit, /fetch\("\/api\/social-network\/datasets\/upload"/);
assert.match(submit, /method: "POST"/);
assert.match(submit, /formData\.append\("manifest"/);
assert.match(submit, /formData\.append\(entry\.partId, entry\.file/);
assert.match(submit, /uploadState\.uploadTaskId = body\.data\.uploadTaskId/);
assert.match(submit, /scheduleUploadTaskPoll\(350\)/);
assert.match(source, /UPLOAD_TASK_STORAGE_KEY = "social-network\.upload-task-id\.v1"/);
assert.match(source, /restoreUploadTaskState\(\);/);
assert.match(renderUploadTask, /persistUploadTaskId\(/);
assert.match(poll, /persistUploadTaskId\(null\)/);
assert.match(clearTaskState, /persistUploadTaskId\(null\)/);

assert.match(poll, /upload-tasks\//);
assert.match(poll, /isLatestRequest\(requestToken\)/);
assert.doesNotMatch(poll, /isRequestCurrent\(requestToken/);
assert.match(poll, /taskUploadStatusIsActive\(body\.data\.status\)/);
assert.match(poll, /error\.status === 404 \|\| error\.status === 410/);
assert.doesNotMatch(poll, /scheduleUploadTaskPoll\(1800\)/);
assert.match(poll, /uploadPollFailure = \{/);
assert.match(poll, /uploadPollRetryCount < 3/);
assert.match(poll, /scheduleUploadTaskPoll\(700 \* uploadPollRetryCount\)/);

assert.match(cancel, /if \(!taskId \|\| !taskView/);
assert.match(cancel, /method: "DELETE"/);
assert.match(cancel, /taskView\.status === "cancelling"/);
assert.match(cancel, /任务状态仍以服务端查询结果为准/);

assert.match(controls, /social-network-task-upload-cancel/);
assert.match(controls, /!uploadState\.uploadTaskId/);
assert.match(controls, /social-network-task-upload-retry/);
assert.match(taskRenderer, /结果预览已就绪/);
assert.doesNotMatch(taskRenderer, /renderPreview|activateUploadPreview/);
assert.match(pageHandler, /scheduleUploadTaskPoll\(0\)/);
assert.doesNotMatch(pageHandler, /submitUpload\(/);

const storage = new Map([["task-key", " task-1 "]]);
const uploadState = {};
const persistence = new Function(
    "window",
    "UPLOAD_TASK_STORAGE_KEY",
    "uploadState",
    `${readPersistedTaskId}\n${persistTaskId}\n${restoreTaskState}\n` +
        "return { readPersistedUploadTaskId, persistUploadTaskId, " +
        "restoreUploadTaskState };"
)({
    sessionStorage: {
        getItem: (key) => storage.get(key) || null,
        setItem: (key, value) => storage.set(key, value),
        removeItem: (key) => storage.delete(key)
    }
}, "task-key", uploadState);

assert.equal(persistence.readPersistedUploadTaskId(), "task-1");
persistence.restoreUploadTaskState();
assert.equal(uploadState.uploadTaskId, "task-1");
assert.equal(uploadState.uploadTaskView.status, "queued");
persistence.persistUploadTaskId(" task-2 ");
assert.equal(storage.get("task-key"), "task-2");
persistence.persistUploadTaskId(null);
assert.equal(storage.has("task-key"), false);

const unavailablePersistence = new Function(
    "window",
    "UPLOAD_TASK_STORAGE_KEY",
    "uploadState",
    `${readPersistedTaskId}\n${persistTaskId}\n${restoreTaskState}\n` +
        "return { readPersistedUploadTaskId, persistUploadTaskId, " +
        "restoreUploadTaskState };"
)({
    sessionStorage: {
        getItem: () => { throw new Error("storage blocked"); },
        setItem: () => { throw new Error("storage blocked"); },
        removeItem: () => { throw new Error("storage blocked"); }
    }
}, "task-key", {});
assert.equal(unavailablePersistence.readPersistedUploadTaskId(), null);
unavailablePersistence.persistUploadTaskId("task-3");
unavailablePersistence.restoreUploadTaskState();

console.log("social-network-upload-task: recovery checks passed");
