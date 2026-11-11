(function () {
    "use strict";

    var BUILTIN_DATASET_ID = "think-tank-social-network";
    var BUILTIN_DATASET_VERSION = "v1";
    var PENDING_GRAPH_FOCUS_KEY = "socialNetwork.pendingBridgeGraphFocus.v1";
    var PAGE_SIZE = 20;
    var state = {
        mount: null,
        contextKey: "",
        identity: null,
        response: null,
        config: null,
        loading: false,
        requestSequence: 0,
        evidenceSequence: 0,
        dialog: null,
        evidenceOpener: null,
        evidenceSnapshot: null
    };

    function formatNumber(value) {
        return Number(value || 0).toLocaleString("zh-CN");
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function isSupportedContext(context) {
        if (!context) {
            return false;
        }
        var datasetId = String(context.datasetId || "");
        var datasetVersion = String(context.datasetVersion || "");
        var sessionId = String(context.sessionId || "");
        var builtin = datasetId === BUILTIN_DATASET_ID &&
            (!datasetVersion || datasetVersion === BUILTIN_DATASET_VERSION) &&
            !sessionId;
        var upload = /^upload-/.test(datasetId) &&
            Boolean(datasetVersion && sessionId);
        return builtin || upload;
    }

    function bridgeStatusLabel(reason) {
        var labels = {
            unsupported_dataset_scope:
                "当前数据身份不完整或不受支持，无法读取跨社群候选。",
            analysis_result_unavailable:
                "社群分析结果暂不可用，当前无法判定跨社群连接；这不代表没有符合条件的账号。",
            analysis_not_requested:
                "社群分析尚未运行，当前无法判定跨社群连接；这不代表没有符合条件的账号。",
            analysis_running: "社群分析仍在运行，暂时无法读取候选。",
            analysis_busy: "社群分析正在处理另一项请求，请稍后刷新。",
            analysis_not_converged:
                "社群分析未收敛，暂时无法提供候选；不会改用不完整结果。",
            analysis_identity_unavailable:
                "社群分析身份信息不完整，暂时无法核对候选。",
            analysis_result_changed:
                "社群结果已变化；为避免混合不同结果，请刷新候选列表。",
            input_changed_before_query:
                "分析数据已变化，请刷新社群结果后重试。",
            input_changed_during_query:
                "读取期间数据发生变化，本次结果已丢弃；请刷新后重试。",
            network_snapshot_unavailable:
                "完整关系数据暂不可用，请稍后刷新。",
            profile_records_incomplete:
                "符合关系条件的账号缺少完整资料记录，候选列表暂不可用；这不代表没有符合条件的账号。",
            candidate_not_found:
                "该账号当前不属于可查看证据的候选范围，请刷新候选列表后重试。",
            complete_partition_unavailable:
                "当前没有可用的完整社群归属，暂时无法提供候选。"
        };
        return labels[reason] ||
            "跨社群候选暂不可用，请检查分析状态后重试。";
    }

    function ensureBound(mount) {
        if (mount.getAttribute("data-bridge-candidates-bound") === "true") {
            return;
        }
        mount.setAttribute("data-bridge-candidates-bound", "true");
        mount.querySelector("[data-ranking-bridge-prev]")
            .addEventListener("click", function () {
                if (state.response && state.config) {
                    state.config.onPage(Math.max(
                        0,
                        Number(state.response.offset || 0) - PAGE_SIZE
                    ));
                }
            });
        mount.querySelector("[data-ranking-bridge-next]")
            .addEventListener("click", function () {
                if (!state.response || !state.config) {
                    return;
                }
                var nextOffset = Number(state.response.offset || 0) +
                    Number(state.response.pageSize || PAGE_SIZE);
                if (nextOffset < Number(state.response.totalCount || 0)) {
                    state.config.onPage(nextOffset);
                }
            });
    }

    function renderLoading(mount) {
        mount.querySelector("[data-ranking-bridge-status]")
            .textContent = "正在核对完整网络及现有社群结果…";
        mount.querySelector("[data-ranking-bridge-table]").innerHTML =
            '<div class="social-network-network-structure-loading" ' +
            'role="status">正在读取跨社群连接候选…</div>';
        mount.querySelector("[data-ranking-bridge-page]").textContent = "加载中";
        mount.querySelector("[data-ranking-bridge-prev]").disabled = true;
        mount.querySelector("[data-ranking-bridge-next]").disabled = true;
    }

    function renderUnavailable(mount, message) {
        mount.querySelector("[data-ranking-bridge-status]").textContent = message;
        mount.querySelector("[data-ranking-bridge-table]").innerHTML =
            '<p class="social-network-network-structure-warning" ' +
            'role="status">' + escapeHtml(message) + "</p>";
        mount.querySelector("[data-ranking-bridge-page]").textContent = "—";
        mount.querySelector("[data-ranking-bridge-prev]").disabled = true;
        mount.querySelector("[data-ranking-bridge-next]").disabled = true;
    }

    function renderEmpty(mount, data) {
        mount.querySelector("[data-ranking-bridge-status]").textContent =
            "完整网络中共有 0 个符合条件的资料账号。";
        mount.querySelector("[data-ranking-bridge-table]").innerHTML =
            '<p class="social-network-empty" role="status">' +
            "当前没有符合条件的账号。</p>";
        updateCandidatePagination(mount, data);
    }

    function candidateRows(items) {
        return items.map(function (item, index) {
            var name = item.displayName || item.username || "未命名账号";
            var username = item.username && item.username !== name
                ? "<small>@" + escapeHtml(item.username) + "</small>"
                : "";
            return "<tr><td>" + escapeHtml(formatNumber(item.rank)) +
                "</td><td><strong>" + escapeHtml(name) + "</strong>" +
                username + "<small>ID " + escapeHtml(item.accountId) +
                "</small></td><td>" +
                escapeHtml(item.communityId || "未归类") + "</td><td>" +
                escapeHtml(formatNumber(item.connectedCommunityCount)) +
                "</td><td>" +
                escapeHtml(formatNumber(item.distinctOtherAccountCount)) +
                "</td><td><span>主动 " +
                escapeHtml(formatNumber(item.outgoingRelationshipCount)) +
                "</span><small>指向该账号 " +
                escapeHtml(formatNumber(item.incomingRelationshipCount)) +
                "</small></td><td><div class=" +
                '"social-network-bridge-candidate-actions">' +
                '<button type="button" data-bridge-detail="' + index +
                '">账号详情</button>' +
                '<button type="button" data-bridge-evidence="' + index +
                '">关系证据</button>' +
                '<button type="button" data-bridge-locate="' + index +
                '">图内定位</button></div></td></tr>';
        }).join("");
    }

    function updateCandidatePagination(mount, data) {
        var offset = Number(data.offset || 0);
        var size = Number(data.pageSize || PAGE_SIZE);
        var total = Number(data.totalCount || 0);
        var first = total ? offset + 1 : 0;
        var last = Math.min(offset + Number((data.items || []).length), total);
        mount.querySelector("[data-ranking-bridge-page]").textContent =
            first + "–" + last + " / " + formatNumber(total);
        mount.querySelector("[data-ranking-bridge-prev]").disabled = offset <= 0;
        mount.querySelector("[data-ranking-bridge-next]").disabled =
            offset + size >= total;
    }

    function renderCandidates(mount, data) {
        var items = data.items || [];
        var table = mount.querySelector("[data-ranking-bridge-table]");
        mount.querySelector("[data-ranking-bridge-status]").textContent =
            "完整网络共有 " + formatNumber(data.totalCount) +
            " 个候选；本页展示账号跨社群关系方向和数量。它们是待核对对象，不是影响力排序。";
        if (!items.length) {
            table.innerHTML =
                '<p class="social-network-empty" role="status">' +
                "当前页没有账号，请返回上一页或刷新候选列表。</p>";
            updateCandidatePagination(mount, data);
            return;
        }
        table.innerHTML =
            '<table class="social-network-bridge-candidate-table">' +
            "<thead><tr><th>名次</th><th>资料账号</th><th>所属社群</th>" +
            "<th>连接社群数</th><th>其他账号数</th>" +
            "<th>跨社群关系方向</th><th>核对方式</th></tr></thead><tbody>" +
            candidateRows(items) + "</tbody></table>";
        table.querySelectorAll("[data-bridge-detail]")
            .forEach(function (button) {
                var item = items[Number(button.getAttribute("data-bridge-detail"))];
                if (item) {
                    button.addEventListener("click", function () {
                        window.openSocialNetworkUserDetail(item.accountId, {
                            metric: "bridge-candidate",
                            metricLabel: "跨社群连接候选",
                            rank: item.rank,
                            value: item.directedRelationshipCount,
                            scope: "所属社群 " + item.communityId +
                                "；连接其他社群 " +
                                formatNumber(item.connectedCommunityCount) +
                                " 个；关联其他账号 " +
                                formatNumber(item.distinctOtherAccountCount) +
                                " 个"
                        }, button);
                    });
                }
            });
        table.querySelectorAll("[data-bridge-evidence]")
            .forEach(function (button) {
                var item = items[Number(
                    button.getAttribute("data-bridge-evidence"))];
                if (item) {
                    button.addEventListener("click", function () {
                        openEvidence(item, button, state.config.context);
                    });
                }
            });
        table.querySelectorAll("[data-bridge-locate]")
            .forEach(function (button) {
                var item = items[Number(
                    button.getAttribute("data-bridge-locate"))];
                if (item) {
                    button.addEventListener("click", function () {
                        locateInGraph(item, state.config.context, mount);
                    });
                }
            });
        updateCandidatePagination(mount, data);
    }

    function requestIsCurrent(sequence, config) {
        return sequence === state.requestSequence &&
            state.mount === config.mount &&
            config.isCurrent() &&
            JSON.stringify(window.socialNetworkWorkspace.getDatasetContext()) ===
                JSON.stringify(config.context);
    }

    async function loadCandidates(mount, config) {
        state.mount = mount;
        state.config = Object.assign({ mount: mount }, config);
        state.loading = true;
        state.evidenceSnapshot = null;
        state.evidenceData = null;
        ensureBound(mount);
        if (state.contextKey !== config.contextKey) {
            state.contextKey = config.contextKey;
            state.identity = null;
            state.response = null;
            closeEvidence();
        }
        var sequence = ++state.requestSequence;
        var context = config.context || {};
        if (!isSupportedContext(context)) {
            state.identity = null;
            state.response = null;
            state.loading = false;
            renderUnavailable(
                mount,
                bridgeStatusLabel("unsupported_dataset_scope"));
            return;
        }

        renderLoading(mount);
        try {
            var body = await window.socialNetworkWorkspace.requestDataset(
                window.socialNetworkWorkspace.datasetUrl(
                    "network/bridge-candidates",
                    { offset: config.offset, pageSize: PAGE_SIZE },
                    context
                )
            );
            if (!requestIsCurrent(sequence, state.config)) {
                return;
            }
            var data = body.data || {};
            var identity = data.analysisIdentity;
            if (data.state === "unavailable") {
                state.identity = null;
                state.response = data;
                state.loading = false;
                renderUnavailable(mount, bridgeStatusLabel(data.reason));
                return;
            }
            if (!identity || !identity.analysisResultId ||
                    !identity.dataFingerprint) {
                state.identity = null;
                state.response = null;
                state.loading = false;
                renderUnavailable(mount, bridgeStatusLabel(
                    "analysis_identity_unavailable"));
                return;
            }
            if (state.identity && config.offset > 0 &&
                    state.identity.analysisResultId !== identity.analysisResultId) {
                state.identity = null;
                state.response = null;
                state.loading = false;
                renderUnavailable(mount, bridgeStatusLabel(
                    "analysis_result_changed"));
                return;
            }
            state.identity = identity;
            state.response = data;
            state.loading = false;
            if (data.state === "empty" || Number(data.totalCount) === 0) {
                renderEmpty(mount, data);
            } else {
                renderCandidates(mount, data);
            }
        } catch (error) {
            if (error.staleDatasetContext || !requestIsCurrent(sequence, state.config)) {
                return;
            }
            state.identity = null;
            state.response = null;
            state.loading = false;
            renderUnavailable(
                mount,
                "暂时无法读取跨社群候选，请刷新后重试。");
        }
    }

    function ensureDialog() {
        if (state.dialog) {
            return state.dialog;
        }
        var dialog = document.createElement("dialog");
        dialog.id = "social-network-bridge-evidence-dialog";
        dialog.className =
            "social-network-topic-dialog social-network-bridge-evidence-dialog";
        dialog.setAttribute(
            "aria-labelledby", "social-network-bridge-evidence-title");
        dialog.innerHTML =
            '<header class="social-network-topic-dialog-head ' +
            'social-network-topic-task-header">' +
            '<div><p class="page-overline">BRIDGE RELATION EVIDENCE</p>' +
            '<h2 id="social-network-bridge-evidence-title">跨社群关系证据</h2>' +
            '<p data-bridge-evidence-account></p></div>' +
            '<button class="social-network-user-drawer-close" type="button" ' +
            'data-bridge-evidence-close aria-label="关闭关系证据">×</button></header>' +
            '<div class="social-network-bridge-evidence-body">' +
            '<p>方向以候选账号为参照。重复来源会展示作核对，但不会重复计数；关注列表索引从 0 开始，逐行关系文件没有数组索引。</p>' +
            '<div class="social-network-bridge-evidence-filters">' +
            '<label>连接社群<select data-bridge-evidence-community></select></label>' +
            '<label>关系方向<select data-bridge-evidence-direction>' +
            '<option value="all">全部方向</option>' +
            '<option value="outgoing">候选账号关注其他社群</option>' +
            '<option value="incoming">其他社群关注候选账号</option>' +
            '</select></label></div>' +
            '<p data-bridge-evidence-status class="social-network-group-evidence-status" ' +
            'role="status" aria-live="polite"></p>' +
            '<div data-bridge-evidence-list class="social-network-bridge-evidence-list" ' +
            'aria-live="polite"></div>' +
            '<nav class="social-network-group-pagination" ' +
            'aria-label="跨社群关系证据分页">' +
            '<button type="button" data-bridge-evidence-prev>上一页</button>' +
            '<span data-bridge-evidence-page>—</span>' +
            '<button type="button" data-bridge-evidence-next>下一页</button></nav></div>';
        document.body.appendChild(dialog);
        dialog.querySelector("[data-bridge-evidence-close]")
            .addEventListener("click", function () {
                dialog.close();
            });
        dialog.addEventListener("close", function () {
            state.evidenceSequence += 1;
            if (state.evidenceOpener &&
                    document.contains(state.evidenceOpener)) {
                state.evidenceOpener.focus();
            }
        });
        dialog.querySelector("[data-bridge-evidence-community]")
            .addEventListener("change", function () {
                loadEvidence(0);
            });
        dialog.querySelector("[data-bridge-evidence-direction]")
            .addEventListener("change", function () {
                loadEvidence(0);
            });
        dialog.querySelector("[data-bridge-evidence-prev]")
            .addEventListener("click", function () {
                if (state.evidenceData) {
                    loadEvidence(Math.max(
                        0,
                        Number(state.evidenceData.offset || 0) - PAGE_SIZE
                    ));
                }
            });
        dialog.querySelector("[data-bridge-evidence-next]")
            .addEventListener("click", function () {
                if (!state.evidenceData) {
                    return;
                }
                var nextOffset = Number(state.evidenceData.offset || 0) +
                    Number(state.evidenceData.pageSize || PAGE_SIZE);
                if (nextOffset < Number(state.evidenceData.totalCount || 0)) {
                    loadEvidence(nextOffset);
                }
            });
        state.dialog = dialog;
        return dialog;
    }

    function openEvidence(candidate, opener, context) {
        if (!state.identity || !state.identity.analysisResultId) {
            return;
        }
        var dialog = ensureDialog();
        state.evidenceSequence += 1;
        state.evidenceCandidate = candidate;
        state.evidenceContext = context;
        state.evidenceOpener = opener;
        state.evidenceData = null;
        state.evidenceSnapshot = null;
        dialog.querySelector("[data-bridge-evidence-account]").textContent =
            (candidate.displayName || candidate.username || "未命名账号") +
            " · ID " + candidate.accountId;
        var communitySelect = dialog.querySelector(
            "[data-bridge-evidence-community]");
        communitySelect.replaceChildren();
        var all = document.createElement("option");
        all.value = "";
        all.textContent = "全部连接社群";
        communitySelect.appendChild(all);
        (candidate.communities || []).forEach(function (community) {
            var option = document.createElement("option");
            option.value = community.communityId;
            option.textContent = "社群 " + community.communityId +
                " · " + formatNumber(community.directedRelationshipCount) +
                " 条关系";
            communitySelect.appendChild(option);
        });
        communitySelect.value = "";
        dialog.querySelector("[data-bridge-evidence-direction]").value = "all";
        if (!dialog.open) {
            dialog.showModal();
        }
        loadEvidence(0);
    }

    function evidenceStatus(message, isError) {
        if (!state.dialog) {
            return;
        }
        var status = state.dialog.querySelector(
            "[data-bridge-evidence-status]");
        status.textContent = message;
        status.classList.toggle("is-error", Boolean(isError));
        state.dialog.querySelector("[data-bridge-evidence-list]").innerHTML = "";
        state.dialog.querySelector("[data-bridge-evidence-page]")
            .textContent = "—";
        state.dialog.querySelector("[data-bridge-evidence-prev]").disabled = true;
        state.dialog.querySelector("[data-bridge-evidence-next]").disabled = true;
    }

    function nodeTypeLabel(type) {
        return String(type || "").toLowerCase() === "external"
            ? "外部关系账号 · 资料未采集"
            : "资料账号";
    }

    function renderEvidence(data) {
        var list = state.dialog.querySelector("[data-bridge-evidence-list]");
        var offset = Number(data.offset || 0);
        var pageSize = Number(data.pageSize || PAGE_SIZE);
        var total = Number(data.totalCount || 0);
        var items = data.items || [];
        if (!items.length) {
            list.innerHTML =
                '<p class="social-network-empty" role="status">' +
                (total === 0
                    ? "所选社群和方向下没有符合条件的关系。"
                    : "当前页没有关系记录，请返回上一页。") +
                "</p>";
        } else {
            list.innerHTML =
                '<table class="social-network-bridge-evidence-table">' +
                "<thead><tr><th>实际关系</th><th>关系方向</th>" +
                "<th>来源及核对信息</th></tr></thead><tbody>" +
                items.map(function (item) {
                    var sources = (item.sources || []).map(function (source) {
                        var arrayIndex = Number(source.sourceArrayIndex);
                        var hasArrayIndex = Number.isInteger(arrayIndex) && arrayIndex >= 0;
                        return "<li>" +
                            escapeHtml(source.sourceFileName || "来源文件未提供") +
                            " · 第 " +
                            escapeHtml(formatNumber(source.sourceRowNumber)) +
                            " 行" +
                            (hasArrayIndex
                                ? " · 关注列表索引 " +
                                    escapeHtml(formatNumber(arrayIndex))
                                : "") +
                            (source.duplicate
                                ? ' <span class="social-network-bridge-duplicate">' +
                                    "重复来源，不重复计数</span>"
                                : "") + "</li>";
                    }).join("");
                    var direction = item.direction === "outgoing"
                        ? "候选账号发出"
                        : "指向候选账号";
                    return "<tr><td><strong>" +
                        escapeHtml(item.sourceId) + " → " +
                        escapeHtml(item.targetId) + "</strong><small>" +
                        escapeHtml(nodeTypeLabel(item.sourceNodeType)) +
                        " → " +
                        escapeHtml(nodeTypeLabel(item.targetNodeType)) +
                        "</small></td><td>" + escapeHtml(direction) +
                        "<small>来源社群 " +
                        escapeHtml(item.sourceCommunityId || "未归类") +
                        " · 目标社群 " +
                        escapeHtml(item.targetCommunityId || "未归类") +
                        "</small></td><td><ul>" + sources +
                        "</ul></td></tr>";
                }).join("") + "</tbody></table>";
        }
        var first = total ? offset + 1 : 0;
        var last = Math.min(offset + items.length, total);
        state.dialog.querySelector("[data-bridge-evidence-status]")
            .textContent = "匹配 " + formatNumber(total) +
                " 条有效有向关系 · 本页 " + formatNumber(first) +
                "–" + formatNumber(last);
        state.dialog.querySelector("[data-bridge-evidence-page]")
            .textContent = first + "–" + last + " / " +
                formatNumber(total);
        state.dialog.querySelector("[data-bridge-evidence-prev]").disabled =
            offset <= 0;
        state.dialog.querySelector("[data-bridge-evidence-next]").disabled =
            offset + pageSize >= total;
    }

    async function loadEvidence(offset) {
        if (!state.dialog || !state.dialog.open || !state.evidenceCandidate ||
                !state.identity || !state.config ||
                !state.config.isCurrent()) {
            return;
        }
        var sequence = ++state.evidenceSequence;
        var candidate = state.evidenceCandidate;
        var context = state.evidenceContext;
        var communityId = state.dialog.querySelector(
            "[data-bridge-evidence-community]").value;
        var direction = state.dialog.querySelector(
            "[data-bridge-evidence-direction]").value;
        var query = {
            direction: direction,
            expectedAnalysisResultId: state.identity.analysisResultId,
            offset: offset,
            pageSize: PAGE_SIZE
        };
        state.evidenceSnapshot = null;
        if (communityId) {
            query.communityId = communityId;
        }
        evidenceStatus("正在读取关系来源…", false);
        try {
            var body = await window.socialNetworkWorkspace.requestDataset(
                window.socialNetworkWorkspace.datasetUrl(
                    "network/bridge-candidates/" +
                        encodeURIComponent(candidate.accountId) + "/evidence",
                    query,
                    context
                )
            );
            if (sequence !== state.evidenceSequence ||
                    !state.dialog.open ||
                    !state.config.isCurrent()) {
                return;
            }
            var data = body.data || {};
            var identity = data.analysisIdentity;
            if (data.state === "unavailable") {
                evidenceStatus(bridgeStatusLabel(data.reason), true);
                return;
            }
            if (!identity ||
                    identity.analysisResultId !== state.identity.analysisResultId ||
                    identity.dataFingerprint !== state.identity.dataFingerprint) {
                evidenceStatus(
                    bridgeStatusLabel("analysis_result_changed"), true);
                return;
            }
            state.evidenceData = data;
            state.evidenceSnapshot = {
                accountId: candidate.accountId,
                communityId: communityId || null,
                direction: direction,
                offset: Number(data.offset || 0),
                pageSize: Number(data.pageSize || PAGE_SIZE)
            };
            renderEvidence(data);
        } catch (error) {
            if (sequence === state.evidenceSequence &&
                    state.dialog.open &&
                    !error.staleDatasetContext) {
                evidenceStatus(
                    "暂时无法读取关系来源，请关闭后刷新候选再试。",
                    true);
            }
        }
    }

    function locateInGraph(candidate, context, mount) {
        var status = mount.querySelector("[data-ranking-bridge-status]");
        try {
            window.sessionStorage.setItem(
                PENDING_GRAPH_FOCUS_KEY,
                JSON.stringify({
                    datasetId: context.datasetId,
                    datasetVersion: context.datasetVersion ||
                        BUILTIN_DATASET_VERSION,
                    accountId: candidate.accountId,
                    communityId: candidate.communityId
                })
            );
        } catch (error) {
            status.textContent =
                "浏览器暂不允许跨页面保留定位账号，请先打开关系网络图并搜索该账号 ID。";
            return;
        }
        status.textContent = "正在打开关系网络图并定位 " +
            candidate.accountId + "…";
        window.location.hash = "#/social-network/network";
    }

    function closeEvidence() {
        state.evidenceSequence += 1;
        if (state.dialog && state.dialog.open) {
            state.dialog.close();
        }
    }

    function resetIdentity() {
        state.identity = null;
        state.response = null;
        state.loading = false;
        state.evidenceData = null;
        state.evidenceSnapshot = null;
    }

    function getExportSnapshot() {
        if (state.loading || !state.config || !state.config.isCurrent() ||
                !state.identity || !state.identity.analysisResultId ||
                !state.response ||
                Number(state.response.offset || 0) !==
                    Number(state.config.offset || 0) ||
                state.response.analysisIdentity !== state.identity) {
            return null;
        }
        var query = {
            analysisResultId: state.identity.analysisResultId,
            offset: Number(state.response.offset || 0),
            pageSize: Number(state.response.pageSize || PAGE_SIZE)
        };
        if (state.evidenceSnapshot &&
                (state.response.items || []).some(function (candidate) {
                    return candidate.accountId ===
                        state.evidenceSnapshot.accountId;
                })) {
            query.evidence = Object.assign({}, state.evidenceSnapshot);
        }
        return {
            query: query,
            analysisIdentity: state.identity
        };
    }

    window.socialNetworkBridgeCandidates = {
        load: loadCandidates,
        closeEvidence: closeEvidence,
        resetIdentity: resetIdentity,
        getExportSnapshot: getExportSnapshot
    };
})();
