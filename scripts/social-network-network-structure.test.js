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

const render = functionBlock(
    "renderNetworkStructureView",
    "networkStructureMount"
);
const scope = functionBlock(
    "renderNetworkStructureScope",
    "bindNetworkStructureScopeTerms"
);
const scopeTerm = functionBlock(
    "networkStructureTerm",
    "renderNetworkStructureScope"
);
const bindScopeTerms = functionBlock(
    "bindNetworkStructureScopeTerms",
    "renderKCoreChart"
);
const coreChart = functionBlock(
    "renderKCoreChart",
    "renderKCoreNodeEvidence"
);
const coreNodes = functionBlock(
    "renderKCoreNodeEvidence",
    "renderNetworkStructureEvidence"
);
const coreView = functionBlock(
    "renderNetworkStructureEvidence",
    "bindNetworkStructureEvidence"
);
const bindCoreView = functionBlock(
    "bindNetworkStructureEvidence",
    "renderNetworkStructureView"
);
const structureRender = functionBlock(
    "renderNetworkStructureView",
    "networkStructureMount"
);
const load = functionBlock(
    "loadNetworkStructure",
    "handleSocialNetworkDatasetContextChanged"
);
const contextChanged = functionBlock(
    "handleSocialNetworkDatasetContextChanged",
    "setTaskUploadStatus"
);
const pageShownStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function (task) {"
);
const pageShownEnd = source.indexOf(
    "\n    };",
    pageShownStart
);
assert.ok(pageShownStart >= 0 && pageShownEnd > pageShownStart);
const pageShown = source.slice(pageShownStart, pageShownEnd);
const exportDialog = functionBlock(
    "openModuleExportDialog",
    "downloadModuleExport"
);
const exportDownload = functionBlock("downloadModuleExport", "firstPresent");

assert.ok(index.indexOf("social-network-network-structure-model.js") <
    index.indexOf("js/social-network.js?v=20261107-overview-heading-gap-1"));
assert.match(index,
    /css\/style\.css\?v=20261107-overview-heading-gap-1/);
assert.match(index,
    /social-network-network-structure-model\.js\?v=20261106-network-structure-4/);
assert.match(source,
    /var networkStructureModel = window\.socialNetworkNetworkStructureModel/);
assert.match(pageShown, /if \(task === "relations"\)/);
assert.match(pageShown, /loadNetworkStructure\(taskCard\)/);
assert.match(pageShown,
    /taskCard\.classList\.toggle\(\s*"is-network-structure",\s*task === "relations"\s*\)/);
assert.match(pageShown, /networkStructureRequestSequence \+= 1/);
assert.match(pageShown, /networkStructureMountElement\.remove\(\)/);

