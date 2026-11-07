"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const source = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network.js"),
    "utf8"
);
const modelSource = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network-ranking-model.js"),
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
const routes = fs.readFileSync(
    path.join(root, "src/main/resources/static/js/social-network-routes.js"),
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

const view = functionBlock("ensureRankingTaskView", "rankingTaskIsCurrent");
assert.match(view,
    /metricControl\.addEventListener\("change"[\s\S]*?setCondition\("ranking",\s*\{\s*metric:\s*this\.value\s*\}\)[\s\S]*?loadRankingTask\(taskCard\)/);
assert.match(view,
    /data-ranking-task-view[\s\S]*?rankingTaskView = button\.getAttribute\([\s\S]*?loadRankingTask\(taskCard\)/);
const selection = functionBlock(
    "rankingTaskSelectionHtml",
    "ensureRankingTaskView"
);
const render = functionBlock(
    "renderRankingTaskResult",
    "rankingReasonLabel"
);
const crossRender = functionBlock(
    "renderRankingCrossMetricResult",
    "loadRankingTask"
);
const crossFailure = functionBlock(
    "renderCrossMetricFailure",
    "renderRankingCrossMetricResult"
);
const load = functionBlock("loadRankingTask", "handleSocialNetworkDatasetContextChanged");
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

assert.ok(index.indexOf("social-network-ranking-model.js") <
    index.indexOf("js/social-network.js?v=20261107-overview-heading-gap-1"));
assert.match(index,
    /social-network-ranking-model\.js\?v=20261107-ranking-readable-1/);
assert.match(index, /social-network-module-export-users-metric-field/);
assert.match(routes, /"\/social-network\/relations\/ranking": "ranking"/);
assert.match(source, /var socialNetworkRankingModel = window\.socialNetworkRankingModel/);
assert.match(modelSource, /id: "indegree"/);
assert.match(modelSource, /id: "followers_count"/);
assert.match(modelSource, /id: "tweet_count"/);
assert.match(modelSource, /id: "k_core"/);
assert.match(modelSource, /id: "betweenness"/);

assert.match(selection, /<optgroup label=/);
assert.match(selection, /socialNetworkRankingModel\.metrics\(\)/);
assert.match(view, /rankingTaskSelectionHtml\(selectedMetric\)/);
assert.match(view, /data-ranking-task-view="single"/);
assert.match(view, /data-ranking-task-view="comparison"/);
assert.match(view, /data-ranking-task-comparison-view hidden/);
assert.match(css,
    /\.social-network-cross-metric-view\[hidden\]\s*\{\s*display:\s*none\s*!important;/);
assert.match(css,
    /\.social-network-cross-metric-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\);/);
assert.match(css,
    /\.social-network-cross-metric-table\s*\{[\s\S]*?table-layout:\s*fixed;/);
assert.match(css,
    /\.social-network-cross-metric-table th,[\s\S]*?overflow-wrap:\s*anywhere;/);
assert.match(css,
    /\.social-network-node-ranking-table table\s*\{[\s\S]*?table-layout:\s*fixed;/);
assert.match(css,
    /\.social-network-node-ranking-table :is\(th, td\):nth-child\(3\)\s*\{[\s\S]*?width:\s*14%;[\s\S]*?text-align:\s*center;/);
assert.match(css,
    /\[data-ranking-top20-table\][\s\S]*?nth-child\(5\)[\s\S]*?width:\s*22%;/);
assert.match(css,
    /\[data-ranking-spearman-table\][\s\S]*?nth-child\(4\)[\s\S]*?width:\s*22%;/);
assert.match(css,
    /\.social-network-cross-metric-node-table th:first-child,[\s\S]*?width:\s*19%;/);
assert.match(css,
    /social-network-task-heading-copy,[\s\S]*?social-network-topic-task-header > div:first-child\)\s*\{[\s\S]*?gap:\s*6px;/);
assert.match(view, /RANKING RESULTS/);
assert.match(view, /<h2>排序结果<\/h2>/);
assert.doesNotMatch(view, /可查看账号资料，或从该账号继续查看它与其他账号的关系/);
assert.match(view, /整体名次有多接近/);
assert.match(view, /前列账号有多少相同/);
assert.match(view, /共同入选账号占两份名单合并后总人数的比例/);
assert.match(view, /role="region" aria-label="当前指标 Top 20 排行"/);
assert.match(render, /当前只展示前 20 项；上方说明的是完整计算范围/);
assert.match(view, /loadRankingTask\(taskCard\)/);
assert.match(source, /ranking: "NODE RANKING"/);
assert.match(source, /"user-relations": "USER RELATIONS"/);
assert.match(source, /groups: "GROUPS & COMMUNITIES"/);
assert.match(view, /openModuleExportDialog\(\s*"users"/);
assert.match(render, /item\.rank/);
assert.match(render, /result\.rows\.length/);
assert.match(render, /未采集/);
assert.match(render, /result\.nodeCount/);
assert.match(render, /data-ranking-relationship-index/);
assert.match(render, /unavailable/);
assert.doesNotMatch(render, /算法版本|数据快照|result\.algorithmVersion|item\.rawValue/);
assert.match(crossRender, /排序接近程度/);
assert.match(crossRender, /重合比例/);
assert.match(crossRender, /群体间连接位置/);
assert.match(view, /每项显示该角度的数值，下方小字是它在全部账号中的名次/);
assert.doesNotMatch(crossRender, /（数值 \/ 排名）/);
assert.match(crossFailure, /crossMetricReasonLabel\(message\)/);
assert.match(crossFailure,
    /"暂时无法比较不同角度：" \+\s*evidenceMessage/);
assert.doesNotMatch(crossFailure, /escapeHtml\(message\)/);
assert.doesNotMatch(
    crossRender,
    /Spearman|Jaccard|阈值 A|正分账号|算法版本|数据快照|名单规模（A \/ B）/
);
assert.match(load, /buildDatasetUrl\("rankings",\s*\{\s*metric:\s*metric,\s*limit:\s*20/);
assert.match(load, /var lookup = rankingTaskOrdinaryLookup/);
assert.match(load, /buildDatasetUrl\(\s*"network\/structure-roles"/);
assert.match(load, /fromRankingResponse\(result, metric\)/);
assert.match(load, /fromStructureRolesResponse/);
assert.match(load, /fromCrossMetricResponse/);
assert.match(load, /requestView === "comparison"/);
assert.match(load, /rankingTaskIsCurrent/);
assert.match(load, /rankingTaskRolesCache/);
assert.doesNotMatch(render, /计算时间|snapshot\.snapshotCalculatedAt/);
assert.match(modelSource, /function fromCrossMetricResponse/);
assert.match(modelSource, /spearmanPairs\.slice\(0, 10\)/);
assert.match(modelSource, /top20Comparisons\.slice\(0, 10\)/);
assert.match(contextChanged, /activeSocialNetworkTask === "ranking"\)\s*\{\s*loadRankingTask\(\)/);
assert.match(pageShown, /task === "ranking"/);
assert.match(pageShown, /loadRankingTask\(taskCard\)/);
assert.match(pageShown, /is-node-ranking/);
assert.match(pageShown, /rankingTaskMountElement\.remove\(\)/);

assert.match(exportDialog, /social-network-module-export-users-metric-field/);
assert.match(exportDialog, /isOrdinaryExportMetric/);
assert.match(exportDialog, /query = \{ metric: usersMetricControl\.value \}/);
assert.match(exportDialog, /请选择入度、出度或 PageRank/);
assert.match(exportDialog, /两类结构角色排行/);
assert.doesNotMatch(exportDialog, /social-network-central-export-users-metric/);
assert.match(css, /\.social-network-task-card\.is-node-ranking\s*\{\s*max-width: none;/);
assert.match(css, /\.social-network-node-ranking-table\s*\{\s*min-width: 0;\s*overflow-x: auto;/);

console.log("social-network-ranking: contract checks passed");
