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
        `^    function ${name}\\(`,
        "m"
    );
    const startMatch = startPattern.exec(source);
    assert.ok(startMatch, `${name} is defined`);
    const endPattern = new RegExp(
        `\\n    function ${nextName}\\(`,
        "g"
    );
    endPattern.lastIndex = startMatch.index + startMatch[0].length;
    const endMatch = endPattern.exec(source);
    assert.ok(endMatch, `${name} has a bounded implementation`);
    return source.slice(startMatch.index, endMatch.index);
}

const warningList = functionBlock(
    "renderQualityWarningList",
    "renderQualityReportView"
);
const view = functionBlock("renderQualityReportView", "qualityReportMount");
const summary = functionBlock(
    "renderQualityReportSummaryEntries",
    "renderQualityReportCoverage"
);
const dataLoad = functionBlock("loadDatasetPage", "renderDatasetSourceDialog");
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

assert.match(source, /var socialNetworkQualityModel = window\.socialNetworkQualityModel/);
assert.ok(index.indexOf("social-network-quality-model.js") <
    index.indexOf("js/social-network.js?v=20261107-overview-heading-gap-1"));
assert.match(index,
    /id="social-network-quality-report-export"[\s\S]*?data-social-network-quality-export hidden/);
assert.match(index,
    /id="social-network-overview-retry"[\s\S]*?hidden/);
assert.match(index,
    /class="social-network-task-heading-actions"[\s\S]*?id="social-network-quality-report-export"[\s\S]*?id="social-network-overview-retry"/);
assert.match(index, /id="social-network-task-eyebrow"/);
assert.match(index, /<p id="social-network-task-eyebrow">ANALYSIS OVERVIEW<\/p>/);
assert.doesNotMatch(index, /social-network-overview-readiness/);
assert.ok(index.indexOf('id="social-network-overview-retry"') <
    index.indexOf('<section id="social-network-overview-home"'));
