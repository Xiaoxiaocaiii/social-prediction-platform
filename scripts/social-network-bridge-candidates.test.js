"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");

const read = (path) => fs.readFileSync(path, "utf8");
const ranking = read("src/main/resources/static/js/social-network.js");
const candidates = read(
    "src/main/resources/static/js/social-network-bridge-candidates.js"
);
const graph = read("src/main/resources/static/js/social-network-graph.js");
const router = read("src/main/resources/static/js/router.js");
const html = read("src/main/resources/static/index.html");
const css = read("src/main/resources/static/css/style.css");

assert.match(ranking, /data-ranking-task-view="bridge"/);
assert.match(ranking, /data-ranking-task-bridge-view hidden/);
assert.match(ranking, /bridgeView\.load\(mount/);
assert.match(ranking, /exportButton\.disabled = false/);
assert.doesNotMatch(ranking, /上传数据的跨社群候选导出将在后续阶段开放/);
assert.match(ranking, /getExportSnapshot\(\)/);
assert.match(ranking, /openModuleExportDialog\(\s*"bridge-candidates"/);

assert.match(html,
    /social-network\.js\?v=[^"]+[\s\S]*?social-network-bridge-candidates\.js\?v=/);
assert.match(html,
    /social-network-bridge-candidates\.js\?v=[^"]+[\s\S]*?social-network-graph\.js\?v=/);
assert.match(html,
    /social-network-bridge-candidates\.js\?v=20261110-e05-upload-context-1/);

assert.match(candidates, /var builtin = datasetId === BUILTIN_DATASET_ID/);
assert.match(candidates, /var upload = \/\^upload-\//);
assert.match(candidates, /Boolean\(datasetVersion && sessionId\)/);
assert.match(candidates, /return builtin \|\| upload/);
assert.match(candidates,
    /JSON\.stringify\(window\.socialNetworkWorkspace\.getDatasetContext\(\)\)[\s\S]*?JSON\.stringify\(config\.context\)/);
assert.match(candidates, /network\/bridge-candidates/);
assert.match(candidates, /pageSize: PAGE_SIZE/);
assert.match(candidates, /expectedAnalysisResultId: state\.identity\.analysisResultId/);
assert.match(candidates, /identity\.dataFingerprint !== state\.identity\.dataFingerprint/);
assert.match(candidates, /state\.evidenceSnapshot = null/);
assert.match(candidates, /function getExportSnapshot/);
assert.match(candidates, /analysisResultId: state\.identity\.analysisResultId/);
assert.match(candidates, /query\.evidence = Object\.assign/);
assert.match(candidates, /state\.loading \|\| !state\.config/);
assert.match(candidates, /network\/bridge-candidates\/[\s\S]*?\/evidence/);
assert.match(candidates, /sourceFileName/);
assert.match(candidates, /Number\.isInteger\(arrayIndex\) && arrayIndex >= 0/);
assert.match(candidates, /逐行关系文件没有数组索引/);
assert.match(candidates, /重复来源，不重复计数/);
assert.match(candidates, /window\.openSocialNetworkUserDetail/);
assert.match(candidates, /PENDING_GRAPH_FOCUS_KEY/);
assert.match(candidates, /window\.location\.hash = "#\/social-network\/network"/);
assert.match(candidates, /profile_records_incomplete:[\s\S]*?候选列表暂不可用/);
assert.match(candidates, /candidate_not_found:[\s\S]*?刷新候选列表/);
assert.match(candidates, /analysis_result_unavailable:[\s\S]*?不代表没有符合条件的账号/);
assert.match(candidates, /function renderEmpty[\s\S]*?共有 0 个符合条件的资料账号/);
assert.match(candidates, /function renderUnavailable[\s\S]*?role="status"/);

assert.match(router, /"\/social-network\/relations\/ranking": true/);
assert.match(graph, /capturePendingBridgeGraphFocus\(\)/);
assert.match(graph, /finishPendingBridgeGraphFocus\(layoutSequence\)/);
assert.match(graph, /graphDatasetVersion !== request\.datasetVersion/);
assert.match(graph,
    /var node = nodeById\.get\(request\.accountId\);[\s\S]*?if \(!node\) \{[\s\S]*?setStatus\([\s\S]*?未显示在当前画布中[\s\S]*?"limited"\);[\s\S]*?return;/);
assert.match(graph, /候选账号[\s\S]*?未显示在当前画布中/);
assert.match(graph, /画布可能受社群筛选或展示上限影响/);
assert.match(graph, /完整候选排行不受画布筛选影响/);
assert.match(graph, /if \(!nodes\.length\)[\s\S]*?finishPendingBridgeGraphFocus\(loadSequence\)/);

assert.match(css, /\.social-network-bridge-candidates-view\[hidden\]\s*\{\s*display:\s*none !important;/);
assert.match(css,
    /dialog\.social-network-bridge-evidence-dialog\[open\][\s\S]*?top:\s*50vh;[\s\S]*?left:\s*50vw;/);
assert.match(css,
    /\.social-network-bridge-evidence-filters select:focus-visible[\s\S]*?box-shadow:/);
assert.match(css, /\.social-network-bridge-candidate-actions\s*\{[\s\S]*?flex-wrap:\s*nowrap;[\s\S]*?gap:\s*8px;/);
assert.match(css,
    /\.social-network-bridge-candidate-table :is\(th, td\):nth-child\(7\)\s*\{\s*width:\s*24%;/);

console.log("social-network-bridge-candidates: C-04/C-05 contract checks passed");
