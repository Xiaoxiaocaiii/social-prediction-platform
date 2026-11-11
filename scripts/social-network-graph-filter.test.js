"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");

const controller = fs.readFileSync(
    "src/main/java/com/prediction/socialnetwork/application/SocialNetworkController.java",
    "utf8"
);
const html = fs.readFileSync("src/main/resources/static/index.html", "utf8");
const graph = fs.readFileSync(
    "src/main/resources/static/js/social-network-graph.js",
    "utf8"
);
const css = fs.readFileSync("src/main/resources/static/css/style.css", "utf8");

const filterStart = controller.indexOf("List<NetworkNode> filteredNodes =");
const nodeLimitStart = controller.indexOf(
    "List<NetworkNode> nodes = filteredNodes.stream().limit(nodeLimit).toList();"
);
const filteredEdgeStart = controller.indexOf(
    "List<NetworkEdge> filteredEdges = scopedEdges.stream()"
);
assert.ok(filterStart >= 0 && filterStart < nodeLimitStart,
    "apply the community filter before limiting the returned nodes");
assert.ok(filteredEdgeStart > filterStart && filteredEdgeStart < nodeLimitStart,
    "apply the community filter to full-scope edges before limiting nodes");
assert.match(controller, /case "unassigned" -> !communityByNodeId\.containsKey/);
assert.match(controller, /case "community" -> selectedCommunityExists/);
assert.match(controller, /filteredIds\.contains\(edge\.source\(\)\)[\s\S]*filteredIds\.contains\(edge\.target\(\)\)/);
assert.match(controller, /data\.put\("state", truncated[\s\S]*?filteredNodes\.isEmpty\(\)/,
    "report bounded and empty graph states separately");
assert.match(graph,
    /if \(loadSequence !== graphLoadSequence \|\|[\s\S]*?requestContextIdentity !== datasetContextIdentity\([\s\S]*?\)\) \{\s*return;\s*\}/,
    "discard graph responses after a reload or dataset-context switch");
assert.match(graph,
    /error\.staleDatasetContext \|\|[\s\S]*?loadSequence !== graphLoadSequence/,
    "ignore failures from stale dataset requests");
assert.match(graph, /sequence !== evidenceRequestSequence \|\|[\s\S]*?social-network-graph-evidence-dialog/,
    "discard evidence responses after the dialog context changes");

assert.match(html, /id="social-network-graph-community-filter"/);
assert.match(graph, /communityFilter: activeCommunityFilter/);
assert.match(graph, /communityId: activeCommunityId/);
assert.match(graph, /loadGraph\(true\)/,
    "reload the graph when the selected community changes");
assert.match(graph, /communityCatalog\.forEach/,
    "keep the full community catalog visible while filtering the canvas");
assert.match(graph, /if \(!nodes\.length\) \{[\s\S]*?setStatus\("当前筛选范围没有可展示的账号。", "empty"\)/,
    "show a clear empty state when the selected range has no nodes");
assert.match(css, /\.social-network-graph-community-heading\s*\{[\s\S]*display:\s*flex/);

console.log("social-network-graph-filter: 16 checks passed");
