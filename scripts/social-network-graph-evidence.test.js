"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");

const calculator = fs.readFileSync(
    "src/main/java/com/prediction/socialnetwork/application/SocialNetworkGroupInsightCalculator.java",
    "utf8"
);
const resultStore = fs.readFileSync(
    "src/main/java/com/prediction/socialnetwork/application/SocialNetworkAnalysisResultStore.java",
    "utf8"
);
const html = fs.readFileSync("src/main/resources/static/index.html", "utf8");
const graph = fs.readFileSync(
    "src/main/resources/static/js/social-network-graph.js",
    "utf8"
);
const css = fs.readFileSync("src/main/resources/static/css/style.css", "utf8");

assert.match(calculator, /directedRelationshipCount/);
assert.match(calculator, /uniqueDirected\.add\(new DirectedPair/);
assert.match(
    resultStore,
    /louvain-community-pairs-v2\.json/,
    "persisted pair counts must not reuse artifacts without the directed count"
);
assert.match(html, /id="social-network-graph-evidence-dialog"/);
assert.match(html, /data-graph-evidence-tab="members"/);
assert.match(html, /data-graph-evidence-tab="ties"/);
assert.match(html, /data-graph-evidence-tab="between"/);
assert.match(graph, /network\/louvain\/insights/);
assert.match(graph, /\/between\/[\s\S]*?\/evidence/);
assert.match(graph, /section = evidenceMode/);
assert.match(graph, /data\.dataFingerprint !== expectedFingerprint/);
assert.match(graph, /source\.sourceId \+ " → " \+ source\.targetId/);
assert.match(graph, /重复来源（不计入有效关系数）/);
assert.match(graph, /对无向联系[\s\S]*?条有向关注/);
assert.match(graph, /datasetContext\.datasetId === "think-tank-social-network"/);
assert.match(css, /\.social-network-graph-community-item:focus-visible/);
assert.match(css, /\.social-network-graph-evidence-dialog\s*\{[\s\S]*width:/);
assert.match(
    css,
    /dialog\.social-network-graph-evidence-dialog\[open\][\s\S]*?top:\s*50vh;[\s\S]*?left:\s*50vw;/
);
assert.match(
    css,
    /\.social-network-graph-evidence-pair select\s*\{[\s\S]*?appearance:\s*none;[\s\S]*?border-radius:\s*8px/
);
assert.match(
    css,
    /\.social-network-graph-evidence-pair select:focus-visible\s*\{[\s\S]*?box-shadow:/
);
assert.match(
    css,
    /\.social-network-graph-page \.social-network-graph-toolbar \.button,\s*\.social-network-graph-page #social-network-graph-user-detail\s*\{[\s\S]*?background:\s*var\(--surface\)/
);
assert.match(
    css,
    /\.social-network-graph-page \.social-network-graph-toolbar \.button-primary\s*\{[\s\S]*?background:\s*var\(--primary-weak\)/
);
assert.match(
    css,
    /\.social-network-graph-view-controls\s*button\[aria-label\]\s*\{[\s\S]*?width:\s*38px;[\s\S]*?height:\s*38px;/
);

console.log("social-network-graph-evidence: 23 checks passed");
