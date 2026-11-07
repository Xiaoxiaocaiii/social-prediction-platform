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

const ensureView = functionBlock("ensureTopicTaskView", "startTopicTaskPage");
const renderer = functionBlock(
    "renderTopicTaskResult",
    "loadTopicTaskAnalysis"
);
const topicCountLabel = functionBlock(
    "topicCountLabel",
    "renderTopicRelatedUsers"
);
const relatedUsersRenderer = functionBlock(
    "renderTopicRelatedUsers",
    "renderTopicFollowing"
);
const followingRenderer = functionBlock(
    "renderTopicFollowing",
    "updateTopicTaskCategoryView"
);
const categoryViewUpdater = functionBlock(
    "updateTopicTaskCategoryView",
    "loadTopicTaskAnalysis"
);
const draftRenderer = functionBlock(
    "updateTopicTaskDraftStatus",
    "initializeTopicDialogs"
);
const detail = functionBlock(
    "openTopicTweetDetail",
    "renderTopicTaskResult"
);
const load = functionBlock(
    "loadTopicTaskAnalysis",
    "renderTopic"
);
const exportQuery = functionBlock(
    "currentExportQuery",
    "initializeModuleExportControls"
);
const pageShownStart = source.indexOf(
    "window.onSocialNetworkTaskPageShown = function (task) {"
);
const shown = source.slice(
    pageShownStart,
    source.indexOf("\n    };", pageShownStart)
);
const contextClear = functionBlock(
    "clearTopicTaskForDatasetChange",
    "clearContextBoundViews"
);

const escapeHtmlBlock = functionBlock("escapeHtml", "uploadStatus");
const escapeHtml = new Function(
    `${escapeHtmlBlock}; return escapeHtml;`
)();
const currentContext = {
    datasetId: "dataset-a",
    datasetVersion: "v1",
    sessionId: null
};
const contextKey = function (context) {
    return JSON.stringify([
        context.datasetId,
        context.datasetVersion || "",
        context.sessionId || ""
    ]);
};
const draftState = {
    draft: "unsubmitted draft",
    submittedTerm: "Climate"
};
const workspaceState = {
    getCondition(task) {
        assert.equal(task, "topic");
        return { ...draftState };
    }
};
const number = function (value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? String(parsed) : "0";
};

const resultNodes = {
    "[data-topic-results]": { hidden: true },
    "[data-topic-idle]": { hidden: false },
    "[data-topic-draft-status]": { textContent: "" },
    "[data-topic-results-title]": { textContent: "" },
    "[data-topic-result-count]": { textContent: "" },
    "[data-topic-result-status]": { textContent: "" },
    "[data-topic-result-table]": {
        innerHTML: "",
        setAttribute(name, value) {
            this[name] = value;
        }
    },
    "[data-topic-result-note]": { textContent: "" },
    "[data-topic-task-export]": { disabled: true },
    "[data-topic-query-error]": { hidden: false }
};
const categoryButtons = ["tweets", "users", "following"].map((category) => ({
    category,
    attributes: { "data-topic-category": category },
    classList: {
        toggle(name, selected) {
            this[name] = selected;
        }
    },
    getAttribute(name) {
        return this.attributes[name];
    },
    setAttribute(name, value) {
        this.attributes[name] = value;
    }
}));
const mount = {
    querySelector(selector) {
        assert.ok(resultNodes[selector], `unexpected selector: ${selector}`);
        return resultNodes[selector];
    },
    querySelectorAll(selector) {
        assert.equal(selector, "[data-topic-category]");
        return categoryButtons;
    }
};
const rows = Array.from({ length: 31 }, function (_, index) {
    const suffix = String(index).padStart(2, "0");
    return {
        tweetId: "tweet-" + suffix,
        authorUserId: index === 0 ? "author-1" : null,
        authorStatus: index === 0 ? "linked" : "unassigned",
        textOriginal: index === 0
            ? '<script>alert("x")</script>\nsecond line & more'
            : index === 29 ? "" : "original " + suffix,
        createdAt: "2026-01-" + suffix,
        likeCount: index === 1 || index === 2 ? 99 : 100 - index,
        replyCount: 3,
        repostCount: 1
    };
}).reverse();
const cachedResult = {
    contextKey: contextKey(currentContext),
    submittedTerm: "Climate",
    data: {
        query: "Climate",
        normalizedQuery: "climate",
        matchedTweetCount: 46,
        returnedTweetCount: 31,
        tweets: rows,
        relatedUserCount: 216,
        returnedUserCount: 31,
        users: Array.from({ length: 31 }, (_, index) => ({
            userId: "user-" + String(index).padStart(2, "0"),
            matchedTweetCount: 2,
            likeTotal: 100,
            replyTotal: 20,
            repostTotal: 4
        })),
        network: {
            nodeCount: 216,
            edgeCount: 500,
            totalEdgeCount: 4759,
            truncated: true,
            edges: Array.from({ length: 500 }, (_, index) => ({
                source: index === 0 ? "outside-source" : "user-" +
                    String(index % 30).padStart(2, "0"),
                target: index === 0 ? "outside-target" : "user-" +
                    String((index + 1) % 30).padStart(2, "0"),
                relationType: "following"
            }))
        }
    },
    tweets: []
};

