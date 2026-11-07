"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const overviewModel = require(
    "../src/main/resources/static/js/social-network-overview-model.js"
);

let checks = 0;

function check(actual, expected, message) {
    assert.deepEqual(actual, expected, message);
    checks += 1;
}

const zeroCounts = overviewModel.fromResponse({
    data: {
        datasetId: "zero-sample",
        type: "builtin",
        counts: {
            normalized_user_count: 0,
            user_record_count: 626,
            relationship_candidate_count: 0,
            relationship_record_count: 626,
            tweet_record_count: 0
        }
    }
}, { datasetId: "zero-sample" });

check(zeroCounts.userCount, 0, "normalized zero must not fall back to raw users");
check(zeroCounts.relationshipCandidateCount, 0,
    "zero candidates must not fall back to a different relationship count");
check(zeroCounts.tweetRecordCount, 0, "tweet zero must remain visible");
check(zeroCounts.userCountBasis, "已规范化用户",
    "normalized count must retain its data basis");

const nestedNormalized = overviewModel.fromResponse({
    data: {
        counts: { user_record_count: 626 },
        qualitySummary: { userQuality: { normalized_user_count: 0 } }
    }
}, {});

check(nestedNormalized.userCount, 0,
    "nested normalized zero must take precedence over raw records");

const fallback = overviewModel.fromResponse({
    data: { counts: { user_record_count: 12 } }
}, {});
check(fallback.userCount, 12,
    "raw user records are used only when normalized count is absent");
check(fallback.userCountBasis, "用户记录；规范化用户数未提供",
    "fallback must explain its count basis");

const missing = overviewModel.fromResponse({ data: { counts: {} } }, {});
check(missing.userCount, null, "missing user count must remain missing");
check(missing.relationshipCandidateCount, null,
    "missing candidate count must remain missing");
check(missing.tweetRecordCount, null, "missing tweet count must remain missing");

const warning = overviewModel.fromResponse({
    data: {
        status: "available_with_warnings",
        counts: { quality_issue_count: 72, capability_impact_count: 2 }
    }
}, {});
check(warning.hasQualityNotice, true,
    "quality status and counts should produce a warning");
check(warning.qualityIssueCount, 72, "quality issue count must be preserved");
check(warning.capabilityImpactCount, 2,
    "capability impact count must be preserved");

const temporary = overviewModel.fromResponse({ data: {} }, {
    datasetId: "upload-session",
    sessionId: "session-1"
});
check(temporary.isTemporary, true, "upload session must be identified as temporary");

const overview = overviewModel.overviewFromResponses({
    data: {
        state: "available",
        nodeCount: 0,
        edgeCount: 0,
        isolatedNodeCount: 0,
        density: 0,
        weaklyConnectedComponentCount: 0,
        largestWeaklyConnectedComponentNodeCount: 0,
        largestWeaklyConnectedComponentRatio: 0
    }
}, {
    data: {
        state: "empty",
        scale: { analysisSampleCount: 0 },
        metrics: {
            like_count: { total: 0 },
            reply_count: { total: 0 },
            repost_count: { total: 0 }
        }
    }
}, {
    data: {
        state: "limited",
        summary: {
            quality_issue_count: 0,
            capability_impact_count: 2
        }
    }
});
check(overview.network.nodeCount, 0,
    "overview must preserve a real zero network count");
check(overview.network.density, 0,
    "overview must preserve a real zero network density");
check(overview.network.isolatedNodeCount, 0,
    "overview must preserve a real zero isolated-node count");
check(overview.interaction.sampleCount, 0,
    "overview must preserve a real zero interaction sample count");
check(overview.interaction.like.total, 0,
    "overview must preserve a real zero interaction total");
check(overview.quality.capabilityImpactCount, 2,
    "overview must include impacted analysis capabilities");

const unavailableOverview = overviewModel.overviewFromResponses(null, null, {
    data: {
        state: "limited",
        summary: { quality_issue_count: 1 }
    }
});
check(unavailableOverview.network, null,
    "failed network summary must remain unavailable");
check(unavailableOverview.interaction, null,
    "failed interaction summary must remain unavailable");
check(unavailableOverview.quality.issueCount, 1,
    "available quality response must render independently");

const html = fs.readFileSync(
    "src/main/resources/static/index.html",
    "utf8"
);
const css = fs.readFileSync(
    "src/main/resources/static/css/style.css",
    "utf8"
);
const overviewStart = html.indexOf('id="social-network-overview-home"');
const overviewEnd = html.indexOf(
    '<section class="social-network-task-card"',
    overviewStart
);
const overviewMarkup = html.slice(overviewStart, overviewEnd);
check((html.match(/id="social-network-overview-home"/g) || []).length,
    1, "overview home must have a unique ID");
check(overviewMarkup.includes("选择分析任务"), false,
    "overview must not repeat the task heading below its page title");
check(overviewMarkup.includes('aria-label="分析任务"'), true,
    "overview task links must retain an accessible section label");
check(/#social-network-overview-home:not\(\[hidden\]\)\)\s*\.social-network-task-heading\s*\{[^}]*margin-bottom:\s*0;/s.test(
    css
), true, "overview heading must not leave extra bottom spacing");
check(/\.social-network-overview-tasks\s*\{\s*margin-top:\s*var\(--sn-space-3\);/s.test(
    css
), true, "overview cards must keep a compact 12px heading gap");
check((overviewMarkup.match(/class="social-network-overview-task"/g) || []).length,
    3, "overview must contain exactly three task links");
check(overviewMarkup.includes(
    'class="social-network-overview-summary"'
), true, "overview must show live analysis summaries");
check(overviewMarkup.includes(
    'class="social-network-overview-readiness-grid"'
), false, "overview must not duplicate analysis readiness beside task links");
check(/class="social-network-overview-data"|class="social-network-overview-counts"|id="social-network-overview-(?:dataset-name|user-count|relationship-count|tweet-count)"/.test(
    overviewMarkup
), false, "overview must not duplicate the current dataset summary");
for (const href of [
    "#/social-network/data",
    "#/social-network/relations",
    "#/social-network/content"
]) {
    check(overviewMarkup.includes('href="' + href + '"'), true,
        "overview link must route to " + href);
}
check(/type="file"|<canvas|echarts/i.test(overviewMarkup), false,
    "overview must not render upload controls or charts");
const datasetStart = html.indexOf('id="social-network-dataset-page"');
const datasetEnd = html.indexOf(
    '<section class="social-network-task-card"',
    datasetStart
);
const datasetMarkup = html.slice(datasetStart, datasetEnd);
check(datasetMarkup.includes('class="social-network-dataset-identity"'), true,
    "dataset page must show the selected dataset identity");
check(datasetMarkup.includes('id="social-network-dataset-source-summary"'), true,
    "dataset page must own dataset source details");

const socialScript = fs.readFileSync(
    "src/main/resources/static/js/social-network.js",
    "utf8"
);
check(socialScript.includes('buildDatasetUrl("network/summary"'), true,
    "overview must request the live network summary");
check(socialScript.includes('buildDatasetUrl("interactions/summary"'), true,
    "overview must request the live interaction summary");
check(socialScript.includes('buildDatasetUrl("quality/summary"'), true,
    "overview must request current quality readiness");
check(socialScript.includes("sameDatasetContext(context, current)"), true,
    "overview must reject results from a stale dataset context");
check(html.indexOf("social-network-overview-model.js") <
    html.indexOf("social-network-dataset-model.js"), true,
    "shared dataset model dependency must load before the dataset model");

console.log("social-network-overview-model: " + checks + " checks passed");
