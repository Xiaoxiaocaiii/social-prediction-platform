"use strict";

const assert = require("node:assert/strict");
const model = require("../src/main/resources/static/js/social-network-upload-preview-model.js");

assert.deepEqual(
    model.categories().map(item => item.id),
    ["files", "quality", "issues", "capabilities"]
);
assert.deepEqual(
    model.dimensions().map(item => item.id),
    [
        "summary",
        "userQuality",
        "relationshipQuality",
        "tweetQuality",
        "interactionQuality",
        "textQuality"
    ]
);

const issues = Array.from({ length: 42 }, (_, index) => ({ code: `Q${index}` }));
const issueSummary = model.issueSummary(issues);
assert.equal(issueSummary.returnedCount, 42);
assert.equal(issueSummary.displayedCount, 40);
assert.equal(issueSummary.omittedCount, 2);
assert.equal(issueSummary.displayed[39].code, "Q39");
assert.equal(model.issueSummary([]).omittedCount, 0);

const files = model.fileResults({
    files: [{
        fileName: "users.csv",
        role: "USERS",
        format: "CSV",
        rowCount: 0,
        readOkCount: 0,
        readSkippedCount: 0,
        readErrorCount: 0,
        qualityStatus: "available"
    }],
    qualitySummary: {
        fileReports: [{
            logicalFileName: "users.csv",
            encoding: "UTF-8",
            headerStatus: "VALID",
            readWarningCount: 0
        }]
    }
});
assert.equal(files[0].role, "用户");
assert.equal(files[0].encoding, "UTF-8");
assert.equal(files[0].headerStatus, "字段检查通过");
assert.equal(files[0].rowCount, 0);
assert.equal(model.fileResults({ files: [], qualitySummary: {} }).length, 0);

const metrics = model.metricEntries({
    userQuality: { normalized_user_count: 0, future_metric: 3 }
}, "userQuality");
assert.equal(metrics[0].label, "规范化用户数");
assert.equal(metrics[0].value, 0);
assert.equal(metrics[1].label, "future metric");

const capabilities = model.capabilityItems({
    items: [{
        capabilityId: "important-nodes",
        moduleId: "B04",
        status: "LIMITED",
        reasonMessage: "用户资料缺失"
    }]
});
assert.equal(capabilities[0].label, "节点排行");
assert.equal(capabilities[0].statusLabel, "受限");
assert.equal(model.datasetStatusLabel("UNAVAILABLE"), "不可用");
assert.equal(model.severityLabel("WARNING"), "警告");

console.log("social-network-upload-preview-model: 20 checks passed");