assert.match(load, /Promise\.all\(\[/);
assert.match(load, /buildDatasetUrl\("network\/summary", null, context\)/);
assert.match(load, /buildDatasetUrl\("quality\/summary", null, context\)/);
assert.doesNotMatch(load, /network\/evidence\/summary/);
assert.match(load, /request\([\s\S]*quality\/summary[\s\S]*\.catch\(function \(\) \{\s*return null;/);
assert.match(load,
    /buildDatasetUrl\("network\/structure-roles",\s*\{\s*limit: 1\s*\},\s*context\s*\)/);
assert.match(load, /fromResponse\([\s\S]*body,[\s\S]*qualityBody,[\s\S]*structureBody\s*\)/);
assert.match(load, /activeSocialNetworkTask !== "relations"/);
assert.match(load, /sequence !== networkStructureRequestSequence/);
assert.match(load, /sameDatasetContext\(context, current\)/);
assert.match(load,
    /networkStructureModel\.fromResponse\([\s\S]*body,[\s\S]*qualityBody,[\s\S]*structureBody\s*\)/);
assert.match(contextChanged, /activeSocialNetworkTask === "relations"\)\s*\{\s*loadNetworkStructure\(\)/);

assert.match(render, /networkStructureModel\.categories\(\)/);
assert.match(render, /aria-pressed/);
assert.match(render, /networkStructureModel\.metricEntries/);
assert.match(render, /showModal\(\)/);
assert.match(render, /pageRankState/);
assert.match(render, /不等同于社会影响力/);
assert.match(render,
    /networkStructureTerm\(\s*"pagerank-status",\s*"PageRank（网络位置估算）"/);
assert.match(render, /aria-label="网络位置估算状态"/);
assert.doesNotMatch(render, /interactionScale/);
assert.match(render, /openModuleExportDialog\("network", exportButton\)/);
assert.match(render, /完整规范化网络/);
assert.match(render, /NETWORK SCOPE/);
assert.doesNotMatch(render, /当前分析范围/);
assert.match(render, /并列最大弱连通分量/);
assert.match(render, /查看指标口径/);
assert.match(render, /role="note"/);
assert.match(render, /PageRank 未收敛/);
assert.match(source, /当前数据没有有效网络节点/);
assert.match(source, /当前有有效节点，但没有有效关系/);
assert.match(structureRender,
    /networkStructureModel\.categories\(\)\.reduce\([\s\S]*?metricEntries\(summary, category\.id\)/);
assert.match(structureRender,
    /networkStructureTerm\(\s*"metric-" \+ entry\.key,\s*entry\.label,\s*entry\.meaning/);
assert.doesNotMatch(structureRender,
    /networkStructureCategory|data-network-structure-category|selectedCategory/);
assert.doesNotMatch(structureRender, /网络结构指标分类/);
assert.match(structureRender, /data-network-structure-view="overview"/);
assert.match(structureRender, /data-network-structure-view="evidence"/);
assert.ok(
    structureRender.indexOf("aria-label=\"完整网络范围计数\"") <
    structureRender.indexOf("renderNetworkStructureScope(summary, context, includeExternal)") &&
    structureRender.indexOf("renderNetworkStructureScope(summary, context, includeExternal)") <
    structureRender.indexOf("            viewToggle +"),
    "shared network scope is rendered once before the view toggle"
);
assert.match(structureRender, /renderNetworkStructureEvidence\(summary\)/);
assert.match(css,
    /\.social-network-scope-term-tooltip\s*\{[\s\S]*?background:\s*var\(--primary-weak\);[\s\S]*?border-left:\s*3px solid var\(--primary\);[\s\S]*?font-size:\s*14px;/);
assert.match(css,
    /\.social-network-scope-term-tooltip strong\s*\{[\s\S]*?color:\s*var\(--primary-strong\);[\s\S]*?font-size:\s*inherit;[\s\S]*?font-weight:\s*700;/);
assert.match(css,
    /\.social-network-scope-term-tooltip > span\s*\{[\s\S]*?color:\s*var\(--text\);[\s\S]*?font-size:\s*inherit;[\s\S]*?font-weight:\s*400;/);
assert.match(exportDialog, /usersMetricField\.hidden = true/);
assert.match(css,
    /\.social-network-network-structure-pagerank > div > strong\s*\{/);
assert.match(css,
    /\.social-network-module-export-users-metric\[hidden\]\s*\{\s*display:\s*none;/);
assert.match(css,
    /\.social-network-network-structure-dialog\s*\{[\s\S]*?position:\s*fixed;[\s\S]*?inset:\s*50% auto auto 50%;[\s\S]*?transform:\s*translate\(-50%, -50%\);[\s\S]*?overflow:\s*auto;/);
assert.match(structureRender,
    /network-structure-dialog-title" tabindex="-1"/);
assert.match(structureRender,
    /dialogTitle\.focus\(\{\s*preventScroll:\s*true\s*\}\)/);
assert.match(structureRender, /dialog\.scrollTop = 0/);

assert.match(scope, /<details class="social-network-network-structure-details">/);
assert.doesNotMatch(scope, /<details[^>]*\sopen(?:\s|>)/);
assert.match(scope, /有效账号资料/);
assert.match(scope, /有效关系中出现的资料外账号/);
assert.match(scope, /没有关注关系的有效资料账号也保留/);
assert.match(scope, /箭头 A → B 表示 A 关注 B/);
assert.match(scope, /重复关系合并/);
assert.match(scope, /自关注和无效关系不进入有效关系集合/);
assert.match(scope, /聚类、路径、核心层级和介数会忽略方向/);
assert.match(scope, /summary\.normalizationVersion/);
assert.match(scope, /summary\.metricVersion/);
assert.match(scope, /summary\.pagerankVersion/);
assert.match(scope, /networkStructureModel\.qualityImpactItems/);
assert.match(scope, /查看完整数据质量报告/);
assert.match(scope, /推文、互动和文本质量提示不用于解释本页网络指标/);
assert.match(scope, /includeExternal/);

assert.match(bindScopeTerms, /aria-expanded/);
assert.match(bindScopeTerms, /event\.key !== "Escape"/);
assert.match(bindScopeTerms, /openTerm\.focus\(\)/);
assert.match(scopeTerm, /aria-describedby=/);
assert.match(scopeTerm, /role="tooltip"/);
assert.match(source, /function positionNetworkStructureTooltip/);
assert.match(source, /repositionVisibleNetworkStructureTooltips/);

assert.match(coreChart, /row\.ratio/);
assert.match(coreChart, /row\.count/);
assert.match(coreChart, /row\.sampleSize/);
assert.match(coreChart, /<title>/);
assert.match(coreChart, /data-kcore-chart-bar/);
assert.match(coreChart, /preserveAspectRatio="none"/);
assert.match(coreChart, /style="min-width:/);
assert.doesNotMatch(coreChart, /style="width:/);
assert.match(coreChart, /aria-describedby="\s*'\s*\+\s*tooltipId/);
assert.match(coreView, /kCoreLoadState !== "available"/);
assert.doesNotMatch(coreView, /renderNetworkEvidenceSummary/);
assert.doesNotMatch(source, /全网事实摘要|查看\s*['"]?\s*\+?\s*formatNumber\(visibleFacts\.length\)/);
assert.match(structureRender, /renderNetworkStructureConclusion\(summary\)/);
assert.match(structureRender, /节点构成/);
assert.match(structureRender, /查看计算详情/);
assert.doesNotMatch(source, /networkEvidenceValueDefinition|networkEvidenceReferenceLabel/);
assert.match(coreView, /排行前 20 名估算全体分布/);
assert.match(coreView, /包含 core=0/);
assert.match(coreView, /core≥k/);
assert.match(coreView, /完整总体/);
assert.match(coreView, /最高 core number/);
assert.match(coreView, /最高层账号数/);
assert.match(coreView, /formatNumber\(distribution\.sampleSize\)/);
assert.match(coreView, /data-kcore-mode="exact"/);
assert.match(coreView, /data-kcore-mode="cumulative"/);
assert.match(coreView, /data-kcore-select=/);
assert.match(coreNodes, /data-kcore-search-form/);
assert.match(coreNodes, /data-kcore-page="previous"/);
assert.match(coreNodes, /data-kcore-page="next"/);
assert.match(coreNodes, /data-kcore-user-detail=/);
assert.match(coreNodes, /data-kcore-close/);
assert.match(coreNodes, /networkStructureModel\.kCoreEvidenceNodes/);
assert.match(bindCoreView, /openSocialNetworkUserDetail/);
assert.match(bindCoreView, /currentNetworkStructure = null/);
assert.match(bindCoreView, /addEventListener\("mouseover"/);
assert.match(bindCoreView, /addEventListener\("focusin"/);
assert.match(bindCoreView, /data-kcore-close/);
assert.match(bindCoreView, /networkStructureSelectedCore === selectedCore\s*\?\s*null/);

assert.match(exportDialog, /网络结构摘要/);
assert.match(exportDialog, /及互动摘要/);
assert.match(exportDialog, /不包含关系图节点或边明细/);
assert.doesNotMatch(exportDialog, /submit\.disabled = exportType === "network"/);
assert.doesNotMatch(exportDownload, /snapshot\.exportType === "network"\)\s*\{\s*return;/);
assert.match(exportDownload, /hasDetailedNetworkResponse/);
assert.match(exportDownload, /sourceuserid/);
assert.match(exportDownload, /已阻止下载/);
assert.match(css, /\.social-network-task-card\.is-network-structure\s*\{\s*max-width: none;/);
assert.doesNotMatch(css, /social-network-network-evidence-/);
assert.match(css,
    /\.social-network-scope-term-tooltip strong\s*\{[\s\S]*?font-size:\s*inherit;[\s\S]*?font-weight:\s*600;/);
assert.match(css,
    /\.social-network-network-structure-metric-heading\s*\{[\s\S]*?justify-content:\s*space-between/);
assert.match(css,
    /body\.social-network-shell-active \.social-network-task-card :is\([\s\S]*?\.social-network-network-structure,[\s\S]*?\)\s*\{\s*gap:\s*var\(--sn-space-2\);/);
assert.match(css,
    /body\.social-network-shell-active \.social-network-task-card\s+\.social-network-network-structure-scope-note,[\s\S]*?\.social-network-network-structure-version\s*\{\s*margin:\s*0;/);
assert.doesNotMatch(css, /\.social-network-network-structure-metric strong/);
assert.match(css, /\.social-network-network-structure-metric > strong/);
assert.match(css,
    /\.social-network-network-structure-details > summary::after/);
assert.match(css,
    /\.social-network-network-structure-details > summary:focus-visible/);
assert.match(css,
    /\.social-network-scope-term-trigger:focus-visible/);
assert.match(css,
    /\.social-network-scope-term-trigger:hover \+ \.social-network-scope-term-tooltip/);
assert.match(css,
    /\.social-network-scope-term-tooltip\s*\{[\s\S]*?position:\s*fixed/);
assert.match(css,
    /\.social-network-scope-term-tooltip\s*\{[\s\S]*?width:\s*fit-content;[\s\S]*?border-left:\s*3px solid var\(--primary\);[\s\S]*?color:\s*var\(--text\);[\s\S]*?background:\s*var\(--primary-weak\)/);
assert.match(css, /\.social-network-network-structure-core-bar\s*\{/);
assert.match(css,
    /\.social-network-network-structure-core-chart\s*\{[^}]*width:\s*100%;/);
assert.match(css,
    /\.social-network-network-structure-core-table\s*\{[^}]*table-layout:\s*fixed;/);
assert.match(css,
    /\.social-network-network-structure-core-table :is\(th, td\)\s*\{[^}]*width:\s*25%;[^}]*text-align:\s*center;/);
assert.match(css,
    /\.social-network-network-structure-core-bar-hit-area\s*\{[\s\S]*?pointer-events:\s*all/);
assert.match(css,
    /\.social-network-network-structure-core-chart-tooltip\s*\{[\s\S]*?position:\s*fixed/);
assert.match(css,
    /\.social-network-network-structure-core-chart-tooltip\s*\{[\s\S]*?width:\s*fit-content;[\s\S]*?border-left:\s*3px solid var\(--primary\);[\s\S]*?color:\s*var\(--text\);[\s\S]*?background:\s*var\(--primary-weak\)/);
assert.match(css,
    /\.social-network-network-structure-core-table\s*\{/);
assert.match(css,
    /\.social-network-network-structure-core-node-table\s*\{/);
assert.match(css,
    /\.social-network-network-structure-core-pagination\s*\{/);
assert.match(index, /style\.css\?v=20261107-overview-heading-gap-1/);
assert.match(index,
    /social-network-network-structure-model\.js\?v=20261106-network-structure-4/);
assert.match(index, /social-network\.js\?v=20261107-overview-heading-gap-1/);

console.log("social-network-network-structure: contract checks passed");
