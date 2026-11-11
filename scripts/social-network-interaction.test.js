"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

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

const summary = functionBlock(
    "renderInteractionTaskSummary",
    "renderInteractionTaskTweets"
);
const tweets = functionBlock(
    "renderInteractionTaskTweets",
    "renderInteractionTaskUsers"
);
const users = functionBlock(
    "renderInteractionTaskUsers",
    "renderInteractionTaskTimeseries"
);
const timeseries = functionBlock(
    "renderInteractionTaskTimeseries",
    "updateInteractionTaskCategoryView"
);
const timeseriesRenderer = new Function(
    "document",
    "formatNumber",
    "escapeHtml",
    `${timeseries}; return renderInteractionTaskTimeseries;`
);
const categoryView = functionBlock(
    "updateInteractionTaskCategoryView",
    "updateInteractionTaskMetricButtons"
);
const metricButtons = functionBlock(
    "updateInteractionTaskMetricButtons",
    "renderInteractionTaskLoading"
);
const loading = functionBlock(
    "renderInteractionTaskLoading",
    "renderInteractionTaskCachedResult"
);
const cachedResult = functionBlock(
    "renderInteractionTaskCachedResult",
    "getInteractionTaskMetricCache"
);
const metricCache = functionBlock(
    "getInteractionTaskMetricCache",
    "loadInteractionTask"
);
const load = functionBlock("loadInteractionTask", "renderTopic");
const interactionView = functionBlock(
    "ensureInteractionTaskView",
    "renderInteractionTaskSummary"
);
const startPage = functionBlock("startInteractionTaskPage", "renderTopic");
const exportDialog = functionBlock("openModuleExportDialog", "downloadModuleExport");
const contextChanged = functionBlock(
    "handleSocialNetworkDatasetContextChanged",
    "setTaskUploadStatus"
);
const pageShownStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function (task) {"
);
const pageShownEnd = source.indexOf("\n    };", pageShownStart);
assert.ok(pageShownStart >= 0 && pageShownEnd > pageShownStart);
const pageShown = source.slice(pageShownStart, pageShownEnd);

assert.match(index, /social-network\.js\?v=20261108-global-graph-entry-1/);
assert.match(index, /style\.css\?v=20261111-ranking-bridge-spacing-1/);
assert.match(source, /interactionTaskResultCache/);
assert.doesNotMatch(interactionView, /social-network-content-task-nav/);
assert.match(pageShown, /interaction:\s*"INTERACTION ANALYSIS"/);
assert.match(pageShown, /topic:\s*"TOPIC ANALYSIS"/);
assert.match(interactionView,
    /INTERACTION RESULTS<\/p><h2>互动统计结果<\/h2>/);
assert.match(interactionView, /data-interaction-metric/);
assert.match(interactionView, /data-interaction-task-refresh/);
assert.match(interactionView, /data-interaction-task-export/);
assert.match(interactionView, /data-interaction-category="tweets"/);
assert.match(interactionView, /data-interaction-category="users"/);
assert.match(interactionView, /data-interaction-category="timeseries"/);
assert.match(interactionView, /aria-pressed="true">推文排行/);
assert.match(interactionView, /aria-pressed="false">用户排行/);
assert.match(interactionView, /aria-pressed="false">时间分布/);
assert.doesNotMatch(interactionView, /时间分布（UI19）/);
assert.match(interactionView, /setCondition\("interaction", \{ metric: metric \}\)/);
assert.match(interactionView, /category: button\.getAttribute\("data-interaction-category"\)/);
assert.match(index, /<nav id="social-network-content-tabs"[\s\S]*?href="#\/social-network\/content\/topic">主题分析<\/a>/);