const categoryRenderers = new Function(
    "formatNumber",
    "escapeHtml",
    `${topicCountLabel}; ${relatedUsersRenderer}; ${followingRenderer}; ` +
        `${categoryViewUpdater}; return { renderTopicRelatedUsers, ` +
        "renderTopicFollowing, updateTopicTaskCategoryView };"
)(number, escapeHtml);
const render = new Function(
    "mount",
    "topicTaskResult",
    "readDatasetContext",
    "datasetContextKey",
    "workspaceState",
    "formatNumber",
    "escapeHtml",
    "updateTopicTaskDraftStatus",
    "renderTopicRelatedUsers",
    "renderTopicFollowing",
    "updateTopicTaskCategoryView",
    `${renderer}; renderTopicTaskResult(mount);`
);
const updateDraftStatus = new Function(
    "mount",
    "workspaceState",
    `${draftRenderer}; updateTopicTaskDraftStatus(mount);`
);
render(
    mount,
    cachedResult,
    () => currentContext,
    contextKey,
    workspaceState,
    number,
    escapeHtml,
    (target, message) => {
        if (message) {
            resultNodes["[data-topic-draft-status]"].textContent = message;
        } else {
            updateDraftStatus(target, workspaceState);
        }
    },
    categoryRenderers.renderTopicRelatedUsers,
    categoryRenderers.renderTopicFollowing,
    categoryRenderers.updateTopicTaskCategoryView
);

assert.equal(resultNodes["[data-topic-results]"].hidden, false);
assert.equal(resultNodes["[data-topic-idle]"].hidden, true);
assert.match(
    resultNodes["[data-topic-result-count]"].textContent,
    /全部匹配 46 条 · 接口返回 31 条 · 页面展示 30 条/
);
assert.match(
    resultNodes["[data-topic-result-count]"].textContent,
    /上次成功查询/
);
assert.match(
    resultNodes["[data-topic-draft-status]"].textContent,
    /未提交草稿/
);
const table = resultNodes["[data-topic-result-table]"].innerHTML;
assert.equal((table.match(/data-topic-open-original=/g) || []).length, 30);
assert.doesNotMatch(table, /<script>alert/);
assert.match(table, /&lt;script&gt;alert\(&quot;x&quot;\)&lt;\/script&gt;/);
assert.match(table, /second line &amp; more/);
assert.match(table, /class="social-network-topic-task-original-empty">原文为空/);
const renderedIds = Array.from(
    table.matchAll(/<tr><td><strong>([^<]*)<\/strong>/g),
    (match) => match[1]
);
assert.equal(renderedIds.length, 30, table.slice(0, 400));
assert.equal(renderedIds[0], "tweet-00");
assert.deepEqual(renderedIds.slice(1, 3), ["tweet-01", "tweet-02"]);
assert.match(table, /接口统计值/);
assert.match(table, /<\/tr><\/thead><tbody>/);
assert.equal(categoryButtons[0].attributes["aria-pressed"], "true");
assert.match(ensureView, /还没有查询结果/);
assert.match(ensureView, /点击“查询主题”，匹配推文会显示在这里/);
assert.match(ensureView, /data-topic-category="tweets"/);
assert.match(ensureView, /data-topic-category="users"/);
assert.match(ensureView, /data-topic-category="following"/);
assert.match(
    source,
    /setCondition\("topic",\s*\{\s*category:[\s\S]*?\}, false\)/
);
assert.match(
    source,
    /openSocialNetworkUserDetail\(\s*userId,\s*null,\s*userDetailButton\s*\)/
);

