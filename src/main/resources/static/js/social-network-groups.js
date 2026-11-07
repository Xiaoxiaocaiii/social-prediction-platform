(function () {
    "use strict";

    var root = document.getElementById("social-network-group-page");
    var workspace = window.socialNetworkWorkspace;
    var model = window.socialNetworkGroupsModel;
    if (!root || !workspace || !model) {
        return;
    }

    var state = {
        active: false,
        analysisType: "clique",
        clique: null,
        louvain: null,
        cliqueInsight: null,
        louvainInsight: null,
        insightOffset: 0,
        insightLoading: false,
        insightSequence: 0,
        sharedPage: null,
        sharedLoading: false,
        sharedSequence: 0,
        sharedExpanded: false,
        cliqueOffset: 0,
        selectedTarget: null,
        evidenceSection: "members",
        evidenceOffset: 0,
        evidencePage: null,
        sequence: 0,
        contextKey: "",
        pollTimer: null,
        evidenceLoading: false,
        exportSequence: 0,
        exportInvoker: null,
        exportSnapshot: null
    };

    var evidenceReasons = {
        analysis_not_requested: "尚未运行该分析",
        analysis_running: "分析仍在运行",
        clique_index_not_available: "该团当前不可查询",
        clique_membership_not_retained: "该团的成员明细未保留",
        community_not_found: "未找到该社群",
        complete_partition_unavailable: "当前没有完整且收敛的社群结果",
        input_changed_before_evidence_query: "输入数据已变化，请刷新分析状态",
        input_changed_during_evidence_query: "查询期间输入数据发生变化，未返回旧数据证据",
        input_fingerprint_unavailable: "当前输入指纹不可用"
    };

    function escapeHtml(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function groupTerm(id, label, explanation) {
        var tooltipId = "social-network-group-term-" + id;
        return '<span class="social-network-scope-term">' +
            '<button class="social-network-scope-term-trigger" type="button" ' +
            'data-group-term aria-label="解释：' + escapeHtml(label) +
            '" aria-describedby="' + tooltipId +
            '" aria-expanded="false">i</button>' +
            '<span class="social-network-scope-term-tooltip" id="' +
            tooltipId + '" role="tooltip"><strong>' +
            escapeHtml(label) + '</strong>：' +
            escapeHtml(explanation) + '</span></span>';
    }

    function positionGroupTermTooltip(tooltip, anchorRect) {
        if (!tooltip || !anchorRect) {
            return;
        }
        var viewportWidth = document.documentElement.clientWidth ||
            window.innerWidth;
        var viewportHeight = document.documentElement.clientHeight ||
            window.innerHeight;
        var margin = 12;
        tooltip.style.left = margin + "px";
        tooltip.style.top = margin + "px";
        var tooltipRect = tooltip.getBoundingClientRect();
        var maxLeft = Math.max(margin, viewportWidth - tooltipRect.width - margin);
        var left = Math.min(
            maxLeft,
            Math.max(
                margin,
                anchorRect.left + anchorRect.width / 2 -
                    tooltipRect.width / 2
            )
        );
        var maxTop = Math.max(
            margin,
            viewportHeight - tooltipRect.height - margin
        );
        var above = anchorRect.top - tooltipRect.height - 8;
        var below = anchorRect.bottom + 8;
        var top = above;
        if (above < margin && below <= maxTop) {
            top = below;
        } else if (above < margin) {
            top = Math.min(maxTop, Math.max(margin, above));
        }
        tooltip.style.left = Math.round(left) + "px";
        tooltip.style.top = Math.round(top) + "px";
    }

    function repositionGroupTermTooltips() {
        root.querySelectorAll(
            ".social-network-scope-term-trigger:hover, " +
            ".social-network-scope-term-trigger:focus, " +
            ".social-network-scope-term.is-open " +
            ".social-network-scope-term-trigger, " +
            ".social-network-group-tabs " +
            "[data-group-analysis]:focus-visible"
        ).forEach(function (trigger) {
            var tooltip = trigger.matches("[data-group-analysis]")
                ? trigger.querySelector(
                    ".social-network-group-tab-help " +
                    ".social-network-scope-term-tooltip")
                : trigger.nextElementSibling;
            positionGroupTermTooltip(
                tooltip,
                trigger.getBoundingClientRect()
            );
        });
    }

    function positionGroupTermFromEvent(event) {
        var trigger = event.target.closest("[data-group-term]");
        if (trigger) {
            positionGroupTermTooltip(
                trigger.nextElementSibling,
                trigger.getBoundingClientRect()
            );
            return;
        }
        var tabHelpTrigger = event.target.closest(
            ".social-network-group-tab-help " +
            ".social-network-scope-term-trigger");
        if (tabHelpTrigger) {
            positionGroupTermTooltip(
                tabHelpTrigger.nextElementSibling,
                tabHelpTrigger.getBoundingClientRect()
            );
            return;
        }
        var analysisTab = event.target.closest(
            ".social-network-group-tabs [data-group-analysis]");
        if (analysisTab && event.type === "focusin") {
            positionGroupTermTooltip(
                analysisTab.querySelector(
                    ".social-network-group-tab-help " +
                    ".social-network-scope-term-tooltip"),
                analysisTab.getBoundingClientRect()
            );
        }
    }

    function toggleGroupTerm(event) {
        var trigger = event.target.closest("[data-group-term]");
        if (!trigger) {
            return false;
        }
        root.querySelectorAll("[data-group-term]").forEach(function (item) {
            if (item !== trigger) {
                item.setAttribute("aria-expanded", "false");
                item.parentElement.classList.remove("is-open");
            }
        });
        var expanded = trigger.getAttribute("aria-expanded") === "true";
        trigger.setAttribute("aria-expanded", String(!expanded));
        trigger.parentElement.classList.toggle("is-open", !expanded);
        if (!expanded) {
            positionGroupTermTooltip(
                trigger.nextElementSibling,
                trigger.getBoundingClientRect()
            );
        }
        return true;
    }

    function formatNumber(value) {
        return value === null || value === undefined ||
            !Number.isFinite(Number(value))
            ? "—"
            : Number(value).toLocaleString("zh-CN");
    }

    function formatDecimal(value) {
        return value === null || value === undefined ||
            !Number.isFinite(Number(value))
            ? "—"
            : Number(value).toLocaleString("zh-CN", {
                minimumFractionDigits: 4,
                maximumFractionDigits: 4
            });
    }

    function currentContext() {
        return workspace.getDatasetContext();
    }

    function contextKey(context) {
        return [
            context.datasetId || "",
            context.datasetVersion || "",
            context.sessionId || ""
        ].join("|");
    }

    function activeAnalysis() {
        return state.analysisType === "clique" ? state.clique : state.louvain;
    }

    function activeInsight() {
        return state.analysisType === "clique"
            ? state.cliqueInsight : state.louvainInsight;
    }

    function accountName(nodeId) {
        var insight = activeInsight();
        var names = insight && insight.names || {};
        return names[nodeId] ? "@" + names[nodeId] : "资料未采集 · " + nodeId;
    }

    function setText(selector, value) {
        var element = root.querySelector(selector);
        if (element) {
            element.textContent = value;
        }
    }

    function setNotice(message, kind) {
        var notice = root.querySelector("[data-group-notice]");
        notice.hidden = !message;
        notice.textContent = message || "";
        notice.dataset.kind = kind || "";
    }

    function stopPolling() {
        if (state.pollTimer) {
            window.clearTimeout(state.pollTimer);
            state.pollTimer = null;
        }
    }

    function isCurrentRequest(sequence, generation, expectedContextKey) {
        return state.active &&
            sequence === state.sequence &&
            generation === state.sequence &&
            expectedContextKey === contextKey(currentContext());
    }

    function requestUrl(resource, query) {
        return workspace.datasetUrl(resource, query, currentContext());
    }

    function requestOptions(method) {
        return {
            method: method || "GET",
            headers: { "Accept": "application/json" }
        };
    }

    function groupExportTargetKey(target) {
        var selected = target === undefined ? state.selectedTarget : target;
        return selected
            ? [selected.type, selected.id, selected.secondId || ""].join("|")
            : "";
    }

    function groupExportSnapshotMatches(snapshot) {
        var analysis = activeAnalysis();
        return Boolean(snapshot && state.active &&
            snapshot.contextKey === contextKey(currentContext()) &&
            snapshot.analysisType === state.analysisType &&
            analysis &&
            snapshot.dataFingerprint === analysis.dataFingerprint &&
            snapshot.cacheKey === analysis.cacheKey &&
            snapshot.listOffset === (state.analysisType === "clique"
                ? Number(analysis.offset) || 0 : 0) &&
            snapshot.targetKey === groupExportTargetKey() &&
            snapshot.evidenceSection === state.evidenceSection &&
            snapshot.evidenceOffset === state.evidenceOffset);
    }

    function openGroupExportDialog(invoker) {
        var dialog = document.getElementById("social-network-group-export-dialog");
        var analysis = activeAnalysis();
        var supported = model.isSupportedContext(currentContext());
        var ready = supported && analysis &&
            analysis.state !== "NOT_REQUESTED" &&
            analysis.state !== "RUNNING" && analysis.state !== "BUSY" &&
            Boolean(analysis.dataFingerprint);
        var scope = "当前分析尚不可导出。";
        if (ready && state.analysisType === "clique") {
            var start = Number(analysis.offset) || 0;
            var count = (analysis.cliques || []).length;
            scope = "Clique 当前列表页：" +
                (count ? (formatNumber(start + 1) + "–" +
                    formatNumber(start + count)) : "0") +
                " / " + formatNumber(analysis.retainedCliqueCount) + " 个已保留团。";
        } else if (ready) {
            scope = "Louvain 当前完整分区：" +
                formatNumber((analysis.communities || []).length) +
                " 个社群，完整性与收敛状态会写入文件。";
        }
        if (ready && state.selectedTarget) {
            scope += " 同时重读所选目标的当前" +
                (state.evidenceSection === "members" ? "成员" : "关系来源") +
                "证据页。";
        } else if (ready) {
            scope += " 尚未选择证据目标，本次不包含来源证据。";
        }
        document.getElementById("social-network-group-export-note")
            .textContent = scope +
                " 文件超过 2 MiB 时整体拒绝，不会截断。";
        document.getElementById("social-network-group-export-dataset")
            .textContent = "数据集：" + (currentContext().datasetId || "尚未读取");
        document.getElementById("social-network-group-export-status")
            .textContent = ready
                ? "导出只读取现有分析结果，不会启动或重跑算法。"
                : "请先读取一个已启动分析的结果。";
        document.getElementById("social-network-group-export-submit")
            .disabled = !ready;
        state.exportInvoker = invoker || document.activeElement;
        if (!dialog.open) {
            dialog.showModal();
        }
        document.getElementById("social-network-group-export-format").focus();
    }

    async function downloadGroupExport() {
        var dialog = document.getElementById("social-network-group-export-dialog");
        var submit = document.getElementById("social-network-group-export-submit");
        var status = document.getElementById("social-network-group-export-status");
        var analysis = activeAnalysis();
        if (!dialog.open || !analysis) {
            return;
        }
        if (!model.isSupportedContext(currentContext()) ||
                analysis.state === "NOT_REQUESTED" ||
                analysis.state === "RUNNING" ||
                analysis.state === "BUSY" ||
                !analysis.dataFingerprint) {
            status.textContent = "当前数据或分析结果不可用，未生成文件。";
            return;
        }
        var format = document.getElementById(
            "social-network-group-export-format").value;
        var target = state.selectedTarget
            ? {
                type: state.selectedTarget.type,
                id: state.selectedTarget.id,
                secondId: state.selectedTarget.secondId || null,
                section: state.evidenceSection,
                offset: state.evidenceOffset,
                pageSize: model.PAGE_SIZE
            }
            : null;
        var snapshot = {
            contextKey: contextKey(currentContext()),
            analysisType: state.analysisType,
            dataFingerprint: analysis.dataFingerprint,
            cacheKey: analysis.cacheKey,
            listOffset: state.analysisType === "clique"
                ? Number(analysis.offset) || 0 : 0,
            targetKey: groupExportTargetKey(),
            evidenceSection: state.evidenceSection,
            evidenceOffset: state.evidenceOffset
        };
        var request = {
            analysisType: state.analysisType,
            format: format,
            offset: snapshot.listOffset,
            pageSize: analysis.pageSize || model.PAGE_SIZE,
            evidence: target
        };
        var sequence = ++state.exportSequence;
        state.exportSnapshot = snapshot;
        submit.disabled = true;
        status.textContent = "正在读取当前结果并组装文件…";
        try {
            var response = await workspace.requestDataset(
                requestUrl("network/exports"), {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(request)
                });
            if (sequence !== state.exportSequence ||
                    !groupExportSnapshotMatches(snapshot) ||
                    !dialog.open) {
                return;
            }
            var data = response.data || {};
            var content = String(data.result == null ? "" : data.result);
            var blob = new Blob([content], {
                type: data.contentType || "application/octet-stream"
            });
            if (blob.size > 2 * 1024 * 1024) {
                status.textContent = "最终文件超过 2 MiB，已整体拒绝下载。";
                return;
            }
            var safeDatasetId = String(currentContext().datasetId || "dataset")
                .replace(/[^a-zA-Z0-9_-]+/g, "-");
            var fileName = safeDatasetId + "-" + state.analysisType +
                "-current-" + new Date().toISOString().replace(/[:.]/g, "-") +
                "." + (format === "print" ? "txt" : format);
            var url = URL.createObjectURL(blob);
            var link = document.createElement("a");
            link.href = url;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.setTimeout(function () {
                URL.revokeObjectURL(url);
            }, 0);
            status.textContent = "已下载 · " +
                formatNumber(blob.size) + " 字节";
        } catch (error) {
            if (sequence !== state.exportSequence ||
                    !groupExportSnapshotMatches(snapshot)) {
                return;
            }
            status.textContent = (error && error.status
                ? "HTTP " + error.status + " · " : "") +
                (error && error.message || "导出失败，未生成文件。");
        } finally {
            if (sequence === state.exportSequence && dialog.open) {
                submit.disabled = false;
            }
        }
    }

    function initializeGroupExportControls() {
        var dialog = document.getElementById("social-network-group-export-dialog");
        var submit = document.getElementById("social-network-group-export-submit");
        var cancel = document.getElementById("social-network-group-export-cancel");
        var close = document.getElementById("social-network-group-export-close");
        if (!dialog || !submit || !cancel || !close) {
            return;
        }
        submit.addEventListener("click", downloadGroupExport);
        [cancel, close].forEach(function (button) {
            button.addEventListener("click", function () {
                dialog.close();
            });
        });
        dialog.addEventListener("close", function () {
            state.exportSequence += 1;
            state.exportSnapshot = null;
            submit.disabled = false;
            var invoker = state.exportInvoker;
            state.exportInvoker = null;
            window.requestAnimationFrame(function () {
                if (invoker && invoker.isConnected &&
                        invoker.getClientRects().length) {
                    invoker.focus();
                }
            });
        });
    }

    function stateMessage(analysis) {
        if (!analysis) {
            return "状态尚未读取";
        }
        if (analysis.state === "NOT_REQUESTED") {
            return "尚未运行";
        }
        var label = model.labelForStatus(analysis.state);
        if (analysis.state === "COMPLETED_UNCONVERGED" ||
                (state.analysisType === "louvain" &&
                    analysis.workerCompleted && !analysis.converged)) {
            return label + "；结果不完整，不按正常收敛结果解释";
        }
        if (state.analysisType === "clique" && !analysis.complete &&
                analysis.enumeratedCliqueCount > 0) {
            return label + "；以下统计仅代表已发现结果";
        }
        if (analysis.terminationDetail) {
            return label + " · " + analysis.terminationDetail;
        }
        return label;
    }

    function renderSummary(analysis) {
        var summary = root.querySelector("[data-group-summary]");
        var metrics = [];
        if (!analysis || analysis.state === "NOT_REQUESTED") {
            metrics = [
                ["分析状态", "尚未运行", "状态查询不会启动计算"],
                ["输入节点", "—", "完整规范化网络"],
                ["关系规模", "—", "有向输入关系"],
                ["结果规模", "—", "尚无分析结果"]
            ];
        } else if (state.analysisType === "clique") {
            var countLabel = analysis.complete ? "极大团总数" : "已发现极大团";
            metrics = [
                [countLabel, formatNumber(analysis.enumeratedCliqueCount),
                    analysis.complete ? "规模至少为 3；重叠团分别计数" : "不代表全网完整统计",
                    ["summary-maximal-clique", "极大团",
                        "至少 3 个账号组成，团内任意两者都有关注关系，并且不能再加入其他账号；它不等于规模最大的团。"]],
                ["保留成员列表", formatNumber(analysis.retainedCliqueCount),
                    analysis.membershipListComplete ? "成员明细完整保留" : "成员明细可能不完整"],
                ["最大观测规模", formatNumber(analysis.largestObservedCliqueSize),
                    "不完整枚举时不称为全局最大团"],
                ["输入范围", formatNumber(analysis.inputNodeCount) + " 节点",
                    formatNumber(analysis.inputDirectedEdgeCount) + " 条有向关系"]
            ];
        } else {
            var quality = analysis.quality || {};
            metrics = [
                ["算法社群", formatNumber((analysis.communities || []).length),
                    "算法标签不表示现实群体",
                    ["summary-community", "算法社群",
                        "算法根据账号之间的关注联系划分出的网络分组，不代表现实中的组织或真实社会群体。"]],
                ["已分配节点", formatNumber(quality.assignedNodeCount),
                    "覆盖范围来自完整输入网络"],
                ["未分配孤立节点", formatNumber(analysis.unassignedNodeCount),
                    "单独作为未归类节点显示"],
                ["模块度 Q", formatDecimal(quality.modularity),
                    "无权无向简单投影 · " +
                        formatNumber(analysis.iterations) + " 轮",
                    ["summary-modularity", "模块度 Q",
                        "衡量这次分组中组内联系相对随机基准是否集中；不是现实群体真实性或影响力分数，不宜直接比较不同网络或参数下的数值。"]]
            ];
        }
        summary.innerHTML = metrics.map(function (metric) {
            var label = escapeHtml(metric[0]);
            if (metric[3]) {
                label += groupTerm(
                    metric[3][0], metric[3][1], metric[3][2]);
            }
            return '<article class="social-network-group-metric">' +
                '<span>' + label + "</span>" +
                '<strong>' + escapeHtml(metric[1]) + "</strong>" +
                '<small>' + escapeHtml(metric[2]) + "</small></article>";
        }).join("");
    }

    function renderFindings(analysis) {
        var target = root.querySelector("[data-group-findings]");
        var response = activeInsight();
        if (!analysis || analysis.state === "NOT_REQUESTED") {
            target.innerHTML = '<p class="social-network-group-findings-message">' +
                "运行分析后，这里会说明哪些账号反复出现在紧密组合中，" +
                "以及网络分组之间是否有联系。</p>";
            return;
        }
        if (!analysis.complete || (state.analysisType === "clique" &&
                !analysis.membershipListComplete) ||
                (state.analysisType === "louvain" && !analysis.converged)) {
            target.innerHTML = '<p class="social-network-group-findings-message">' +
                "当前没有完整可解释的结果；不会把未完成的统计当作全网结论。</p>";
            return;
        }
        if (state.insightLoading) {
            target.innerHTML = '<p class="social-network-group-findings-message">正在整理已有结果…</p>';
            return;
        }
        if (state.analysisType === "louvain") {
            var currentInsight = response &&
                response.dataFingerprint === analysis.dataFingerprint
                ? response : null;
            renderLouvainFindings(
                target,
                analysis,
                currentInsight && currentInsight.available
                    ? currentInsight.communityPairs || [] : [],
                Boolean(currentInsight && currentInsight.available),
                currentInsight && currentInsight.reason);
            return;
        }
        if (!response || !response.available ||
                response.dataFingerprint !== analysis.dataFingerprint) {
            target.innerHTML = '<p class="social-network-group-findings-message">' +
                "结论暂不可用，请刷新状态；不会使用过期或不匹配的数据。</p>";
            return;
        }
        renderCliqueFindings(target, response.insight);
    }

    function renderCliqueFindings(target, insight) {
        var participants = insight && insight.participants || [];
        if (!insight || !insight.totalCount || !participants.length) {
            target.innerHTML = '<p class="social-network-group-findings-message">' +
                "当前完整结果没有规模至少为 3 的极大团。</p>";
            return;
        }
        var top = participants.slice(0, 3).map(function (entry) {
            return escapeHtml(accountName(entry.nodeId));
        }).join("、");
        var ranking = participants.map(function (entry) {
            return '<li><span>' + escapeHtml(accountName(entry.nodeId)) +
                '</span><strong>' + formatNumber(entry.count) + " 个团 · " +
                (Number(entry.count) / Number(insight.totalCount) * 100).toFixed(1) +
                "%</strong></li>";
        }).join("");
        var pair = insight.firstNodeId && insight.secondNodeId
            ? '<p>' + escapeHtml(accountName(insight.firstNodeId)) + " 与 " +
                escapeHtml(accountName(insight.secondNodeId)) + " 共同出现在 <strong>" +
                formatNumber(insight.commonCliqueCount) + "</strong> 个团中。</p>" +
                '<details class="social-network-group-disclosure" data-group-shared' +
                (state.sharedExpanded ? " open" : "") +
                '><summary>查看共同出现的团</summary>' +
                "<small>列出这两个账号共同出现的极大团；团之间可以重叠。</small>" +
                renderSharedCliques() + "</details>"
            : "";
        var common = (insight.commonMembers || []).map(function (id) {
            return escapeHtml(accountName(id));
        }).join("、");
        var maximum = (insight.maximumCliques || []).map(function (clique) {
            return '<button type="button" class="social-network-group-findings-button" ' +
                'data-group-select="clique" data-group-index="' + Number(clique.index) +
                '">团 #' + formatNumber(Number(clique.index) + 1) + " · " +
                formatNumber(clique.members.length) + " 个账号 · 查看依据</button>";
        }).join("");
        var end = Math.min(Number(insight.offset) + (insight.maximumCliques || []).length,
            Number(insight.maximumCount));
        var pagination = '<nav class="social-network-group-pagination" aria-label="最大团分页">' +
            '<button type="button" data-group-max-prev' +
                (insight.offset <= 0 ? " disabled" : "") + '>上一页</button>' +
            "<span>" + formatNumber(Number(insight.offset) + 1) + "–" +
                formatNumber(end) + " / " + formatNumber(insight.maximumCount) + "</span>" +
            '<button type="button" data-group-max-next' +
                (end >= insight.maximumCount ? " disabled" : "") + '>下一页</button></nav>';
        target.innerHTML = '<article class="social-network-group-finding">' +
            '<h2>哪些账号反复出现在紧密组合中？</h2><p><strong>' + top +
            "</strong> 出现在较多紧密关注组合中。</p>" +
            '<details class="social-network-group-disclosure"><summary>查看参与团数前 ' +
            participants.length + ' 的账号</summary>' +
            "<small>按参与的极大团数量排序；同一账号可以出现在多个团中。</small>" +
            '<ol class="social-network-group-ranking">' + ranking + "</ol>" +
            "<small>暂不支持按单个账号筛选全部参与团。</small></details>" +
            pair + '<small>团可以重叠；参与次数不是互动量或影响力。</small></article>' +
            '<article class="social-network-group-finding">' +
            '<h2>最大的紧密组合有多大？</h2><p>最大团有 <strong>' +
            formatNumber(insight.maximumSize) + "</strong> 个账号，共 <strong>" +
            formatNumber(insight.maximumCount) + "</strong> 个；这些最大团共同包含 <strong>" +
            formatNumber((insight.commonMembers || []).length) + "</strong> 个账号。</p>" +
            '<details class="social-network-group-disclosure"><summary>查看共同账号</summary>' +
            "<small>这里列出同时出现在每一个最大团中的账号。</small><p>" +
            (common || "没有共同账号") + "</p></details>" +
            '<details class="social-network-group-disclosure"><summary>查看最大团及来源依据</summary>' +
            "<small>可分页查看最大团成员，并核对其关系来源。</small>" +
            '<div class="social-network-group-maximum">' +
            maximum + "</div>" + pagination + "</details>" +
            '<small>任一方向有关注即算相连，不代表团内人人互相关注。</small></article>';
    }

    function renderSharedCliques() {
        if (state.sharedLoading) {
            return "<p>正在读取共同团…</p>";
        }
        var page = state.sharedPage;
        if (!page) {
            return "";
        }
        if (!page.available || !page.page ||
                page.dataFingerprint !== (state.clique || {}).dataFingerprint) {
            return "<p>共同团暂不可用，请刷新状态。</p>";
        }
        var data = page.page;
        var rows = (data.cliques || []).map(function (clique) {
            return '<button type="button" class="social-network-group-findings-button" ' +
                'data-group-select="clique" data-group-index="' + Number(clique.index) +
                '">团 #' + formatNumber(Number(clique.index) + 1) + " · " +
                formatNumber(clique.members.length) + " 个账号 · 查看依据</button>";
        }).join("");
        var end = Math.min(Number(data.offset) + (data.cliques || []).length,
            Number(data.totalCount));
        return '<div class="social-network-group-maximum">' + (rows || "没有共同团") + "</div>" +
            '<nav class="social-network-group-pagination" aria-label="共同团分页">' +
            '<button type="button" data-group-shared-prev' +
                (data.offset <= 0 ? " disabled" : "") + '>上一页</button><span>' +
            (data.totalCount ? formatNumber(Number(data.offset) + 1) + "–" +
                formatNumber(end) + " / " + formatNumber(data.totalCount) : "0") +
            '</span><button type="button" data-group-shared-next' +
                (end >= data.totalCount ? " disabled" : "") + '>下一页</button></nav>';
    }

    function renderLouvainFindings(target, analysis, pairs, pairInsightAvailable, reason) {
        var quality = analysis.quality || {};
        var top = pairs[0];
        var pairText = top
            ? '<p>在本次划分中，<strong>' + escapeHtml(top.first) + " 与 " +
                escapeHtml(top.second) + "</strong> 之间有 <strong>" +
                formatNumber(top.tieCount) + "</strong> 条无向联系，是联系条数最多的一对。</p>" +
                '<button type="button" class="social-network-group-findings-button" ' +
                'data-group-between="' + escapeHtml(top.first) +
                '" data-group-between-second="' + escapeHtml(top.second) +
                '">查看这两个社群之间的关注依据</button>'
            : pairInsightAvailable
                ? "<p>当前结果没有社群之间的关注联系。</p>"
                : '<p class="social-network-group-findings-message">' +
                    (reason === "analysis_graph_unavailable"
                        ? "分组结果已恢复，但社群间关系明细暂不可用；不代表没有跨社群联系。"
                        : "社群间联系排行暂不可用；不代表没有跨社群联系。") +
                    "</p>";
        target.innerHTML = '<article class="social-network-group-finding">' +
            '<h2>这些分组彼此隔离吗？</h2><p><strong>' +
            (Number(quality.crossCommunityTieCount) > 0
                ? "并非彼此隔离。" : "没有发现跨社群联系。") +
            "</strong> 本次算法划分得到 <strong>" +
            formatNumber((analysis.communities || []).length) + "</strong> 个社群；" +
            formatNumber(quality.internalTieCount) + " 条联系在社群内部，" +
            formatNumber(quality.crossCommunityTieCount) + " 条连接不同社群。</p>" +
            '<p>' + formatNumber(quality.assignedNodeCount) + " 个账号已分组，" +
            formatNumber(analysis.unassignedNodeCount) + " 个孤立节点未分配。</p>" +
            '<small>社群是算法分组，不代表现实组织；不同参数可能改变结果。</small></article>' +
            '<article class="social-network-group-finding">' +
            '<h2>哪些分组之间的联系条数最多？</h2>' + pairText +
            '<small>联系条数受分组规模影响，不能直接解释为“联系更紧密”。</small></article>';
    }

    function renderDistribution(analysis) {
        var chart = root.querySelector("[data-group-distribution]");
        var title = state.analysisType === "clique"
            ? "团规模分布" : "社群规模分布";
        setText("[data-group-distribution-title]", title);
        var rows = state.analysisType === "clique"
            ? model.cliqueDistribution(analysis && analysis.cliqueSizeDistribution)
            : model.louvainDistribution(analysis && analysis.communities);
        if (!analysis || analysis.state === "NOT_REQUESTED") {
            setText("[data-group-distribution-note]", "运行分析后显示分布；尚未运行不等于结果为零。");
            chart.innerHTML = '<p class="social-network-group-empty">等待显式启动分析</p>';
            return;
        }
        if (!rows.length) {
            setText("[data-group-distribution-note]", "当前状态没有可展示的分布数据。");
            chart.innerHTML = '<p class="social-network-group-empty">暂无可展示分布</p>';
            return;
        }
        var max = Math.max.apply(null, rows.map(function (row) {
            return state.analysisType === "clique" ? row.count : row.size;
        }).concat([1]));
        var displayedRows = state.analysisType === "clique"
            ? rows
            : rows.slice(0, 8);
        setText("[data-group-distribution-note]",
            state.analysisType === "clique"
                ? (analysis.complete ? "完整枚举统计" : "仅统计已发现团") +
                    " · 最小规模 3 · 重叠团分别计数"
                : (rows.length > displayedRows.length
                    ? "按规模显示前 8 个社群" : "按社群规模排序") +
                    " · 仅描述算法划分");
        chart.innerHTML = displayedRows.map(function (row) {
            var value = state.analysisType === "clique" ? row.count : row.size;
            var label = state.analysisType === "clique"
                ? "规模 " + row.size
                : row.id;
            return '<div class="social-network-group-bar-row">' +
                '<span class="social-network-group-bar-label">' +
                    escapeHtml(label) + "</span>" +
                '<span class="social-network-group-bar-track"><i style="width:' +
                    Math.max(2, value / max * 100) + '%"></i></span>' +
                '<strong>' + escapeHtml(formatNumber(value)) + "</strong></div>";
        }).join("");

        if (state.analysisType === "clique") {
            var participants = model.topParticipants(
                analysis.nodeParticipationCounts, 5);
            if (participants.length) {
                chart.insertAdjacentHTML("beforeend",
                    '<div class="social-network-group-participants"><strong>' +
                    "团参与度较高节点</strong><span>" +
                    participants.map(function (entry) {
                        return escapeHtml(accountName(entry.nodeId)) + " · " +
                            escapeHtml(formatNumber(entry.count));
                    }).join("　") + "</span></div>");
            }
        }
    }

    function renderList(analysis) {
        var list = root.querySelector("[data-group-list]");
        var previous = root.querySelector("[data-group-list-prev]");
        var next = root.querySelector("[data-group-list-next]");
        var page = root.querySelector("[data-group-list-page]");
        var title = state.analysisType === "clique" ? "极大团列表" : "社群列表";
        var titleElement = root.querySelector("[data-group-list-title]");
        titleElement.innerHTML = escapeHtml(title) + groupTerm(
            state.analysisType === "clique" ? "list-maximal-clique" : "list-community",
            state.analysisType === "clique" ? "极大团" : "社群",
            state.analysisType === "clique"
                ? "团内任意两账号都存在至少一个方向的关注关系；极大表示无法再加入其他账号，不表示它一定是全网最大的团。"
                : "Louvain 根据网络连接模式划分出的账号组，不代表现实中的组织或真实社会群体。");
        if (!analysis || analysis.state === "NOT_REQUESTED") {
            setText("[data-group-list-count]", "尚未运行；不会把未运行显示成零条");
            setText("[data-group-list-page]", "—");
            previous.disabled = true;
            next.disabled = true;
            list.innerHTML = '<p class="social-network-group-empty">点击“开始计算”后查看结果。</p>';
            return;
        }
        if (state.analysisType === "clique") {
            var cliques = Array.isArray(analysis.cliques) ? analysis.cliques : [];
            var total = Number(analysis.retainedCliqueCount) || 0;
            var offset = Number(analysis.offset) || 0;
            setText("[data-group-list-count]",
                (analysis.membershipListComplete
                    ? "可分页查看已保留的全部成员列表"
                    : "仅能查看当前已保留的成员列表") +
                    " · 共 " + formatNumber(total));
            page.textContent = total
                ? (formatNumber(offset + 1) + "–" +
                    formatNumber(Math.min(offset + cliques.length, total)) +
                    " / " + formatNumber(total))
                : "0";
            previous.disabled = offset <= 0;
            next.disabled = offset + cliques.length >= total ||
                cliques.length === 0;
            if (!cliques.length) {
                list.innerHTML = '<p class="social-network-group-empty">' +
                    (total ? "当前页没有保留的成员明细。" : "当前没有可展示的团成员列表。") +
                    "</p>";
                return;
            }
            list.innerHTML = cliques.map(function (members, index) {
                var cliqueIndex = offset + index;
                var selected = state.selectedTarget &&
                    state.selectedTarget.type === "clique" &&
                    state.selectedTarget.id === String(cliqueIndex);
                return '<button type="button" class="social-network-group-result-row' +
                    (selected ? " is-selected" : "") +
                    '" data-group-select="clique" data-group-index="' +
                    cliqueIndex + '" aria-pressed="' + String(Boolean(selected)) +
                    '"><span class="social-network-group-result-title">' +
                    "团 #" + formatNumber(cliqueIndex + 1) +
                    "</span><span>" + formatNumber(members.length) +
                    " 个成员</span><small>" +
                    members.slice(0, 4).map(function (id) {
                        return escapeHtml(accountName(id));
                    }).join(" · ") +
                    (members.length > 4 ? " · …" : "") +
                    "</small></button>";
            }).join("");
            return;
        }

        var communities = model.louvainDistribution(analysis.communities);
        setText("[data-group-list-count]",
            formatNumber(communities.length) + " 个算法社群；未分配节点单独统计");
        page.textContent = communities.length ? "全部" : "0";
        previous.disabled = true;
        next.disabled = true;
        var rows = communities.map(function (community) {
            var original = analysis.communities.find(function (item) {
                return String(item.id) === community.id;
            }) || {};
            var selected = state.selectedTarget &&
                state.selectedTarget.type === "louvain" &&
                state.selectedTarget.id === community.id;
            return '<button type="button" class="social-network-group-result-row' +
                (selected ? " is-selected" : "") +
                '" data-group-select="louvain" data-group-id="' +
                escapeHtml(community.id) + '" aria-pressed="' +
                String(Boolean(selected)) + '"><span class="social-network-group-result-title">' +
                escapeHtml(community.id) + "</span><span>" +
                formatNumber(community.size) + " 个节点</span><small>" +
                formatNumber(original.internalTieCount) +
                " 条内部无向关系</small></button>";
        });
        if (analysis.unassignedNodeCount > 0) {
            rows.push('<div class="social-network-group-unassigned"><strong>未分配孤立节点</strong>' +
                "<span>" + formatNumber(analysis.unassignedNodeCount) +
                " 个 · 不属于上述社群</span></div>");
        }
        list.innerHTML = rows.length
            ? rows.join("")
            : '<p class="social-network-group-empty">当前没有可展示的社群。</p>';
    }

    function renderEvidence(loading) {
        var list = root.querySelector("[data-group-evidence-list]");
        var pageLabel = root.querySelector("[data-group-evidence-page]");
        var previous = root.querySelector("[data-group-evidence-prev]");
        var next = root.querySelector("[data-group-evidence-next]");
        var selected = state.selectedTarget;
        root.querySelectorAll("[data-group-evidence-section]")
            .forEach(function (button) {
                var active = button.dataset.groupEvidenceSection ===
                    state.evidenceSection;
                button.hidden = Boolean(selected && selected.type === "between" &&
                    button.dataset.groupEvidenceSection === "members");
                button.setAttribute("aria-selected", String(active));
            });
        var sectionLabel = state.evidenceSection === "members" ? "成员来源" : "关系来源";
        setText("[data-group-evidence-title]",
            selected ? selected.label + " · " + sectionLabel : "成员与来源证据");
        if (!selected) {
            setText("[data-group-evidence-subtitle]",
                "选择一个团或社群后按需读取来源行。");
            setText("[data-group-evidence-status]",
                "证据按需分页读取；代表性首屏已抽测，后续页和瞬时峰值尚未全面核验。");
            list.innerHTML = '<p class="social-network-group-empty">尚未选择结果。</p>';
            pageLabel.textContent = "—";
            previous.disabled = true;
            next.disabled = true;
            return;
        }
        setText("[data-group-evidence-subtitle]",
            "来源文件与行号用于追溯输入记录；后续分页成本尚未全面核验。");
        if (loading || state.evidenceLoading) {
            setText("[data-group-evidence-status]", "正在读取当前页来源证据…");
            list.innerHTML = '<p class="social-network-group-empty">正在加载…</p>';
            pageLabel.textContent = "—";
            previous.disabled = true;
            next.disabled = true;
            return;
        }
        var evidence = state.evidencePage;
        if (!evidence) {
            setText("[data-group-evidence-status]", "选择结果以读取来源证据。");
            list.innerHTML = '<p class="social-network-group-empty">尚未请求证据。</p>';
            pageLabel.textContent = "—";
            previous.disabled = true;
            next.disabled = true;
            return;
        }
        if (!evidence.evidenceAvailable) {
            var reason = evidenceReasons[evidence.unavailableReason] ||
                evidence.unavailableReason || "证据当前不可用";
            setText("[data-group-evidence-status]", reason);
            list.innerHTML = '<p class="social-network-group-empty is-error">' +
                escapeHtml(reason) + "</p>";
            pageLabel.textContent = "—";
            previous.disabled = true;
            next.disabled = true;
            return;
        }

        var entries = state.evidenceSection === "members"
            ? (evidence.members || [])
            : (evidence.ties || []);
        var start = Number(evidence.offset) || 0;
        var total = Number(evidence.totalCount) || 0;
        setText("[data-group-evidence-status]",
            (evidence.analysisComplete ? "分析完整" : "分析结果不完整") +
            (evidence.converged === false ? " · Louvain 未收敛" : "") +
            " · 本页 " + formatNumber(entries.length) +
            " / " + formatNumber(total));
        pageLabel.textContent = total
            ? formatNumber(start + 1) + "–" +
                formatNumber(Math.min(start + entries.length, total)) +
                " / " + formatNumber(total)
            : "0";
        previous.disabled = start <= 0;
        next.disabled = start + entries.length >= total ||
            entries.length === 0;
        if (!entries.length) {
            list.innerHTML = '<p class="social-network-group-empty">当前页没有来源记录。</p>';
            return;
        }
        list.innerHTML = state.evidenceSection === "members"
            ? entries.map(renderMemberEvidence).join("")
            : entries.map(renderTieEvidence).join("");
    }

    function renderMemberEvidence(member) {
        var sources = (member.profileSources || []).map(function (source) {
            return '<li><code>' + escapeHtml(source.sourceFileName) +
                "</code><span>第 " + formatNumber(source.sourceRowNumber) +
                " 行 · " + escapeHtml(source.normalizationStatus || "资料记录") +
                "</span></li>";
        }).join("");
        return '<article class="social-network-group-evidence-item">' +
            '<header><strong>' + escapeHtml(accountName(member.nodeId)) +
            " · " + escapeHtml(member.nodeId) + "</strong><span>" +
            escapeHtml(member.nodeType || "节点") + "</span></header>" +
            (sources ? "<ul>" + sources + "</ul>" :
                '<p class="social-network-group-source-empty">没有资料源行</p>') +
            "</article>";
    }

    function renderRelationshipSources(sources, duplicate) {
        if (!sources || !sources.length) {
            return "";
        }
        return sources.map(function (source) {
            return "<li>" +
                escapeHtml(accountName(source.sourceId)) + " → " +
                escapeHtml(accountName(source.targetId)) + " · " +
                escapeHtml(source.sourceFileName) + " 第 " +
                formatNumber(source.sourceRowNumber) + " 行" +
                (source.sourceArrayIndex >= 0 ? " · 列表索引 " +
                    formatNumber(source.sourceArrayIndex) : "") +
                (duplicate ? " · 重复来源" : "") + "</li>";
        }).join("");
    }

    function renderTieEvidence(tie) {
        var valid = renderRelationshipSources(
            tie.supportingRelationships, false);
        var duplicates = renderRelationshipSources(
            tie.duplicateRelationshipRecords, true);
        return '<article class="social-network-group-evidence-item">' +
            '<header><strong>' + escapeHtml(accountName(tie.leftNodeId)) + " — " +
            escapeHtml(accountName(tie.rightNodeId)) + "</strong><span>无向关系 · 权重 " +
            formatNumber(tie.weight) + "</span></header>" +
            '<ul class="social-network-group-source-directions">' + valid +
            duplicates + "</ul></article>";
    }

    function render(loading) {
        var context = currentContext();
        var analysis = activeAnalysis();
        var supported = model.isSupportedContext(context);
        root.querySelectorAll("[data-group-analysis]").forEach(function (button) {
            var active = button.dataset.groupAnalysis === state.analysisType;
            button.setAttribute("aria-selected", String(active));
        });
        var running = model.isRunning(analysis && analysis.state);
        var waiting = analysis && analysis.state === "BUSY";
        var start = root.querySelector("[data-group-start]");
        var refresh = root.querySelector("[data-group-refresh]");
        var exportButton = root.querySelector("[data-group-export]");
        start.disabled = !supported || running || waiting || Boolean(loading);
        refresh.disabled = !supported || Boolean(loading);
        exportButton.disabled = !supported || !analysis ||
            analysis.state === "NOT_REQUESTED" || running || waiting ||
            !analysis.dataFingerprint;
        start.textContent = running
            ? "正在计算…"
            : waiting
                ? "等待其他分析"
            : analysis && analysis.state !== "NOT_REQUESTED"
                ? "重新计算"
                : "开始计算";
        var status = root.querySelector("[data-group-status]");
        status.textContent = analysis
            ? stateMessage(analysis)
            : loading ? "正在读取状态…" : "状态尚未读取";
        status.dataset.state = analysis ? analysis.state || "" : "loading";
        if (!supported) {
            setNotice(
                "当前上下文是上传数据或非内置数据集。Clique/Louvain 本阶段只支持内置 think-tank-social-network/v1；未发送状态、计算或证据请求。",
                "warning");
        } else {
            setNotice("", "");
        }
        renderSummary(analysis);
        renderFindings(analysis);
        renderDistribution(analysis);
        renderList(analysis);
        renderEvidence(false);
    }

    function schedulePoll(sequence, generation, expectedContextKey) {
        stopPolling();
        state.pollTimer = window.setTimeout(function () {
            state.pollTimer = null;
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            loadStatus({ poll: true });
        }, 1800);
    }

    async function loadInsights() {
        var context = currentContext();
        var analysis = activeAnalysis();
        if (!state.active || !analysis || !analysis.complete ||
                (state.analysisType === "clique" && !analysis.membershipListComplete) ||
                (state.analysisType === "louvain" && !analysis.converged)) {
            return;
        }
        var type = state.analysisType;
        var expectedContextKey = contextKey(context);
        var expectedFingerprint = analysis.dataFingerprint;
        var sequence = ++state.insightSequence;
        state.insightLoading = true;
        renderFindings(analysis);
        try {
            var resource = type === "clique"
                ? "network/cliques/insights" : "network/louvain/insights";
            var query = type === "clique"
                ? { offset: state.insightOffset, pageSize: model.PAGE_SIZE } : null;
            var response = await workspace.requestDataset(
                requestUrl(resource, query), requestOptions("GET"));
            if (!state.active || sequence !== state.insightSequence ||
                    expectedContextKey !== contextKey(currentContext()) ||
                    type !== state.analysisType ||
                    expectedFingerprint !== (activeAnalysis() || {}).dataFingerprint) {
                return;
            }
            if (type === "clique") {
                state.cliqueInsight = response.data;
            } else {
                state.louvainInsight = response.data;
            }
        } catch (error) {
            if (!state.active || sequence !== state.insightSequence ||
                    expectedContextKey !== contextKey(currentContext()) ||
                    type !== state.analysisType) {
                return;
            }
            if (type === "clique") {
                state.cliqueInsight = null;
            } else {
                state.louvainInsight = null;
            }
        }
        state.insightLoading = false;
        render(false);
    }

    async function loadSharedCliques(offset) {
        var insight = state.cliqueInsight && state.cliqueInsight.insight;
        if (!state.active || state.analysisType !== "clique" || !insight ||
                !insight.firstNodeId || !insight.secondNodeId) {
            return;
        }
        var sequence = ++state.sharedSequence;
        var expectedContextKey = contextKey(currentContext());
        var expectedFingerprint = (state.clique || {}).dataFingerprint;
        state.sharedLoading = true;
        renderFindings(state.clique);
        try {
            var response = await workspace.requestDataset(requestUrl(
                "network/cliques/insights/shared",
                { first: insight.firstNodeId, second: insight.secondNodeId,
                    offset: offset, pageSize: model.PAGE_SIZE }), requestOptions("GET"));
            if (!state.active || sequence !== state.sharedSequence ||
                    state.analysisType !== "clique" ||
                    expectedContextKey !== contextKey(currentContext()) ||
                    expectedFingerprint !== (state.clique || {}).dataFingerprint) {
                return;
            }
            state.sharedPage = response.data;
        } catch (error) {
            if (!state.active || sequence !== state.sharedSequence ||
                    expectedContextKey !== contextKey(currentContext())) {
                return;
            }
            state.sharedPage = { available: false };
        }
        state.sharedLoading = false;
        renderFindings(state.clique);
    }

    async function loadStatus(options) {
        var settings = options || {};
        var context = currentContext();
        if (!model.isSupportedContext(context)) {
            render(false);
            return;
        }
        var sequence = ++state.sequence;
        var generation = sequence;
        var expectedContextKey = contextKey(context);
        var resource = state.analysisType === "clique"
            ? "network/cliques" : "network/louvain";
        var query = state.analysisType === "clique"
            ? { offset: state.cliqueOffset, pageSize: model.PAGE_SIZE }
            : null;
        if (!settings.poll) {
            stopPolling();
        }
        state.insightSequence += 1;
        state.sharedSequence += 1;
        state.sharedPage = null;
        state.sharedLoading = false;
        state.sharedExpanded = false;
        state.insightLoading = false;
        if (state.analysisType === "clique") {
            state.cliqueInsight = null;
        } else {
            state.louvainInsight = null;
        }
        render(true);
        try {
            var response = await workspace.requestDataset(
                requestUrl(resource, query), requestOptions("GET"));
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            if (state.analysisType === "clique") {
                state.clique = response.data;
            } else {
                state.louvain = response.data;
            }
            if (state.analysisType === "clique" && state.selectedTarget &&
                    state.selectedTarget.type === "clique") {
                var currentCliques = response.data.cliques || [];
                var selectedIndex = Number(state.selectedTarget.id);
                if (selectedIndex < response.data.offset ||
                        selectedIndex >= response.data.offset +
                            currentCliques.length) {
                    clearSelection();
                }
            }
            render(false);
            if (model.isRunning(response.data.state)) {
                schedulePoll(sequence, generation, expectedContextKey);
            } else if (response.data.complete) {
                await loadInsights();
            }
        } catch (error) {
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            if (error && (error.staleDatasetContext ||
                    error.staleTaskRequest)) {
                return;
            }
            setNotice(error && error.message
                ? error.message
                : "暂时无法读取分析状态，请重试。", "error");
            root.querySelector("[data-group-status]").textContent =
                "状态读取失败";
            root.querySelector("[data-group-list]").innerHTML =
                '<p class="social-network-group-empty is-error" role="alert">' +
                escapeHtml(error && error.message ||
                    "暂时无法读取分析状态，请重试。") + "</p>";
            restoreControls();
        }
    }

    function clearSelection() {
        state.selectedTarget = null;
        state.evidenceOffset = 0;
        state.evidencePage = null;
        state.evidenceLoading = false;
    }

    function restoreControls() {
        var supported = model.isSupportedContext(currentContext());
        var analysis = activeAnalysis();
        var running = model.isRunning(analysis && analysis.state);
        var waiting = analysis && analysis.state === "BUSY";
        root.querySelector("[data-group-refresh]").disabled = !supported;
        root.querySelector("[data-group-start]").disabled =
            !supported || running || waiting;
    }

    async function loadEvidence() {
        if (!state.active || !state.selectedTarget ||
                !model.isSupportedContext(currentContext())) {
            return;
        }
        var context = currentContext();
        var expectedContextKey = contextKey(context);
        var sequence = ++state.sequence;
        var generation = sequence;
        var selected = state.selectedTarget;
        var base;
        if (selected.type === "clique") {
            base = "network/cliques/" + encodeURIComponent(selected.id) + "/evidence";
        } else if (selected.type === "between") {
            base = "network/louvain/communities/" + encodeURIComponent(selected.id) +
                "/between/" + encodeURIComponent(selected.secondId) + "/evidence";
        } else {
            base = "network/louvain/communities/" +
                encodeURIComponent(selected.id) + "/evidence";
        }
        var query = {
            section: state.evidenceSection,
            offset: state.evidenceOffset,
            pageSize: model.PAGE_SIZE
        };
        state.evidenceLoading = true;
        renderEvidence(true);
        try {
            var response = await workspace.requestDataset(
                requestUrl(base, query), requestOptions("GET"));
            if (!isCurrentRequest(sequence, generation, expectedContextKey) ||
                    !state.selectedTarget ||
                    state.selectedTarget.id !== selected.id ||
                    state.evidenceSection !== query.section) {
                return;
            }
            state.evidencePage = response.data;
            state.evidenceLoading = false;
            renderEvidence(false);
        } catch (error) {
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            state.evidenceLoading = false;
            var message = error && error.message ||
                "暂时无法读取来源证据。";
            state.evidencePage = {
                evidenceAvailable: false,
                unavailableReason: message
            };
            renderEvidence(false);
        }
    }

    async function startAnalysis() {
        var context = currentContext();
        if (!model.isSupportedContext(context) ||
                model.isRunning(activeAnalysis() && activeAnalysis().state)) {
            return;
        }
        var current = activeAnalysis();
        var force = Boolean(current && current.state &&
            current.state !== "NOT_REQUESTED");
        if (force && !window.confirm(
                "重新计算会绕过当前匹配缓存结果，并启动一次新的分析。继续吗？")) {
            return;
        }
        stopPolling();
        clearSelection();
        state.insightSequence += 1;
        state.sharedSequence += 1;
        state.sharedPage = null;
        state.sharedLoading = false;
        state.sharedExpanded = false;
        state.insightLoading = false;
        if (state.analysisType === "clique") {
            state.cliqueInsight = null;
        } else {
            state.louvainInsight = null;
        }
        var sequence = ++state.sequence;
        var generation = sequence;
        var expectedContextKey = contextKey(context);
        var resource = state.analysisType === "clique"
            ? "network/cliques" : "network/louvain";
        render(true);
        try {
            var response = await workspace.requestDataset(
                requestUrl(resource, force ? { force: true } : null),
                requestOptions("POST"));
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            if (state.analysisType === "clique") {
                state.clique = response.data;
            } else {
                state.louvain = response.data;
            }
            render(false);
            if (model.isRunning(response.data.state)) {
                schedulePoll(sequence, generation, expectedContextKey);
            } else {
                await loadStatus();
            }
        } catch (error) {
            if (!isCurrentRequest(sequence, generation, expectedContextKey)) {
                return;
            }
            setNotice(error && error.message ||
                "分析未能启动；请检查状态后重试。", "error");
            root.querySelector("[data-group-status]").textContent =
                "启动请求未完成";
            renderSummary(activeAnalysis());
            restoreControls();
        }
    }

    function changeListPage(direction) {
        var current = state.clique;
        if (state.analysisType !== "clique" || !current) {
            return;
        }
        state.cliqueOffset = Math.max(
            0,
            (Number(current.offset) || 0) + direction * model.PAGE_SIZE);
        clearSelection();
        loadStatus();
    }

    function changeEvidencePage(direction) {
        var evidence = state.evidencePage;
        if (!evidence) {
            return;
        }
        state.evidenceOffset = Math.max(
            0,
            (Number(evidence.offset) || 0) +
                direction * model.PAGE_SIZE);
        loadEvidence();
    }

    function handleClick(event) {
        if (toggleGroupTerm(event)) {
            return;
        }
        var exportButton = event.target.closest("[data-group-export]");
        if (exportButton) {
            openGroupExportDialog(exportButton);
            return;
        }
        var analysisTab = event.target.closest("[data-group-analysis]");
        if (analysisTab) {
            var nextType = analysisTab.dataset.groupAnalysis;
            if (nextType !== state.analysisType) {
                state.analysisType = nextType;
                state.cliqueOffset = 0;
                state.insightOffset = 0;
                state.insightLoading = false;
                clearSelection();
                loadStatus();
            }
            return;
        }
        if (event.target.closest("[data-group-refresh]")) {
            loadStatus();
            return;
        }
        if (event.target.closest("[data-group-start]")) {
            startAnalysis();
            return;
        }
        if (event.target.closest("[data-group-shared-prev]")) {
            loadSharedCliques(Math.max(0, state.sharedPage.page.offset - model.PAGE_SIZE));
            return;
        }
        if (event.target.closest("[data-group-shared-next]")) {
            loadSharedCliques(state.sharedPage.page.offset + model.PAGE_SIZE);
            return;
        }
        var between = event.target.closest("[data-group-between]");
        if (between) {
            state.selectedTarget = {
                type: "between",
                id: between.dataset.groupBetween,
                secondId: between.dataset.groupBetweenSecond,
                label: between.dataset.groupBetween + " 与 " +
                    between.dataset.groupBetweenSecond
            };
            state.evidenceSection = "ties";
            state.evidenceOffset = 0;
            state.evidencePage = null;
            render(false);
            loadEvidence();
            return;
        }
        if (event.target.closest("[data-group-max-prev]")) {
            state.insightOffset = Math.max(0, state.insightOffset - model.PAGE_SIZE);
            loadInsights();
            return;
        }
        if (event.target.closest("[data-group-max-next]")) {
            state.insightOffset += model.PAGE_SIZE;
            loadInsights();
            return;
        }
        var result = event.target.closest("[data-group-select]");
        if (result) {
            state.selectedTarget = result.dataset.groupSelect === "clique"
                ? {
                    type: "clique",
                    id: result.dataset.groupIndex,
                    label: "团 #" +
                        formatNumber(Number(result.dataset.groupIndex) + 1)
                }
                : {
                    type: "louvain",
                    id: result.dataset.groupId,
                    label: result.dataset.groupId
                };
            state.evidenceSection = "members";
            state.evidenceOffset = 0;
            state.evidencePage = null;
            render(false);
            loadEvidence();
            return;
        }
        var evidenceTab = event.target.closest(
            "[data-group-evidence-section]");
        if (evidenceTab) {
            var section = evidenceTab.dataset.groupEvidenceSection;
            if (section !== state.evidenceSection) {
                state.evidenceSection = section;
                state.evidenceOffset = 0;
                state.evidencePage = null;
                loadEvidence();
            }
            return;
        }
        if (event.target.closest("[data-group-list-prev]")) {
            changeListPage(-1);
            return;
        }
        if (event.target.closest("[data-group-list-next]")) {
            changeListPage(1);
            return;
        }
        if (event.target.closest("[data-group-evidence-prev]")) {
            changeEvidencePage(-1);
            return;
        }
        if (event.target.closest("[data-group-evidence-next]")) {
            changeEvidencePage(1);
        }
    }

    function handleKeydown(event) {
        if (event.key === "Escape") {
            var openTerm = root.querySelector(
                '[data-group-term][aria-expanded="true"]');
            if (openTerm) {
                openTerm.setAttribute("aria-expanded", "false");
                openTerm.parentElement.classList.remove("is-open");
                openTerm.focus();
                event.stopPropagation();
                return;
            }
        }
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
            return;
        }
        var tab = event.target.closest(
            "[data-group-analysis], [data-group-evidence-section]");
        if (!tab) {
            return;
        }
        var selector = tab.hasAttribute("data-group-analysis")
            ? "[data-group-analysis]"
            : "[data-group-evidence-section]";
        var tabs = Array.from(root.querySelectorAll(selector));
        var currentIndex = tabs.indexOf(tab);
        if (currentIndex < 0 || tabs.length < 2) {
            return;
        }
        event.preventDefault();
        var step = event.key === "ArrowRight" ? 1 : -1;
        var nextIndex = (currentIndex + step + tabs.length) % tabs.length;
        tabs[nextIndex].focus();
        tabs[nextIndex].click();
    }

    function handleToggle(event) {
        var disclosure = event.target.closest("[data-group-shared]");
        if (!disclosure || state.analysisType !== "clique") {
            return;
        }
        state.sharedExpanded = Boolean(disclosure.open);
        if (state.sharedExpanded &&
                (!state.sharedPage || !state.sharedPage.available) &&
                !state.sharedLoading) {
            loadSharedCliques(0);
        }
    }

    function onShown() {
        state.active = true;
        state.contextKey = contextKey(currentContext());
        loadStatus();
    }

    function onHidden() {
        state.active = false;
        state.sequence += 1;
        state.insightSequence += 1;
        state.sharedSequence += 1;
        state.exportSequence += 1;
        stopPolling();
        state.evidenceLoading = false;
        state.insightLoading = false;
        var exportDialog = document.getElementById(
            "social-network-group-export-dialog");
        if (exportDialog && exportDialog.open) {
            exportDialog.close();
        }
    }

    initializeGroupExportControls();
    root.addEventListener("click", handleClick);
    root.addEventListener("toggle", handleToggle, true);
    root.addEventListener("keydown", handleKeydown);
    root.addEventListener("mouseover", positionGroupTermFromEvent);
    root.addEventListener("focusin", positionGroupTermFromEvent);
    window.addEventListener("resize", repositionGroupTermTooltips);
    window.addEventListener("scroll", repositionGroupTermTooltips, true);
    window.addEventListener("social-network-dataset-context-changed", function () {
        if (!state.active) {
            return;
        }
        state.sequence += 1;
        state.insightSequence += 1;
        state.sharedSequence += 1;
        state.exportSequence += 1;
        stopPolling();
        clearSelection();
        state.clique = null;
        state.louvain = null;
        state.cliqueInsight = null;
        state.louvainInsight = null;
        state.insightOffset = 0;
        state.insightLoading = false;
        state.cliqueOffset = 0;
        state.contextKey = contextKey(currentContext());
        var exportDialog = document.getElementById(
            "social-network-group-export-dialog");
        if (exportDialog && exportDialog.open) {
            exportDialog.close();
        }
        loadStatus();
    });

    window.socialNetworkGroupsPage = {
        onShown: onShown,
        onHidden: onHidden
    };
})();
