"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const model = require(
    "../src/main/resources/static/js/social-network-groups-model.js"
);

const pageSource = fs.readFileSync(
    path.join(
        __dirname,
        "../src/main/resources/static/js/social-network-groups.js"
    ),
    "utf8"
);
const indexSource = fs.readFileSync(
    path.join(__dirname, "../src/main/resources/static/index.html"),
    "utf8"
);
const styleSource = fs.readFileSync(
    path.join(__dirname, "../src/main/resources/static/css/style.css"),
    "utf8"
);

function makeElement(dataset) {
    const classes = new Set();
    return {
        dataset: Object.assign({}, dataset || {}),
        attributes: {},
        style: {},
        classList: {
            add: (name) => classes.add(name),
            remove: (name) => classes.delete(name),
            contains: (name) => classes.has(name),
            toggle(name, force) {
                const shouldAdd = force === undefined ? !classes.has(name) : force;
                if (shouldAdd) classes.add(name);
                else classes.delete(name);
                return shouldAdd;
            }
        },
        parentElement: {
            classList: {
                add: (name) => classes.add(name),
                remove: (name) => classes.delete(name),
                contains: (name) => classes.has(name),
                toggle(name, force) {
                    const shouldAdd = force === undefined ? !classes.has(name) : force;
                    if (shouldAdd) classes.add(name);
                    else classes.delete(name);
                    return shouldAdd;
                }
            }
        },
        nextElementSibling: {
            style: {},
            getBoundingClientRect: () => ({ width: 120, height: 60 })
        },
        disabled: false,
        hidden: false,
        open: false,
        textContent: "",
        innerHTML: "",
        listeners: {},
        getBoundingClientRect: () => ({
            left: 100, top: 50, right: 120, bottom: 70, width: 20, height: 20
        }),
        addEventListener(name, callback) {
            this.listeners[name] = callback;
        },
        dispatch(name, event) {
            return this.listeners[name] && this.listeners[name](event || {});
        },
        setAttribute(name, value) {
            this.attributes[name] = value;
        },
        getAttribute(name) {
            return this.attributes[name] || null;
        },
        focus() {
            this.focused = true;
        },
        showModal() {
            this.open = true;
        },
        close() {
            this.open = false;
            this.dispatch("close");
        },
        click() {
            this.clicked = true;
        },
        remove() {
            this.removed = true;
        },
        insertAdjacentHTML(position, html) {
            this.innerHTML += html;
        },
        closest(selector) {
            return this.matches && this.matches[selector] || null;
        }
    };
}