draftState.category = "users";
render(
    mount,
    cachedResult,
    () => currentContext,
    contextKey,
    workspaceState,
    number,
    escapeHtml,
    (target) => updateDraftStatus(target, workspaceState),
    categoryRenderers.renderTopicRelatedUsers,
    categoryRenderers.renderTopicFollowing,
    categoryRenderers.updateTopicTaskCategoryView
);
assert.equal(resultNodes["[data-topic-results-title]"].textContent, "相关用户");
assert.match(
    resultNodes["[data-topic-result-count]"].textContent,
    /主题匹配推文 46 条 · 全部相关已关联用户 216 位 · 接口返回 31 位 · 页面展示 30 位/
);
assert.equal(
    (resultNodes["[data-topic-result-table]"].innerHTML.match(
        /data-topic-user-detail=/g
    ) || []).length,
    30
);
assert.match(
    resultNodes["[data-topic-result-note]"].textContent,
    /不是有效样本数或均值/
);
assert.equal(categoryButtons[0].attributes["aria-pressed"], "false");
assert.equal(categoryButtons[1].attributes["aria-pressed"], "true");

draftState.category = "following";
render(
    mount,
    cachedResult,
    () => currentContext,
    contextKey,
    workspaceState,
    number,
    escapeHtml,
    (target) => updateDraftStatus(target, workspaceState),
    categoryRenderers.renderTopicRelatedUsers,
    categoryRenderers.renderTopicFollowing,
    categoryRenderers.updateTopicTaskCategoryView
);
const followingTable = resultNodes["[data-topic-result-table]"].innerHTML;
assert.equal(resultNodes["[data-topic-results-title]"].textContent, "关注关系");
assert.match(
    resultNodes["[data-topic-result-count]"].textContent,
    /全部真实关注边 4759 条 · 接口返回 500 条（接口最多 500 条） · 页面展示 30 条/
);
assert.match(followingTable, /outside-source/);
assert.match(followingTable, /outside-target/);
const followingBody = followingTable.match(/<tbody>([\s\S]*?)<\/tbody>/)[1];
assert.equal((followingBody.match(/<tr>/g) || []).length, 30);
assert.match(
    resultNodes["[data-topic-result-note]"].textContent,
    /端点可能不在用户表前 30 位/
);
assert.equal(categoryButtons[2].attributes["aria-pressed"], "true");

cachedResult.data.matchedTweetCount = 3;
cachedResult.data.relatedUserCount = 0;
cachedResult.data.returnedUserCount = 0;
cachedResult.data.users = [];
draftState.category = "users";
render(
    mount,
    cachedResult,
    () => currentContext,
    contextKey,
    workspaceState,
    number,
    escapeHtml,
    (target) => updateDraftStatus(target, workspaceState),
    categoryRenderers.renderTopicRelatedUsers,
    categoryRenderers.renderTopicFollowing,
    categoryRenderers.updateTopicTaskCategoryView
);
assert.match(
    resultNodes["[data-topic-result-table]"].innerHTML,
    /存在匹配推文，但没有可纳入主题用户列表的已关联作者/
);
assert.match(
    resultNodes["[data-topic-result-count]"].textContent,
    /接口返回 0 位 · 页面展示 0 位/
);

cachedResult.data.relatedUserCount = 2;
cachedResult.data.users = [{ userId: "linked-1" }, { userId: "linked-2" }];
cachedResult.data.returnedUserCount = 2;
cachedResult.data.network.totalEdgeCount = 0;
cachedResult.data.network.edges = [];
draftState.category = "following";
render(
    mount,
    cachedResult,
    () => currentContext,
    contextKey,
    workspaceState,
    number,
    escapeHtml,
    (target) => updateDraftStatus(target, workspaceState),
    categoryRenderers.renderTopicRelatedUsers,
    categoryRenderers.renderTopicFollowing,
    categoryRenderers.updateTopicTaskCategoryView
);
assert.match(
    resultNodes["[data-topic-result-table]"].innerHTML,
    /没有返回真实关注边；这不表示这些用户在全网中孤立/
);

