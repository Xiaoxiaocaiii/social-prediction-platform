"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const routerSource = fs.readFileSync(
    path.join(
        __dirname,
        "../src/main/resources/static/js/router.js"
    ),
    "utf8"
);

function bootstrap(readyState) {
    const windowListeners = new Map();
    const documentListeners = new Map();
    const pageCalls = [];
    const window = {
        addEventListener(name, callback) {
            windowListeners.set(name, callback);
        },
        socialNetworkRouteTable: {
            resolve(routePath) {
                return routePath === "/social-network/data/upload"
                    ? { page: "social-network-task", task: "upload" }
                    : null;
            }
        },
        showPage(page, task) {
            pageCalls.push({ page, task });
        }
    };
    const document = {
        readyState,
        addEventListener(name, callback) {
            documentListeners.set(name, callback);
        }
    };
    const context = {
        window,
        document,
        location: { hash: "#/social-network/data/upload" },
        performance: { now: () => 10 },
        fetch: () => Promise.resolve(),
        JSON,
        Date
    };

    vm.runInNewContext(routerSource, context);
    return { window, windowListeners, documentListeners, pageCalls };
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

console.log("social-network-router-lifecycle: 9 checks passed");
