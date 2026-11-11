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

const view = functionBlock("userRelationsTaskMount", "handleSocialNetworkDatasetContextChanged");
const result = functionBlock("renderUserRelationsLocalResult", "loadUserRelationsLocal");
const load = functionBlock("loadUserRelationsLocal", "handleSocialNetworkDatasetContextChanged");
const graphOption = functionBlock("userRelationsGraphOption", "showUserRelationsGraphFallback");
const graphFallback = functionBlock("showUserRelationsGraphFallback", "mountUserRelationsLocalChart");
const chartMount = functionBlock("mountUserRelationsLocalChart", "renderUserRelationsLocalResult");
const chartDispose = functionBlock("disposeUserRelationsLocalChart", "userRelationsNodeIsExternal");
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

assert.match(routes, /"\/social-network\/relations\/user": "user-relations"/);
assert.ok(index.indexOf("social-network-ranking-model.js") <
    index.indexOf("js/social-network.js?v=20261108-global-graph-entry-1"));
assert.match(view, /data-user-relations-search>查找用户/);
assert.match(view, /空关键词不会列出全部用户/);
assert.match(view,
    /networkStructureTerm\(\s*"user-relations-neighbor-level",\s*"一阶 \/ 二阶邻居",[\s\S]*?直接关注或被关注[\s\S]*?不区分关注方向/);
assert.match(view,
    /networkStructureTerm\(\s*"user-relations-neighbor-limit",\s*"邻居上限"/);
assert.match(view, /bindNetworkStructureScopeTerms\(mount\)/);
assert.match(view,
    /data-user-relations-center-section[\s\S]*?data-user-relations-results[\s\S]*?data-user-relations-pagination><\/div><\/section><\/section>/);
assert.doesNotMatch(view, /当前条件与最近一次成功结果一致/);
assert.match(view, /draft\.hidden = !draft\.textContent/);
assert.match(view, /data-user-relations-category="graph"/);
assert.match(view, /data-user-relations-category="nodes"/);
assert.match(view, /data-user-relations-category="edges"/);
assert.doesNotMatch(view, /network\/graph/);
assert.match(view, /data-user-relations-chart-action="zoom-in"/);
assert.match(view, /data-user-relations-chart-action="zoom-out"/);
assert.match(view, /data-user-relations-chart-action="reset"/);
assert.match(view,
    /social-network-user-relations-result-toolbar[\s\S]*?social-network-user-relations-result-tabs[\s\S]*?social-network-user-relations-chart-toolbar/);
assert.match(view, /resultToolbar\.addEventListener\("click"/);
assert.match(result,
    /social-network-user-relations-chart-toolbar"\)\.hidden\s*=\s*userRelationsResultCategory !== "graph" \|\| !nodes\.length/);
assert.match(result, /mountUserRelationsLocalChart\(data, nodes, edges\)/);
assert.match(result, /本次查询没有显示关系/);
assert.match(result, /局部图展示本次响应中的全部边/);
assert.match(graphOption, /edgeSymbol: \["none", "arrow"\]/);
assert.match(graphOption, /reciprocal/);
assert.match(graphOption, /category: external \? 1 : 0/);
assert.match(graphOption, /isCenter/);
assert.match(chartMount, /echarts\.init/);
assert.match(view, /type: "graphRoam"/);
assert.doesNotMatch(chartMount, /data-user-relations-chart-action/);
assert.match(chartMount, /new ResizeObserver/);
assert.match(chartMount, /window\.addEventListener\(\s*"resize"/);
assert.match(graphFallback, /data-user-relations-graph-fallback/);
assert.match(chartDispose, /ResizeObserver\.disconnect/);
assert.match(chartDispose, /window\.removeEventListener\("resize"/);
assert.match(chartDispose, /userRelationsLocalChart\.dispose\(\)/);
assert.match(view, /function openUserRelationsAtCenter/);
assert.match(view, /function resetUserRelationsForDatasetChange/);
assert.match(source, /data-user-detail-open-relations/);
assert.match(source, /data-ranking-relationship-index/);
assert.match(view, /query: query,\s*limit: 20/);
assert.match(view, /\.trim\(\)/);
assert.match(view, /items\.slice\(0, 20\)/);
assert.match(result, /本次返回/);
assert.match(result,
    /networkStructureTerm\(\s*"user-relations-node",\s*"节点",[\s\S]*?代表一个账号/);
assert.match(result, /" 个节点"\s*\+\s*networkStructureTerm/);
assert.match(result,
    /networkStructureTerm\(\s*"user-relations-directed-edge",\s*"有向边",[\s\S]*?A → B 表示 A 关注 B/);
assert.match(result, /" 条有向边"\s*\+\s*networkStructureTerm/);
assert.match(result, /\.innerHTML\s*=\s*rangeNote/);
assert.doesNotMatch(result, /PageRank|Top-K/);
assert.match(result, /data\.truncated === true/);
assert.match(result, /limits\.maxNodes/);
assert.match(result, /data\.includeExternalNodes/);
assert.match(result, /不区分关注方向/);
assert.match(result, /edges\.slice\(0, 20\)/);
assert.match(result, /本次返回集合/);
assert.match(result, /data-user-relations-use-center/);
assert.match(load, /buildDatasetUrl\("network\/local"/);
assert.match(load, /includeExternalNodes: parameters\.includeExternal/);
assert.match(load, /requireCurrentTaskRequest/);
assert.match(load, /userRelationsRequestIsCurrent/);
assert.match(load, /draft\.hidden = false/);
assert.match(contextChanged, /resetUserRelationsForDatasetChange\(\)/);
assert.match(pageShown, /task === "user-relations"/);
assert.match(pageShown, /loadUserRelationsLocal\(userRelationsMount\)/);
assert.match(pageShown, /is-user-relations/);
assert.match(pageShown, /disposeUserRelationsLocalChart\(\)/);
assert.match(css, /\.social-network-task-card\.is-user-relations\s*\{\s*max-width: none;/);
assert.match(css, /\.social-network-user-relations-edges\s*\{/);
assert.match(css, /\.social-network-user-relations-chart\s*\{/);
assert.match(css, /\.social-network-user-relations-chart-legend\s*\{/);
assert.match(css,
    /\.social-network-user-relations-results\s*\{[\s\S]*?gap:\s*6px;[\s\S]*?padding:\s*10px 0 0;[\s\S]*?border-top:\s*1px solid var\(--border\);/);
assert.match(css,
    /social-network-user-relations-results > \.social-network-user-relations-scope-note[\s\S]*?margin:\s*0;/);
assert.match(css,
    /\.social-network-user-relations-center\s*\{[\s\S]*?gap:\s*8px;/);
assert.match(css,
    /\.social-network-user-relations-control-label\s*\{[\s\S]*?display:\s*flex;[\s\S]*?align-items:\s*center;/);
assert.match(css,
    /\.social-network-user-relations-results\[hidden\][\s\S]*?display:\s*none;/);
assert.match(css,
    /\.social-network-user-relations-result-toolbar\s*\{[\s\S]*?display:\s*flex;[\s\S]*?justify-content:\s*space-between;/);
assert.match(css,
    /\.social-network-user-relations-chart-toolbar\[hidden\]\s*\{\s*display:\s*none;/);
assert.match(css, /\.social-network-user-relations-graph-error\s*\{/);

console.log("social-network-user-relations: contract checks passed");