const detailNodes = {
    "social-network-topic-detail-query": { textContent: "" },
    "social-network-topic-detail-dataset": { textContent: "" },
    "social-network-topic-detail-id": { textContent: "" },
    "social-network-topic-detail-author-state": { textContent: "" },
    "social-network-topic-detail-time": { textContent: "" },
    "social-network-topic-detail-original": { textContent: "" },
    "social-network-topic-detail-likes": { textContent: "" },
    "social-network-topic-detail-replies": { textContent: "" },
    "social-network-topic-detail-reposts": { textContent: "" },
    "social-network-topic-detail-author": { hidden: false }
};
const detailDialog = {
    open: false,
    showModal() {
        this.open = true;
    }
};
const detailFunction = new Function(
    "topicTaskResult",
    "datasetContextKey",
    "readDatasetContext",
    "initializeTopicDialogs",
    "document",
    "formatNumber",
    "var topicDetailAuthorUserId = ''; var topicDetailOpener = null;" +
        `${detail}; return openTopicTweetDetail;`
)(
    cachedResult,
    contextKey,
    () => currentContext,
    () => {},
    {
        getElementById(id) {
            return id === "social-network-topic-detail-dialog"
                ? detailDialog
                : detailNodes[id];
        }
    },
    number
);
const opener = {};
detailFunction(0, opener);
assert.equal(detailDialog.open, true);
assert.equal(
    detailNodes["social-network-topic-detail-original"].textContent,
    '<script>alert("x")</script>\nsecond line & more'
);
assert.equal(
    detailNodes["social-network-topic-detail-author"].hidden,
    false
);
cachedResult.tweets = [{ tweetId: "empty", textOriginal: "" }];
detailFunction(0, opener);
assert.equal(
    detailNodes["social-network-topic-detail-original"].textContent,
    "原文为空"
);
assert.equal(
    detailNodes["social-network-topic-detail-author"].hidden,
    true
);

const getExportQuery = new Function(
    "workspaceState",
    `${exportQuery}; return currentExportQuery;`
)(workspaceState);
assert.deepEqual(getExportQuery("topic"), { term: "Climate" });
assert.match(load, /buildDatasetUrl\("text\/topic"/);
assert.match(load, /limit: 30/);
assert.match(load, /data\.query \|\| data\.normalizedQuery \|\| query/);
assert.match(load, /sameDatasetContext\(context, readDatasetContext\(\)\)/);
assert.match(shown, /workspaceState\.invalidateRequest\("topic"\)/);
assert.match(contextClear, /draft: "",\s*submittedTerm: null/);
assert.match(
    source,
    /activeModuleExportSnapshot\.query\.term[\s\S]*?不使用输入草稿/
);

assert.match(index, /style\.css\?v=20261107-overview-heading-gap-1/);
assert.match(index, /social-network\.js\?v=20261107-overview-heading-gap-1/);
assert.doesNotMatch(ensureView, /social-network-content-task-nav/);
assert.match(ensureView,
    /TOPIC SEARCH RESULTS<\/p><h2>主题查询与相关推文<\/h2>/);
assert.match(ensureView, /还没有查询结果[\s\S]*?点击“查询主题”/);
assert.match(css,
    /\.social-network-topic-task-idle\[hidden\]\s*\{\s*display:\s*none;/);
assert.match(index, /id="social-network-topic-detail-dialog"/);
assert.match(index, /id="social-network-topic-rules-dialog"/);
assert.match(index, /id="social-network-topic-detail-original"[\s\S]*?<\/pre>/);
assert.match(index, /中文处理按连续中文片段每两个汉字切分/);
assert.match(css, /social-network-topic-detail-original[\s\S]*?white-space: pre-wrap/);
assert.match(css, /social-network-topic-task-excerpt[\s\S]*?-webkit-line-clamp: 2/);
assert.match(css, /social-network-topic-task-categories button:focus-visible/);
assert.match(css, /social-network-topic-task-categories[\s\S]*?grid-template-columns: repeat\(3/);
assert.match(css, /@media \(max-width: 760px\)[\s\S]*?social-network-topic-dialog/);

console.log("social-network-topic: checks passed");
