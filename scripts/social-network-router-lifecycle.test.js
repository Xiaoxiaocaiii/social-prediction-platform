"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const routeTable = require(
    "../src/main/resources/static/js/social-network-routes.js"
);

const routerSource = fs.readFileSync(
    path.join(
        __dirname,
        "../src/main/resources/static/js/router.js"
    ),
    "utf8"
);

function bootstrap(readyState, hash = "#/social-network/data/upload") {
    const windowListeners = new Map();
    const documentListeners = new Map();
    const pageCalls = [];
    const graphBackLink = {
        attributes: {},
        setAttribute(name, value) {
            this.attributes[name] = value;
        },
        getAttribute(name) {
            return this.attributes[name] || null;
        }
    };
    const window = {
        addEventListener(name, callback) {
            windowListeners.set(name, callback);
        },
        socialNetworkRouteTable: routeTable,
        showPage(page, task) {
            pageCalls.push({ page, task });
        }
    };
    const document = {
        readyState,
        getElementById(id) {
            return id === "social-network-graph-back" ? graphBackLink : null;
        },
        addEventListener(name, callback) {
            documentListeners.set(name, callback);
        }
    };
    const context = {
        window,
        document,
        location: { hash },
        performance: { now: () => 10 },
        fetch: () => Promise.resolve(),
        JSON,
        Date
    };

    vm.runInNewContext(routerSource, context);
    return {
        window,
        windowListeners,
        documentListeners,
        location: context.location,
        graphBackLink,
        pageCalls
    };
}

const loading = bootstrap("loading");
assert.equal(loading.pageCalls.length, 0);
assert.ok(loading.documentListeners.has("DOMContentLoaded"));
assert.ok(loading.windowListeners.has("hashchange"));
assert.equal(loading.windowListeners.has("load"), false);
loading.documentListeners.get("DOMContentLoaded")();
assert.deepEqual(loading.pageCalls, [{
    page: "social-network-task",
    task: "upload"
}]);
loading.windowListeners.get("hashchange")();
assert.equal(loading.pageCalls.length, 2);

const complete = bootstrap("complete");
assert.deepEqual(complete.pageCalls, [{
    page: "social-network-task",
    task: "upload"
}]);
assert.equal(complete.windowListeners.has("load"), false);

const graphRoute = bootstrap("complete", "#/social-network/network");
assert.deepEqual(graphRoute.pageCalls, [{
    page: "social-network-graph",
    task: undefined
}]);
assert.equal(
    graphRoute.graphBackLink.getAttribute("href"),
    "#/social-network"
);

const unknownSocialRoute = bootstrap("complete", "#/social-network/not-registered");
assert.deepEqual(unknownSocialRoute.pageCalls, [{
    page: "social-network-task",
    task: "unknown"
}]);

const graphHashChange = bootstrap("complete", "#/");
graphHashChange.location.hash = "#/social-network/network";
graphHashChange.windowListeners.get("hashchange")();
assert.equal(
    graphHashChange.pageCalls.at(-1).page,
    "social-network-graph"
);
assert.equal(
    graphHashChange.graphBackLink.getAttribute("href"),
    "#/social-network"
);

const networkStructureToGraph = bootstrap(
    "complete",
    "#/social-network/relations"
);
networkStructureToGraph.location.hash = "#/social-network/network";
networkStructureToGraph.windowListeners.get("hashchange")();
assert.equal(
    networkStructureToGraph.graphBackLink.getAttribute("href"),
    "#/social-network/relations"
);

const groupsToGraph = bootstrap(
    "complete",
    "#/social-network/relations/groups"
);
groupsToGraph.location.hash = "#/social-network/network";
groupsToGraph.windowListeners.get("hashchange")();
assert.equal(
    groupsToGraph.graphBackLink.getAttribute("href"),
    "#/social-network/relations/groups"
);
groupsToGraph.location.hash = "#/social-network/relations/groups";
groupsToGraph.windowListeners.get("hashchange")();
groupsToGraph.location.hash = "#/social-network/network";
groupsToGraph.windowListeners.get("hashchange")();
assert.equal(
    groupsToGraph.graphBackLink.getAttribute("href"),
    "#/social-network/relations/groups"
);

const rankingToGraph = bootstrap(
    "complete",
    "#/social-network/relations/ranking"
);
rankingToGraph.location.hash = "#/social-network/network";
rankingToGraph.windowListeners.get("hashchange")();
assert.equal(
    rankingToGraph.graphBackLink.getAttribute("href"),
    "#/social-network/relations/ranking"
);

const otherPageToGraph = bootstrap("complete", "#/predict");
otherPageToGraph.location.hash = "#/social-network/network";
otherPageToGraph.windowListeners.get("hashchange")();
assert.equal(
    otherPageToGraph.graphBackLink.getAttribute("href"),
    "#/social-network"
);

console.log("social-network-router-lifecycle: 18 checks passed");