assert.match(metricButtons, /aria-pressed/);
assert.match(metricButtons, /is-active/);
assert.match(categoryView, /aria-pressed/);
assert.match(categoryView, /data-interaction-task-results-title/);
assert.match(categoryView, /当前指标用户 Top 10/);
assert.match(categoryView, /用户总量不等于包含未归属样本的全局互动总量/);
assert.match(categoryView, /详情显示全数据集用户资料/);
assert.match(loading, /updateInteractionTaskMetricButtons/);
assert.match(loading, /updateInteractionTaskCategoryView/);
assert.match(loading, /role="status"/);
assert.match(loading, /cached && cached\.summary/);
assert.match(summary, /validCount > 0/);
assert.match(summary, /不可计算/);
assert.match(summary, /hasValidCount \? "不可计算" : "未提供"/);
assert.match(summary, /summary\.zeroCount/);
assert.match(summary, /summary\.missingCount/);
assert.match(tweets, /\.slice\(0, 10\)/);
assert.match(tweets, /data\.totalCount/);
assert.match(tweets, /data\.truncated/);
assert.match(tweets, /authorStatus === "linked"/);
assert.match(tweets, /window\.openSocialNetworkUserDetail/);
assert.match(tweets, /formatNumber\(index \+ 1\)/);
assert.doesNotMatch(tweets, /textOriginal|前往原平台|查看原文/);
assert.match(interactionView, /序号仅表示接口返回顺序，不是服务端名次/);
assert.match(interactionView, /此排行接口未提供推文原文/);

assert.match(users, /\.slice\(0, 10\)/);
assert.match(users, /item\.total/);
assert.match(users, /item\.average/);
assert.match(users, /item\.sampleCount/);
assert.match(users, /item\.median/);
assert.match(users, /<details class="social-network-interaction-user-median">/);
assert.match(users, /data-interaction-user-detail/);
assert.match(users, /window\.openSocialNetworkUserDetail/);
assert.match(users, /event\.currentTarget\.getAttribute\([\s\S]*?data-interaction-user-detail/);
assert.match(users, /null,\s*event\.currentTarget/);
assert.match(users, /当前指标没有可用的已关联用户样本/);
assert.match(timeseries, /data\.items/);
assert.match(timeseries, /leftPeriod < rightPeriod/);
assert.match(timeseries, /leftPeriod > rightPeriod/);
assert.match(timeseries, /sorted\.slice\(-30\)/);
assert.match(timeseries, /escapeHtml\(period\)/);
assert.match(timeseries, /item\.sampleCount/);
assert.match(timeseries, /max > 0 \? total \/ max \* 100 : 0/);
assert.match(timeseries, /当前指标有效样本的时间覆盖率/);
assert.match(timeseries, /当前指标没有可用的时间分组/);
const timeseriesResult = { innerHTML: "" };
const timeseriesCount = { textContent: "" };
const renderTimeseries = timeseriesRenderer(
    {
        querySelector(selector) {
            return selector === "[data-interaction-task-results]"
                ? timeseriesResult : timeseriesCount;
        }
    },
    function (value) {
        const number = Number(value);
        return Number.isFinite(number) ? number.toLocaleString("en-US") : "0";
    },
    function (value) {
        return String(value).replace(/[&<>"']/g, function (character) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                "\"": "&quot;",
                "'": "&#39;"
            }[character];
        });
    }
);
const longPeriod = "z-period-" + "long-label-".repeat(24);
const manyPeriods = Array.from({ length: 31 }, function (_, index) {
    return {
        period: "period-" + String(index).padStart(2, "0"),
        total: index * 3,
        sampleCount: index + 1
    };
}).reverse();
manyPeriods.push({
    period: longPeriod,
    total: 99,
    sampleCount: 7
});
renderTimeseries({
    data: {
        items: manyPeriods,
        totalCount: manyPeriods.length,
        missingDateCount: 2,
        state: "available"
    }
});
const renderedPeriods = Array.from(
    timeseriesResult.innerHTML.matchAll(
        /class="social-network-timeseries-period">([^<]*)<\/span>/g
    ),
    function (match) {
        return match[1];
    }
);
assert.equal(renderedPeriods.length, 30);
assert.equal(renderedPeriods[0], "period-02");
assert.equal(renderedPeriods[29], longPeriod);
assert.match(timeseriesCount.textContent, /本次显示 30 组 \/ 返回分组 32 组/);
assert.match(timeseriesResult.innerHTML, /7 个样本/);

renderTimeseries({
    data: {
        items: [
            { period: "zero-a", total: 0, sampleCount: 3 },
            { period: "zero-b", total: 0, sampleCount: 2 }
        ],
        totalCount: 2,
        missingDateCount: 0,
        state: "available"
    }
});
assert.equal(
    (timeseriesResult.innerHTML.match(/width:0\.00%/g) || []).length,
    2
);