function bootstrap(datasetContext) {
    const root = makeElement();
    const elements = new Map();
    const analysisTabs = [
        makeElement({ groupAnalysis: "clique" }),
        makeElement({ groupAnalysis: "louvain" })
    ];
    const evidenceTabs = [
        makeElement({ groupEvidenceSection: "members" }),
        makeElement({ groupEvidenceSection: "ties" })
    ];
    const calls = [];
    const downloads = [];
    const exportedBlobs = [];
    const listeners = new Map();
    const timers = new Map();
    let timerId = 0;
    let cliqueResult = {
        state: "NOT_REQUESTED",
        inputNodeCount: 0,
        inputDirectedEdgeCount: 0,
        enumeratedCliqueCount: 0,
        cliques: []
    };
    let cliqueInsight = null;
    let louvainInsight = null;
    let louvainResult = {
        state: "NOT_REQUESTED",
        complete: false,
        communities: [],
        unassignedNodeCount: 0,
        inputNodeCount: 0,
        inputDirectedEdgeCount: 0,
        quality: {}
    };

    root.addEventListener = (name, callback) => listeners.set(name, callback);
    root.querySelector = (selector) => {
        if (!elements.has(selector)) {
            elements.set(selector, makeElement());
        }
        return elements.get(selector);
    };
    root.querySelectorAll = (selector) => {
        if (selector === "[data-group-analysis]") return analysisTabs;
        if (selector === "[data-group-evidence-section]") return evidenceTabs;
        return [];
    };

    const context = {
        datasetId: datasetContext.datasetId,
        datasetVersion: datasetContext.datasetVersion || null,
        sessionId: datasetContext.sessionId || null
    };
    const workspace = {
        getDatasetContext: () => Object.assign({}, context),
        datasetUrl(resource, query, requestContext) {
            const params = new URLSearchParams(query || {});
            if (requestContext && requestContext.sessionId) {
                params.set("datasetVersion", requestContext.datasetVersion);
                params.set("sessionId", requestContext.sessionId);
            }
            return resource + (params.size ? "?" + params : "");
        },
        async requestDataset(url, options) {
            calls.push({ url, method: options.method, options });
            if (url.split("?")[0].endsWith("network/exports")) {
                return { data: {
                    result: "{\"exportType\":\"clique\",\"data\":{}}",
                    contentType: "application/json; charset=UTF-8"
                } };
            }
            if (url.startsWith("network/cliques/insights/shared")) {
                return { data: {
                    available: true, dataFingerprint: "fixture",
                    page: { totalCount: 2, offset: 0,
                        cliques: [{ index: 1, members: ["a", "b", "d"] }] }
                } };
            }
            if (url.startsWith("network/cliques/insights")) {
                return { data: cliqueInsight };
            }
            if (url.startsWith("network/louvain/insights")) {
                return { data: louvainInsight };
            }
            if (url.includes("/evidence")) {
                const query = new URLSearchParams(url.split("?")[1]);
                const offset = Number(query.get("offset"));
                return {
                    data: {
                        evidenceAvailable: true,
                        analysisComplete: true,
                        converged: true,
                        section: query.get("section"),
                        offset,
                        pageSize: 20,
                        totalCount: 40,
                        members: query.get("section") === "members"
                            ? Array.from({ length: 20 }, (_, index) => ({
                                nodeId: "member-" + (offset + index),
                                nodeType: "PROFILE",
                                profileSources: []
                            }))
                            : [],
                        ties: query.get("section") === "ties"
                            ? Array.from({ length: 20 }, (_, index) => ({
                                leftNodeId: "left-" + (offset + index),
                                rightNodeId: "right-" + (offset + index),
                                weight: 1,
                                supportingRelationships: [],
                                duplicateRelationshipRecords: []
                            }))
                            : []
                    }
                };
            }
            if (url.startsWith("network/cliques")) {
                return { data: cliqueResult };
            }
            if (url.startsWith("network/louvain") &&
                    options.method === "POST") {
                return { data: { state: "RUNNING", communities: [] } };
            }
            if (url.startsWith("network/louvain")) {
                return { data: louvainResult };
            }
            return { data: { evidenceAvailable: true, members: [], ties: [] } };
        }
    };
    const window = {
        socialNetworkWorkspace: workspace,
        socialNetworkGroupsModel: model,
        innerWidth: 552,
        innerHeight: 688,
        addEventListener(name, callback) {
            listeners.set(name, callback);
        },
        setTimeout(callback) {
            const id = ++timerId;
            timers.set(id, callback);
            return id;
        },
        clearTimeout(id) {
            timers.delete(id);
        },
        requestAnimationFrame(callback) {
            callback();
        },
        confirm: () => true
    };
    const documentElements = new Map();
    const document = {
        documentElement: { clientWidth: 552, clientHeight: 688 },
        body: { appendChild() {} },
        getElementById(id) {
            if (id === "social-network-group-page") return root;
            if (!documentElements.has(id)) {
                const element = makeElement();
                if (id === "social-network-group-export-format") {
                    element.value = "json";
                }
                documentElements.set(id, element);
            }
            return documentElements.get(id);
        },
        createElement() {
            const element = makeElement();
            element.click = () => downloads.push(element);
            return element;
        }
    };
    const urlApi = {
        createObjectURL(blob) {
            exportedBlobs.push(blob);
            return "blob:group-export";
        },
        revokeObjectURL() {}
    };
    vm.runInNewContext(pageSource, {
        window,
        document,
        Blob,
        URL: urlApi,
        Date,
        JSON,
        URLSearchParams,
        encodeURIComponent,
        Number,
        String,
        Boolean,
        Math,
        Array,
        Object,
        Promise
    });
    return {
        window,
        root,
        elements,
        analysisTabs,
        evidenceTabs,
        calls,
        listeners,
        timers,
        downloads,
        exportedBlobs,
        documentElements,
        setCliqueResult(value) {
            cliqueResult = value;
        },
        setCliqueInsight(value) {
            cliqueInsight = value;
        },
        setLouvainResult(value) {
            louvainResult = value;
        },
        setLouvainInsight(value) {
            louvainInsight = value;
        }
    };
}