assert.match(css,
    /social-network-task-heading-actions button\[hidden\]\s*\{\s*display: none !important;/);
assert.doesNotMatch(index, /CURRENT ANALYSIS DATA/);
assert.doesNotMatch(index, /social-network-dataset-meta/);
assert.match(pageShown, /isDataQualityTask\(task\)/);
assert.match(pageShown, /overview: "ANALYSIS OVERVIEW"/);
assert.match(pageShown, /relations: "NETWORK STRUCTURE"/);
assert.match(pageShown, /interaction: "INTERACTION ANALYSIS"/);
assert.match(pageShown, /loadDatasetPage\(task === "quality"\)/);
assert.match(pageShown, /data: "DATA & QUALITY"/);
assert.match(pageShown, /quality: "DATA & QUALITY"/);

assert.match(dataLoad, /loadShellDatasetSummary\(\)/);
assert.doesNotMatch(dataLoad, /loadShellDatasetVersion\(\)/);
assert.match(dataLoad, /sameDatasetSelection\(context, current\)/);
assert.match(dataLoad, /reportFromResponse\(body\)/);
assert.match(dataLoad, /renderQualityReportView\(currentQualityReport, current, details\)/);
assert.match(dataLoad, /social-network-dataset-quality/);
assert.match(dataLoad, /qualityReportRequestSequence/);
assert.match(dataLoad, /shellDatasetSummaryCache = null/);
assert.match(contextChanged, /loadDatasetPage\(activeSocialNetworkTask === "quality"\)/);

assert.match(view, /social-network-quality-report-overview/);
assert.match(view, /social-network-quality-report-block/);
assert.match(summary, /数据规模/);
assert.match(summary, /质量概况/);
assert.match(summary, /relationshipCandidateCount/);
assert.match(summary, /tweetRecordCount/);
assert.doesNotMatch(view, /social-network-data-tabs/);
assert.doesNotMatch(index, /id="social-network-data-tabs"/);
assert.match(index, /id="social-network-dataset-quality"/);
assert.match(index, /DATASET OVERVIEW/);
assert.match(view, /覆盖摘要/);
assert.match(view, /data-quality-dimension/);
assert.match(view, /aria-pressed/);
assert.match(view,
    /document\.getElementById\(\s*"social-network-quality-report-export"\s*\)/);
assert.match(view, /exportButton\.hidden = false/);
assert.match(view, /openModuleExportDialog\("quality", exportButton\)/);
assert.match(view, /refreshButton\.hidden = false/);
assert.match(view, /refreshButton\.onclick = loadDatasetPage/);
assert.doesNotMatch(view, /social-network-quality-report-header|质量报告<\/h2>/);
assert.doesNotMatch(source, /function qualityReportHeadingMarkup/);
assert.match(pageShown,
    /qualityReportExportButton\.hidden = true;[\s\S]*?overviewRefreshButton\.hidden = true/);
assert.match(view, /renderQualityReportView\(report, context, details\)/);
assert.match(view, /selectedButton\.focus\(\)/);
assert.match(view, /pageButton\.focus\(\)/);
assert.doesNotMatch(view, /social-network-quality-report-scope|报告绑定当前分析数据/);

assert.match(warningList, /paginateWarnings\([\s\S]*qualityReportWarningPage, 10/);
assert.match(warningList, /warningsForDimension/);
assert.match(warningList, /当前数据摘要响应返回/);
assert.match(warningList, /不代表服务端全量分页或逐条定位/);
assert.match(warningList, /affectedScope/);
assert.match(warningList, /escapeHtml\(message\)/);
assert.doesNotMatch(view, /不包含未激活上传预览/);
assert.doesNotMatch(view, /原始记录、规范化结果与纳入分析的样本口径各自独立/);
assert.doesNotMatch(view, /当前没有逐条详情分页接口/);
assert.doesNotMatch(view, /不展示文件行号、原始记录或伪造的完整问题清单/);

assert.match(source, /报告不受本页分类或分页影响/);
assert.match(css, /\.social-network-task-card\.is-quality-report\s*\{\s*max-width: none;/);
assert.match(css, /body\.social-network-shell-active \.social-network-dataset-source\s*\{\s*gap: var\(--sn-space-3\);\s*margin-top: 0;\s*padding-top: var\(--sn-space-2\);/);
assert.match(css, /social-network-quality-report-overview\s*\{\s*display: grid/);
assert.match(css, /social-network-quality-report-overview\s*\{[^}]*align-items: start/s);
assert.match(css,
    /\.social-network-task-heading:has\([\s\S]*?#social-network-quality-report-export:not\(\[hidden\]\)\)\s*\{\s*display: flex;[\s\S]*?justify-content: space-between;/s);
assert.match(css,
    /body\.social-network-shell-active #page-social-network-task\s*\.social-network-task-heading \.button,[\s\S]*?appearance:\s*none;[\s\S]*?min-height:\s*36px;/s);
assert.match(css,
    /\.social-network-task-heading-actions\s*\{[^}]*display: flex;[^}]*gap: var\(--sn-space-2\);/s);
assert.doesNotMatch(css,
    /social-network-task-heading-actions:has\([\s\S]*?#social-network-quality-report-export:not\(\[hidden\]\)\)\s*#social-network-overview-retry/);
assert.match(css,
    /\.social-network-quality-report-metrics > div\s*\{[^}]*border:\s*1px solid var\(--border\);[^}]*border-radius:\s*8px;[^}]*background:\s*var\(--surface-soft\);/s);
assert.match(index, /data-social-network-quality-export/);
assert.match(source, /class="social-network-scope-term-tooltip"/);
assert.match(source, /function positionQualityReportTooltip\(trigger\)[\s\S]*?positionNetworkStructureTooltip\(/);
assert.match(css, /\.social-network-scope-term-tooltip\s*\{[^}]*background: var\(--primary-weak\)/s);
assert.match(css, /\.social-network-quality-help-trigger:hover \+\s*\.social-network-scope-term-tooltip/);
assert.match(css, /\.social-network-quality-report-dimensions button:focus-visible/);
assert.match(css, /\.social-network-quality-report-pagination button:focus-visible/);

console.log("social-network-quality-report: contract checks passed");
