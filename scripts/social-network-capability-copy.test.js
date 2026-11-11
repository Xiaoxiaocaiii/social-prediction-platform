"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

function readProjectFile(relativePath) {
    return fs.readFileSync(path.join(__dirname, "..", relativePath), "utf8");
}

const indexSource = readProjectFile(
    "src/main/resources/static/index.html"
);
const rankingSource = readProjectFile(
    "src/main/resources/static/js/social-network.js"
);
const groupsSource = readProjectFile(
    "src/main/resources/static/js/social-network-groups.js"
);
const bridgeSource = readProjectFile(
    "src/main/resources/static/js/social-network-bridge-candidates.js"
);

assert.match(
    indexSource,
    /当前支持内置智库数据集的关系网络、Louvain 社群及跨社群连接候选查看与证据导出/
);
assert.match(
    indexSource,
    /上传数据支持 Louvain 社群划分与导出、Clique 团体分析与成员来源核对及当前团列表页导出、关系网络查看、社群筛选与跨社群候选导出/
);
assert.doesNotMatch(indexSource, /Clique 导出尚未开放/);
assert.match(indexSource, /内置数据完整链路仍待最终验收/);
assert.doesNotMatch(
    indexSource,
    /传播关系判断|关键节点与连接|全局关系图与社群展示已有代码，当前暂停/
);
assert.doesNotMatch(
    indexSource,
    /用户、主题和互动之间的连接|全量用户关注关系网络/
);
assert.match(rankingSource, /节点指标排行/);
assert.match(rankingSource, /排名不代表内容热度或现实影响力/);
assert.match(
    groupsSource,
    /当前数据上下文不支持此分析/
);
assert.match(groupsSource, /isGroupExportSupported\(context, analysisType\)/);
assert.match(
    groupsSource,
    /model\.isUploadContext\(context\) &&[\s\S]*analysisType === "louvain"[\s\S]*analysisType === "clique"/
);
assert.match(bridgeSource, /var upload = \/\^upload-\//);
assert.match(bridgeSource, /return builtin \|\| upload/);

console.log("social-network-capability-copy: scope and limitations passed");