function click(handler, selector, element) {
    const target = makeElement();
    target.matches = { [selector]: element };
    handler({ target });
}

async function flushPromises() {
    await Promise.resolve();
    await Promise.resolve();
    await new Promise((resolve) => setImmediate(resolve));
}

async function main() {
    assert.doesNotMatch(
        indexSource,
        /social-network-group-intro|social-network-group-context|data-group-method-copy|data-group-fingerprint/
    );
    assert.match(
        indexSource,
        /data-group-analysis="clique"[\s\S]*?aria-describedby="social-network-group-clique-method"[\s\S]*?id="social-network-group-clique-method"[\s\S]*?role="tooltip">[\s\S]*?Clique 计算口径/
    );
    assert.match(
        indexSource,
        /data-group-analysis="louvain"[\s\S]*?aria-describedby="social-network-group-louvain-method"[\s\S]*?id="social-network-group-louvain-method"[\s\S]*?role="tooltip">[\s\S]*?Louvain 计算口径/
    );
    assert.match(indexSource, /style\.css\?v=20261111-ranking-bridge-spacing-1/);
    assert.match(indexSource,
        /social-network-groups-model\.js\?v=20261110-e07-upload-clique-1/);
    assert.match(indexSource,
        /social-network-groups\.js\?v=20261111-e08-upload-clique-export-1/);
    assert.match(
        indexSource,
        /<div class="social-network-group-actions">[\s\S]*?<a class="button button-secondary"\s+href="#\/social-network\/network">全局关系图<\/a>[\s\S]*?<\/div>/
    );
    assert.match(indexSource, /data-group-export disabled>导出当前结果/);
    assert.match(indexSource, /单次文件上限为 2 MiB，超限整体拒绝/);
    assert.match(pageSource, /requestUrl\("network\/exports"\)/);
    assert.match(pageSource, /不会启动或重跑算法/);
    assert.match(
        pageSource,
        /代表性首屏已抽测，后续页和瞬时峰值尚未全面核验/
    );
    assert.doesNotMatch(
        pageSource,
        /查询成本尚待实测|成本尚待实测/
    );
    assert.match(
        styleSource,
        /\.social-network-scope-term-trigger:hover \+ \.social-network-scope-term-tooltip/
    );
    assert.doesNotMatch(
        styleSource,
        /\.social-network-group-tabs \[data-group-analysis\]:hover[\s\S]*?\.social-network-group-tab-help[\s\S]*?\.social-network-scope-term-tooltip/
    );
    assert.match(
        pageSource,
        /\.social-network-group-tab-help "\s*\+\s*"\.social-network-scope-term-trigger"/
    );
    assert.doesNotMatch(
        pageSource,
        /\.social-network-group-tabs \[data-group-analysis\]:hover/
    );
    assert.match(
        styleSource,
        /\.social-network-group-findings\s*\{\s*display: grid;\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/
    );
    assert.match(
        styleSource,
        /\.social-network-group-results\s*\{\s*display: grid;\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/
    );
    assert.match(
        styleSource,
        /@media \(max-width: 1220px\)\s*\{\s*\.social-network-group-findings\s*\{\s*grid-template-columns: minmax\(0, 1fr\);/
    );
    assert.match(
        styleSource,
        /@media \(max-width: 1220px\)\s*\{[\s\S]*?\.social-network-group-results\s*\{\s*grid-template-columns: minmax\(0, 1fr\);/
    );

    const page = bootstrap({
        datasetId: "think-tank-social-network",
        datasetVersion: "v1"
    });
    const controller = page.window.socialNetworkGroupsPage;
    controller.onShown();
    await flushPromises();
    assert.deepEqual(page.calls.map((call) => call.method), ["GET"]);
    assert.equal(page.calls[0].url.startsWith("network/cliques"), true);
    assert.equal(
        page.elements.get("[data-group-status]").textContent,
        "尚未运行"
    );
    const initialSummary = page.elements.get("[data-group-summary]").innerHTML;
    assert.match(initialSummary, /分析状态/);
    assert.match(initialSummary, /<strong>—<\/strong>/);
    assert.doesNotMatch(initialSummary, /0 节点|0 条有向关系/);
    assert.equal(page.elements.get("[data-group-export]").disabled, true);

    const exportPage = bootstrap({
        datasetId: "think-tank-social-network",
        datasetVersion: "v1"
    });
    exportPage.setCliqueResult({
        state: "COMPLETED",
        complete: true,
        dataFingerprint: "fixture-fingerprint",
        cacheKey: "fixture-cache",
        offset: 0,
        pageSize: 20,
        retainedCliqueCount: 1,
        cliques: [["a", "b", "c"]]
    });
    exportPage.window.socialNetworkGroupsPage.onShown();
    await flushPromises();
    assert.equal(exportPage.elements.get("[data-group-export]").disabled, false);
    click(
        exportPage.listeners.get("click"),
        "[data-group-export]",
        makeElement()
    );
    const exportDialog =
        exportPage.documentElements.get("social-network-group-export-dialog");
    assert.equal(exportDialog.open, true);
    await exportPage.documentElements
        .get("social-network-group-export-submit").dispatch("click");
    await flushPromises();
    const exportCalls = exportPage.calls.filter((call) =>
        call.url.endsWith("network/exports"));
    assert.equal(exportCalls.length, 1);
    assert.equal(exportCalls[0].method, "POST");
    assert.equal(JSON.parse(exportCalls[0].options.body).analysisType, "clique");
    assert.equal(exportPage.calls.filter((call) => call.method === "POST").length, 1);
    assert.equal(exportPage.downloads.length, 1);
    assert.equal(exportPage.exportedBlobs.length, 1);
    assert.equal(
        exportPage.exportedBlobs[0].size,
        Buffer.byteLength("{\"exportType\":\"clique\",\"data\":{}}", "utf8")
    );
    assert.match(
        exportPage.documentElements.get("social-network-group-export-status")
            .textContent,
        /已下载/
    );
    exportPage.window.socialNetworkGroupsPage.onHidden();

    click(
        page.listeners.get("click"),
        "[data-group-analysis]",
        page.analysisTabs[1]
    );
    await flushPromises();
    assert.deepEqual(page.calls.map((call) => call.method), ["GET", "GET"]);
    assert.equal(page.calls[1].url.startsWith("network/louvain"), true);

    click(
        page.listeners.get("click"),
        "[data-group-start]",
        page.root.querySelector("[data-group-start]")
    );
    await flushPromises();
    assert.deepEqual(page.calls.map((call) => call.method), [
        "GET", "GET", "POST"
    ]);
    assert.equal(page.calls[2].url.startsWith("network/louvain"), true);
    assert.equal(page.calls[2].url.includes("force=true"), false);
    assert.equal(page.timers.size, 1);

    controller.onHidden();
    assert.equal(page.timers.size, 0);

    const evidencePage = bootstrap({
        datasetId: "think-tank-social-network",
        datasetVersion: "v1"
    });
    evidencePage.setLouvainResult({
        state: "COMPLETED",
        complete: true,
        workerCompleted: true,
        converged: true,
        communities: [{
            id: "C001",
            nodeCount: 40,
            internalTieCount: 80
        }],
        communityByNodeId: { member: "C001" },
        unassignedNodeCount: 0,
        quality: { assignedNodeCount: 40, modularity: 0.42 },
        iterations: 5
    });
    evidencePage.window.socialNetworkGroupsPage.onShown();
    await flushPromises();
    click(
        evidencePage.listeners.get("click"),
        "[data-group-analysis]",
        evidencePage.analysisTabs[1]
    );
    await flushPromises();
    click(
        evidencePage.listeners.get("click"),
        "[data-group-select]",
        makeElement({ groupSelect: "louvain", groupId: "C001" })
    );
    await flushPromises();
    assert.equal(
        evidencePage.calls.at(-1).url,
        "network/louvain/communities/C001/evidence?section=members&offset=0&pageSize=20"
    );
    click(
        evidencePage.listeners.get("click"),
        "[data-group-evidence-section]",
        evidencePage.evidenceTabs[1]
    );
    await flushPromises();
    assert.equal(
        evidencePage.calls.at(-1).url.includes("section=ties&offset=0"),
        true
    );
    click(
        evidencePage.listeners.get("click"),
        "[data-group-evidence-next]",
        evidencePage.root.querySelector("[data-group-evidence-next]")
    );
    await flushPromises();
    assert.equal(
        evidencePage.calls.at(-1).url.includes("section=ties&offset=20"),
        true
    );
    evidencePage.window.socialNetworkGroupsPage.onHidden();

    const findingsPage = bootstrap({
        datasetId: "think-tank-social-network", datasetVersion: "v1"
    });
    findingsPage.setCliqueResult({
        state: "COMPLETED", complete: true, membershipListComplete: true,
        dataFingerprint: "fixture", retainedCliqueCount: 2,
        enumeratedCliqueCount: 2, offset: 0,
        cliques: [["a", "b", "c"], ["a", "b", "d"]],
        cliqueSizeDistribution: { 3: 2 },
        nodeParticipationCounts: { a: 2, b: 2, c: 1, d: 1 }
    });
    findingsPage.setCliqueInsight({
        available: true, dataFingerprint: "fixture",
        names: { a: "Alice", b: "Bob", c: "<script>alert(1)</script>" },
        insight: {
            totalCount: 2,
            participants: [{ nodeId: "a", count: 2 }, { nodeId: "b", count: 2 }],
            firstNodeId: "a", secondNodeId: "b", commonCliqueCount: 2,
            maximumSize: 3, maximumCount: 2, commonMembers: ["a", "b", "c"],
            offset: 0, maximumCliques: [{ index: 1, members: ["a", "b", "d"] }]
        }
    });
    findingsPage.window.socialNetworkGroupsPage.onShown();
    await flushPromises();
    const findingHtml = findingsPage.elements.get("[data-group-findings]").innerHTML;
    assert.match(findingHtml, /@Alice/);
    assert.match(findingHtml, /共同出现在 <strong>2<\/strong> 个团/);
    assert.match(findingHtml,
        /<details class="social-network-group-disclosure" data-group-shared><summary>查看共同出现的团<\/summary>/);
    assert.match(findingHtml, /列出这两个账号共同出现的极大团/);
    assert.match(findingHtml, /同时出现在每一个最大团中的账号/);
    assert.match(findingHtml, /可分页查看最大团成员，并核对其关系来源/);
    assert.doesNotMatch(findingHtml, /<button[^>]*data-group-shared/);
    assert.match(findingHtml, /&lt;script&gt;/);
    assert.doesNotMatch(findingHtml, /<script>/);
    assert.equal(findingsPage.calls.filter((call) => call.method === "POST").length, 0);
    const cliqueMetricHtml =
        findingsPage.elements.get("[data-group-summary]").innerHTML;
    assert.match(cliqueMetricHtml, /aria-label="解释：极大团"/);
    assert.match(cliqueMetricHtml, /不等于规模最大的团/);

    const termPage = bootstrap({
        datasetId: "think-tank-social-network", datasetVersion: "v1"
    });
    termPage.window.socialNetworkGroupsPage.onShown();
    await flushPromises();
    const termTrigger = makeElement();
    termTrigger.nextElementSibling = makeElement();
    click(termPage.listeners.get("click"), "[data-group-term]", termTrigger);
    assert.equal(termTrigger.getAttribute("aria-expanded"), "true");
    assert.equal(termTrigger.parentElement.classList.contains("is-open"), true);
    assert.notEqual(termTrigger.nextElementSibling.style.left, "");
    termPage.elements.set(
        '[data-group-term][aria-expanded="true"]',
        termTrigger
    );
    let escapeStopped = false;
    termPage.listeners.get("keydown")({
        key: "Escape",
        target: termTrigger,
        stopPropagation() {
            escapeStopped = true;
        }
    });
    assert.equal(termTrigger.getAttribute("aria-expanded"), "false");
    assert.equal(termTrigger.focused, true);
    assert.equal(escapeStopped, true);
    termPage.window.socialNetworkGroupsPage.onHidden();

    const sharedDisclosure = makeElement();
    sharedDisclosure.open = true;
    sharedDisclosure.matches = { "[data-group-shared]": sharedDisclosure };
    findingsPage.listeners.get("toggle")({ target: sharedDisclosure });
    await flushPromises();
    assert.match(findingsPage.calls.at(-1).url, /insights\/shared\?first=a&second=b/);
    assert.match(findingsPage.elements.get("[data-group-findings]").innerHTML, /共同团分页/);
    assert.match(findingsPage.elements.get("[data-group-findings]").innerHTML,
        /data-group-shared open/);
    const sharedCalls = findingsPage.calls.length;
    sharedDisclosure.open = false;
    findingsPage.listeners.get("toggle")({ target: sharedDisclosure });
    sharedDisclosure.open = true;
    findingsPage.listeners.get("toggle")({ target: sharedDisclosure });
    await flushPromises();
    assert.equal(findingsPage.calls.length, sharedCalls);
    click(findingsPage.listeners.get("click"), "[data-group-select]",
        makeElement({ groupSelect: "clique", groupIndex: "1" }));
    await flushPromises();
    assert.match(findingsPage.calls.at(-1).url, /cliques\/1\/evidence\?section=members/);
    click(findingsPage.listeners.get("click"), "[data-group-evidence-next]",
        findingsPage.root.querySelector("[data-group-evidence-next]"));
    await flushPromises();
    assert.match(findingsPage.calls.at(-1).url, /cliques\/1\/evidence\?section=members&offset=20/);

    findingsPage.setLouvainResult({
        state: "COMPLETED", complete: true, converged: true,
        dataFingerprint: "fixture", communities: [
            { id: "C1", nodeCount: 1 }, { id: "C2", nodeCount: 1 }
        ],
        unassignedNodeCount: 0,
        quality: {
            assignedNodeCount: 2, internalTieCount: 4, crossCommunityTieCount: 3
        }
    });
    findingsPage.setLouvainInsight({
        available: true, dataFingerprint: "fixture",
        names: { a: "Alice", b: "Bob" },
        communityPairs: [{ first: "C1", second: "C2", tieCount: 3 }]
    });
    click(findingsPage.listeners.get("click"), "[data-group-analysis]",
        findingsPage.analysisTabs[1]);
    await flushPromises();
    const louvainMetricHtml =
        findingsPage.elements.get("[data-group-summary]").innerHTML;
    assert.match(louvainMetricHtml, /aria-label="解释：算法社群"/);
    assert.match(louvainMetricHtml, /aria-label="解释：模块度 Q"/);
    assert.match(louvainMetricHtml, /不是现实群体真实性或影响力分数/);
    assert.match(findingsPage.elements.get("[data-group-findings]").innerHTML,
        /C1 与 C2<\/strong> 之间有 <strong>3<\/strong>/);
    click(findingsPage.listeners.get("click"), "[data-group-between]",
        makeElement({ groupBetween: "C1", groupBetweenSecond: "C2" }));
    await flushPromises();
    assert.match(findingsPage.calls.at(-1).url, /communities\/C1\/between\/C2\/evidence\?section=ties/);
    assert.equal(findingsPage.evidenceTabs[0].hidden, true);
    findingsPage.setLouvainInsight({
        available: true, dataFingerprint: "stale",
        names: {}, communityPairs: [{ first: "OLD", second: "OLD2", tieCount: 999 }]
    });
    click(findingsPage.listeners.get("click"), "[data-group-refresh]",
        findingsPage.root.querySelector("[data-group-refresh]"));
    await flushPromises();
    const staleFallbackHtml =
        findingsPage.elements.get("[data-group-findings]").innerHTML;
    assert.match(staleFallbackHtml, /本次算法划分得到 <strong>2<\/strong> 个社群/);
    assert.match(staleFallbackHtml, /3 条连接不同社群/);
    assert.match(staleFallbackHtml, /社群间联系排行暂不可用/);
    assert.match(staleFallbackHtml, /不代表没有跨社群联系/);
    assert.doesNotMatch(staleFallbackHtml, /999|没有发现跨社群联系/);
    findingsPage.setLouvainInsight({
        available: false, reason: "analysis_graph_unavailable",
        dataFingerprint: "fixture", names: {}, communityPairs: []
    });
    click(findingsPage.listeners.get("click"), "[data-group-refresh]",
        findingsPage.root.querySelector("[data-group-refresh]"));
    await flushPromises();
    const unavailableInsightHtml =
        findingsPage.elements.get("[data-group-findings]").innerHTML;
    assert.match(unavailableInsightHtml, /分组结果已恢复/);
    assert.match(unavailableInsightHtml, /3 条连接不同社群/);
    assert.match(unavailableInsightHtml, /不代表没有跨社群联系/);
    assert.doesNotMatch(unavailableInsightHtml, /没有发现跨社群联系/);
    findingsPage.setLouvainResult({
        state: "COMPLETED_UNCONVERGED", complete: false, converged: false,
        communities: [], dataFingerprint: "fixture"
    });
    const callsBeforeUnconverged = findingsPage.calls.length;
    click(findingsPage.listeners.get("click"), "[data-group-refresh]",
        findingsPage.root.querySelector("[data-group-refresh]"));
    await flushPromises();
    assert.match(findingsPage.elements.get("[data-group-findings]").innerHTML, /没有完整可解释/);
    assert.equal(findingsPage.calls.slice(callsBeforeUnconverged)
        .some((call) => call.url.startsWith("network/louvain/insights")), false);
    findingsPage.window.socialNetworkGroupsPage.onHidden();

    const uploadPage = bootstrap({
        datasetId: "upload-temporary-session",
        datasetVersion: "session-temporary-session",
        sessionId: "temporary-session"
    });
    uploadPage.window.socialNetworkGroupsPage.onShown();
    await flushPromises();
    assert.equal(uploadPage.calls.length, 1);
    assert.equal(uploadPage.calls[0].url.startsWith("network/louvain"), true);
    assert.match(uploadPage.calls[0].url,
        /datasetVersion=session-temporary-session&sessionId=temporary-session/);
    assert.equal(uploadPage.analysisTabs[0].disabled, false);
    assert.equal(uploadPage.analysisTabs[1].disabled, false);
    assert.equal(
        uploadPage.elements.get("[data-group-start]").disabled,
        false
    );
    assert.equal(
        uploadPage.elements.get("[data-group-export]").disabled,
        true
    );
    uploadPage.setLouvainResult({
        state: "COMPLETED",
        complete: true,
        converged: true,
        dataFingerprint: "upload-fingerprint",
        cacheKey: "upload-cache-key",
        communities: [{ id: "C001", nodeCount: 2 }]
    });
    click(
        uploadPage.listeners.get("click"),
        "[data-group-refresh]",
        uploadPage.root.querySelector("[data-group-refresh]")
    );
    await flushPromises();
    assert.equal(
        uploadPage.elements.get("[data-group-export]").disabled,
        false
    );
    click(
        uploadPage.listeners.get("click"),
        "[data-group-export]",
        makeElement()
    );
    assert.equal(
        uploadPage.documentElements
            .get("social-network-group-export-dialog").open,
        true
    );
    await uploadPage.documentElements
        .get("social-network-group-export-submit").dispatch("click");
    await flushPromises();
    const uploadExport = uploadPage.calls.find((call) =>
        call.url.split("?")[0].endsWith("network/exports"));
    assert.ok(uploadExport);
    assert.match(uploadExport.url,
        /datasetVersion=session-temporary-session&sessionId=temporary-session/);
    assert.equal(JSON.parse(uploadExport.options.body).analysisType, "louvain");
    assert.equal(uploadPage.downloads.length, 1);

    uploadPage.setCliqueResult({
        state: "NOT_REQUESTED", complete: false, offset: 0, pageSize: 20,
        cliques: []
    });
    click(
        uploadPage.listeners.get("click"),
        "[data-group-analysis]",
        uploadPage.analysisTabs[0]
    );
    await flushPromises();
    assert.match(uploadPage.calls.at(-1).url,
        /^network\/cliques\?offset=0&pageSize=20&datasetVersion=session-temporary-session&sessionId=temporary-session$/);
    assert.equal(uploadPage.elements.get("[data-group-export]").disabled, true);
    click(
        uploadPage.listeners.get("click"),
        "[data-group-start]",
        uploadPage.root.querySelector("[data-group-start]")
    );
    await flushPromises();
    const cliqueStart = uploadPage.calls.find((call) =>
        call.method === "POST" && call.url.startsWith("network/cliques"));
    assert.ok(cliqueStart);
    assert.match(cliqueStart.url,
        /datasetVersion=session-temporary-session&sessionId=temporary-session/);

    uploadPage.setCliqueResult({
        state: "COMPLETED", complete: true, membershipListComplete: true,
        dataFingerprint: "upload-clique-fingerprint", cacheKey: "upload-clique-cache",
        retainedCliqueCount: 1, enumeratedCliqueCount: 1, offset: 0,
        cliques: [["a", "b", "c"]]
    });
    click(
        uploadPage.listeners.get("click"),
        "[data-group-refresh]",
        uploadPage.root.querySelector("[data-group-refresh]")
    );
    await flushPromises();
    click(
        uploadPage.listeners.get("click"),
        "[data-group-select]",
        makeElement({ groupSelect: "clique", groupIndex: "0" })
    );
    await flushPromises();
    const cliqueEvidence = uploadPage.calls.at(-1);
    assert.match(cliqueEvidence.url,
        /^network\/cliques\/0\/evidence\?section=members&offset=0&pageSize=20&datasetVersion=session-temporary-session&sessionId=temporary-session$/);
    assert.equal(uploadPage.elements.get("[data-group-export]").disabled, false);
    click(
        uploadPage.listeners.get("click"),
        "[data-group-export]",
        makeElement()
    );
    await uploadPage.documentElements
        .get("social-network-group-export-submit").dispatch("click");
    await flushPromises();
    const cliqueExport = uploadPage.calls
        .filter((call) => call.url.split("?")[0].endsWith("network/exports"))
        .at(-1);
    assert.ok(cliqueExport);
    assert.match(cliqueExport.url,
        /datasetVersion=session-temporary-session&sessionId=temporary-session/);
    const cliqueExportRequest = JSON.parse(cliqueExport.options.body);
    assert.equal(cliqueExportRequest.analysisType, "clique");
    assert.equal(cliqueExportRequest.evidence.type, "clique");
    assert.equal(cliqueExportRequest.evidence.id, "0");
    assert.equal(uploadPage.downloads.length, 2);

    uploadPage.setCliqueResult({
        state: "COMPLETED", complete: true, membershipListComplete: true,
        dataFingerprint: "upload-clique-empty-fingerprint",
        cacheKey: "upload-clique-empty-cache",
        retainedCliqueCount: 0, enumeratedCliqueCount: 0, offset: 0,
        cliques: []
    });
    click(
        uploadPage.listeners.get("click"),
        "[data-group-refresh]",
        uploadPage.root.querySelector("[data-group-refresh]")
    );
    await flushPromises();
    assert.equal(uploadPage.elements.get("[data-group-export]").disabled, false);
    assert.match(
        uploadPage.elements.get("[data-group-list]").innerHTML,
        /当前没有可展示的团成员列表/
    );
    click(
        uploadPage.listeners.get("click"),
        "[data-group-export]",
        makeElement()
    );
    assert.match(
        uploadPage.documentElements
            .get("social-network-group-export-note").textContent,
        /没有团条目/
    );

    uploadPage.setCliqueResult({
        state: "TIME_LIMIT", complete: false, membershipListComplete: false,
        dataFingerprint: "upload-clique-partial-fingerprint",
        cacheKey: "upload-clique-partial-cache",
        retainedCliqueCount: 1, enumeratedCliqueCount: 2, offset: 0,
        cliques: [["a", "b", "c"]]
    });
    click(
        uploadPage.listeners.get("click"),
        "[data-group-refresh]",
        uploadPage.root.querySelector("[data-group-refresh]")
    );
    await flushPromises();
    click(
        uploadPage.listeners.get("click"),
        "[data-group-export]",
        makeElement()
    );
    const partialExportNote = uploadPage.documentElements
        .get("social-network-group-export-note").textContent;
    assert.match(partialExportNote, /成员明细未全部保留/);
    assert.match(partialExportNote, /分析未完整完成/);

    console.log("social-network-groups-page: terminology annotations and behavior passed");
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
