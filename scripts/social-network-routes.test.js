"use strict";

const assert = require("node:assert/strict");
const routeTable = require(
    "../src/main/resources/static/js/social-network-routes.js"
);

const expectedRoutes = new Map([
    ["/social-network", ["overview", "分析概览"]],
    ["/social-network/data", ["data", "数据与质量"]],
    ["/social-network/data/upload", ["upload", "上传数据"]],
    ["/social-network/data/quality", ["quality", "数据与质量"]],
    ["/social-network/relations", ["relations", "网络结构"]],
    ["/social-network/relations/ranking", ["ranking", "节点排行"]],
    ["/social-network/relations/user", ["user-relations", "用户关系"]],
    ["/social-network/relations/groups", ["groups", "团体与社群"]],
    ["/social-network/content", ["interaction", "互动分析"]],
    ["/social-network/content/topic", ["topic", "主题分析"]]
]);

assert.deepEqual(new Set(routeTable.paths), new Set(expectedRoutes.keys()));
assert.equal(routeTable.paths.length, 10);

for (const [path, [task, title]] of expectedRoutes) {
    const route = routeTable.resolve(path);
    assert.equal(route.page, "social-network-task");
    assert.equal(route.task, task);
    assert.equal(route.title, title);
}

assert.equal(routeTable.resolve("/social-network/network"), null);
assert.equal(routeTable.resolve("/social-network/unknown"), null);
assert.equal(routeTable.getTask("unknown"), null);
assert.equal(routeTable.getTask("overview").returnHref, "#/");
assert.equal(routeTable.getTask("topic").returnHref, "#/social-network");
assert.equal(
    routeTable.getTask("groups").returnHref,
    "#/social-network/relations"
);

console.log("social-network-routes: 38 checks passed");