renderTimeseries({
    data: {
        items: [],
        totalCount: 0,
        missingDateCount: 0,
        state: "empty"
    }
});
assert.match(timeseriesCount.textContent, /本次显示 0 组 \/ 返回分组 0 组/);
assert.match(timeseriesResult.innerHTML, /当前指标没有可用的时间分组/);
assert.doesNotMatch(
    timeseriesResult.innerHTML,
    /social-network-interaction-timeseries-row/
);
assert.match(cachedResult, /cached\[category\]/);
assert.match(cachedResult, /category === "users"/);
assert.match(cachedResult, /category === "timeseries"/);
assert.match(metricCache, /contextKey: contextKey/);
assert.match(metricCache, /summary: null,\s*tweets: null,\s*users: null,\s*timeseries: null/);

assert.match(load, /var query = \{ metric: metric, limit: 10 \}/);
assert.match(load, /categoryEndpoint = category === "timeseries"\s*\?\s*"timeseries"\s*:\s*category/);
assert.match(load, /categoryQuery = category === "timeseries"\s*\?\s*\{ metric: metric \}/);
assert.match(load, /interactionUrl\("summary", null\)/);
assert.match(load, /request\(interactionUrl\(categoryEndpoint, categoryQuery\)\)/);
assert.equal((load.match(/request\(interactionUrl\(/g) || []).length, 2);
assert.doesNotMatch(load, /interactionUrl\("tweets"|interactionUrl\("users"/);
assert.match(load, /Promise\.allSettled/);
assert.match(load, /requireCurrentTaskRequest/);
assert.match(load, /activeSocialNetworkTask !== "interaction"/);
assert.match(load, /interactionTaskIdentityPending/);
assert.match(load, /requestParameters = \{[\s\S]*metric: metric,[\s\S]*category: category,[\s\S]*datasetContext: context/);
assert.match(load, /workspaceState\.getCondition\("interaction"\)\.category !==\s*category/);
assert.match(load, /if \(!forceRefresh && metricCache\.summary && metricCache\[category\]\)/);
assert.match(load, /if \(!metricCache\.summary\)/);
assert.match(load, /if \(!metricCache\[category\]\)/);
assert.match(load, /metricCache\.users = null/);
assert.match(load, /datasetContextKey\(readDatasetContext\(\)\)/);
assert.match(startPage, /interactionTaskIdentityPending = true/);
assert.match(startPage, /loadShellDatasetVersion\(\)\.finally/);
assert.match(startPage, /loadInteractionTask\(taskCard, false\)/);

assert.match(pageShown, /task === "interaction"/);
assert.match(pageShown, /startInteractionTaskPage\(taskCard\)/);
assert.match(pageShown, /workspaceState\.invalidateRequest\("interaction"\)/);
assert.match(pageShown, /interactionTaskIdentitySequence \+= 1/);
assert.match(pageShown, /social-network-interaction-task"\);[\s\S]*?\.remove\(\)/);
assert.match(contextChanged, /activeSocialNetworkTask === "interaction"/);
assert.match(contextChanged, /interactionTaskResultCache = null/);
assert.match(contextChanged, /workspaceState\.invalidateRequest\("interaction"\)/);
assert.match(contextChanged, /startInteractionTaskPage\(interactionTaskCard\)/);

assert.match(exportDialog, /结果包不等同当前可见表格/);
assert.match(exportDialog, /且不包含推文原文/);
assert.match(css, /\.social-network-task-card\.is-interaction-analysis\s*\{\s*max-width: none;/);
assert.match(css, /\.social-network-interaction-task-table\s*\{\s*overflow-x: auto;/);
assert.match(css, /\.social-network-interaction-task-table\s+\.social-network-interaction-user-detail\s*\{[\s\S]*?border: 1px solid var\(--border-strong\);[\s\S]*?background: var\(--surface\);/);
assert.match(css, /\.social-network-interaction-task-table\s+\.social-network-interaction-user-detail:focus-visible/);
assert.match(css, /\.social-network-task-card\.is-interaction-analysis\s+\.social-network-interaction-summary/);
assert.match(css, /\.social-network-interaction-timeseries-row\s*\{[\s\S]*?minmax\(120px, 2fr\)/);
assert.match(css, /\.social-network-timeseries-period\s*\{[\s\S]*?overflow-wrap: anywhere/);

console.log("social-network-interaction: UI17/UI18/UI19 contract checks passed");
