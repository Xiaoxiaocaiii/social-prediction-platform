(function () {
    "use strict";

    var datasetId = "think-tank-social-network";
    var loaded = false;
    var statusLabels = {
        idle: "待加载",
        loading: "加载中",
        available: "可用",
        empty: "暂无数据",
        available_with_warnings: "可用（含质量警告）",
        limited: "部分可用",
        failed: "加载失败",
        disabled: "已禁用",
        unavailable: "不可用",
        checking: "检查中"
    };

    function formatNumber(value) {
        return value === null || value === undefined
            ? "-"
            : Number(value).toLocaleString("zh-CN");
    }

    function formatDecimal(value) {
        return value === null || value === undefined
            ? "-"
            : Number(value).toLocaleString("zh-CN", { maximumFractionDigits: 2 });
    }

    function formatDensity(value) {
        return value === null || value === undefined
            ? "-"
            : Number(value).toLocaleString("zh-CN", { maximumFractionDigits: 4 });
    }

    function setText(id, value) {
        var element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    function statusLabel(status) {
        var value = status && typeof status === "object"
            ? (status.state || status.status)
            : status;
        return statusLabels[value] || value || "未知";
    }

    function renderModuleError(elementId, message, error) {
        if (typeof console !== "undefined" && console.error) {
            console.error("[social-network] " + message, error);
        }
        var element = document.getElementById(elementId);
        if (element) {
            element.innerHTML = '<div class="social-network-empty is-error">' +
                escapeHtml(message) + '</div>';
        }
    }

    async function request(url) {
        var response = await fetch(url, {
            headers: { "Accept": "application/json" }
        });
        var body = await response.json();
        if (!response.ok || !body.success) {
            throw new Error(body.message || "社交网络接口请求失败");
        }
        return body;
    }

    async function exportResult() {
        var format = document.getElementById("social-network-export-format").value;
        var exportType = document.getElementById("social-network-export-type").value;
        var status = document.getElementById("social-network-export-status");
        var output = document.getElementById("social-network-export-result");
        var button = document.getElementById("social-network-export-button");
        status.textContent = "生成中";
        output.textContent = "正在生成导出结果…";
        button.disabled = true;
        try {
            var response = await fetch(
                "/api/social-network/datasets/" + encodeURIComponent(datasetId) + "/exports",
                {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        exportType: exportType,
                        format: format
                    })
                });
            var body = await response.json();
            if (!response.ok || !body.success) {
                throw new Error(body.message || "导出暂时不可用");
            }
            var result = body.data && body.data.result;
            output.textContent = format === "json"
                ? JSON.stringify(result, null, 2)
                : String(result == null ? "" : result);
            status.textContent = "已生成 · " +
                formatNumber(body.data && body.data.contentLength) + " 字节";
        } catch (error) {
            status.textContent = "生成失败";
            output.textContent = "导出暂时不可用，请稍后重试。";
            if (typeof console !== "undefined" && console.error) {
                console.error("[social-network] export failed", error);
            }
        } finally {
            button.disabled = false;
        }
    }

    function renderSummary(body) {
        var data = body.data || {};
        var counts = data.counts || {};
        setText("social-network-dataset-status", statusLabel(data));
        setText("social-network-dataset-version", "版本 " + (data.version || "-"));
        setText("social-network-user-count", formatNumber(
            counts.normalized_user_count || counts.user_record_count));
        setText("social-network-relationship-count", formatNumber(
            counts.relationship_candidate_count));
        setText("social-network-tweet-count", formatNumber(
            counts.tweet_record_count));
    }

    function renderCapabilities(body) {
        var data = body.data || {};
        setText("social-network-capability-version", data.capabilityVersion || "-");
        var container = document.getElementById("social-network-capabilities");
        var items = data.items || [];
        if (!items.length) {
            container.innerHTML = '<div class="social-network-empty">暂无能力状态。</div>';
            return;
        }
        container.innerHTML = items.map(function (item) {
            var status = item.state || item.status || "checking";
            return '<article class="social-network-capability capability-' + status + '">' +
                '<div class="social-network-capability-top">' +
                '<span class="social-network-capability-module">' +
                escapeHtml(item.moduleId || "") + '</span>' +
                '<span class="social-network-capability-status">' +
                escapeHtml(statusLabel(status)) + '</span></div>' +
                '<h3>' + escapeHtml(item.capabilityId || "") + '</h3>' +
                '<p>' + escapeHtml(item.reasonMessage || "") + '</p>' +
                '<small>' + escapeHtml(item.fallback || "") + '</small>' +
                '</article>';
        }).join("");
    }

    function renderNetworkSummary(body) {
        var data = body.data || {};
        setText("social-network-scope", data.scope || "-");
        setText("social-network-density", data.density === null
            ? "不可计算"
            : formatDensity(data.density));
        setText("social-network-isolated-count", formatNumber(data.isolatedNodeCount));
        setText("social-network-external-count", formatNumber(data.externalNodeCount));
        var pageRank = data.pageRank || {};
        setText("social-network-pagerank-state", pageRank.converged
            ? "已收敛 · " + pageRank.iterations + " 次"
            : "未收敛");

        var interactions = data.interactionScale || {};
        var metrics = interactions.metrics || {};
        var container = document.getElementById("social-network-interactions");
        if (interactions.state === "empty" || interactions.available === false) {
            container.innerHTML = '<div class="social-network-empty">' +
                escapeHtml(interactions.reason || "互动摘要不可用") + '</div>';
            return;
        }
        var labels = {
            like_count: "点赞",
            reply_count: "评论",
            repost_count: "转发"
        };
        container.innerHTML = Object.keys(labels).map(function (key) {
            var metric = metrics[key] || {};
            return '<div class="social-network-interaction-row">' +
                '<div><strong>' + labels[key] + '</strong>' +
                '<span>有效样本 ' + formatNumber(metric.validSampleCount) + '</span></div>' +
                '<b>' + formatNumber(metric.total) + '</b>' +
                '<small>平均 ' + formatDecimal(metric.average) + '</small>' +
                '</div>';
        }).join("");
    }

    function renderQuality(body) {
        var data = body.data || {};
        setText("social-network-quality-status", statusLabel(data));
        var container = document.getElementById("social-network-quality-items");
        var warnings = body.warnings || [];
        if (!warnings.length) {
            container.innerHTML = '<div class="social-network-quality-ok">当前没有需要单独提示的质量问题。</div>';
            return;
        }
        container.innerHTML = warnings.map(function (warning) {
            var count = Number(warning.count || 0);
            var countLabel = count > 0 ? "（" + formatNumber(count) + " 条）" : "";
            return '<div class="social-network-quality-item">' +
                '<span class="social-network-quality-badge">' +
                escapeHtml(statusLabel(warning.severity || "limited")) + '</span>' +
                '<div><strong>' + escapeHtml(warning.code || "QUALITY") +
                escapeHtml(countLabel) + '</strong>' +
                '<p>' + escapeHtml(warning.message || "") + '</p></div>' +
                '</div>';
        }).join("");
    }

    function renderUserResults(body) {
        var container = document.getElementById("social-network-user-results");
        var data = body.data || {};
        var items = data.items || [];
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">没有匹配的用户，请调整搜索条件。</div>';
            return;
        }
        container.innerHTML = items.map(function (item) {
            var title = item.displayName || item.username || item.userId;
            return '<button type="button" class="social-network-user-result" data-user-id="' +
                escapeHtml(item.userId) + '">' +
                '<strong>' + escapeHtml(title) + '</strong>' +
                '<span>' + escapeHtml(item.username || "未提供用户名") +
                ' · ' + escapeHtml(item.userId) + '</span></button>';
        }).join("");
        container.querySelectorAll("[data-user-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadLocalNetwork(button.getAttribute("data-user-id"));
            });
        });
    }

    function renderLocalNetwork(body) {
        var data = body.data || {};
        var container = document.getElementById("social-network-local-view");
        var nodes = data.nodes || [];
        var edges = data.edges || [];
        setText("social-network-local-status",
            nodes.length + " 个节点 · " + edges.length + " 条有向边");
        if (data.state === "empty" || !nodes.length) {
            container.innerHTML = '<div class="social-network-empty">当前中心用户没有可展示的关系。</div>';
            return;
        }
        var nodeHtml = nodes.map(function (node) {
            var label = node.displayName || node.username || node.id;
            var type = node.nodeType === "external" ? "外部节点" : "用户节点";
            return '<div class="social-network-local-node">' +
                '<strong>' + escapeHtml(label) + '</strong>' +
                '<span>' + escapeHtml(type) + ' · ' + escapeHtml(node.id) + '</span>' +
                '<small>入度 ' + formatNumber(node.inDegree) +
                ' · 出度 ' + formatNumber(node.outDegree) + '</small></div>';
        }).join("");
        var edgeHtml = edges.slice(0, 20).map(function (edge) {
            return '<li><code>' + escapeHtml(edge.source) + '</code>' +
                ' → <code>' + escapeHtml(edge.target) + '</code></li>';
        }).join("");
        var truncation = data.truncated
            ? '<p class="social-network-note">' + escapeHtml(data.truncationReason ||
                "当前局部网络已按上限截取。") + '</p>' : "";
        container.innerHTML = '<div class="social-network-local-meta">' +
            '<span>中心节点：' + escapeHtml(data.centerNodeId || "-") + '</span>' +
            '<span>范围：' + escapeHtml(data.scope || "-") + '</span>' +
            '</div><div id="social-network-local-chart" class="social-network-local-chart"></div>' +
            '<div class="social-network-local-grid">' +
            '<div><h3>节点</h3><div class="social-network-local-nodes">' +
            nodeHtml + '</div></div><div><h3>有向关系（最多展示 20 条）</h3>' +
            '<ol class="social-network-local-edges">' + edgeHtml + '</ol></div></div>' +
            truncation;
        if (typeof echarts !== "undefined") {
            var chart = echarts.init(document.getElementById("social-network-local-chart"));
            chart.setOption({
                animation: false,
                tooltip: { formatter: "{b}" },
                series: [{
                    type: "graph",
                    layout: "force",
                    roam: true,
                    draggable: true,
                    symbolSize: 18,
                    edgeSymbol: ["none", "arrow"],
                    edgeSymbolSize: 8,
                    data: nodes.map(function (node) {
                        return {
                            id: node.id,
                            name: node.displayName || node.username || node.id,
                            category: node.nodeType === "external" ? 1 : 0,
                            itemStyle: node.id === data.centerNodeId
                                ? { color: "#ff7a3c" } : undefined
                        };
                    }),
                    links: edges.map(function (edge) {
                        return { source: edge.source, target: edge.target };
                    }),
                    categories: [{ name: "用户节点" }, { name: "外部节点" }],
                    force: { repulsion: 80, edgeLength: 90 }
                }]
            });
            window.addEventListener("resize", function () {
                chart.resize();
            }, { once: true });
        }
    }

    async function searchUsers() {
        var query = document.getElementById("social-network-user-query").value;
        var result = await request("/api/social-network/datasets/" +
            encodeURIComponent(datasetId) + "/users/search?query=" +
            encodeURIComponent(query) + "&limit=20");
        renderUserResults(result);
    }

    async function loadLocalNetwork(centerNodeId) {
        var depth = document.getElementById("social-network-depth").value;
        var topK = document.getElementById("social-network-top-k").value;
        var includeExternal = document.getElementById("social-network-include-external").checked;
        var status = document.getElementById("social-network-local-status");
        status.textContent = "正在加载局部网络…";
        try {
            var result = await request("/api/social-network/datasets/" +
                encodeURIComponent(datasetId) + "/network/local?centerNodeId=" +
                encodeURIComponent(centerNodeId) + "&depth=" + depth + "&topK=" + topK +
                "&includeExternalNodes=" + includeExternal);
            renderLocalNetwork(result);
        } catch (error) {
            status.textContent = "局部网络不可用";
            renderModuleError("social-network-local-view", "局部网络暂时不可用。", error);
        }
    }

    function renderRanking(body) {
        var data = body.data || {};
        var items = data.items || [];
        setText("social-network-ranking-status",
            data.label + " · " + formatNumber(data.sampleSize) + " 个样本");
        setText("social-network-ranking-note",
            (data.metricType === "network" ? "计算指标" : "原始账户字段") +
            "： " + (data.label || "-") + "。当前范围：" + (data.scope || "-") +
            "，不生成综合影响力分。");
        var container = document.getElementById("social-network-ranking-table");
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">' +
                escapeHtml(data.reason || "当前指标暂无可用样本。") + '</div>';
            return;
        }
        container.innerHTML = '<table><thead><tr>' +
            '<th>名次</th><th>节点</th><th>数值</th><th>状态</th>' +
            '<th>样本量/范围</th><th>进入原因</th></tr></thead><tbody>' +
            items.map(function (item) {
                var name = item.displayName || item.username || item.nodeId;
                return '<tr class="social-network-ranking-row" data-user-id="' +
                    escapeHtml(item.nodeId) + '"><td>' + formatNumber(item.rank) + '</td>' +
                    '<td><strong>' + escapeHtml(name) + '</strong><small>' +
                    escapeHtml(item.nodeId) + '</small></td><td>' +
                    formatDecimal(item.value) + '</td><td>' +
                    escapeHtml(item.status || "available") + '</td><td>' +
                    formatNumber(item.sampleSize) + ' · ' + escapeHtml(data.scope || "-") +
                    '</td><td>' + escapeHtml(item.reason || "") + '</td></tr>';
            }).join("") + '</tbody></table>';
        container.querySelectorAll(".social-network-ranking-row").forEach(function (row) {
            row.addEventListener("click", function () {
                loadUserDetail(row.getAttribute("data-user-id"));
            });
        });
    }

    async function loadRanking() {
        var metric = document.getElementById("social-network-ranking-metric").value;
        var result = await request("/api/social-network/datasets/" +
            encodeURIComponent(datasetId) + "/rankings?metric=" +
            encodeURIComponent(metric) + "&limit=20");
        renderRanking(result);
    }

    function renderUserDetail(body, target) {
        target = target || {
            statusId: "social-network-user-drawer-status",
            bodyId: "social-network-user-drawer-body"
        };
        var data = body.data || {};
        var profile = data.profile || {};
        var network = data.networkPosition || {};
        var raw = data.rawAccountFields || {};
        var tweets = data.tweetEvidence || {};
        var metrics = tweets.interactions || {};
        setText(target.statusId, data.userId + " · " +
            (data.nodeType === "external" ? "外部节点" : "用户节点"));
        var interactionHtml = Object.keys({
            like_count: "点赞",
            reply_count: "评论",
            repost_count: "转发"
        }).map(function (key) {
            var metric = metrics[key] || {};
            return '<div><span>' + ({ like_count: "点赞", reply_count: "评论",
                repost_count: "转发" }[key]) + '</span><strong>' +
                formatNumber(metric.total) + '</strong><small>平均 ' +
                formatDecimal(metric.average) + '</small></div>';
        }).join("");
        var neighborItems = (data.neighbors || {}).items || [];
        document.getElementById(target.bodyId).innerHTML =
            '<div class="social-network-detail-identity"><h3>' +
            escapeHtml(profile.displayName || profile.username || data.userId) +
            '</h3><p>用户名：' + escapeHtml(profile.username || "资料未采集") +
            ' · 账号 ID：' + escapeHtml(data.userId) + '</p><p>资料状态：' +
            escapeHtml(profile.status || "资料未采集") + '</p><p>' +
            escapeHtml(profile.bio || "未提供简介") + '</p></div>' +
            '<div class="social-network-detail-grid"><div><h3>原始账户字段</h3>' +
            '<p>粉丝数：' + formatNumber(raw.followersCount) + '</p><p>关注数：' +
            formatNumber(raw.followingCount) + '</p><p>用户资料推文总数：' +
            formatNumber(raw.declaredTweetCount) + '</p></div><div><h3>网络位置</h3>' +
            '<p>入度：' + formatNumber(network.inDegree) + '</p><p>出度：' +
            formatNumber(network.outDegree) + '</p><p>PageRank：' +
            formatDecimal(network.pageRank) + '</p></div></div>' +
            '<div class="social-network-detail-interactions"><h3>推文样本与互动摘要</h3>' +
            '<p>当前采集且成功关联的推文样本：' + formatNumber(tweets.sampleCount) +
            '</p><div>' + interactionHtml + '</div></div>' +
            '<div><h3>关注邻居摘要</h3><p>当前展示 ' +
            formatNumber(neighborItems.length) + ' 条关系，方向沿用 source → target。</p></div>' +
            '<p class="social-network-note">关键词和主题摘要将在文本业务任务中接入；资料未采集时不补充用户信息。</p>';
    }

    function closeSocialUserDrawer() {
        var drawer = document.getElementById("social-network-user-drawer");
        drawer.classList.remove("is-open");
        drawer.setAttribute("aria-hidden", "true");
    }

    async function loadUserDetail(userId) {
        var result = await request("/api/social-network/datasets/" +
            encodeURIComponent(datasetId) + "/users/" + encodeURIComponent(userId));
        renderUserDetail(result, {
            statusId: "social-network-user-drawer-status",
            bodyId: "social-network-user-drawer-body"
        });
        var drawer = document.getElementById("social-network-user-drawer");
        drawer.classList.add("is-open");
        drawer.setAttribute("aria-hidden", "false");
    }

    var interactionLabels = {
        like_count: "点赞",
        reply_count: "评论",
        repost_count: "转发"
    };

    function interactionBasePath() {
        return "/api/social-network/datasets/" + encodeURIComponent(datasetId) +
            "/interactions/";
    }

    function renderInteractionSummary(body) {
        var data = body.data || {};
        var scale = data.scale || {};
        var container = document.getElementById("social-network-interaction-summary");
        if (data.state === "empty") {
            container.innerHTML = '<div class="social-network-empty">当前没有可用互动摘要。</div>';
            return;
        }
        var metric = document.getElementById("social-network-interaction-metric").value;
        var summary = (scale.metrics || {})[metric] || {};
        var label = interactionLabels[metric] || metric;
        container.innerHTML =
            '<div class="social-network-interaction-kpi">' +
            '<span>当前指标</span><strong>' + escapeHtml(label) + '</strong>' +
            '<small>独立统计</small></div>' +
            '<div class="social-network-interaction-kpi">' +
            '<span>总量</span><strong>' + formatNumber(summary.total) + '</strong>' +
            '<small>有效样本 ' + formatNumber(summary.validSampleCount) + '</small></div>' +
            '<div class="social-network-interaction-kpi">' +
            '<span>平均值</span><strong>' + formatDecimal(summary.average) + '</strong>' +
            '<small>中位数 ' + formatDecimal(summary.median) + '</small></div>' +
            '<div class="social-network-interaction-kpi">' +
            '<span>数据覆盖</span><strong>' + formatNumber(scale.analysisSampleCount) +
            '</strong><small>缺失 ' + formatNumber(summary.missingCount) +
            ' · 零值 ' + formatNumber(summary.zeroCount) + '</small></div>';
    }

    function renderInteractionTweets(body) {
        var data = body.data || {};
        var items = data.items || [];
        var container = document.getElementById("social-network-interaction-tweets");
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">当前指标没有可用推文样本。</div>';
            return;
        }
        container.innerHTML = '<table><thead><tr><th>推文 ID</th><th>作者</th>' +
            '<th>互动值</th><th>时间</th></tr></thead><tbody>' +
            items.map(function (item) {
                return '<tr><td>' + escapeHtml(item.tweetId || "-") +
                    '</td><td>' + escapeHtml(item.authorUserId || "未归属来源") +
                    '</td><td><strong>' + formatNumber(item.value) +
                    '</strong></td><td>' + escapeHtml(item.createdAt || "-") +
                    '</td></tr>';
            }).join("") + "</tbody></table>";
    }

    function renderInteractionUsers(body) {
        var data = body.data || {};
        var items = data.items || [];
        var container = document.getElementById("social-network-interaction-users");
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">当前指标没有可用用户样本。</div>';
            return;
        }
        container.innerHTML = '<table><thead><tr><th>用户 ID</th><th>总量</th>' +
            '<th>平均</th><th>样本</th></tr></thead><tbody>' +
            items.map(function (item) {
                return '<tr><td><button class="social-network-inline-link" ' +
                    'data-social-user-id="' + escapeHtml(item.userId) + '">' +
                    escapeHtml(item.userId) + '</button>' +
                    '</td><td><strong>' + formatNumber(item.total) +
                    '</strong></td><td>' + formatDecimal(item.average) +
                    '</td><td>' + formatNumber(item.sampleCount) +
                    '</td></tr>';
            }).join("") + "</tbody></table>";
        container.querySelectorAll("[data-social-user-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadUserDetail(button.getAttribute("data-social-user-id"));
            });
        });
    }

    function renderInteractionTimeseries(body) {
        var data = body.data || {};
        var items = data.items || [];
        var container = document.getElementById("social-network-interaction-timeseries");
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">当前指标没有可用时间样本。</div>';
            return;
        }
        var visible = items.slice(-30);
        var max = Math.max.apply(null, visible.map(function (item) {
            return Number(item.total) || 0;
        })) || 1;
        container.innerHTML = visible.map(function (item) {
            var width = Math.max(2, ((Number(item.total) || 0) / max) * 100);
            return '<div class="social-network-timeseries-row">' +
                '<span>' + escapeHtml(item.period) + '</span>' +
                '<div><i style="width:' + width.toFixed(2) + '%"></i></div>' +
                '<strong>' + formatNumber(item.total) + '</strong></div>';
        }).join("");
    }

    async function loadInteractionAnalysis() {
        var metric = document.getElementById("social-network-interaction-metric").value;
        var query = "?metric=" + encodeURIComponent(metric);
        var results = await Promise.allSettled([
            request(interactionBasePath() + "summary"),
            request(interactionBasePath() + "tweets" + query + "&limit=10"),
            request(interactionBasePath() + "users" + query + "&limit=10"),
            request(interactionBasePath() + "timeseries" + query)
        ]);
        var renderers = [
            ["social-network-interaction-summary", renderInteractionSummary,
                "互动摘要暂时不可用。"],
            ["social-network-interaction-tweets", renderInteractionTweets,
                "互动推文暂时不可用。"],
            ["social-network-interaction-users", renderInteractionUsers,
                "互动用户暂时不可用。"],
            ["social-network-interaction-timeseries", renderInteractionTimeseries,
                "互动时间序列暂时不可用。"]
        ];
        var failed = 0;
        results.forEach(function (result, index) {
            if (result.status === "fulfilled") {
                renderers[index][1](result.value);
            } else {
                failed += 1;
                renderModuleError(renderers[index][0], renderers[index][2], result.reason);
            }
        });
        setText("social-network-interaction-status", failed === results.length
            ? "互动分析暂时不可用"
            : (interactionLabels[metric] || metric) + " · " +
              (failed ? "部分可用" : "已加载"));
    }

    function renderTopic(body) {
        var data = body.data || {};
        setText("social-network-topic-status",
            data.state === "empty" || data.available === false
                ? "无匹配结果"
                : data.state === "limited"
                    ? "部分可用 · " + formatNumber(data.matchedTweetCount) + " 条推文"
                : "已匹配 · " + formatNumber(data.matchedTweetCount) + " 条推文");
        document.getElementById("social-network-topic-summary").innerHTML =
            '<div class="social-network-interaction-kpi"><span>匹配推文</span><strong>' +
            formatNumber(data.matchedTweetCount) + '</strong><small>精确词元匹配</small></div>' +
            '<div class="social-network-interaction-kpi"><span>关联用户</span><strong>' +
            formatNumber(data.relatedUserCount) + '</strong><small>排除未归属来源</small></div>' +
            '<div class="social-network-interaction-kpi"><span>关系边</span><strong>' +
            formatNumber((data.network || {}).edgeCount) + '</strong><small>真实关注关系</small></div>' +
            '<div class="social-network-interaction-kpi"><span>处理版本</span><strong>' +
            escapeHtml(data.textProcessingVersion || "-") + '</strong><small>原文保留</small></div>';
        var tweets = data.tweets || [];
        document.getElementById("social-network-topic-tweets").innerHTML =
            data.state !== "empty" && tweets.length
            ? '<table><thead><tr><th>推文 ID</th><th>作者</th><th>原文</th><th>点赞</th></tr></thead><tbody>' +
            tweets.map(function (item) {
                return '<tr><td>' + escapeHtml(item.tweetId) + '</td><td>' +
                    escapeHtml(item.authorUserId || "未归属来源") + '</td><td>' +
                    escapeHtml(item.textOriginal || "-") + '</td><td>' +
                    formatNumber(item.likeCount) + '</td></tr>';
            }).join("") + "</tbody></table>"
            : '<div class="social-network-empty">没有匹配推文。</div>';
        var users = data.users || [];
        document.getElementById("social-network-topic-users").innerHTML = users.length
            ? '<table><thead><tr><th>用户 ID</th><th>匹配推文</th><th>点赞总量</th><th>评论总量</th></tr></thead><tbody>' +
            users.map(function (item) {
                return '<tr><td><button class="social-network-inline-link" data-social-user-id="' +
                    escapeHtml(item.userId) + '">' + escapeHtml(item.userId) + '</button></td><td>' +
                    formatNumber(item.matchedTweetCount) + '</td><td>' +
                    formatNumber(item.likeTotal) + '</td><td>' +
                    formatNumber(item.replyTotal) + '</td></tr>';
            }).join("") + "</tbody></table>"
            : '<div class="social-network-empty">没有已关联用户。</div>';
        document.querySelectorAll("#social-network-topic-users [data-social-user-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadUserDetail(button.getAttribute("data-social-user-id"));
            });
        });
        var network = data.network || {};
        var edges = network.edges || [];
        document.getElementById("social-network-topic-network").innerHTML = edges.length
            ? '<div class="social-network-topic-network-meta">节点 ' + formatNumber(network.nodeCount) +
            ' · 边 ' + formatNumber(network.edgeCount) + ' · 仅显示真实关注关系</div>' +
            '<div class="social-network-topic-edge-list">' + edges.slice(0, 30).map(function (edge) {
                return '<span>' + escapeHtml(edge.source) + ' → ' + escapeHtml(edge.target) + '</span>';
            }).join("") + '</div>'
            : '<div class="social-network-empty">当前主题相关用户之间没有可展示的真实关注边。</div>';
    }

    async function loadTopicAnalysis() {
        var input = document.getElementById("social-network-topic-query");
        var query = input.value.trim();
        if (!query) {
            return;
        }
        var button = document.getElementById("social-network-topic-button");
        button.disabled = true;
        button.textContent = "分析中…";
        try {
            var result = await request("/api/social-network/datasets/" +
                encodeURIComponent(datasetId) + "/text/topic?query=" +
                encodeURIComponent(query) + "&limit=30");
            renderTopic(result);
        } finally {
            button.disabled = false;
            button.textContent = "开始分析";
        }
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;"
            }[ch];
        });
    }

    async function loadWorkspace() {
        var status = document.getElementById("social-network-status");
        status.textContent = "正在加载数据集、质量报告和能力矩阵…";
        status.className = "social-network-status card-panel is-loading";
        try {
            var detail = await request("/api/social-network/datasets/" + encodeURIComponent(datasetId));
            var network = await request(
                "/api/social-network/datasets/" + encodeURIComponent(datasetId) + "/network/summary");
            renderSummary(detail);
            renderNetworkSummary(network);
            renderQuality(detail);
            renderCapabilities({ data: detail.data.capabilitySummary || {} });
            await loadRanking().catch(function (error) {
                renderModuleError("social-network-ranking-table",
                    "指标排行暂时不可用。", error);
            });
            await loadInteractionAnalysis();
            document.getElementById("social-network-search-button").addEventListener(
                "click", function () {
                    searchUsers().catch(function (error) {
                        renderModuleError("social-network-user-results",
                            "用户搜索暂时不可用。", error);
                    });
                });
            document.getElementById("social-network-ranking-button").addEventListener(
                "click", function () {
                    loadRanking().catch(function (error) {
                        renderModuleError("social-network-ranking-table",
                            "指标排行暂时不可用。", error);
                    });
                });
            document.getElementById("social-network-interaction-button").addEventListener(
                "click", function () {
                    loadInteractionAnalysis().catch(function (error) {
                        setText("social-network-interaction-status", "互动分析暂时不可用");
                        if (typeof console !== "undefined" && console.error) {
                            console.error("[social-network] interaction module failed", error);
                        }
                    });
                });
            document.getElementById("social-network-export-button").addEventListener(
                "click", function () {
                    exportResult();
                });
            var topicInput = document.getElementById("social-network-topic-query");
            var topicButton = document.getElementById("social-network-topic-button");
            topicInput.addEventListener("input", function () {
                topicButton.disabled = !topicInput.value.trim();
            });
            topicInput.addEventListener("keydown", function (event) {
                if (event.key === "Enter" && topicInput.value.trim()) {
                    loadTopicAnalysis().catch(function (error) {
                        setText("social-network-topic-status", "主题分析暂时不可用");
                        if (typeof console !== "undefined" && console.error) {
                            console.error("[social-network] topic module failed", error);
                        }
                    });
                }
            });
            topicButton.addEventListener("click", function () {
                loadTopicAnalysis().catch(function (error) {
                    setText("social-network-topic-status", "主题分析暂时不可用");
                    if (typeof console !== "undefined" && console.error) {
                        console.error("[social-network] topic module failed", error);
                    }
                });
            });
            document.querySelectorAll("[data-close-social-user-drawer]").forEach(function (element) {
                element.addEventListener("click", closeSocialUserDrawer);
            });
            document.addEventListener("keydown", function (event) {
                if (event.key === "Escape") {
                    closeSocialUserDrawer();
                }
            });
            document.getElementById("social-network-user-query").addEventListener(
                "keydown", function (event) {
                    if (event.key === "Enter") {
                        searchUsers().catch(function (error) {
                            renderModuleError("social-network-user-results",
                                "用户搜索暂时不可用。", error);
                        });
                    }
                });
            status.textContent = "数据集已加载。能力状态由后端质量报告统一判定。";
            status.className = "social-network-status card-panel is-ready";
            loaded = true;
        } catch (error) {
            status.textContent = "社交网络数据暂不可用。";
            status.className = "social-network-status card-panel is-error";
            if (typeof console !== "undefined" && console.error) {
                console.error("[social-network] workspace failed", error);
            }
        }
    }

    window.onSocialNetworkPageShown = function (name) {
        if (name === "social-network" && !loaded) {
            loadWorkspace();
        }
    };

    function initializeDeepLink() {
        window.onSocialNetworkPageShown("social-network");
    }

    if (location.hash === "#/social-network") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initializeDeepLink, { once: true });
        } else {
            initializeDeepLink();
        }
    }
})();
