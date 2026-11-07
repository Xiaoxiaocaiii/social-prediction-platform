"use strict";

const assert = require("node:assert/strict");
const model = require(
    "../src/main/resources/static/js/social-network-quality-model.js"
);

assert.deepEqual(
    model.categories().map(item => item.id),
    ["users", "relationships", "tweets", "interactions", "text", "other"]
);

const report = model.reportFromResponse({
    data: {
        datasetId: "active-dataset",
        version: "v3",
        name: "Active dataset",
        type: "upload",
        qualitySummary: {
            datasetStatus: "available_with_warnings",
            summary: {
                user_record_count: 5,
                relationship_candidate_count: 0,
                quality_issue_count: 9
            },
            userQuality: {
                normalized_user_count: 0,
                unknown_counter: 2
            },
            relationshipQuality: {
                normalized_relationship_count: 0
            },
            coverage: {
                user_id_coverage: 0,
                unknown_coverage: 0.5
            }
        }
    },
    warnings: [
        { code: "USER_A", affectedScope: "user", count: 2 },
        { code: "REL_A", affectedScope: "relationships", count: 0 },
        { code: "TWEET_A", affectedScope: "tweet", count: 3 },
        { code: "INT_A", affectedScope: "interaction", count: 1 },
        { code: "TEXT_A", affectedScope: "text", count: 4 },
        { code: "FILE_A", affectedScope: "file", count: 1 },
        { code: "UNKNOWN", affectedScope: "future_dimension", count: 1 },
        { code: "MISSING_SCOPE", count: 1 }
    ]
});

assert.equal(report.identity.datasetId, "active-dataset");
assert.equal(report.identity.datasetVersion, "v3");
assert.equal(report.identity.datasetName, "Active dataset");
assert.equal(report.quality.datasetStatus, "available_with_warnings");
assert.equal(report.warnings.length, 8);

assert.equal(
    model.metricEntries(report.quality, "users")
        .find(item => item.key === "normalized_user_count").label,
    "已识别用户"
);
assert.equal(
    model.metricEntries(report.quality, "users")
        .find(item => item.key === "normalized_user_count").value,
    0
);
assert.equal(
    model.metricEntries(report.quality, "users")
        .find(item => item.key === "unknown_counter").known,
    false
);
assert.equal(
    model.summaryEntries(report.quality)
        .find(item => item.key === "relationship_candidate_count").value,
    0
);
assert.equal(
    model.coverageEntries(report.quality)
        .find(item => item.key === "user_id_coverage").value,
    0
);
assert.equal(
    model.coverageEntries(report.quality)
        .find(item => item.key === "unknown_coverage").known,
    false
);

for (const [warning, dimension] of [
    [report.warnings[0], "users"],
    [report.warnings[1], "relationships"],
    [report.warnings[2], "tweets"],
    [report.warnings[3], "interactions"],
    [report.warnings[4], "text"],
    [report.warnings[5], "other"],
    [report.warnings[6], "other"],
    [report.warnings[7], "other"]
]) {
    assert.equal(model.warningDimension(warning), dimension);
}
assert.equal(model.warningsForDimension(report.warnings, "users").length, 1);
assert.equal(model.warningsForDimension(report.warnings, "other").length, 3);

const warningList = Array.from({ length: 23 }, (_, index) => ({
    code: `WARN_${index}`
}));
const firstPage = model.paginateWarnings(warningList, 1, 10);
assert.equal(firstPage.returnedCount, 23);
assert.equal(firstPage.displayedCount, 10);
assert.equal(firstPage.start, 1);
assert.equal(firstPage.end, 10);
assert.equal(firstPage.pageCount, 3);
assert.equal(model.paginateWarnings(warningList, 3, 10).displayedCount, 3);
assert.equal(model.paginateWarnings(warningList, 99, 10).page, 3);
assert.equal(model.paginateWarnings([], 1, 10).start, 0);
assert.equal(model.severityLabel("warning"), "警告");
assert.equal(model.statusLabel("available_with_warnings"), "可用（含质量警告）");

console.log("social-network-quality-model: checks passed");
