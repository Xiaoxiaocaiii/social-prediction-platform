(function () {
    "use strict";

    var BUILTIN_DATASET_CONTEXT = {
        datasetId: "think-tank-social-network",
        datasetVersion: null,
        sessionId: null
    };
    var workspaceState = window.createSocialNetworkState(BUILTIN_DATASET_CONTEXT);
    var socialNetworkExportModel = window.socialNetworkExportModel;
    var socialNetworkOverviewModel = window.socialNetworkOverviewModel;
    var uploadPreviewModel = window.socialNetworkUploadPreviewModel;
    var socialNetworkQualityModel = window.socialNetworkQualityModel;
    var socialNetworkRankingModel = window.socialNetworkRankingModel;
    var shellDatasetSummaryLookup = null;
    var shellDatasetSummaryCache = null;
    var activeSocialNetworkTask = "";
    var socialUserDrawerOpener = null;
    var socialUserDrawerEvidence = null;
    var socialUserDrawerUserId = "";
    var socialUserDrawerSequence = 0;
    var qualityReportRequestSequence = 0;
    var qualityReportContextKey = "";
    var qualityReportDimension = "users";
    var qualityReportWarningPage = 1;
    var currentQualityReport = null;
    var networkStructureRequestSequence = 0;
    var networkStructureContextKey = "";
    var networkStructureView = "overview";
    var networkStructureKCoreMode = "exact";
    var networkStructureSelectedCore = null;
    var networkStructureNodeQuery = "";
    var networkStructureNodePage = 1;
    var networkStructureTooltipPositioningBound = false;
    var currentNetworkStructure = null;
    var networkStructureLookup = null;
    var networkStructureModel = window.socialNetworkNetworkStructureModel;
    var interactionTaskResultCache = null;
    var interactionTaskIdentityPending = false;
    var interactionTaskIdentitySequence = 0;
    var topicTaskResult = null;
    var topicTaskIdentityPending = false;
    var topicTaskIdentitySequence = 0;
    var topicDetailDialogBound = false;
    var topicDetailAuthorUserId = "";
    var topicDetailOpener = null;
    var rankingTaskRequestSequence = 0;
    var rankingTaskContextKey = "";
    var rankingTaskView = "single";
    var rankingTaskRolesCache = null;
    var rankingTaskRolesLookup = null;
    var rankingTaskOrdinaryLookup = null;
    var userRelationsSearchSequence = 0;
    var userRelationsLocalSequence = 0;
    var userRelationsContextKey = "";
    var userRelationsSearchResult = null;
    var userRelationsLocalResult = null;
    var userRelationsResultCategory = "graph";
    var userRelationsNodePage = 1;
    var userRelationsLocalChart = null;
    var userRelationsChartResizeObserver = null;
    var userRelationsChartResizeHandler = null;
    var userRelationsChartInitialOption = null;
    var uploadFiles = workspaceState.state.uploadDraft.files;
    var uploadState = workspaceState.state;
    var uploadPollTimer = null;
    var uploadPollFailure = null;
    var uploadPollRetryCount = 0;
    var uploadPollInFlight = false;
    var uploadPollQueued = false;
    var uploadCancelInFlight = false;
    var uploadSubmitting = false;
    var uploadActivationInFlight = false;
    var uploadPreviewCategory = "files";
    var uploadQualityDimension = "summary";
    var localNetworkChart = null;
    var activeUploadContext = null;
    var activeModuleExportType = "";
    var activeModuleExportSnapshot = null;
    var activeModuleExportInvoker = null;
    var moduleExportControlsBound = false;
    var datasetPageRequestSequence = 0;
    var datasetPageControlsBound = false;
    var currentDatasetDetails = null;
    var overviewRequestSequence = 0;
    var overviewControlsBound = false;
    var UPLOAD_TASK_STORAGE_KEY = "social-network.upload-task-id.v1";
    var MAX_UPLOAD_FILES = 10;
    var MAX_UPLOAD_FILE_BYTES = 128 * 1024 * 1024;
    var MAX_UPLOAD_REQUEST_BYTES = 160 * 1024 * 1024;
    restoreUploadTaskState();

    function readDatasetContext() {
        return workspaceState.getDatasetContext();
    }

    function normalizeDatasetContext(context) {
        if (!context || typeof context.datasetId !== "string" ||
                !context.datasetId.trim()) {
            throw new Error("数据集上下文必须包含 datasetId");
        }
        var datasetVersion = context.datasetVersion == null
            ? ""
            : String(context.datasetVersion).trim();
        var sessionId = context.sessionId == null
            ? ""
            : String(context.sessionId).trim();
        if (sessionId && !datasetVersion) {
            throw new Error("上传会话上下文必须包含 datasetVersion");
        }
        return {
            datasetId: context.datasetId.trim(),
            datasetVersion: datasetVersion || null,
            sessionId: sessionId || null
        };
    }

    function sameDatasetContext(first, second) {
        return first.datasetId === second.datasetId &&
            first.datasetVersion === second.datasetVersion &&
            first.sessionId === second.sessionId;
    }

    function publishDatasetContextChange() {
        window.dispatchEvent(new Event("social-network-dataset-context-changed"));
    }

    function buildDatasetUrl(resource, query, contextOverride) {
        var context = contextOverride || readDatasetContext();
        var path = "/api/social-network/datasets/" +
            encodeURIComponent(context.datasetId);
        if (resource) {
            path += "/" + String(resource).replace(/^\/+/, "");
        }
        var params = new URLSearchParams();
        if (context.datasetVersion) {
            params.set("datasetVersion", context.datasetVersion);
        }
        if (context.sessionId) {
            params.set("sessionId", context.sessionId);
        }
        Object.keys(query || {}).forEach(function (key) {
            var value = query[key];
            if (value !== null && value !== undefined) {
                params.set(key, String(value));
            }
        });
        var queryString = params.toString();
        return path + (queryString ? "?" + queryString : "");
    }

    var workspaceContextApi = window.socialNetworkWorkspace || {};
    workspaceContextApi.getDatasetContext = readDatasetContext;
    workspaceContextApi.setDatasetContext = function (context) {
        var next = normalizeDatasetContext(context);
        if (workspaceState.setDatasetContext(next)) {
            publishDatasetContextChange();
        }
        return readDatasetContext();
    };
    workspaceContextApi.resetDatasetContext = function () {
        return workspaceContextApi.setDatasetContext(BUILTIN_DATASET_CONTEXT);
    };
    workspaceContextApi.datasetUrl = buildDatasetUrl;
    workspaceContextApi.requestDataset = request;
    window.socialNetworkWorkspace = workspaceContextApi;

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

    function formatPercent(value) {
        return (Number(value) * 100).toLocaleString("zh-CN", {
            maximumFractionDigits: 1
        }) + "%";
    }

    function setText(id, value) {
        var element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    function setOptionalMetric(id, value, formatter) {
        var element = document.getElementById(id);
        if (!element) {
            return;
        }
        var unavailable = value === null || value === undefined;
        element.textContent = unavailable ? "不可用" : formatter(value);
        element.classList.toggle("is-unavailable", unavailable);
    }

    function statusLabel(status) {
        var value = status && typeof status === "object"
            ? (status.state || status.status)
            : status;
        return statusLabels[value] || value || "未知";
    }

    function renderModuleError(elementId, message, error) {
        if (isStaleDatasetRequest(error)) {
            return;
        }
        if (typeof console !== "undefined" && console.error) {
            console.error("[social-network] " + message, error);
        }
        var element = document.getElementById(elementId);
        if (element) {
            element.innerHTML = '<div class="social-network-empty is-error">' +
                escapeHtml(message) + '</div>';
        }
    }

    function isStaleDatasetRequest(error) {
        return Boolean(error &&
            (error.staleDatasetContext || error.staleTaskRequest));
    }

    function staleDatasetRequestError() {
        var error = new Error("数据集已切换，已忽略旧请求结果。");
        error.staleDatasetContext = true;
        return error;
    }

    function staleTaskRequestError() {
        var error = new Error("任务条件已变化，已忽略旧请求结果。");
        error.staleTaskRequest = true;
        return error;
    }

    function beginTaskRequest(task, parameters) {
        return workspaceState.beginRequest(task, parameters);
    }

    function requireCurrentTaskRequest(token, parameters) {
        if (!workspaceState.isRequestCurrent(token, parameters)) {
            throw staleTaskRequestError();
        }
    }

    function recoverExpiredDatasetContext(context) {
        if (!context.sessionId ||
                !sameDatasetContext(context, readDatasetContext())) {
            return false;
        }
        activeUploadContext = null;
        workspaceContextApi.resetDatasetContext();
        clearContextBoundViews();
        setText("social-network-task-status", "上传会话已失效");
        setText("social-network-task-message",
            "已切回内置数据；当前任务正在按新的数据身份刷新。");
        return true;
    }

    async function request(url, options) {
        var revision = workspaceState.getDatasetRevision();
        var context = readDatasetContext();
        var response;
        var body;
        try {
            response = await fetch(url, options || {
                headers: { "Accept": "application/json" }
            });
            body = await response.json();
        } catch (error) {
            if (revision !== workspaceState.getDatasetRevision()) {
                throw staleDatasetRequestError();
            }
            throw error;
        }
        if (revision !== workspaceState.getDatasetRevision()) {
            throw staleDatasetRequestError();
        }
        if (!response.ok || !body.success) {
            var apiError = new Error(body.message || "社交网络接口请求失败");
            apiError.code = body.code || null;
            apiError.status = response.status;
            if (apiError.code === "SN_DATASET_SESSION_EXPIRED" &&
                    recoverExpiredDatasetContext(context)) {
                throw staleDatasetRequestError();
            }
            throw apiError;
        }
        return body;
    }

    function currentExportQuery(exportType) {
        if (exportType === "users") {
            return { metric: workspaceState.getCondition("ranking").metric };
        }
        if (exportType === "interactions") {
            return { metric: workspaceState.getCondition("interaction").metric };
        }
        if (exportType === "topic") {
            return {
                term: workspaceState.getCondition("topic").submittedTerm || ""
            };
        }
        return {};
    }

    function initializeModuleExportControls() {
        if (moduleExportControlsBound) {
            return;
        }
        var dialog = document.getElementById("social-network-module-export-dialog");
        document.getElementById("social-network-module-export-submit")
            .addEventListener("click", downloadModuleExport);
        document.getElementById("social-network-module-export-cancel")
            .addEventListener("click", function () {
                dialog.close();
            });
        document.getElementById("social-network-module-export-close")
            .addEventListener("click", function () {
                dialog.close();
            });
        dialog.addEventListener("close", function () {
            var invoker = activeModuleExportInvoker;
            workspaceState.invalidateRequest("moduleExport");
            activeModuleExportType = "";
            activeModuleExportSnapshot = null;
            activeModuleExportInvoker = null;

            window.requestAnimationFrame(function () {
                if (invoker && invoker.isConnected &&
                        invoker.getClientRects().length && !invoker.disabled) {
                    invoker.focus();
                    return;
                }
                var shellMain = document.getElementById("social-network-shell-main");
                if (shellMain && document.body.classList.contains(
                        "social-network-shell-active")) {
                    shellMain.focus();
                }
            });
        });
        document.getElementById("social-network-module-export-users-metric")
            .addEventListener("change", function () {
                if (activeModuleExportType !== "users" ||
                        !activeModuleExportSnapshot) {
                    return;
                }
                var snapshot = activeModuleExportSnapshot;
                activeModuleExportSnapshot = socialNetworkExportModel.createSnapshot(
                    "users",
                    snapshot.datasetContext,
                    { metric: this.value }
                );
                workspaceState.invalidateRequest("moduleExport");
                document.getElementById(
                    "social-network-module-export-status").textContent =
                    "导出范围已更新：包含所选普通排行及两类结构角色排行。";
                document.getElementById(
                    "social-network-module-export-submit").disabled = false;
            });
        moduleExportControlsBound = true;
    }

    function openModuleExportDialog(exportType, invoker) {
        var dialog = document.getElementById("social-network-module-export-dialog");
        var title = document.getElementById("social-network-module-export-title");
        var note = document.getElementById("social-network-module-export-note");
        var dataset = document.getElementById("social-network-module-export-dataset");
        var format = document.getElementById("social-network-module-export-format");
        var submit = document.getElementById("social-network-module-export-submit");
        var close = document.getElementById("social-network-module-export-close");
        var usersMetricField = document.getElementById(
            "social-network-module-export-users-metric-field");
        var usersMetricControl = document.getElementById(
            "social-network-module-export-users-metric");
        var labels = {
            network: ["网络结构摘要", "导出完整网络结构摘要。"],
            users: ["重要节点排行", "users 导出是组合包，包含一个普通指标排行以及 k-core、介数中心性两类结构角色排行。"],
            interactions: ["推文与互动分析", "导出互动分析结果包，包含当前指标摘要、推文 Top 10、用户 Top 10 和字符串排序末尾最多 30 个时间分组；结果包不等同当前可见表格，且不包含推文原文。"],
            topic: ["主题查询结果包", "导出固定使用最后一次成功查询词，不读取当前输入草稿。"],
            quality: ["数据质量报告", "导出当前数据集的质量计数与覆盖摘要。"]
        };
        if (!labels[exportType]) {
            return;
        }
        initializeModuleExportControls();
        var context = readDatasetContext();
        var query = currentExportQuery(exportType);
        usersMetricField.hidden = true;
        if (exportType === "users") {
            var selectedMetric = workspaceState.getCondition("ranking").metric;
            var needsMetricChoice =
                !socialNetworkRankingModel.isOrdinaryExportMetric(selectedMetric);
            usersMetricField.hidden = !needsMetricChoice;
            usersMetricControl.value = needsMetricChoice
                ? "indegree"
                : selectedMetric;
            query = { metric: usersMetricControl.value };
        }
        activeModuleExportType = exportType;
        activeModuleExportSnapshot = socialNetworkExportModel.createSnapshot(
            exportType,
            context,
            query
        );
        activeModuleExportInvoker = invoker || document.activeElement;
        workspaceState.setCondition("export", {
            type: exportType,
            format: format.value
        });
        title.textContent = "导出" + labels[exportType][0];
        note.textContent = labels[exportType][1] +
            " 单次响应上限为 2 MiB，超过时会提示缩小范围。";
        if (exportType === "users" && !usersMetricField.hidden) {
            note.textContent =
                "当前页面指标不属于 users 导出支持的普通排行。请选择入度、出度或 PageRank；" +
                "导出包同时包含所选普通排行 Top 20、k-core 候选和介数中心性候选（各最多 20 项）。" +
                "单次响应上限为 2 MiB。";
        } else if (exportType === "users") {
            note.textContent =
                "导出包包含所选普通指标 Top 20，以及 k-core 和介数中心性两类结构角色排行（各最多 20 项）。" +
                "单次响应上限为 2 MiB。";
        } else if (exportType === "network") {
            note.textContent =
                "包含完整网络计数、结构指标、PageRank 状态及互动摘要；" +
                "不包含关系图节点或边明细，若服务返回明细将阻止下载。";
        } else if (exportType === "quality") {
            note.textContent =
                "可打印摘要按结论、指标口径、数据范围、质量提示和复核信息组织；" +
                "JSON/CSV 保留原结构。报告不受本页分类或分页影响，单次响应上限为 2 MiB。";
        } else if (exportType === "topic") {
            var topicCondition = workspaceState.getCondition("topic");
            note.textContent = "结果包使用最后一次成功查询“" +
                activeModuleExportSnapshot.query.term +
                "”，不使用输入草稿；包含主题相关推文、关联用户及真实关注关系的既有导出范围。" +
                "互动统计缺失或解析失败可能按 0 计入，单次响应上限为 2 MiB。";
            if (String(topicCondition.draft || "").trim() !==
                    activeModuleExportSnapshot.query.term) {
                note.textContent += " 当前输入草稿尚未提交。";
            }
        }
        dataset.textContent = "数据集：" + context.datasetId +
            (context.datasetVersion ? " · 版本 " + context.datasetVersion : "");
        document.getElementById("social-network-module-export-status").textContent =
            "参数已从共享状态快照读取";
        submit.disabled = exportType === "topic" &&
            !activeModuleExportSnapshot.query.term;
        format.disabled = false;
        dialog.showModal();
        var initialFocus = exportType === "users" && !usersMetricField.hidden
            ? usersMetricControl
            : format.disabled ? close : format;
        initialFocus.focus();
    }

    async function downloadModuleExport() {
        var dialog = document.getElementById("social-network-module-export-dialog");
        var submit = document.getElementById("social-network-module-export-submit");
        var status = document.getElementById("social-network-module-export-status");
        var format = document.getElementById("social-network-module-export-format").value;
        var snapshot = activeModuleExportSnapshot;
        if (!snapshot) {
            return;
        }
        if (!socialNetworkExportModel.matchesDataset(snapshot, readDatasetContext())) {
            status.textContent = "当前数据集已变化，请关闭后重新打开导出范围。";
            return;
        }
        var exportType = snapshot.exportType;
        var query = snapshot.query;
        var requestParameters = {
            exportType: exportType,
            format: format,
            query: query,
            datasetContext: snapshot.datasetContext
        };
        workspaceState.setCondition("export", {
            type: exportType,
            format: format
        });
        var requestToken = beginTaskRequest("moduleExport", requestParameters);
        submit.disabled = true;
        status.textContent = "正在生成导出文件…";
        try {
            var body = await request(buildDatasetUrl(
                "exports",
                null,
                snapshot.datasetContext
            ), {
                method: "POST",
                headers: {
                    "Accept": "application/json",
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    exportType: exportType,
                    format: format,
                    query: query,
                    scope: { view: "current_module" }
                })
            });
            requireCurrentTaskRequest(requestToken, requestParameters);
            var data = body.data || {};
            var hasDetailedNetworkResponse = false;
            if (exportType === "network" && data.result &&
                    typeof data.result === "object") {
                hasDetailedNetworkResponse =
                    Object.prototype.hasOwnProperty.call(data.result, "nodes") ||
                    Object.prototype.hasOwnProperty.call(data.result, "edges");
            } else if (exportType === "network" && format === "csv" &&
                    typeof data.result === "string") {
                var csvHeader = data.result.split(/\r?\n/, 1)[0]
                    .replace(/^\uFEFF/, "")
                    .split(",")
                    .map(function (field) {
                        return field.trim().toLowerCase();
                    });
                hasDetailedNetworkResponse = csvHeader.some(function (field) {
                    return ["nodeid", "source", "target", "sourceuserid",
                        "targetuserid"].indexOf(field) !== -1;
                });
            }
            if (hasDetailedNetworkResponse) {
                status.textContent =
                    "当前网络导出响应包含节点或边明细，已阻止下载；请更新服务后重试。";
                return;
            }
            var fileContent = format === "json"
                ? JSON.stringify({
                    exportType: data.exportType || exportType,
                    metadata: data.metadata,
                    data: data.result
                })
                : String(data.result == null ? "" : data.result);
            var extensions = { json: "json", csv: "csv", print: "txt" };
            var safeDatasetId = snapshot.datasetContext.datasetId
                .replace(/[^a-zA-Z0-9_-]+/g, "-");
            var fileName = safeDatasetId + "-" + exportType +
                "-" + new Date().toISOString().replace(/[:.]/g, "-") +
                "." + extensions[format];
            var blob = new Blob([fileContent], {
                type: data.contentType || "application/octet-stream"
            });
            var url = URL.createObjectURL(blob);
            var link = document.createElement("a");
            link.href = url;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.setTimeout(function () {
                URL.revokeObjectURL(url);
            }, 1000);
            status.textContent = "已下载 · " + formatNumber(data.contentLength) +
                " 字节" + (data.resultState === "limited" ? " · 结果已截断，范围见文件元信息" : "");
        } catch (error) {
            if (isStaleDatasetRequest(error)) {
                status.textContent = "数据集已切换，请在当前数据集重新导出。";
                return;
            }
            status.textContent = error.message || "导出失败，请稍后重试。";
            if (typeof console !== "undefined" && console.error) {
                console.error("[social-network] module export failed", error);
            }
        } finally {
            if (workspaceState.isLatestRequest(requestToken) && dialog.open &&
                    activeModuleExportSnapshot === snapshot) {
                submit.disabled = false;
            }
        }
    }

    function firstPresent() {
        for (var index = 0; index < arguments.length; index += 1) {
            if (arguments[index] !== null && arguments[index] !== undefined) {
                return arguments[index];
            }
        }
        return null;
    }

    function renderSummary(body) {
        var data = body.data || {};
        var counts = data.counts || {};
        var qualitySummary = data.qualitySummary || {};
        var userQuality = qualitySummary.userQuality || {};
        var normalizedUserCount = firstPresent(
            counts.normalized_user_count,
            userQuality.normalized_user_count
        );
        setText("social-network-source-description", data.sourceDescription ||
            "当前数据由项目开发者整理并提供，具体采集来源、时间范围、数据处理方式和授权说明以最终数据说明文档为准。");
        setText("social-network-dataset-scope", data.type === "builtin"
            ? "内置数据集 · 版本 " + (data.version || "-")
            : "当前分析数据集");
        setText("social-network-dataset-status", statusLabel(data));
        setText("social-network-dataset-version", "版本 " + (data.version || "-"));
        setText("social-network-user-count", formatNumber(
            normalizedUserCount === null
                ? counts.user_record_count
                : normalizedUserCount));
        setText("social-network-relationship-count", formatNumber(
            counts.relationship_candidate_count));
        setText("social-network-tweet-count", formatNumber(
            counts.tweet_record_count));
    }

    function renderNetworkSummary(body) {
        var data = body.data || {};
        setText("social-network-scope", data.scope || "-");
        setText("social-network-density", data.density === null
            ? "不可计算"
            : formatDensity(data.density));
        setText("social-network-isolated-count", formatNumber(data.isolatedNodeCount));
        setText("social-network-external-count", formatNumber(data.externalNodeCount));
        setText("social-network-structure-scope",
            "完整规范化网络 · " + formatNumber(data.nodeCount) + " 个节点（含外部 " +
            formatNumber(data.externalNodeCount) + "）· " +
            formatNumber(data.edgeCount) + " 条有效有向关系");
        setText("social-network-metric-version",
            "指标版本 " + (data.metricVersion || "-"));
        setOptionalMetric(
            "social-network-component-count",
            data.weaklyConnectedComponentCount,
            formatNumber);
        setOptionalMetric(
            "social-network-largest-component-ratio",
            data.largestWeaklyConnectedComponentRatio,
            formatPercent);
        var totalNodeCount = Number(data.nodeCount);
        var largestComponentNodeCount =
            data.largestWeaklyConnectedComponentNodeCount;
        setText("social-network-largest-component-count",
            totalNodeCount > 0
                ? formatNumber(largestComponentNodeCount) + " / " +
                    formatNumber(totalNodeCount) + " 个节点"
                : "完整网络无节点");
        setOptionalMetric(
            "social-network-average-clustering",
            data.averageClusteringCoefficient,
            formatDensity);
        setOptionalMetric(
            "social-network-reciprocity-rate",
            data.reciprocityRate,
            formatPercent);
        setOptionalMetric(
            "social-network-average-path-length",
            data.largestWeaklyConnectedComponentAveragePathLength,
            formatDecimal);
        var pageRank = data.pageRank || {};
        setText("social-network-pagerank-state", pageRank.converged
            ? "已收敛 · " + pageRank.iterations + " 轮迭代"
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
        var users = data.userQuality || {};
        var relationships = data.relationshipQuality || {};
        var tweets = data.tweetQuality || {};
        var warnings = body.warnings || [];
        var conflictWarning = warnings.find(function (warning) {
            return warning.code === "CONFLICTING_TWEET_RECORD";
        });
        setText("social-network-quality-status", statusLabel(
            data.status || data.datasetStatus || data.state));
        setText("social-network-quality-user-count", formatNumber(users.normalized_user_count));
        setText("social-network-quality-user-raw", formatNumber(users.raw_user_count));
        setText("social-network-quality-relationship-count",
            formatNumber(relationships.normalized_relationship_count));
        setText("social-network-quality-relationship-duplicates",
            formatNumber(relationships.duplicate_relationship_count));
        setText("social-network-quality-relationship-invalid",
            formatNumber(relationships.invalid_relationship_count));
        setText("social-network-quality-tweet-raw", formatNumber(tweets.raw_tweet_count));
        setText("social-network-quality-tweet-duplicates",
            formatNumber(tweets.duplicate_tweet_count));
        setText("social-network-quality-conflict-count",
            formatNumber(conflictWarning ? conflictWarning.count : 0));
        setText("social-network-quality-conflict-records",
            formatNumber(tweets.conflict_duplicate_count));
        setText("social-network-quality-linked-tweets",
            formatNumber(tweets.linked_author_count));
        setText("social-network-quality-unlinked-authors",
            formatNumber(tweets.unlinked_author_count));
        setText("social-network-quality-unassigned-source",
            formatNumber(tweets.unassigned_source_count));
        var container = document.getElementById("social-network-quality-items");
        if (!warnings.length) {
            container.innerHTML = '<div class="social-network-quality-ok">当前没有需要单独提示的质量问题。</div>';
            return;
        }
        container.innerHTML = warnings.map(function (warning) {
            var count = Number(warning.count || 0);
            var countUnit = warning.code === "CONFLICTING_TWEET_RECORD" ? " 个 ID" : " 条";
            var countLabel = count > 0
                ? "（" + formatNumber(count) + countUnit + "）"
                : "";
            var title = warning.code === "CONFLICTING_TWEET_RECORD"
                ? "推文记录冲突"
                : warning.code || "数据质量提示";
            var message = warning.code === "CONFLICTING_TWEET_RECORD"
                ? "相同推文 ID 对应的内容字段不一致；涉及 " +
                    formatNumber(tweets.conflict_duplicate_count) +
                    " 条原始记录，这些冲突记录不会作为可靠样本参与相关分析。"
                : warning.message || "";
            return '<div class="social-network-quality-item">' +
                '<span class="social-network-quality-badge">' +
                escapeHtml(statusLabel(warning.severity || "limited")) + '</span>' +
                '<div><strong>' + escapeHtml(title) +
                escapeHtml(countLabel) + '</strong>' +
                '<p>' + escapeHtml(message) + '</p></div>' +
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
        if (localNetworkChart) {
            localNetworkChart.dispose();
            localNetworkChart = null;
        }
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
                '<small>数据内被关注数（入度） ' + formatNumber(node.inDegree) +
                ' · 数据内关注数（出度） ' + formatNumber(node.outDegree) + '</small></div>';
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
            localNetworkChart = echarts.init(
                document.getElementById("social-network-local-chart"));
            localNetworkChart.setOption({
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
        }
    }

    async function searchUsers() {
        var query = workspaceState.getCondition("userSearch").query;
        var requestParameters = { query: query, limit: 20 };
        var requestToken = beginTaskRequest("userSearch", requestParameters);
        var result = await request(buildDatasetUrl("users/search", {
            query: query,
            limit: 20
        }));
        requireCurrentTaskRequest(requestToken, requestParameters);
        renderUserResults(result);
    }

    async function loadLocalNetwork(centerNodeId) {
        workspaceState.setCondition("localNetwork", {
            centerNodeId: centerNodeId
        });
        var conditions = workspaceState.getCondition("localNetwork");
        var requestParameters = {
            centerNodeId: centerNodeId,
            depth: conditions.depth,
            topK: conditions.topK,
            includeExternal: conditions.includeExternal
        };
        var requestToken = beginTaskRequest("localNetwork", requestParameters);
        var status = document.getElementById("social-network-local-status");
        status.textContent = "正在加载局部网络…";
        try {
            var result = await request(buildDatasetUrl("network/local", {
                centerNodeId: centerNodeId,
                depth: conditions.depth,
                topK: conditions.topK,
                includeExternalNodes: conditions.includeExternal
            }));
            requireCurrentTaskRequest(requestToken, requestParameters);
            renderLocalNetwork(result);
        } catch (error) {
            if (isStaleDatasetRequest(error)) {
                return;
            }
            status.textContent = "局部网络不可用";
            renderModuleError("social-network-local-view", "局部网络暂时不可用。", error);
        }
    }

    function renderRanking(body) {
        var data = body.data || {};
        var items = data.items || [];
        var metric = data.metric || workspaceState.getCondition("ranking").metric;
        var label = rankingMetricLabels[metric] || data.label || "-";
        var itemUnit = data.metricType === "network" ? "个节点" : "个账号";
        setText("social-network-ranking-status",
            label + " · " + formatNumber(data.sampleSize) + " " + itemUnit);
        setText("social-network-ranking-note",
            (rankingMetricNotes[metric] || "") + "当前范围：" +
            (data.scope || "-") + "。各指标独立排序，不生成综合影响力分。");
        var container = document.getElementById("social-network-ranking-table");
        if (data.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">' +
                escapeHtml(data.reason || "当前指标暂无可用样本。") + '</div>';
            return;
        }
        container.innerHTML = '<table><thead><tr>' +
            '<th>名次</th><th>节点</th><th>数值</th><th>状态</th>' +
            '<th>样本量/范围</th><th>进入原因</th><th>详情</th></tr></thead><tbody>' +
            items.map(function (item) {
                var name = item.displayName || item.username || item.nodeId;
                return '<tr class="social-network-ranking-row" data-user-id="' +
                    escapeHtml(item.nodeId) + '"><td>' + formatNumber(item.rank) + '</td>' +
                    '<td><strong>' + escapeHtml(name) + '</strong><small>' +
                    escapeHtml(item.nodeId) + '</small></td><td>' +
                    formatDecimal(item.value) + '</td><td>' +
                    escapeHtml(item.status || "available") + '</td><td>' +
                    formatNumber(item.sampleSize) + ' · ' + escapeHtml(data.scope || "-") +
                    '</td><td>' + escapeHtml("按" + label + "降序排列") +
                    '</td><td><button type="button" class="social-network-user-detail-action" ' +
                    'data-detail-user-id="' + escapeHtml(item.nodeId) + '">' +
                    '查看用户详情</button></td></tr>';
            }).join("") + '</tbody></table>';
        container.querySelectorAll("[data-detail-user-id]").forEach(function (button, index) {
            button.addEventListener("click", function () {
                var item = items[index];
                window.openSocialNetworkUserDetail(item.nodeId, {
                    metric: metric,
                    metricLabel: label,
                    rank: item.rank,
                    value: item.value,
                    scope: data.scope || "",
                    metricType: data.metricType || ""
                }, button);
            });
        });
    }

    async function loadRanking() {
        var metric = workspaceState.getCondition("ranking").metric;
        var requestParameters = { metric: metric, limit: 20 };
        var requestToken = beginTaskRequest("ranking", requestParameters);
        var result = await request(buildDatasetUrl("rankings", {
            metric: metric,
            limit: 20
        }));
        requireCurrentTaskRequest(requestToken, requestParameters);
        renderRanking(result);
    }

    function renderStructuralRoleBlock(containerId, stateId, block, unavailableMessage) {
        block = block || {};
        var container = document.getElementById(containerId);
        var items = block.items || [];
        setText(stateId, statusLabel(block.state));
        if (block.state === "unavailable") {
            container.innerHTML = '<div class="social-network-empty is-unavailable">' +
                escapeHtml(block.reason || unavailableMessage) + '</div>';
            return;
        }
        if (block.state === "empty" || !items.length) {
            container.innerHTML = '<div class="social-network-empty">' +
                escapeHtml(block.reason || "当前范围内没有可用节点。") + '</div>';
            return;
        }
        container.innerHTML = '<table><thead><tr>' +
            '<th>名次</th><th>节点</th><th>数值</th><th>节点类型</th>' +
            '</tr></thead><tbody>' + items.map(function (item) {
                var name = item.displayName || item.username || item.nodeId;
                var nodeType = item.nodeType === "external" ? "外部节点" : "资料用户";
                return '<tr><td>' + formatNumber(item.rank) + '</td>' +
                    '<td><strong>' + escapeHtml(name) + '</strong><small>' +
                    escapeHtml(item.nodeId) + '</small></td><td>' +
                    formatDecimal(item.value) + '</td><td>' +
                    escapeHtml(nodeType) + '</td></tr>';
            }).join("") + '</tbody></table>';
    }

    function renderStructuralRoles(body) {
        var data = body.data || {};
        setText("social-network-structural-roles-status",
            data.algorithmVersion || "结构指标");
        setText("social-network-structural-roles-scope",
            "完整范围：" + formatNumber(data.nodeCount) + " 个节点 · " +
            formatNumber(data.undirectedTieCount) + " 条无向关系。" +
            (data.scope || ""));
        renderStructuralRoleBlock(
            "social-network-kcore-table",
            "social-network-kcore-state",
            data.kCore,
            "当前范围没有可用的 k-core 节点。");
        renderStructuralRoleBlock(
            "social-network-betweenness-table",
            "social-network-betweenness-state",
            data.betweenness,
            "当前范围内无法计算归一化介数中心性。");
        var crossCommunityEvidence = data.crossCommunityEvidence || {};
        setText(
            "social-network-cross-community-evidence",
            (crossCommunityEvidence.state === "unavailable"
                ? "跨社群辅助证据暂不可用："
                : "跨社群辅助证据：") +
                (crossCommunityEvidence.reason ||
                    "社群结果尚未通过负责人复核。"));
    }

    async function loadStructuralRoles() {
        var requestParameters = { limit: 20 };
        var requestToken = beginTaskRequest("structuralRoles", requestParameters);
        var result = await request(buildDatasetUrl("network/structure-roles", {
            limit: 20
        }));
        requireCurrentTaskRequest(requestToken, requestParameters);
        renderStructuralRoles(result);
    }

    function detailHasValue(value) {
        return value !== null && value !== undefined &&
            String(value).trim() !== "";
    }

    function detailField(label, value, formatter) {
        if (!detailHasValue(value)) {
            return "";
        }
        var rendered = formatter ? formatter(value) : escapeHtml(value);
        return "<p><span>" + escapeHtml(label) + "：</span>" + rendered + "</p>";
    }

    function userDetailDatasetLabel() {
        var context = readDatasetContext();
        return "当前数据：" + context.datasetId +
            (context.datasetVersion ? " · 版本 " + context.datasetVersion : "");
    }

    function activateUserDetailCategory(category, focusTab) {
        var selected = document.querySelector(
            '[data-user-detail-tab="' + category + '"]');
        if (!selected) {
            return;
        }
        document.querySelectorAll("[data-user-detail-tab]").forEach(function (tab) {
            var isSelected = tab === selected;
            tab.setAttribute("aria-selected", isSelected ? "true" : "false");
            tab.tabIndex = isSelected ? 0 : -1;
        });
        document.querySelectorAll("[data-user-detail-panel]").forEach(function (panel) {
            panel.hidden = panel.getAttribute("data-user-detail-panel") !== category;
        });
        if (focusTab) {
            selected.focus();
        }
    }

    document.querySelectorAll("[data-user-detail-tab]").forEach(function (tab) {
        tab.addEventListener("click", function () {
            activateUserDetailCategory(tab.getAttribute("data-user-detail-tab"), false);
        });
        tab.addEventListener("keydown", function (event) {
            var tabs = Array.prototype.slice.call(
                document.querySelectorAll("[data-user-detail-tab]"));
            var currentIndex = tabs.indexOf(tab);
            var nextIndex;
            if (event.key === "ArrowRight") {
                nextIndex = (currentIndex + 1) % tabs.length;
            } else if (event.key === "ArrowLeft") {
                nextIndex = (currentIndex + tabs.length - 1) % tabs.length;
            } else if (event.key === "Home") {
                nextIndex = 0;
            } else if (event.key === "End") {
                nextIndex = tabs.length - 1;
            } else {
                return;
            }
            event.preventDefault();
            activateUserDetailCategory(
                tabs[nextIndex].getAttribute("data-user-detail-tab"), true);
        });
    });

    function renderUserDetail(body, sourceEvidence) {
        var data = body.data || {};
        var profile = data.profile || {};
        var network = data.networkPosition || {};
        var raw = data.rawAccountFields || {};
        var tweets = data.tweetEvidence || {};
        var metrics = tweets.interactions || {};
        var external = data.nodeType === "external";
        var userId = data.userId || socialUserDrawerUserId;
        var displayName = profile.displayName || profile.username || userId;
        var profileStatus = profile.status || (external
            ? "外部节点 · 资料未采集"
            : statusLabel(data.profileStatus));
        var profileFields = [
            detailField("用户名", profile.username),
            detailField("简介", profile.bio),
            detailField("位置", profile.location),
            detailField("创建时间", profile.createdAt)
        ].filter(Boolean).join("");
        var rawFields = raw.available === true
            ? [
                detailField("粉丝数（账号资料）", raw.followersCount, formatNumber),
                detailField("关注数（账号资料）", raw.followingCount, formatNumber),
                detailField("推文总数（账号资料）", raw.declaredTweetCount, formatNumber)
            ].filter(Boolean).join("")
            : "";
        var metricLabels = {
            like_count: "点赞",
            reply_count: "评论",
            repost_count: "转发"
        };
        var tweetSampleCount = Number(tweets.sampleCount) || 0;
        var interactionHtml = Object.keys(metricLabels).map(function (key) {
            var metric = metrics[key] || {};
            var validSamples = Number(metric.validSampleCount) || 0;
            var evidence = validSamples > 0
                ? "<p>总量：" + escapeHtml(formatNumber(metric.total)) +
                    " · 平均：" + escapeHtml(formatDecimal(metric.average)) +
                    " · 中位数：" + escapeHtml(formatDecimal(metric.median)) +
                    "</p><small>有效样本 " +
                    escapeHtml(formatNumber(validSamples)) + "</small>"
                : "<p>无有效样本</p><small>未显示服务端占位均值。</small>";
            return '<div class="social-network-detail-interaction-metric"><h4>' +
                escapeHtml(metricLabels[key]) + "</h4>" + evidence + "</div>";
        }).join("");
        var sourceHtml = sourceEvidence &&
            detailHasValue(sourceEvidence.metric) &&
            detailHasValue(sourceEvidence.rank)
            ? '<section class="social-network-detail-evidence">' +
                "<h3>本次排行依据</h3><p>" +
                escapeHtml(sourceEvidence.metricLabel || sourceEvidence.metric) +
                " · 服务端名次 " + escapeHtml(formatNumber(sourceEvidence.rank)) +
                " · 当前值 " + escapeHtml(formatDecimal(sourceEvidence.value)) +
                "</p>" + (sourceEvidence.scope
                    ? "<small>" + escapeHtml(sourceEvidence.scope) + "</small>"
                    : "") + "</section>"
            : "";
        var neighbors = (data.neighbors || {}).items || [];
        var neighborHtml = neighbors.length
            ? '<p>本次返回 ' + escapeHtml(formatNumber(neighbors.length)) +
                " 条关系项（最多 50 条）；关系项不等同于去重后的邻居总数。</p>" +
                '<ul class="social-network-user-neighbor-list">' +
                neighbors.map(function (neighbor) {
                    var outgoing = neighbor.direction === "outgoing";
                    var direction = outgoing
                        ? userId + " → " + neighbor.nodeId
                        : neighbor.nodeId + " → " + userId;
                    return "<li>" + escapeHtml(direction) +
                        (neighbor.external ? " · 外部节点" : "") + "</li>";
                }).join("") + "</ul>"
            : '<p class="social-network-empty">本次没有可展示的关系项。</p>';
        var profilePanel = document.getElementById(
            "social-network-user-panel-profile");
        var tweetsPanel = document.getElementById(
            "social-network-user-panel-tweets");
        var neighborsPanel = document.getElementById(
            "social-network-user-panel-neighbors");

        setText("social-network-user-drawer-title", displayName);
        setText("social-network-user-drawer-identity",
            "ID：" + userId + " · 资料状态：" + profileStatus);
        setText("social-network-user-drawer-context", userDetailDatasetLabel());
        setText("social-network-user-drawer-status", data.scope || "用户详情");
        profilePanel.innerHTML =
            sourceHtml +
            '<section class="social-network-detail-identity"><h3>个人资料</h3>' +
            (profileFields || '<p class="social-network-empty">' +
                (external
                    ? "当前节点没有可用的个人资料字段。"
                    : "当前资料未提供可展示的个人字段。") +
                "</p>") + "</section>" +
            '<section class="social-network-detail-grid">' +
            '<div><h3>原始账号资料</h3>' +
            (rawFields || '<p class="social-network-empty">' +
                "原始账号字段未提供。</p>") + "</div>" +
            '<div><h3>计算网络位置</h3>' +
            detailField("数据内被关注数（入度）", network.inDegree, formatNumber) +
            detailField("数据内关注数（出度）", network.outDegree, formatNumber) +
            detailField("PageRank（结构位置）", network.pageRank, formatDecimal) +
            "</div></section>" +
            '<p class="social-network-note">不提供按主题过滤的作者摘要；原始账号资料与数据集中的计算网络位置分别呈现。</p>' +
            '<section class="social-network-detail-evidence">' +
            "<h3>局部关系</h3><p>使用当前数据，以此节点 ID 查询真实关注关系。</p>" +
            '<button class="button button-secondary" type="button" ' +
            'data-user-detail-open-relations>查看用户关系</button></section>';
        profilePanel.querySelector("[data-user-detail-open-relations]")
            .addEventListener("click", function (event) {
                openUserRelationsAtCenter(userId, event.currentTarget);
            });

        tweetsPanel.innerHTML =
            '<section class="social-network-detail-interactions"><h3>已关联推文与互动</h3>' +
            "<p>" + (tweetSampleCount > 0
                ? "当前采集且成功关联的推文样本：" +
                    escapeHtml(formatNumber(tweetSampleCount))
                : "当前没有已关联的推文样本。") + "</p>" +
            (tweetSampleCount > 0
                ? "<div>" + interactionHtml + "</div>"
                : '<p class="social-network-empty">' +
                    "无可用于计算互动摘要的推文样本。</p>") + "</section>";
        neighborsPanel.innerHTML =
            '<section class="social-network-detail-neighbors"><h3>关注邻居</h3>' +
            neighborHtml + "</section>";
    }

    function closeSocialUserDrawer(restoreFocus) {
        var drawer = document.getElementById("social-network-user-drawer");
        if (!drawer) {
            return;
        }
        socialUserDrawerSequence += 1;
        workspaceState.invalidateRequest("userDetail");
        drawer.classList.remove("is-open");
        drawer.setAttribute("aria-hidden", "true");
        if (restoreFocus !== false && socialUserDrawerOpener &&
                socialUserDrawerOpener.isConnected &&
                !socialUserDrawerOpener.closest("[hidden]")) {
            socialUserDrawerOpener.focus();
        }
        socialUserDrawerOpener = null;
        socialUserDrawerEvidence = null;
        socialUserDrawerUserId = "";
    }

    document.querySelectorAll("[data-close-social-user-drawer]").forEach(function (element) {
        element.addEventListener("click", function () {
            closeSocialUserDrawer(true);
        });
    });
    document.addEventListener("keydown", function (event) {
        var drawer = document.getElementById("social-network-user-drawer");
        if (!drawer || !drawer.classList.contains("is-open")) {
            return;
        }
        if (event.key === "Escape") {
            event.preventDefault();
            closeSocialUserDrawer(true);
            return;
        }
        if (event.key !== "Tab") {
            return;
        }
        var panel = drawer.querySelector(".social-network-user-drawer-panel");
        var focusable = Array.prototype.slice.call(panel.querySelectorAll(
            'button:not([disabled]), a[href], input:not([disabled]), ' +
            'select:not([disabled]), textarea:not([disabled]), ' +
            '[tabindex]:not([tabindex="-1"])'
        )).filter(function (element) {
            return element.tabIndex >= 0 && !element.closest("[hidden]");
        });
        if (!focusable.length) {
            event.preventDefault();
            return;
        }
        var currentIndex = focusable.indexOf(document.activeElement);
        var nextIndex;
        if (currentIndex < 0) {
            nextIndex = event.shiftKey ? focusable.length - 1 : 0;
        } else {
            nextIndex = (currentIndex +
                (event.shiftKey ? focusable.length - 1 : 1)) % focusable.length;
        }
        event.preventDefault();
        focusable[nextIndex].focus();
        window.setTimeout(function () {
            if (drawer.classList.contains("is-open") &&
                    !panel.contains(document.activeElement)) {
                focusable[nextIndex].focus();
            }
        }, 0);
    });

    async function loadUserDetail(userId, sourceEvidence) {
        var requestParameters = { userId: userId };
        var requestToken = beginTaskRequest("userDetail", requestParameters);
        var result = await request(buildDatasetUrl(
            "users/" + encodeURIComponent(userId)));
        requireCurrentTaskRequest(requestToken, requestParameters);
        var drawer = document.getElementById("social-network-user-drawer");
        if (!drawer || !drawer.classList.contains("is-open") ||
                socialUserDrawerUserId !== String(userId)) {
            throw staleTaskRequestError();
        }
        renderUserDetail(result, sourceEvidence);
    }

    window.openSocialNetworkUserDetail = function (userId, sourceEvidence, opener) {
        if (!detailHasValue(userId)) {
            return;
        }
        var drawer = document.getElementById("social-network-user-drawer");
        var isAlreadyOpen = drawer.classList.contains("is-open");
        if (!isAlreadyOpen) {
            socialUserDrawerOpener = opener || document.activeElement;
        }
        socialUserDrawerEvidence = sourceEvidence || null;
        socialUserDrawerUserId = String(userId);
        var sequence = ++socialUserDrawerSequence;
        drawer.classList.add("is-open");
        drawer.setAttribute("aria-hidden", "false");
        setText("social-network-user-drawer-title", "用户详情");
        setText("social-network-user-drawer-identity",
            "ID：" + socialUserDrawerUserId + " · 正在读取资料状态");
        setText("social-network-user-drawer-context", userDetailDatasetLabel());
        setText("social-network-user-drawer-status", "正在加载用户详情…");
        document.querySelectorAll("[data-user-detail-panel]").forEach(function (panel) {
            panel.innerHTML = '<div class="social-network-empty">正在加载用户证据…</div>';
        });
        activateUserDetailCategory("profile", false);
        if (!isAlreadyOpen) {
            var closeButton = drawer.querySelector(".social-network-user-drawer-close");
            if (closeButton) {
                closeButton.focus();
            }
        }
        loadUserDetail(socialUserDrawerUserId, socialUserDrawerEvidence)
            .catch(function (error) {
                if (sequence !== socialUserDrawerSequence ||
                        isStaleDatasetRequest(error)) {
                    return;
                }
                setText("social-network-user-drawer-status", "用户详情暂不可用");
                var profilePanel = document.getElementById(
                    "social-network-user-panel-profile");
                profilePanel.innerHTML =
                    '<div class="social-network-empty is-error">' +
                    "用户详情暂时无法加载。</div>" +
                    '<button type="button" class="button button-secondary" ' +
                    'data-user-detail-retry>重试</button>';
                profilePanel.querySelector("[data-user-detail-retry]")
                    .addEventListener("click", function (event) {
                        window.openSocialNetworkUserDetail(
                            socialUserDrawerUserId,
                            socialUserDrawerEvidence,
                            event.currentTarget);
                    });
            });
    };

    var interactionLabels = {
        like_count: "点赞",
        reply_count: "评论",
        repost_count: "转发"
    };

    var rankingMetricLabels = {
        indegree: "数据内被关注数（入度）",
        outdegree: "数据内关注数（出度）",
        pagerank: "网络结构重要度（PageRank）",
        followers_count: "粉丝数（账号资料）",
        following_count: "关注数（账号资料）",
        tweet_count: "推文总数（账号资料）"
    };

    var rankingMetricNotes = {
        indegree: "统计当前规范化关系数据中指向该用户的唯一有效关注关系数量。",
        outdegree: "统计当前规范化关系数据中该用户指向其他节点的唯一有效关注关系数量。",
        pagerank: "根据当前有向关系网络计算节点的相对结构位置；收敛状态和迭代轮数见网络总览。",
        followers_count: "来自账号原始资料，不等于当前关系数据中已记录的被关注关系数量，也不表示增长。",
        following_count: "来自账号原始资料，不替代当前关系数据计算出的关注关系数量。",
        tweet_count: "来自账号原始资料，不等于本数据集中已采集并纳入分析的推文数。"
    };

    function interactionUrl(resource, query) {
        return buildDatasetUrl("interactions/" + resource, query);
    }

    function renderInteractionSummary(body) {
        var data = body.data || {};
        var scale = data.scale || {};
        var container = document.getElementById("social-network-interaction-summary");
        if (data.state === "empty") {
            container.innerHTML = '<div class="social-network-empty">当前没有可用互动摘要。</div>';
            return;
        }
        var metric = workspaceState.getCondition("interaction").metric;
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
                window.openSocialNetworkUserDetail(
                    button.getAttribute("data-social-user-id"),
                    null,
                    button);
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
        var conditions = workspaceState.getCondition("interaction");
        var metric = conditions.metric;
        var query = { metric: metric, limit: 10 };
        var requestParameters = { metric: metric, category: conditions.category };
        var requestToken = beginTaskRequest("interaction", requestParameters);
        var results = await Promise.allSettled([
            request(interactionUrl("summary")),
            request(interactionUrl("tweets", query)),
            request(interactionUrl("users", query)),
            request(interactionUrl("timeseries", { metric: metric }))
        ]);
        requireCurrentTaskRequest(requestToken, requestParameters);
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

    function interactionTaskMount(taskCard) {
        var mount = document.getElementById("social-network-interaction-task");
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-interaction-task";
            mount.className = "social-network-interaction-task";
            taskCard.appendChild(mount);
        }
        return mount;
    }

    function ensureInteractionTaskView(taskCard) {
        var mount = interactionTaskMount(taskCard);
        if (mount.querySelector("[data-interaction-metric]")) {
            return mount;
        }
        mount.innerHTML =
            '<header class="social-network-interaction-task-header">' +
            '<div><p class="social-network-user-relations-eyebrow">' +
            "INTERACTION RESULTS</p><h2>互动统计结果</h2>" +
            "<p>点赞、评论与转发分别统计，不合成为综合互动分。</p></div>" +
            '<button class="button button-secondary" type="button" ' +
            'data-interaction-task-export>导出互动分析结果</button></header>' +
            '<div class="social-network-interaction-task-controls">' +
            '<div class="social-network-interaction-metric-buttons" role="group" ' +
            'aria-label="选择互动指标">' +
            Object.keys(interactionLabels).map(function (metric) {
                return '<button class="button button-secondary" type="button" ' +
                    'data-interaction-metric="' + escapeHtml(metric) + '">' +
                    escapeHtml(interactionLabels[metric]) + '</button>';
            }).join("") + "</div>" +
            '<button class="button button-primary" type="button" ' +
            'data-interaction-task-refresh>刷新当前结果</button></div>' +
            '<section class="social-network-interaction-task-summary" ' +
            'aria-label="当前指标互动摘要">' +
            '<div class="social-network-interaction-summary" ' +
            'data-interaction-task-summary></div>' +
            '<p class="social-network-interaction-task-scope" ' +
            'data-interaction-task-scope></p></section>' +
            '<div class="social-network-interaction-category-nav" role="group" ' +
            'aria-label="互动结果类别">' +
            '<button type="button" data-interaction-category="tweets" ' +
            'aria-pressed="true">推文排行</button>' +
            '<button type="button" data-interaction-category="users" ' +
            'aria-pressed="false">用户排行</button>' +
            '<button type="button" data-interaction-category="timeseries" ' +
            'aria-pressed="false">时间分布</button></div>' +
            '<section class="social-network-interaction-task-results" ' +
            'aria-labelledby="social-network-interaction-task-results-title">' +
            '<div class="social-network-interaction-task-results-head">' +
            '<div><h3 id="social-network-interaction-task-results-title" ' +
            'data-interaction-task-results-title>推文排行</h3>' +
            '<p data-interaction-task-count>等待读取当前指标</p></div>' +
            '<span class="social-network-version" ' +
            'data-interaction-task-status role="status" aria-live="polite">待加载</span>' +
            '</div>' +
            '<div class="social-network-interaction-table ' +
            'social-network-interaction-task-table" data-interaction-task-results ' +
            'role="region" aria-label="当前指标推文 Top 10" tabindex="0"></div>' +
            '<p class="social-network-interaction-task-note" data-interaction-task-note>' +
            '序号仅表示接口返回顺序，不是服务端名次；此排行接口未提供推文原文，' +
            '因此不提供“查看原文”或原平台跳转入口。</p></section>';

        mount.querySelectorAll("[data-interaction-metric]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    var metric = button.getAttribute("data-interaction-metric");
                    workspaceState.setCondition("interaction", { metric: metric });
                    loadInteractionTask(taskCard, false);
                });
            });
        mount.querySelectorAll("[data-interaction-category]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    workspaceState.setCondition("interaction", {
                        category: button.getAttribute("data-interaction-category")
                    });
                    loadInteractionTask(taskCard, false);
                });
            });
        mount.querySelector("[data-interaction-task-refresh]")
            .addEventListener("click", function () {
                loadInteractionTask(taskCard, true);
            });
        mount.querySelector("[data-interaction-task-export]")
            .addEventListener("click", function (event) {
                openModuleExportDialog("interactions", event.currentTarget);
            });
        return mount;
    }

    function renderInteractionTaskSummary(body, metric) {
        var data = body.data || {};
        var scale = data.scale || {};
        var summary = (scale.metrics || data.metrics || {})[metric] || {};
        var validCount = Number(summary.validSampleCount);
        var hasValidCount = Number.isFinite(validCount) && validCount >= 0;
        var hasSamples = hasValidCount && validCount > 0;
        var container = document.querySelector(
            "[data-interaction-task-summary]"
        );
        var label = interactionLabels[metric] || metric;
        var total = hasSamples ? formatNumber(summary.total) : "不可计算";
        var average = hasSamples
            ? formatDecimal(summary.average)
            : hasValidCount ? "不可计算" : "未提供";
        var median = hasSamples
            ? formatDecimal(summary.median)
            : hasValidCount ? "不可计算" : "未提供";
        container.innerHTML =
            '<article class="social-network-interaction-kpi">' +
            '<span>当前指标</span><strong>' + escapeHtml(label) +
            '</strong><small>独立统计，不生成综合分</small></article>' +
            '<article class="social-network-interaction-kpi">' +
            '<span>互动总量</span><strong>' + total +
            '</strong><small>有效样本 ' +
            (hasValidCount ? formatNumber(validCount) : "未提供") +
            '</small></article>' +
            '<article class="social-network-interaction-kpi">' +
            '<span>平均值</span><strong>' + average +
            '</strong><small>中位数 ' + median + '</small></article>' +
            '<article class="social-network-interaction-kpi">' +
            '<span>纳入分析推文</span><strong>' +
            formatNumber(scale.analysisSampleCount) +
            '</strong><small>缺失 ' + formatNumber(summary.missingCount) +
            ' · 有效零值 ' + formatNumber(summary.zeroCount) +
            '</small></article>';
        document.querySelector("[data-interaction-task-scope]").textContent =
            (data.scope || "去重且纳入分析的推文样本") +
            "。平均值按当前指标有效样本数计算；未关联作者仍计入全局互动摘要。";
    }

    function renderInteractionTaskTweets(body, metric) {
        var data = body.data || {};
        var items = (data.items || []).slice(0, 10);
        var container = document.querySelector(
            "[data-interaction-task-results]"
        );
        var count = document.querySelector("[data-interaction-task-count]");
        if (data.state === "empty" || !items.length) {
            count.textContent = "本次显示 0 条 / 当前指标有效推文 " +
                formatNumber(data.totalCount) + " 条";
            container.innerHTML =
                '<div class="social-network-empty">当前指标没有有效推文样本；' +
                "这不代表其他互动指标也没有样本。</div>";
            return;
        }

        count.textContent = "本次显示 " + formatNumber(items.length) +
            " 条 / 当前指标有效推文 " + formatNumber(data.totalCount) + " 条" +
            (data.truncated === true || Number(data.totalCount) > items.length
                ? " · 当前为 Top 10，列表已截断"
                : "");
        container.innerHTML =
            '<table><thead><tr><th scope="col">序号</th>' +
            '<th scope="col">推文 ID</th><th scope="col">作者及关联状态</th>' +
            '<th scope="col">' + escapeHtml(interactionLabels[metric] || metric) +
            '</th><th scope="col">发布时间字段</th></tr></thead><tbody>' +
            items.map(function (item, index) {
                var authorStatus = item.authorStatus || "";
                var authorId = item.authorUserId || "";
                var linked = authorStatus === "linked" && Boolean(authorId);
                var authorText = authorId || "未归属来源";
                var statusText = authorStatus === "linked"
                    ? "已关联"
                    : authorStatus === "unlinked_author"
                        ? "未关联用户"
                        : authorStatus === "unassigned_source"
                            ? "未归属来源"
                            : "关联状态未提供";
                var authorHtml = linked
                    ? '<button class="social-network-inline-link" type="button" ' +
                        'data-interaction-author="' +
                        escapeHtml(authorId) + '">' +
                        escapeHtml(authorText) + '</button>'
                    : '<span>' + escapeHtml(authorText) + '</span>';
                return "<tr><td>" + formatNumber(index + 1) +
                    '</td><td class="social-network-interaction-tweet-id">' +
                    escapeHtml(item.tweetId || "未提供") +
                    "</td><td>" + authorHtml + '<small class="' +
                    (linked ? "is-linked" : "is-unlinked") + '">' +
                    escapeHtml(statusText) + "</small></td><td><strong>" +
                    formatNumber(item.value) + "</strong></td><td>" +
                    escapeHtml(item.createdAt || "未提供") + "</td></tr>";
            }).join("") + "</tbody></table>";
        container.querySelectorAll("[data-interaction-author]")
            .forEach(function (button) {
                button.addEventListener("click", function (event) {
                    window.openSocialNetworkUserDetail(
                        event.currentTarget.getAttribute(
                            "data-interaction-author"
                        ),
                        null,
                        event.currentTarget
                    );
                });
            });
    }

    function renderInteractionTaskUsers(body, metric) {
        var data = body.data || {};
        var items = (data.items || []).slice(0, 10);
        var container = document.querySelector(
            "[data-interaction-task-results]"
        );
        var count = document.querySelector("[data-interaction-task-count]");
        var label = interactionLabels[metric] || metric;
        if (data.state === "empty" || !items.length) {
            count.textContent = "本次显示 0 位 / 当前指标有效样本用户 " +
                formatNumber(data.totalCount) + " 位";
            container.innerHTML =
                '<div class="social-network-empty">当前指标没有可用的已关联用户样本；' +
                "这不代表其他互动指标也没有样本。</div>";
            return;
        }

        count.textContent = "本次显示 " + formatNumber(items.length) +
            " 位 / 当前指标有效样本用户 " + formatNumber(data.totalCount) + " 位" +
            (data.truncated === true || Number(data.totalCount) > items.length
                ? " · 当前为 Top 10，列表已截断"
                : "");
        container.innerHTML =
            '<table><thead><tr><th scope="col">用户 ID</th>' +
            '<th scope="col">' + escapeHtml(label) + '总量</th>' +
            '<th scope="col">平均</th><th scope="col">有效推文样本数</th>' +
            '<th scope="col">操作</th></tr></thead><tbody>' +
            items.map(function (item) {
                var userId = item.userId == null ? "" : String(item.userId);
                return "<tr><td>" + escapeHtml(userId || "未提供") +
                    "</td><td><strong>" + formatNumber(item.total) +
                    "</strong></td><td><strong>" + formatDecimal(item.average) +
                    '</strong><details class="social-network-interaction-user-median">' +
                    "<summary>中位数</summary><span>" +
                    formatDecimal(item.median) + "</span></details></td><td>" +
                    formatNumber(item.sampleCount) + "</td><td>" +
                    (userId
                        ? '<button class="button button-secondary ' +
                            'social-network-interaction-user-detail" type="button" ' +
                            'data-interaction-user-detail="' +
                            escapeHtml(userId) + '">查看详情</button>'
                        : "未提供用户 ID") +
                    "</td></tr>";
            }).join("") + "</tbody></table>";
        container.querySelectorAll("[data-interaction-user-detail]")
            .forEach(function (button) {
                button.addEventListener("click", function (event) {
                    window.openSocialNetworkUserDetail(
                        event.currentTarget.getAttribute(
                            "data-interaction-user-detail"
                        ),
                        null,
                        event.currentTarget
                    );
                });
            });
    }

    function renderInteractionTaskTimeseries(body) {
        var data = body.data || {};
        var items = Array.isArray(data.items) ? data.items : [];
        var container = document.querySelector(
            "[data-interaction-task-results]"
        );
        var count = document.querySelector("[data-interaction-task-count]");
        var totalGroups = Number(data.totalCount);
        if (!Number.isFinite(totalGroups) || totalGroups < 0) {
            totalGroups = items.length;
        }
        if (data.state === "empty" || !items.length) {
            count.textContent = "本次显示 0 组 / 返回分组 " +
                formatNumber(totalGroups) + " 组";
            container.innerHTML =
                '<div class="social-network-empty">当前指标没有可用的时间分组；' +
                "这不代表其他互动指标也没有样本。</div>";
            return;
        }

        var sorted = items.slice().sort(function (left, right) {
            var leftPeriod = left.period == null ? "" : String(left.period);
            var rightPeriod = right.period == null ? "" : String(right.period);
            return leftPeriod < rightPeriod ? -1 :
                leftPeriod > rightPeriod ? 1 : 0;
        });
        var visible = sorted.slice(-30);
        var values = visible.map(function (item) {
            var value = Number(item.total);
            return Number.isFinite(value) ? Math.max(0, value) : 0;
        });
        var max = Math.max.apply(null, values);
        count.textContent = "本次显示 " + formatNumber(visible.length) +
            " 组 / 返回分组 " + formatNumber(totalGroups) +
            " 组 · 字符串排序末尾最多 30 组" +
            " · 缺失发布时间字段 " +
            formatNumber(data.missingDateCount) +
            " 条（不代表当前指标有效样本的时间覆盖率）";
        container.innerHTML =
            '<div class="social-network-interaction-timeseries" role="list">' +
            visible.map(function (item, index) {
                var period = item.period == null ? "" : String(item.period);
                var total = values[index];
                var width = max > 0 ? total / max * 100 : 0;
                return '<div class="social-network-interaction-timeseries-row" ' +
                    'role="listitem"><span class="social-network-timeseries-period">' +
                    escapeHtml(period) +
                    '</span><div class="social-network-timeseries-track" ' +
                    'role="img" aria-label="' + escapeHtml(period) +
                    "，总量 " + escapeHtml(formatNumber(total)) +
                    '"><i style="width:' + width.toFixed(2) +
                    '%"></i></div><strong>' + formatNumber(total) +
                    '</strong><span class="social-network-timeseries-samples">' +
                    formatNumber(item.sampleCount) + " 个样本</span></div>";
            }).join("") + "</div>";
    }

    function updateInteractionTaskCategoryView(mount, category) {
        var isUsers = category === "users";
        var isTimeseries = category === "timeseries";
        mount.querySelectorAll("[data-interaction-category]")
            .forEach(function (button) {
                var selected = button.getAttribute("data-interaction-category") ===
                    category;
                button.setAttribute("aria-pressed", String(selected));
                button.classList.toggle("is-active", selected);
            });
        mount.querySelector("[data-interaction-task-results-title]").textContent =
            isUsers ? "用户排行" : isTimeseries
                ? "按发布时间字段分组的互动统计"
                : "推文排行";
        mount.querySelector("[data-interaction-task-results]")
            .setAttribute("aria-label", isUsers
                ? "当前指标用户 Top 10"
                : isTimeseries
                    ? "当前指标按发布时间字段分组的互动统计"
                    : "当前指标推文 Top 10");
        mount.querySelector("[data-interaction-task-note]").textContent =
            isUsers
                ? "仅聚合纳入分析、作者已关联且当前指标值有效的样本；用户总量不等于包含未归属样本的全局互动总量。样本数仅表示该用户当前指标的有效推文数，详情显示全数据集用户资料。"
                : isTimeseries
                    ? "按 created_at 原始字符串分组并排序，展示排序末尾最多 30 组；不是按日期解析的最近 30 天或月度趋势。缺失发布时间字段计数不代表当前指标有效样本的时间覆盖率。"
                    : "序号仅表示接口返回顺序，不是服务端名次；此排行接口未提供推文原文，因此不提供“查看原文”或原平台跳转入口。";
    }

    function updateInteractionTaskMetricButtons(mount, metric) {
        mount.querySelectorAll("[data-interaction-metric]")
            .forEach(function (button) {
                var selected = button.getAttribute("data-interaction-metric") ===
                    metric;
                button.setAttribute("aria-pressed", String(selected));
                button.classList.toggle("is-active", selected);
            });
    }

    function renderInteractionTaskLoading(mount, metric, category, cached) {
        updateInteractionTaskMetricButtons(mount, metric);
        updateInteractionTaskCategoryView(mount, category);
        if (cached && cached.summary) {
            renderInteractionTaskSummary(cached.summary, metric);
        } else {
            mount.querySelector("[data-interaction-task-summary]").innerHTML =
                '<div class="social-network-empty" role="status">' +
                "正在读取当前指标摘要…</div>";
            mount.querySelector("[data-interaction-task-scope]").textContent = "";
        }
        var categoryLabel = category === "users" ? "用户" :
            category === "timeseries" ? "时间分布" : "推文";
        mount.querySelector("[data-interaction-task-count]").textContent =
            category === "timeseries"
                ? "正在读取时间分布…"
                : "正在读取" + categoryLabel + " Top 10…";
        mount.querySelector("[data-interaction-task-results]").innerHTML =
            '<div class="social-network-empty" role="status">' +
            (category === "timeseries"
                ? "正在读取当前指标的时间分组…</div>"
                : "正在读取当前指标的" + categoryLabel + "排行…</div>");
        mount.querySelector("[data-interaction-task-status]").textContent =
            (interactionLabels[metric] || metric) + " · 加载中";
        setText("social-network-task-status",
            "正在读取" + (interactionLabels[metric] || metric) +
            (category === "timeseries"
                ? "互动摘要与时间分布"
                : "互动摘要与" + categoryLabel + " Top 10"));
        setText("social-network-task-message",
            category === "users"
                ? "结果只统计作者已关联且当前指标有效的样本；详情仍使用全数据集上下文。"
                : category === "timeseries"
                    ? "时间分组按 created_at 原始字符串排序，不进行日期解析或月度重采样。"
                    : "结果按当前数据身份和指标加载；序号表示展示顺序，不是服务端名次。");
    }

    function renderInteractionTaskCachedResult(
        mount,
        cached,
        metric,
        category
    ) {
        updateInteractionTaskMetricButtons(mount, metric);
        updateInteractionTaskCategoryView(mount, category);
        if (cached.summary) {
            renderInteractionTaskSummary(cached.summary, metric);
        } else {
            mount.querySelector("[data-interaction-task-summary]").innerHTML =
                '<div class="social-network-empty is-error" role="alert">' +
                "互动摘要暂时不可用；当前排行仍可独立查看。</div>";
        }
        var categoryResult = cached[category];
        if (categoryResult) {
            if (category === "users") {
                renderInteractionTaskUsers(categoryResult, metric);
            } else if (category === "timeseries") {
                renderInteractionTaskTimeseries(categoryResult);
            } else {
                renderInteractionTaskTweets(categoryResult, metric);
            }
        } else {
            var categoryLabel = category === "users" ? "用户排行" :
                category === "timeseries" ? "时间分布" : "推文排行";
            mount.querySelector("[data-interaction-task-count]").textContent =
                categoryLabel + "暂不可用";
            mount.querySelector("[data-interaction-task-results]").innerHTML =
                '<div class="social-network-empty is-error" role="alert">' +
                categoryLabel + "暂时不可用；可刷新当前结果重试。</div>";
        }
        var available = Boolean(cached.summary || categoryResult);
        var partial = !cached.summary || !categoryResult;
        var categoryLabel = category === "users" ? "用户" :
            category === "timeseries" ? "时间分布" : "推文";
        mount.querySelector("[data-interaction-task-status]").textContent =
            available
                ? (interactionLabels[metric] || metric) +
                    (partial ? " · 部分可用" : " · 已加载")
                : "互动分析暂时不可用";
        setText("social-network-task-status", available
            ? (interactionLabels[metric] || metric) +
                (category === "timeseries"
                    ? "互动摘要与时间分布"
                    : "互动摘要与" + categoryLabel + " Top 10")
            : "互动分析暂时不可用");
        setText("social-network-task-message",
            category === "users"
                ? "用户排行仅聚合作者已关联且当前指标有效的样本。"
                : category === "timeseries"
                    ? "分组依据是 created_at 原始字符串；空时间字段计数不代表指标时间覆盖率。"
                    : "当前展示互动摘要与推文排行。");
    }

    function getInteractionTaskMetricCache(contextKey, metric) {
        if (!interactionTaskResultCache ||
                interactionTaskResultCache.contextKey !== contextKey) {
            interactionTaskResultCache = {
                contextKey: contextKey,
                metrics: {}
            };
        }
        if (!interactionTaskResultCache.metrics[metric]) {
            interactionTaskResultCache.metrics[metric] = {
                summary: null,
                tweets: null,
                users: null,
                timeseries: null
            };
        }
        return interactionTaskResultCache.metrics[metric];
    }

    async function loadInteractionTask(taskCard, forceRefresh) {
        var card = taskCard || document.querySelector(
            "#page-social-network-task .social-network-task-card"
        );
        if (!card || activeSocialNetworkTask !== "interaction") {
            return;
        }
        var mount = ensureInteractionTaskView(card);
        if (interactionTaskIdentityPending) {
            if (forceRefresh) {
                interactionTaskResultCache = null;
            }
            var pendingConditions = workspaceState.getCondition("interaction");
            renderInteractionTaskLoading(
                mount,
                pendingConditions.metric,
                pendingConditions.category,
                null
            );
            return;
        }
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        var conditions = workspaceState.getCondition("interaction");
        var metric = conditions.metric;
        var category = conditions.category === "users" ||
            conditions.category === "timeseries"
            ? conditions.category : "tweets";
        var requestParameters = {
            metric: metric,
            category: category,
            datasetContext: context
        };
        var requestToken = beginTaskRequest("interaction", requestParameters);
        var metricCache = getInteractionTaskMetricCache(contextKey, metric);
        if (forceRefresh) {
            metricCache.summary = null;
            metricCache.tweets = null;
            metricCache.users = null;
            metricCache.timeseries = null;
        }
        if (!forceRefresh && metricCache.summary && metricCache[category]) {
            renderInteractionTaskCachedResult(
                mount,
                metricCache,
                metric,
                category
            );
            return;
        }

        renderInteractionTaskLoading(mount, metric, category, metricCache);
        var query = { metric: metric, limit: 10 };
        var categoryEndpoint = category === "timeseries"
            ? "timeseries" : category;
        var categoryQuery = category === "timeseries"
            ? { metric: metric } : query;
        var pendingRequests = [];
        if (!metricCache.summary) {
            pendingRequests.push({
                key: "summary",
                promise: request(interactionUrl("summary", null))
            });
        }
        if (!metricCache[category]) {
            pendingRequests.push({
                key: category,
                promise: request(interactionUrl(categoryEndpoint, categoryQuery))
            });
        }
        var results = await Promise.allSettled(
            pendingRequests.map(function (item) {
                return item.promise;
            })
        );
        try {
            requireCurrentTaskRequest(requestToken, requestParameters);
            if (activeSocialNetworkTask !== "interaction" ||
                    contextKey !== datasetContextKey(readDatasetContext()) ||
                    workspaceState.getCondition("interaction").metric !== metric ||
                    workspaceState.getCondition("interaction").category !==
                        category) {
                return;
            }
        } catch (error) {
            if (isStaleDatasetRequest(error)) {
                return;
            }
            throw error;
        }

        pendingRequests.forEach(function (item, index) {
            metricCache[item.key] = results[index].status === "fulfilled"
                ? results[index].value
                : null;
        });
        renderInteractionTaskCachedResult(
            mount,
            metricCache,
            metric,
            category
        );
    }

    function startInteractionTaskPage(taskCard) {
        var mount = ensureInteractionTaskView(taskCard);
        var conditions = workspaceState.getCondition("interaction");
        var sequence = ++interactionTaskIdentitySequence;
        interactionTaskIdentityPending = true;
        renderInteractionTaskLoading(
            mount,
            conditions.metric,
            conditions.category,
            null
        );
        loadShellDatasetVersion().finally(function () {
            if (sequence !== interactionTaskIdentitySequence) {
                return;
            }
            interactionTaskIdentityPending = false;
            if (activeSocialNetworkTask === "interaction") {
                loadInteractionTask(taskCard, false);
            }
        });
    }

    function topicTaskMount(taskCard) {
        var mount = document.getElementById("social-network-topic-task");
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-topic-task";
            mount.className = "social-network-topic-task";
            taskCard.appendChild(mount);
        }
        return mount;
    }

    function ensureTopicTaskView(taskCard) {
        var mount = topicTaskMount(taskCard);
        if (mount.querySelector("[data-topic-query-form]")) {
            return mount;
        }
        mount.innerHTML =
            '<header class="social-network-topic-task-header">' +
            '<div><p class="social-network-user-relations-eyebrow">' +
            "TOPIC SEARCH RESULTS</p><h2>主题查询与相关推文</h2>" +
            "<p>按一个精确词元查找匹配推文；不进行语义检索或相关度排序。</p></div>" +
            '<button class="button button-secondary" type="button" ' +
            'data-topic-task-export>导出主题结果</button></header>' +
            '<form class="social-network-topic-task-form" data-topic-query-form>' +
            '<label for="social-network-topic-task-query">查询词元</label>' +
            '<div class="social-network-topic-task-query-row">' +
            '<input id="social-network-topic-task-query" type="search" ' +
            'autocomplete="off" placeholder="输入一个词元">' +
            '<button class="button button-primary" type="submit" ' +
            'data-topic-submit>查询主题</button></div>' +
            '<div class="social-network-topic-task-query-help">' +
            '<span>精确词元匹配 · 英文大小写不敏感 · 非语义检索</span>' +
            '<button class="social-network-inline-link" type="button" ' +
            'data-topic-rules>查看匹配规则</button></div>' +
            '<p class="social-network-topic-task-draft" data-topic-draft-status ' +
            'role="status" aria-live="polite">提交非空词元后显示结果。</p>' +
            '<p class="social-network-topic-task-error" data-topic-query-error ' +
            'role="alert" hidden></p></form>' +
            '<section class="social-network-topic-task-results" ' +
            'data-topic-results hidden aria-labelledby="social-network-topic-results-title">' +
            '<header class="social-network-topic-task-results-head">' +
            '<div><h3 id="social-network-topic-results-title" ' +
            'data-topic-results-title>相关推文</h3>' +
            '<p data-topic-result-count></p></div>' +
            '<span class="social-network-version" data-topic-result-status ' +
            'role="status" aria-live="polite"></span></header>' +
            '<div class="social-network-topic-task-categories" role="group" ' +
            'aria-label="主题结果类别">' +
            '<button type="button" data-topic-category="tweets" ' +
            'aria-pressed="true">相关推文</button>' +
            '<button type="button" data-topic-category="users" ' +
            'aria-pressed="false">相关用户</button>' +
            '<button type="button" data-topic-category="following" ' +
            'aria-pressed="false">关注关系</button></div>' +
            '<div class="social-network-topic-task-table" data-topic-result-table ' +
            'role="region" aria-label="主题相关推文" tabindex="0"></div>' +
            '<p class="social-network-topic-task-note" data-topic-result-note></p>' +
            '</section>' +
            '<div class="social-network-topic-task-idle" data-topic-idle>' +
            '<strong>还没有查询结果</strong><span>输入词元并点击“查询主题”，匹配推文会显示在这里。</span></div>';

        var input = mount.querySelector("#social-network-topic-task-query");
        var form = mount.querySelector("[data-topic-query-form]");
        input.value = workspaceState.getCondition("topic").draft || "";
        input.addEventListener("input", function () {
            workspaceState.setCondition("topic", { draft: input.value });
            mount.querySelector("[data-topic-submit]").disabled =
                topicTaskIdentityPending || !input.value.trim();
            var error = mount.querySelector("[data-topic-query-error]");
            error.hidden = true;
            error.textContent = "";
            updateTopicTaskDraftStatus(mount);
        });
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            loadTopicTaskAnalysis(mount);
        });
        mount.querySelector("[data-topic-task-export]")
            .addEventListener("click", function (event) {
                openModuleExportDialog("topic", event.currentTarget);
            });
        mount.querySelector("[data-topic-rules]")
            .addEventListener("click", function () {
                initializeTopicDialogs();
                var dialog = document.getElementById(
                    "social-network-topic-rules-dialog");
                if (dialog && typeof dialog.showModal === "function") {
                    dialog.showModal();
                }
            });
        mount.addEventListener("click", function (event) {
            var categoryButton = event.target.closest("[data-topic-category]");
            if (categoryButton && mount.contains(categoryButton)) {
                workspaceState.setCondition("topic", {
                    category: categoryButton.getAttribute("data-topic-category")
                }, false);
                renderTopicTaskResult(mount);
                return;
            }
            var userDetailButton = event.target.closest(
                "[data-topic-user-detail]");
            if (userDetailButton && mount.contains(userDetailButton)) {
                var userId = userDetailButton.getAttribute(
                    "data-topic-user-detail");
                if (userId && typeof window.openSocialNetworkUserDetail === "function") {
                    window.openSocialNetworkUserDetail(
                        userId,
                        null,
                        userDetailButton
                    );
                }
                return;
            }
            var button = event.target.closest("[data-topic-open-original]");
            if (!button || !mount.contains(button)) {
                return;
            }
            openTopicTweetDetail(
                Number(button.getAttribute("data-topic-open-original")),
                button
            );
        });
        mount.querySelector("[data-topic-submit]").disabled =
            topicTaskIdentityPending || !input.value.trim();
        mount.querySelector("[data-topic-task-export]").disabled =
            !workspaceState.getCondition("topic").submittedTerm;
        initializeTopicDialogs();
        if (topicTaskResult &&
                topicTaskResult.contextKey === datasetContextKey(readDatasetContext())) {
            renderTopicTaskResult(mount);
        } else {
            mount.querySelector("[data-topic-results]").hidden = true;
            mount.querySelector("[data-topic-idle]").hidden = false;
            updateTopicTaskDraftStatus(mount);
        }
        return mount;
    }

    function startTopicTaskPage(taskCard) {
        var mount = ensureTopicTaskView(taskCard);
        var sequence = ++topicTaskIdentitySequence;
        topicTaskIdentityPending = true;
        mount.querySelector("[data-topic-submit]").disabled = true;
        setText("social-network-task-status", "正在读取数据身份");
        setText("social-network-task-message",
            "数据身份确认后可提交主题查询；离开页面不会自动发起请求。");
        loadShellDatasetVersion().finally(function () {
            if (sequence !== topicTaskIdentitySequence ||
                    activeSocialNetworkTask !== "topic") {
                return;
            }
            topicTaskIdentityPending = false;
            var currentMount = document.getElementById(
                "social-network-topic-task");
            if (!currentMount) {
                return;
            }
            var input = currentMount.querySelector(
                "#social-network-topic-task-query");
            currentMount.querySelector("[data-topic-submit]").disabled =
                !String(input.value || "").trim();
            if (topicTaskResult &&
                    topicTaskResult.contextKey ===
                        datasetContextKey(readDatasetContext())) {
                renderTopicTaskResult(currentMount);
                setText("social-network-task-status",
                    "显示上次成功的主题查询结果");
                setText("social-network-task-message",
                    "结果按最近一次成功查询固定；编辑输入框只会修改下一次查询草稿。");
            } else {
                updateTopicTaskDraftStatus(currentMount);
                setText("social-network-task-status", "等待提交主题词元");
                setText("social-network-task-message",
                    "输入一个精确词元并显式提交；未查询时不会展示空结果或统计值。");
            }
        });
    }

    function updateTopicTaskDraftStatus(mount, message) {
        var status = mount.querySelector("[data-topic-draft-status]");
        var conditions = workspaceState.getCondition("topic");
        var submittedTerm = conditions.submittedTerm;
        if (message) {
            status.textContent = message;
            return;
        }
        if (!submittedTerm) {
            status.textContent = "提交非空词元后显示结果。";
        } else if (String(conditions.draft || "").trim() !== submittedTerm) {
            status.textContent =
                "当前输入是未提交草稿；下方结果与导出仍使用上次成功查询“" +
                submittedTerm + "”。";
        } else {
            status.textContent = "当前结果来自最近一次成功提交。";
        }
    }

    function initializeTopicDialogs() {
        if (topicDetailDialogBound) {
            return;
        }
        var detailDialog = document.getElementById(
            "social-network-topic-detail-dialog");
        var rulesDialog = document.getElementById(
            "social-network-topic-rules-dialog");
        if (!detailDialog || !rulesDialog) {
            return;
        }
        [
            ["social-network-topic-detail-close", detailDialog],
            ["social-network-topic-rules-close", rulesDialog]
        ].forEach(function (entry) {
            var button = document.getElementById(entry[0]);
            if (button) {
                button.addEventListener("click", function () {
                    entry[1].close();
                });
            }
        });
        [detailDialog, rulesDialog].forEach(function (dialog) {
            dialog.addEventListener("click", function (event) {
                if (event.target === dialog) {
                    dialog.close();
                }
            });
        });
        document.getElementById("social-network-topic-detail-author")
            .addEventListener("click", function () {
                var authorId = topicDetailAuthorUserId;
                var opener = topicDetailOpener;
                if (!authorId) {
                    return;
                }
                detailDialog.close();
                window.requestAnimationFrame(function () {
                    if (activeSocialNetworkTask === "topic" &&
                            typeof window.openSocialNetworkUserDetail === "function") {
                        window.openSocialNetworkUserDetail(authorId, null, opener);
                    }
                });
            });
        detailDialog.addEventListener("close", function () {
            topicDetailAuthorUserId = "";
            topicDetailOpener = null;
        });
        topicDetailDialogBound = true;
    }

    function openTopicTweetDetail(tweetIndex, opener) {
        var contextKey = datasetContextKey(readDatasetContext());
        if (!topicTaskResult || topicTaskResult.contextKey !== contextKey) {
            return;
        }
        var item = topicTaskResult.tweets[tweetIndex];
        if (!item) {
            return;
        }
        initializeTopicDialogs();
        var dialog = document.getElementById(
            "social-network-topic-detail-dialog");
        var data = topicTaskResult.data;
        var context = readDatasetContext();
        var authorLinked = item.authorStatus === "linked" &&
            item.authorUserId != null && String(item.authorUserId).trim() !== "";
        topicDetailAuthorUserId = authorLinked
            ? String(item.authorUserId)
            : "";
        topicDetailOpener = opener || null;
        document.getElementById("social-network-topic-detail-query").textContent =
            "成功查询：" + String(data.query || data.normalizedQuery || "");
        document.getElementById("social-network-topic-detail-dataset").textContent =
            "数据集：" + context.datasetId +
            (context.datasetVersion ? " · 版本 " + context.datasetVersion : "") +
            (context.sessionId ? " · 临时会话" : "");
        document.getElementById("social-network-topic-detail-id").textContent =
            String(item.tweetId || "未提供");
        document.getElementById("social-network-topic-detail-author-state")
            .textContent = authorLinked
                ? "已关联用户 ID：" + String(item.authorUserId)
                : item.authorUserId
                    ? "作者未关联 · ID：" + String(item.authorUserId)
                    : "未归属来源";
        document.getElementById("social-network-topic-detail-time").textContent =
            item.createdAt == null || item.createdAt === ""
                ? "未提供"
                : String(item.createdAt);
        var originalText = item.textOriginal == null
            ? ""
            : String(item.textOriginal);
        var original = document.getElementById(
            "social-network-topic-detail-original");
        original.textContent = originalText === "" ? "原文为空" : originalText;
        document.getElementById("social-network-topic-detail-likes").textContent =
            formatNumber(item.likeCount);
        document.getElementById("social-network-topic-detail-replies").textContent =
            formatNumber(item.replyCount);
        document.getElementById("social-network-topic-detail-reposts").textContent =
            formatNumber(item.repostCount);
        document.getElementById("social-network-topic-detail-author")
            .hidden = !authorLinked;
        dialog.showModal();
    }

    function renderTopicTaskResult(mount) {
        var cached = topicTaskResult;
        if (!cached || cached.contextKey !== datasetContextKey(readDatasetContext())) {
            mount.querySelector("[data-topic-results]").hidden = true;
            mount.querySelector("[data-topic-idle]").hidden = false;
            return;
        }
        var data = cached.data;
        var results = mount.querySelector("[data-topic-results]");
        var idle = mount.querySelector("[data-topic-idle]");
        var count = mount.querySelector("[data-topic-result-count]");
        var status = mount.querySelector("[data-topic-result-status]");
        var table = mount.querySelector("[data-topic-result-table]");
        var submittedTerm = String(data.query || data.normalizedQuery || "");
        var returnedTweets = Array.isArray(data.tweets) ? data.tweets : [];
        var tweets = returnedTweets.slice().sort(function (left, right) {
            var leftLikes = Number(left.likeCount);
            var rightLikes = Number(right.likeCount);
            leftLikes = Number.isFinite(leftLikes) ? leftLikes : 0;
            rightLikes = Number.isFinite(rightLikes) ? rightLikes : 0;
            if (leftLikes !== rightLikes) {
                return rightLikes - leftLikes;
            }
            var leftId = String(left.tweetId == null ? "" : left.tweetId);
            var rightId = String(right.tweetId == null ? "" : right.tweetId);
            return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
        }).slice(0, 30);
        cached.tweets = tweets;
        var matchedCount = Number(data.matchedTweetCount);
        matchedCount = Number.isFinite(matchedCount) ? matchedCount : returnedTweets.length;
        var returnedCount = Number(data.returnedTweetCount);
        returnedCount = Number.isFinite(returnedCount)
            ? returnedCount
            : returnedTweets.length;
        var conditions = workspaceState.getCondition("topic");
        var category = ["tweets", "users", "following"].indexOf(
            conditions.category
        ) >= 0 ? conditions.category : "tweets";
        var draft = String(conditions.draft || "").trim();
        var draftNotice = draft !== submittedTerm
            ? " · 下方为上次成功查询“" + escapeHtml(submittedTerm) + "”"
            : "";
        var tweetCountText = "全部匹配 " + formatNumber(matchedCount) +
            " 条 · 接口返回 " + formatNumber(returnedCount) +
            " 条 · 页面展示 " + formatNumber(tweets.length) +
            " 条（最多 30 条）" + draftNotice;
        var tweetTable = !tweets.length
            ? '<div class="social-network-empty">该精确词元没有匹配推文。</div>'
            : '<table><thead><tr><th scope="col">推文与作者</th>' +
                '<th scope="col">原文摘要</th><th scope="col">发布时间</th>' +
                '<th scope="col">点赞</th><th scope="col">详情</th></tr></thead><tbody>' +
                tweets.map(function (item, index) {
                    var authorStatus = item.authorStatus === "linked"
                        ? "已关联用户"
                        : item.authorUserId
                            ? "作者未关联"
                            : "未归属来源";
                    var originalText = item.textOriginal == null
                        ? ""
                        : String(item.textOriginal);
                    return "<tr><td><strong>" +
                        escapeHtml(item.tweetId || "未提供") + "</strong>" +
                        '<small class="social-network-topic-task-author-state">' +
                        escapeHtml(authorStatus) + "</small></td><td>" +
                        (originalText === ""
                            ? '<span class="social-network-topic-task-original-empty">原文为空</span>'
                            : '<p class="social-network-topic-task-excerpt">' +
                                escapeHtml(originalText) + "</p>") +
                        "</td><td>" + escapeHtml(item.createdAt || "未提供") +
                        '</td><td><strong>' + formatNumber(item.likeCount) +
                        '</strong><small>接口统计值</small></td><td>' +
                        '<button class="social-network-inline-link" type="button" ' +
                        'data-topic-open-original="' + index +
                        '">查看原文</button></td></tr>';
                }).join("") + "</tbody></table>";
        var view = category === "users"
            ? renderTopicRelatedUsers(data)
            : category === "following"
                ? renderTopicFollowing(data)
                : {
                    title: "相关推文",
                    ariaLabel: "主题相关推文，最多 30 条",
                    count: tweetCountText,
                    note: "按接口点赞统计值降序、推文 ID 升序排列，不代表主题相关度。" +
                        "互动统计缺失或解析失败时可能按 0 计入，无法逐条区分真实零值与折算零。",
                    html: tweetTable
                };
        mount.querySelector("[data-topic-results-title]").textContent =
            view.title;
        count.textContent = view.count;
        table.setAttribute("aria-label", view.ariaLabel);
        table.innerHTML = view.html;
        mount.querySelector("[data-topic-result-note]").textContent = view.note;
        updateTopicTaskCategoryView(mount, category);
        status.textContent = matchedCount === 0
            ? "无精确词元匹配"
            : "成功查询：" + submittedTerm;
        mount.querySelector("[data-topic-task-export]").disabled = !submittedTerm;
        updateTopicTaskDraftStatus(mount);
        mount.querySelector("[data-topic-query-error]").hidden = true;
        idle.hidden = true;
        results.hidden = false;
    }

    function topicCountLabel(value) {
        if (value === null || value === undefined || value === "") {
            return "未提供";
        }
        var count = Number(value);
        return Number.isFinite(count) && count >= 0
            ? formatNumber(count)
            : "未提供";
    }

    function renderTopicRelatedUsers(data) {
        var returnedUsers = Array.isArray(data.users) ? data.users : [];
        var users = returnedUsers.slice(0, 30);
        var matchedTweetCount = topicCountLabel(data.matchedTweetCount);
        var relatedUserCount = topicCountLabel(data.relatedUserCount);
        var returnedUserCount = topicCountLabel(
            data.returnedUserCount == null
                ? returnedUsers.length
                : data.returnedUserCount
        );
        var note = "每位用户的匹配推文数仅覆盖当前主题；接口互动总量不是有效样本数或均值。" +
            "缺失或无法解析的互动值可能按 0 计入，无法逐项区分真实零值与折算零。";
        if (!users.length) {
            return {
                title: "相关用户",
                ariaLabel: "主题相关用户，最多 30 位",
                count: "主题匹配推文 " + matchedTweetCount + " 条 · 全部相关已关联用户 " +
                    relatedUserCount + " 位 · 接口返回 " + returnedUserCount +
                    " 位 · 页面展示 0 位",
                note: note,
                html: '<div class="social-network-empty">' +
                    (Number(data.matchedTweetCount) > 0
                        ? "存在匹配推文，但没有可纳入主题用户列表的已关联作者。"
                        : "当前词元没有匹配推文，因此没有相关用户。") +
                    "</div>"
            };
        }
        var html = '<table><thead><tr><th scope="col">用户 ID</th>' +
            '<th scope="col">匹配推文数</th><th scope="col">点赞总量</th>' +
            '<th scope="col">评论总量</th><th scope="col">转发总量</th>' +
            '<th scope="col">操作</th></tr></thead><tbody>' +
            users.map(function (item) {
                var userId = item.userId == null ? "" : String(item.userId);
                return "<tr><td><strong>" +
                    escapeHtml(userId || "未提供") +
                    "</strong></td><td>" + formatNumber(item.matchedTweetCount) +
                    '</td><td><strong>' + formatNumber(item.likeTotal) +
                    '</strong><small>接口统计值</small></td><td><strong>' +
                    formatNumber(item.replyTotal) +
                    '</strong><small>接口统计值</small></td><td><strong>' +
                    formatNumber(item.repostTotal) +
                    '</strong><small>接口统计值</small></td><td>' +
                    (userId
                        ? '<button class="social-network-inline-link" type="button" ' +
                            'data-topic-user-detail="' + escapeHtml(userId) +
                            '" aria-label="查看用户 ' + escapeHtml(userId) +
                            ' 的详情">查看详情</button>'
                        : "未提供用户 ID") +
                    "</td></tr>";
            }).join("") + "</tbody></table>";
        return {
            title: "相关用户",
            ariaLabel: "主题相关用户，最多 30 位",
            count: "主题匹配推文 " + matchedTweetCount + " 条 · 全部相关已关联用户 " +
                relatedUserCount + " 位 · 接口返回 " + returnedUserCount +
                " 位 · 页面展示 " + formatNumber(users.length) +
                " 位（最多 30 位）",
            note: note,
            html: html
        };
    }

    function renderTopicFollowing(data) {
        var network = data.network || {};
        var returnedEdges = Array.isArray(network.edges) ? network.edges : [];
        var edges = returnedEdges.slice(0, 30);
        var relatedUserCount = topicCountLabel(data.relatedUserCount);
        var totalEdgeCount = topicCountLabel(network.totalEdgeCount);
        var matchedTweetCount = Number(data.matchedTweetCount);
        var note = "仅展示主题已关联用户之间真实的有向关注关系，不含外部节点；端点可能不在用户表前 30 位中。" +
            "接口最多返回 500 条，本表显示其中前 30 条，顺序不代表关系强度或影响力。";
        if (!edges.length) {
            return {
                title: "关注关系",
                ariaLabel: "主题相关用户之间的真实关注关系，最多 30 条",
                count: "全部相关已关联用户 " + relatedUserCount +
                    " 位 · 全部真实关注边 " + totalEdgeCount +
                    " 条 · 接口返回 0 条 · 页面展示 0 条",
                note: note,
                html: '<div class="social-network-empty">' +
                    (matchedTweetCount > 0 && Number(data.relatedUserCount) > 0
                        ? "当前主题作者之间没有返回真实关注边；这不表示这些用户在全网中孤立。"
                        : "当前没有可用于生成主题关注关系的已关联作者。") +
                    "</div>"
            };
        }
        var html = '<table><thead><tr><th scope="col">来源用户 ID</th>' +
            '<th scope="col">关系</th><th scope="col">目标用户 ID</th>' +
            "</tr></thead><tbody>" +
            edges.map(function (item) {
                var source = item.source == null ? "" : String(item.source);
                var target = item.target == null ? "" : String(item.target);
                var relation = item.relationType === "following"
                    ? "关注"
                    : item.relationType == null
                        ? "未提供"
                        : String(item.relationType);
                function endpointCell(userId) {
                    return "<td>" + (userId
                        ? "<strong>" + escapeHtml(userId) + "</strong> " +
                            '<button class="social-network-inline-link" type="button" ' +
                            'data-topic-user-detail="' + escapeHtml(userId) +
                            '" aria-label="查看用户 ' + escapeHtml(userId) +
                            ' 的详情">查看详情</button>'
                        : "未提供用户 ID") + "</td>";
                }
                return "<tr>" + endpointCell(source) +
                    "<td>" + escapeHtml(relation) + "</td>" +
                    endpointCell(target) + "</tr>";
            }).join("") + "</tbody></table>";
        return {
            title: "关注关系",
            ariaLabel: "主题相关用户之间的真实关注关系，最多 30 条",
            count: "全部相关已关联用户 " + relatedUserCount +
                " 位 · 全部真实关注边 " + totalEdgeCount +
                " 条 · 接口返回 " + topicCountLabel(returnedEdges.length) +
                " 条（接口最多 500 条） · 页面展示 " +
                formatNumber(edges.length) + " 条（最多 30 条）",
            note: note,
            html: html
        };
    }

    function updateTopicTaskCategoryView(mount, category) {
        mount.querySelectorAll("[data-topic-category]")
            .forEach(function (button) {
                var selected = button.getAttribute("data-topic-category") === category;
                button.setAttribute("aria-pressed", String(selected));
                button.classList.toggle("is-active", selected);
            });
    }

    async function loadTopicTaskAnalysis(mount) {
        var input = mount.querySelector("#social-network-topic-task-query");
        var query = String(input.value || "").trim();
        if (!query || topicTaskIdentityPending ||
                activeSocialNetworkTask !== "topic") {
            return;
        }
        workspaceState.setCondition("topic", { draft: input.value });
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        var requestParameters = { term: query, contextKey: contextKey };
        var requestToken = beginTaskRequest("topic", requestParameters);
        var button = mount.querySelector("[data-topic-submit]");
        var draftStatus = mount.querySelector("[data-topic-draft-status]");
        button.disabled = true;
        draftStatus.textContent = "正在查询词元“" + query + "”…";
        mount.querySelector("[data-topic-query-error]").hidden = true;
        setText("social-network-task-status", "正在查询主题词元");
        try {
            var result = await request(buildDatasetUrl("text/topic", {
                query: query,
                limit: 30
            }, context));
            requireCurrentTaskRequest(requestToken, requestParameters);
            if (activeSocialNetworkTask !== "topic" ||
                    !sameDatasetContext(context, readDatasetContext())) {
                throw staleDatasetRequestError();
            }
            var data = result.data || {};
            var submittedTerm = String(data.query || data.normalizedQuery || query);
            topicTaskResult = {
                contextKey: contextKey,
                data: data,
                tweets: []
            };
            workspaceState.setCondition("topic", {
                submittedTerm: submittedTerm
            }, false);
            renderTopicTaskResult(mount);
            setText("social-network-task-status",
                Number(data.matchedTweetCount) === 0
                    ? "主题查询完成 · 无匹配推文"
                    : "主题查询完成 · 显示相关推文");
            setText("social-network-task-message",
                "结果固定对应成功响应返回的查询词；输入框内容仍可作为下一次查询草稿。");
        } catch (error) {
            if (isStaleDatasetRequest(error)) {
                return;
            }
            var errorMessage = mount.querySelector("[data-topic-query-error]");
            errorMessage.textContent =
                "本次主题查询失败。" +
                (error.status ? " HTTP " + error.status + "。" : " ") +
                (error.message || "请检查查询词和当前数据后重试。");
            errorMessage.hidden = false;
            updateTopicTaskDraftStatus(mount,
                topicTaskResult && topicTaskResult.contextKey === contextKey
                    ? "本次查询失败；下方如有结果，仍是上次成功查询。"
                    : "本次查询失败；修改词元后可重新提交。");
            setText("social-network-task-status", "主题查询失败");
            if (typeof console !== "undefined" && console.error) {
                console.error("[social-network] topic task failed", error);
            }
        } finally {
            if (workspaceState.isRequestCurrent(requestToken, requestParameters)) {
                button.disabled = !String(input.value || "").trim();
            }
        }
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
                window.openSocialNetworkUserDetail(
                    button.getAttribute("data-social-user-id"),
                    null,
                    button);
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
        var conditions = workspaceState.getCondition("topic");
        var query = conditions.draft.trim();
        if (!query) {
            return;
        }
        var requestToken = beginTaskRequest("topic", { term: query });
        var button = document.getElementById("social-network-topic-button");
        button.disabled = true;
        button.textContent = "分析中…";
        try {
            var result = await request(buildDatasetUrl("text/topic", {
                query: query,
                limit: 30
            }));
            requireCurrentTaskRequest(requestToken, { term: query });
            workspaceState.setCondition("topic", { submittedTerm: query }, false);
            renderTopic(result);
        } finally {
            if (workspaceState.isRequestCurrent(requestToken, { term: query })) {
                button.disabled = false;
                button.textContent = "开始分析";
            }
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

    function uploadStatus(message, state) {
        var taskStatus = document.getElementById("social-network-task-upload-status");
        if (!taskStatus) {
            return;
        }
        taskStatus.textContent = message;
        taskStatus.className = "social-network-upload-status" +
            (state ? " is-" + state : "");
    }

    function formatUploadBytes(bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024)) + " KB";
        }
        return (bytes / (1024 * 1024)).toLocaleString("zh-CN", {
            maximumFractionDigits: 1
        }) + " MiB";
    }

    function uploadFileIssue(file) {
        var name = String(file.name || "");
        if (!/\.(csv|jsonl)$/i.test(name)) {
            return "仅支持 CSV 或 JSONL 文件";
        }
        if (!file.size) {
            return "文件为空";
        }
        if (file.size > MAX_UPLOAD_FILE_BYTES) {
            return "超过单文件 128 MiB 设计上限";
        }
        return "";
    }

    function uploadSelectionIssue() {
        if (uploadFiles.length === 0) {
            return "请先选择至少一个文件。";
        }
        if (uploadFiles.length > MAX_UPLOAD_FILES) {
            return "单次最多选择 10 个文件。";
        }
        var totalBytes = 0;
        for (var index = 0; index < uploadFiles.length; index++) {
            var entry = uploadFiles[index];
            var issue = uploadFileIssue(entry.file);
            if (issue) {
                return entry.file.name + "：" + issue + "。";
            }
            if (!entry.role) {
                return "请为“" + entry.file.name + "”指定数据角色。";
            }
            totalBytes += entry.file.size;
        }
        if (totalBytes > MAX_UPLOAD_REQUEST_BYTES) {
            return "所选文件总大小超过 160 MiB 设计上限。";
        }
        return "";
    }

    function readPersistedUploadTaskId() {
        try {
            var taskId = window.sessionStorage.getItem(
                UPLOAD_TASK_STORAGE_KEY);
            return typeof taskId === "string" && taskId.trim()
                ? taskId.trim()
                : null;
        } catch (error) {
            return null;
        }
    }

    function persistUploadTaskId(taskId) {
        try {
            if (typeof taskId === "string" && taskId.trim()) {
                window.sessionStorage.setItem(
                    UPLOAD_TASK_STORAGE_KEY, taskId.trim());
            } else {
                window.sessionStorage.removeItem(UPLOAD_TASK_STORAGE_KEY);
            }
        } catch (error) {
            // Storage can be unavailable in restricted browser contexts.
        }
    }

    function restoreUploadTaskState() {
        var taskId = readPersistedUploadTaskId();
        if (!taskId) {
            return;
        }
        uploadState.uploadTaskId = taskId;
        uploadState.uploadTaskView = {
            uploadTaskId: taskId,
            status: "queued",
            phase: null,
            preview: null,
            error: null
        };
    }

    function uploadTaskIsBusy() {
        return uploadSubmitting || (uploadState.uploadTaskView &&
            ["queued", "processing", "cancelling", "ready"].indexOf(
                uploadState.uploadTaskView.status) >= 0);
    }

    function taskUploadStatusIsActive(status) {
        return ["queued", "processing", "cancelling"].indexOf(status) >= 0;
    }

    function updateTaskUploadControls() {
        var content = document.getElementById("social-network-task-upload-content");
        if (!content) {
            return;
        }
        var taskView = uploadState.uploadTaskView;
        var status = taskView ? taskView.status : "";
        var locked = uploadTaskIsBusy();
        var input = document.getElementById("social-network-task-upload-input");
        var submit = document.getElementById("social-network-task-upload-submit");
        var cancel = document.getElementById("social-network-task-upload-cancel");
        var retry = document.getElementById("social-network-task-upload-retry");
        if (input) {
            input.disabled = locked;
        }
        content.querySelectorAll(
            "#social-network-task-upload-files select, " +
            "#social-network-task-upload-files button"
        ).forEach(function (control) {
            control.disabled = locked;
        });
        if (submit) {
            submit.disabled = locked || Boolean(uploadSelectionIssue());
            submit.textContent = uploadSubmitting
                ? "正在接收文件…"
                : "上传并检查";
        }
        if (cancel) {
            cancel.hidden = !uploadState.uploadTaskId ||
                !taskUploadStatusIsActive(status);
            cancel.disabled = uploadCancelInFlight || status === "cancelling";
        }
        if (retry) {
            retry.hidden = !uploadPollFailure ||
                uploadPollFailure.taskId !== uploadState.uploadTaskId ||
                uploadPollFailure.terminal;
            retry.disabled = uploadPollInFlight;
        }
    }

    function uploadCheckIsActive() {
        return uploadSubmitting || (uploadState.uploadTaskView &&
            ["queued", "processing", "cancelling"].indexOf(
                uploadState.uploadTaskView.status) >= 0);
    }

    function updateUploadControls() {
        if (activeSocialNetworkTask === "data" && currentDatasetDetails) {
            renderDatasetPageDetails(currentDatasetDetails);
        }
        updateTaskUploadControls();
    }

    function clearUploadSelection() {
        workspaceState.replaceUploadFiles([]);
        var input = document.getElementById("social-network-task-upload-input");
        if (input) {
            input.value = "";
        }
        var source = document.getElementById("social-network-task-upload-source");
        if (source) {
            source.value = "";
        }
        renderTaskUploadFiles();
    }

    function formatUploadTime(value) {
        if (!value) {
            return "时间未提供";
        }
        var date = new Date(value);
        return Number.isNaN(date.getTime())
            ? "时间未提供"
            : date.toLocaleString("zh-CN");
    }

    function uploadResponseError(body, fallback, status) {
        var error = new Error(body && body.message ? body.message : fallback);
        error.code = body && body.code ? body.code : null;
        error.status = status || 0;
        return error;
    }

    async function readUploadResponse(response, fallback) {
        var body;
        try {
            body = await response.json();
        } catch (error) {
            throw new Error(fallback);
        }
        if (!response.ok || !body.success) {
            throw uploadResponseError(body, fallback, response.status);
        }
        return body;
    }

    function renderUploadTask(view) {
        uploadState.uploadTaskView = view;
        persistUploadTaskId(view.status === "cancelled" ||
            view.status === "expired" ? null : uploadState.uploadTaskId);
        var taskMount = document.getElementById(
            "social-network-task-upload-content");
        if (!taskMount) {
            return;
        }
        if (view.status === "ready" || view.status === "failed") {
            renderTaskUploadResult(view);
        } else {
            renderTaskUploadState(view);
        }
        updateUploadControls();
    }

    function renderTaskUploadIssueList(issues) {
        var summary = uploadPreviewModel.issueSummary(issues);
        var note = '<p class="social-network-task-upload-result-note">' +
            "本次响应返回 " + summary.returnedCount + " 项，展示 " +
            summary.displayedCount + " 项；未提供服务端完整问题总数。</p>";
        if (summary.omittedCount) {
            note += '<p class="social-network-task-upload-result-note">本页最多展示 ' +
                summary.limit + " 项，另有 " + summary.omittedCount +
                " 项未展示。</p>";
        }
        if (!summary.displayedCount) {
            return note + '<p class="social-network-empty">本次响应未返回问题定位项；' +
                "这不代表完整数据没有质量问题。</p>";
        }
        return note + '<ul class="social-network-task-upload-issues">' +
            summary.displayed.map(function (issue) {
                var rows = Array.isArray(issue.sourceRowNumbers)
                    ? issue.sourceRowNumbers
                    : [];
                var fileName = issue.sourceFileName || issue.fileName || "";
                var code = issue.code || issue.issueCode || "";
                var details = [];
                if (fileName) {
                    details.push("文件 " + fileName);
                }
                if (rows.length) {
                    details.push("行 " + rows.join(", "));
                }
                return '<li><div class="social-network-task-upload-issue-head">' +
                    '<strong>' + escapeHtml(issue.message || code || "质量问题") +
                    '</strong><span>' +
                    escapeHtml(uploadPreviewModel.severityLabel(issue.severity)) +
                    '</span></div>' +
                    (issue.dimension
                        ? '<small>维度：' + escapeHtml(issue.dimension) + '</small>'
                        : "") +
                    (details.length
                        ? '<small>' + escapeHtml(details.join(" · ")) + '</small>'
                        : "") +
                    (code ? '<small>代码：' + escapeHtml(code) + '</small>' : "") +
                    '</li>';
            }).join("") + '</ul>';
    }

    function renderTaskUploadResultCategory(view) {
        var content = document.getElementById("social-network-task-upload-result-content");
        if (!content) {
            return;
        }
        var preview = view.preview || {};
        var quality = preview.qualitySummary || {};
        if (uploadPreviewCategory === "files") {
            var files = uploadPreviewModel.fileResults(preview);
            content.innerHTML = files.length
                ? '<div class="social-network-upload-preview-table-wrap">' +
                    '<table class="social-network-upload-preview-table ' +
                    'social-network-task-upload-file-table"><thead><tr>' +
                    '<th>文件</th><th>角色</th><th>格式 / 编码</th><th>字段检查</th>' +
                    '<th>原始行数</th><th>读取成功</th><th>警告</th><th>跳过</th>' +
                    '<th>错误</th><th>质量状态</th></tr></thead><tbody>' +
                    files.map(function (file) {
                        return '<tr><td>' + escapeHtml(file.fileName) + '</td><td>' +
                            escapeHtml(file.role) + '</td><td>' +
                            escapeHtml(file.format + " / " + file.encoding) + '</td><td>' +
                            escapeHtml(file.headerStatus) + '</td><td>' +
                            escapeHtml(file.rowCount === undefined
                                ? "未提供"
                                : formatNumber(file.rowCount)) + '</td><td>' +
                            escapeHtml(file.readOkCount === undefined
                                ? "未提供"
                                : formatNumber(file.readOkCount)) + '</td><td>' +
                            escapeHtml(file.readWarningCount === undefined
                                ? "未提供"
                                : formatNumber(file.readWarningCount)) + '</td><td>' +
                            escapeHtml(file.readSkippedCount === undefined
                                ? "未提供"
                                : formatNumber(file.readSkippedCount)) + '</td><td>' +
                            escapeHtml(file.readErrorCount === undefined
                                ? "未提供"
                                : formatNumber(file.readErrorCount)) + '</td><td>' +
                            escapeHtml(file.qualityStatus) + '</td></tr>';
                    }).join("") + '</tbody></table></div>'
                : '<p class="social-network-empty">服务端未返回文件读取明细。</p>';
            return;
        }
        if (uploadPreviewCategory === "quality") {
            var dimensions = uploadPreviewModel.dimensions();
            var selectedDimension = dimensions.some(function (item) {
                return item.id === uploadQualityDimension;
            }) ? uploadQualityDimension : "summary";
            uploadQualityDimension = selectedDimension;
            var entries = uploadPreviewModel.metricEntries(quality, selectedDimension);
            content.innerHTML =
                '<p class="social-network-task-upload-result-note">数据质量状态：' +
                escapeHtml(uploadPreviewModel.datasetStatusLabel(quality.datasetStatus)) +
                '。下方只展示服务端返回的当前维度指标，不将不同口径合并为总量。</p>' +
                '<div class="social-network-task-upload-dimensions" ' +
                'aria-label="质量统计维度">' +
                dimensions.map(function (item) {
                    return '<button type="button" data-upload-quality-dimension="' +
                        escapeHtml(item.id) + '" aria-pressed="' +
                        (item.id === selectedDimension ? "true" : "false") + '">' +
                        escapeHtml(item.label) + '</button>';
                }).join("") + '</div>' +
                (entries.length
                    ? '<dl class="social-network-task-upload-metrics">' +
                        entries.map(function (entry) {
                            return '<div><dt>' + escapeHtml(entry.label) +
                                '</dt><dd>' + escapeHtml(entry.value === null ||
                                    entry.value === undefined
                                    ? "未提供"
                                    : formatNumber(entry.value)) + '</dd>' +
                                (entry.key === entry.label
                                    ? ""
                                    : '<small>' + escapeHtml(entry.key) + '</small>') +
                                '</div>';
                        }).join("") + '</dl>'
                    : '<p class="social-network-empty">服务端未返回此维度的统计指标。</p>');
            content.querySelectorAll("[data-upload-quality-dimension]")
                .forEach(function (button) {
                    button.addEventListener("click", function () {
                        uploadQualityDimension = button.getAttribute(
                            "data-upload-quality-dimension");
                        renderTaskUploadResultCategory(view);
                    });
                });
            return;
        }
        if (uploadPreviewCategory === "issues") {
            content.innerHTML = renderTaskUploadIssueList(quality.issues);
            return;
        }
        var capabilities = uploadPreviewModel.capabilityItems(
            preview.capabilitySummary);
        content.innerHTML = capabilities.length
            ? '<div class="social-network-task-upload-capability-list">' +
                capabilities.map(function (item) {
                    return '<article class="social-network-upload-capability is-' +
                        escapeHtml(item.status) + '"><strong>' +
                        escapeHtml(item.label) + '</strong><span>' +
                        escapeHtml(item.statusLabel) + '</span>' +
                        (item.reasonMessage
                            ? '<p>' + escapeHtml(item.reasonMessage) + '</p>'
                            : "") +
                        (item.fallback
                            ? '<small>' + escapeHtml(item.fallback) + '</small>'
                            : "") + '</article>';
                }).join("") + '</div>'
            : '<p class="social-network-empty">服务端未返回模块能力状态。</p>';
    }

    function updateTaskUploadResultControls(view) {
        var activate = document.getElementById("social-network-task-upload-activate");
        if (!activate) {
            return;
        }
        var quality = view && view.preview && view.preview.qualitySummary || {};
        var canActivate = Boolean(view && view.status === "ready" &&
            view.preview && view.preview.sessionId && view.preview.datasetId &&
            view.preview.datasetVersion &&
            String(quality.datasetStatus || "").toLowerCase() !== "unavailable");
        activate.disabled = !canActivate || uploadActivationInFlight;
        activate.textContent = !canActivate
            ? "预览不可用，无法激活"
            : uploadActivationInFlight
                ? "正在切换数据…"
                : "使用此数据集分析";
    }

    function renderTaskUploadResult(view) {
        var mount = document.getElementById("social-network-task-upload-content");
        if (!mount) {
            return;
        }
        var status = document.getElementById("social-network-task-status");
        var message = document.getElementById("social-network-task-message");
        if (view.status === "failed") {
            var failure = view.error || {};
            mount.innerHTML =
                '<section class="social-network-task-upload-result is-failed" ' +
                'aria-labelledby="social-network-task-upload-result-title">' +
                '<div class="social-network-task-upload-result-heading">' +
                '<div><p class="social-network-data-eyebrow">CHECK RESULT</p>' +
                '<h2 id="social-network-task-upload-result-title">检查未通过</h2></div>' +
                '<span class="social-network-task-upload-state is-failed">不可激活</span>' +
                '</div><p class="social-network-task-upload-result-note">' +
                escapeHtml(failure.message || "文件检查未通过，请检查问题后重新提交。") +
                '</p><section class="social-network-task-upload-result-section">' +
                '<h3>阻断问题</h3>' +
                renderTaskUploadIssueList(failure.issues) +
                '</section><div class="social-network-upload-actions">' +
                '<button id="social-network-task-upload-edit" class="button ' +
                'button-primary" type="button">返回修改文件</button></div></section>';
            if (status) {
                status.textContent = "检查未通过";
            }
            if (message) {
                message.textContent =
                    "失败结果不会改变当前分析数据；修改文件后需重新提交检查。";
            }
            document.getElementById("social-network-task-upload-edit")
                .addEventListener("click", function () {
                    editUploadSelection().catch(function () {
                        uploadStatus("已返回文件选择；预览清理状态暂未确认。", "error");
                    });
                });
            uploadStatus(failure.message || "文件检查未通过。", "error");
            return;
        }

        var preview = view.preview || {};
        var quality = preview.qualitySummary || {};
        var activeContext = readDatasetContext();
        var cachedData = shellDatasetSummaryCache &&
            sameDatasetSelection(shellDatasetSummaryCache.context, activeContext)
            ? shellDatasetSummaryCache.body.data || {}
            : {};
        var categories = uploadPreviewModel.categories();
        uploadPreviewCategory = categories.some(function (item) {
            return item.id === uploadPreviewCategory;
        }) ? uploadPreviewCategory : "files";
        mount.innerHTML =
            '<section class="social-network-task-upload-result" ' +
            'aria-labelledby="social-network-task-upload-result-title">' +
            '<div class="social-network-task-upload-result-heading">' +
            '<div><p class="social-network-data-eyebrow">CHECK RESULT</p>' +
            '<h2 id="social-network-task-upload-result-title">文件检查结果</h2>' +
            '<p class="social-network-task-upload-result-note">待使用数据：' +
            escapeHtml(preview.datasetId || "数据集标识未提供") +
            " · 版本 " + escapeHtml(preview.datasetVersion || "未提供") +
            " · " + escapeHtml(uploadPreviewModel.datasetStatusLabel(
                quality.datasetStatus)) + '</p></div>' +
            '<span class="social-network-task-upload-state is-ready">待确认使用</span>' +
            '</div><p class="social-network-task-upload-current-data">当前分析仍使用：' +
            escapeHtml(cachedData.name || activeContext.datasetId || "未提供") +
            "。预览不会自动切换分析数据。" + '</p>' +
            '<p class="social-network-task-upload-result-note">预计过期：' +
            escapeHtml(formatUploadTime(preview.expiresAt)) +
            '。过期时间以服务端会话状态为准。</p>' +
            '<div class="social-network-task-upload-categories" ' +
            'aria-label="检查结果类别">' +
            categories.map(function (item) {
                return '<button type="button" data-upload-result-category="' +
                    escapeHtml(item.id) + '" aria-pressed="' +
                    (item.id === uploadPreviewCategory ? "true" : "false") + '">' +
                    escapeHtml(item.label) + '</button>';
            }).join("") + '</div>' +
            '<section id="social-network-task-upload-result-content" ' +
            'class="social-network-task-upload-result-content" aria-live="polite"></section>' +
            '<div class="social-network-upload-actions social-network-task-upload-result-actions">' +
            '<button id="social-network-task-upload-edit" class="button ' +
            'button-secondary" type="button">返回修改文件</button>' +
            '<button id="social-network-task-upload-activate" class="button ' +
            'button-primary" type="button">使用此数据集分析</button></div>' +
            '<dialog id="social-network-task-upload-edit-dialog" ' +
            'class="social-network-task-upload-edit-dialog">' +
            '<h2>返回修改文件？</h2>' +
            '<p>当前检查预览将作废并关闭；已选择的文件、角色和来源说明会保留。' +
            '修改后需要重新上传检查。</p>' +
            '<div class="social-network-upload-actions">' +
            '<button id="social-network-task-upload-edit-cancel" ' +
            'class="button button-secondary" type="button">继续查看结果</button>' +
            '<button id="social-network-task-upload-edit-confirm" ' +
            'class="button button-primary" type="button">作废预览并返回修改</button>' +
            '</div></dialog></section>';
        if (status) {
            status.textContent = "检查结果待确认";
        }
        if (message) {
            message.textContent =
                "选择一个结果类别查看；只有明确确认后，才会将上传数据设为当前分析数据。";
        }
        renderTaskUploadResultCategory(view);
        mount.querySelectorAll("[data-upload-result-category]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    uploadPreviewCategory = button.getAttribute(
                        "data-upload-result-category");
                    renderTaskUploadResult(view);
                });
            });
        document.getElementById("social-network-task-upload-activate")
            .addEventListener("click", function () {
                activateUploadPreview().catch(function () {
                    uploadStatus("暂时无法激活此预览，请稍后重试。", "error");
                });
            });
        var editButton = document.getElementById("social-network-task-upload-edit");
        var editDialog = document.getElementById(
            "social-network-task-upload-edit-dialog");
        editButton.addEventListener("click", function () {
            if (editDialog && typeof editDialog.showModal === "function") {
                editDialog.showModal();
            } else if (window.confirm(
                    "返回修改将作废当前检查预览，并保留文件、角色和来源说明。继续吗？")) {
                editUploadSelection().catch(function () {
                    uploadStatus("已返回文件选择；预览清理状态暂未确认。", "error");
                });
            }
        });
        document.getElementById("social-network-task-upload-edit-cancel")
            .addEventListener("click", function () {
                editDialog.close();
                editButton.focus();
            });
        document.getElementById("social-network-task-upload-edit-confirm")
            .addEventListener("click", function () {
                editDialog.close();
                editUploadSelection().catch(function () {
                    uploadStatus("已返回文件选择；预览清理状态暂未确认。", "error");
                });
            });
        updateTaskUploadResultControls(view);
        uploadStatus(String(quality.datasetStatus || "").toLowerCase() === "unavailable"
            ? "预览中的数据状态为不可用，不能激活；当前分析数据未改变。"
            : "服务端检查结果已就绪；当前分析数据未改变。",
        String(quality.datasetStatus || "").toLowerCase() === "unavailable"
            ? "error"
            : "ready");
    }

    function renderTaskUploadState(view) {
        var phaseLabels = {
            validating_files: "正在检查文件格式与必需字段。",
            reading_files: "正在读取文件内容。",
            merging_records: "正在合并同角色文件。",
            normalizing: "正在规范化数据并生成质量报告。",
            building_preview: "正在计算质量与模块能力。",
            registering_session: "正在准备临时分析会话。"
        };
        var labels = {
            queued: "文件已接收，等待检查。",
            processing: "正在检查文件。",
            cancelling: "正在取消检查任务。",
            cancelled: "检查任务已取消，所选文件仍保留。",
            ready: "文件检查已完成，结果预览已就绪；当前分析数据未更改。",
            failed: (view.error && view.error.message) ||
                "文件检查未通过。请修改所选文件后重新提交。",
            expired: "任务记录已不存在或已过期，请重新选择文件并提交。"
        };
        var message = labels[view.status] || "正在处理上传任务。";
        if (taskUploadStatusIsActive(view.status) && view.phase &&
                phaseLabels[view.phase]) {
            message += " " + phaseLabels[view.phase];
        }
        if (uploadPollFailure && uploadPollFailure.taskId === uploadState.uploadTaskId) {
            message = uploadPollFailure.terminal
                ? "任务记录已不存在或已过期，请重新选择文件并提交。"
                : "暂时无法读取检查进度。可手动重试读取状态。";
            uploadStatus(message, "error");
        } else {
            uploadStatus(message, view.status === "failed" ||
                view.status === "expired" ? "error" :
                view.status === "ready" ? "ready" :
                taskUploadStatusIsActive(view.status) ? "busy" : "");
        }
    }

    function scheduleUploadTaskPoll(delay) {
        if (uploadPollTimer) {
            window.clearTimeout(uploadPollTimer);
        }
        uploadPollTimer = window.setTimeout(pollUploadTask, delay);
    }

    async function pollUploadTask() {
        var taskId = uploadState.uploadTaskId;
        if (!taskId) {
            return;
        }
        if (uploadPollInFlight) {
            uploadPollQueued = true;
            return;
        }
        if (uploadPollFailure && uploadPollFailure.taskId === taskId) {
            return;
        }
        uploadPollInFlight = true;
        var requestParameters = { taskId: taskId };
        var requestToken = beginTaskRequest("uploadPoll", requestParameters);
        try {
            var response = await fetch(
                "/api/social-network/datasets/upload-tasks/" +
                    encodeURIComponent(taskId),
                { headers: { "Accept": "application/json" } });
            var body = await readUploadResponse(response, "暂时无法读取上传任务状态。");
            if (!workspaceState.isLatestRequest(requestToken) ||
                    taskId !== uploadState.uploadTaskId) {
                return;
            }
            uploadPollFailure = null;
            uploadPollRetryCount = 0;
            renderUploadTask(body.data);
            if (taskUploadStatusIsActive(body.data.status)) {
                scheduleUploadTaskPoll(body.data.status === "queued" ? 700 : 1000);
            }
        } catch (error) {
            if (!workspaceState.isLatestRequest(requestToken) ||
                    isStaleDatasetRequest(error)) {
                return;
            }
            var terminal = error.status === 404 || error.status === 410;
            uploadPollFailure = {
                taskId: taskId,
                terminal: terminal
            };
            if (terminal) {
                uploadPollRetryCount = 0;
                persistUploadTaskId(null);
                uploadState.uploadTaskView = Object.assign(
                    {}, uploadState.uploadTaskView || {}, { status: "expired" });
            } else if (uploadPollRetryCount < 3) {
                uploadPollRetryCount += 1;
                uploadPollFailure = null;
                scheduleUploadTaskPoll(700 * uploadPollRetryCount);
            }
            renderTaskUploadState(uploadState.uploadTaskView || { status: "expired" });
            updateUploadControls();
        } finally {
            uploadPollInFlight = false;
            if (uploadPollQueued) {
                uploadPollQueued = false;
                if (!uploadCancelInFlight && !uploadPollFailure &&
                        taskId === uploadState.uploadTaskId) {
                    scheduleUploadTaskPoll(0);
                }
            }
        }
    }

    async function submitUpload(event) {
        event.preventDefault();
        var issue = uploadSelectionIssue();
        if (issue || uploadSubmitting || uploadTaskIsBusy()) {
            uploadStatus(issue || "当前任务尚未结束。", "error");
            return;
        }
        clearUploadTaskState();
        uploadPollFailure = null;
        uploadPollRetryCount = 0;
        var sourceDescription = uploadState.uploadDraft.sourceDescription;
        var requestParameters = {
            files: uploadFiles.map(function (entry) {
                return { partId: entry.partId, role: entry.role };
            }),
            sourceDescription: sourceDescription
        };
        var requestToken = beginTaskRequest("uploadSubmit", requestParameters);
        var formData = new FormData();
        formData.append("manifest", JSON.stringify({
            files: requestParameters.files,
            sourceDescription: sourceDescription
        }));
        uploadFiles.forEach(function (entry) {
            formData.append(entry.partId, entry.file, entry.file.name);
        });
        uploadSubmitting = true;
        updateUploadControls();
        uploadStatus("正在安全接收文件…", "busy");
        try {
            var response = await fetch("/api/social-network/datasets/upload", {
                method: "POST",
                headers: { "Accept": "application/json" },
                body: formData
            });
            var body = await readUploadResponse(response, "上传暂时失败，请稍后重试。");
            if (!workspaceState.isRequestCurrent(requestToken, requestParameters)) {
                return;
            }
            uploadState.uploadTaskId = body.data.uploadTaskId;
            if (!uploadState.uploadTaskId) {
                throw new Error("上传已返回，但未获得任务编号。");
            }
            renderUploadTask({
                uploadTaskId: uploadState.uploadTaskId,
                status: body.data.status || "queued",
                phase: null,
                preview: null,
                error: null
            });
            scheduleUploadTaskPoll(350);
        } catch (error) {
            uploadStatus(error.message || "上传暂时失败，请稍后重试。", "error");
        } finally {
            uploadSubmitting = false;
            updateUploadControls();
        }
    }

    async function cancelUploadTask() {
        var taskId = uploadState.uploadTaskId;
        var taskView = uploadState.uploadTaskView;
        if (!taskId || !taskView || uploadCancelInFlight ||
                !taskUploadStatusIsActive(taskView.status) ||
                taskView.status === "cancelling") {
            return;
        }
        if (uploadPollTimer) {
            window.clearTimeout(uploadPollTimer);
            uploadPollTimer = null;
        }
        uploadPollQueued = false;
        workspaceState.invalidateRequest("uploadPoll");
        var requestParameters = { taskId: taskId };
        var requestToken = beginTaskRequest("uploadCancel", requestParameters);
        uploadCancelInFlight = true;
        updateUploadControls();
        uploadStatus("正在提交取消请求…", "busy");
        try {
            var response = await fetch(
                "/api/social-network/datasets/upload-tasks/" +
                    encodeURIComponent(taskId),
                {
                    method: "DELETE",
                    headers: { "Accept": "application/json" }
                });
            var body = await readUploadResponse(response, "暂时无法取消检查任务。");
            if (!workspaceState.isRequestCurrent(requestToken, requestParameters) ||
                    taskId !== uploadState.uploadTaskId) {
                return;
            }
            renderUploadTask(body.data);
            if (taskUploadStatusIsActive(body.data.status) &&
                    body.data.status !== "cancelling") {
                scheduleUploadTaskPoll(600);
            } else if (body.data.status === "cancelling") {
                scheduleUploadTaskPoll(600);
            }
        } catch (error) {
            if (!workspaceState.isRequestCurrent(requestToken, requestParameters) ||
                    isStaleDatasetRequest(error)) {
                return;
            }
            if (error.status === 404 || error.status === 410) {
                persistUploadTaskId(null);
                uploadPollFailure = { taskId: taskId, terminal: true };
                uploadState.uploadTaskView = Object.assign(
                    {}, taskView, { status: "expired" });
                renderTaskUploadState(uploadState.uploadTaskView);
            } else {
                uploadStatus((error.message || "暂时无法取消检查任务。") +
                    " 任务状态仍以服务端查询结果为准。", "error");
                scheduleUploadTaskPoll(1000);
            }
        } finally {
            uploadCancelInFlight = false;
            updateUploadControls();
        }
    }

    function clearUploadTaskState() {
        if (uploadPollTimer) {
            window.clearTimeout(uploadPollTimer);
        }
        workspaceState.invalidateRequest("uploadPoll");
        workspaceState.invalidateRequest("uploadCancel");
        workspaceState.invalidateRequest("uploadSubmit");
        uploadPollTimer = null;
        uploadPollFailure = null;
        uploadPollRetryCount = 0;
        uploadPollQueued = false;
        uploadState.uploadTaskId = null;
        uploadState.uploadTaskView = null;
        persistUploadTaskId(null);
        updateUploadControls();
    }

    async function closeUploadSession(context) {
        if (!context || !context.sessionId) {
            return;
        }
        var url = "/api/social-network/datasets/" +
            encodeURIComponent(context.datasetId) + "/sessions/" +
            encodeURIComponent(context.sessionId) + "?datasetVersion=" +
            encodeURIComponent(context.datasetVersion);
        var response = await fetch(url, {
            method: "DELETE",
            headers: { "Accept": "application/json" }
        });
        await readUploadResponse(response, "上传会话暂未关闭。");
    }

    function updateDatasetEntryState() {
        if (activeSocialNetworkTask === "data" && currentDatasetDetails) {
            renderDatasetPageDetails(currentDatasetDetails);
        }
    }

    function clearTopicTaskForDatasetChange() {
        workspaceState.invalidateRequest("topic");
        workspaceState.setCondition("topic", {
            draft: "",
            submittedTerm: null
        }, false);
        topicTaskResult = null;
        topicTaskIdentitySequence += 1;
        topicTaskIdentityPending = false;
        [
            "social-network-topic-detail-dialog",
            "social-network-topic-rules-dialog"
        ].forEach(function (id) {
            var dialog = document.getElementById(id);
            if (dialog && dialog.open) {
                dialog.close();
            }
        });
        var oldInput = document.getElementById("social-network-topic-query");
        if (oldInput) {
            oldInput.value = "";
        }
        var taskInput = document.getElementById(
            "social-network-topic-task-query");
        if (taskInput) {
            taskInput.value = "";
        }
        var moduleExport = document.getElementById(
            "social-network-module-export-dialog");
        if (activeModuleExportType === "topic" &&
                moduleExport && moduleExport.open) {
            moduleExport.close();
        }
    }

    function clearContextBoundViews() {
        clearTopicTaskForDatasetChange();
        var search = document.getElementById("social-network-user-results");
        if (search) {
            search.innerHTML = '<div class="social-network-empty">切换数据集后，请重新搜索用户。</div>';
        }
        var local = document.getElementById("social-network-local-view");
        if (local) {
            if (localNetworkChart) {
                localNetworkChart.dispose();
                localNetworkChart = null;
            }
            var chartElement = document.getElementById("social-network-local-chart");
            if (!localNetworkChart && chartElement && typeof echarts !== "undefined") {
                var chart = echarts.getInstanceByDom(chartElement);
                if (chart) {
                    chart.dispose();
                }
            }
            local.innerHTML = '<div class="social-network-empty">选择用户后查看当前数据集中的局部网络。</div>';
        }
        setText("social-network-local-status", "等待查询");
        setText("social-network-topic-status", "等待查询");
        [
            "social-network-topic-summary",
            "social-network-topic-tweets",
            "social-network-topic-users",
            "social-network-topic-network"
        ].forEach(function (id) {
            var element = document.getElementById(id);
            if (element) {
                element.innerHTML = '<div class="social-network-empty">切换数据集后重新查询。</div>';
            }
        });
        closeSocialUserDrawer(false);
        setText("social-network-user-drawer-status", "等待查询");
        document.querySelectorAll("[data-user-detail-panel]").forEach(function (panel) {
            panel.innerHTML =
                '<div class="social-network-empty">切换数据集后重新查询。</div>';
        });
        updateDatasetEntryState();
    }

    async function activateUploadPreview() {
        var taskView = uploadState.uploadTaskView;
        var preview = taskView && taskView.preview;
        if (!preview || taskView.status !== "ready" || uploadActivationInFlight) {
            return false;
        }
        if (String((preview.qualitySummary || {}).datasetStatus || "")
                .toLowerCase() === "unavailable") {
            uploadStatus("服务端质量状态为不可用，不能激活此数据集。", "error");
            return false;
        }
        uploadActivationInFlight = true;
        updateTaskUploadResultControls(taskView);
        uploadStatus("正在验证预览并切换当前数据…", "busy");
        var shellActive = document.body.classList.contains(
            "social-network-shell-active");
        var currentContext = readDatasetContext();
        var previous = currentContext;
        var next = {
            datasetId: preview.datasetId,
            datasetVersion: preview.datasetVersion,
            sessionId: preview.sessionId
        };
        try {
            try {
                workspaceContextApi.setDatasetContext(next);
                clearContextBoundViews();
                await loadShellDatasetSummary();
            } catch (error) {
                if (!sameDatasetContext(readDatasetContext(), next)) {
                    uploadStatus(
                        "激活期间当前数据已发生变化，保留了最新选择；预览仍可重试。",
                        "error");
                    return false;
                }
                workspaceContextApi.setDatasetContext(previous);
                clearContextBoundViews();
                await loadShellDatasetSummary().catch(function () {});
                uploadStatus("无法加载此预览数据，已恢复原分析数据。预览仍保留，可重试。",
                    "error");
                return false;
            }
            if (!sameDatasetContext(readDatasetContext(), next)) {
                uploadStatus(
                    "激活期间当前数据已发生变化，保留了最新选择；预览未覆盖当前数据。",
                    "error");
                return false;
            }
            activeUploadContext = next;
            updateDatasetEntryState();
            clearUploadTaskState();
            clearUploadSelection();
            if (shellActive) {
                window.location.hash = "#/social-network/data";
            } else {
                uploadStatus("当前工作台已切换到上传数据集。会话仅在当前页面有效。",
                    "ready");
            }
            if (previous && previous.sessionId &&
                    previous.sessionId !== next.sessionId) {
                closeUploadSession(previous).then(function () {
                    if (shellActive) {
                        setText("social-network-dataset-switch-status",
                            "当前数据已切换；旧临时会话已结束。");
                    }
                }).catch(function () {
                    if (shellActive) {
                        setText("social-network-dataset-switch-status",
                            "当前数据已切换；旧临时会话未能立即关闭，将由会话清理机制处理。");
                    } else {
                        uploadStatus(
                            "已切换新数据集；旧临时会话未能立即关闭，将由会话清理机制处理。",
                            "error");
                    }
                });
            } else if (shellActive) {
                setText("social-network-dataset-switch-status",
                    "上传数据已设为当前分析数据。");
            }
            return true;
        } finally {
            uploadActivationInFlight = false;
            if (uploadState.uploadTaskView === taskView) {
                updateTaskUploadResultControls(taskView);
            }
        }
    }

    async function editUploadSelection() {
        var taskView = uploadState.uploadTaskView;
        var preview = taskView && taskView.preview;
        var taskCard = document.querySelector(
            "#page-social-network-task .social-network-task-card");
        if (uploadActivationInFlight) {
            return;
        }
        clearUploadTaskState();
        if (preview && preview.sessionId &&
                (!activeUploadContext ||
                    preview.sessionId !== activeUploadContext.sessionId)) {
            try {
                await closeUploadSession({
                    datasetId: preview.datasetId,
                    datasetVersion: preview.datasetVersion,
                    sessionId: preview.sessionId
                });
            } catch (error) {
                uploadStatus("已返回文件选择；未激活的预览会话未能立即关闭。",
                    "error");
            }
        }
        updateDatasetEntryState();
        renderTaskUploadSelection(taskCard);
        setText("social-network-task-status", "文件选择阶段");
        setText("social-network-task-message",
            "文件与来源说明草稿已保留；重新提交后会创建新的检查任务。");
        var issue = uploadSelectionIssue();
        uploadStatus(issue || "文件选择已保留，可修改角色或重新上传。",
            issue ? "error" : "");
    }

    async function switchToBuiltinDataset() {
        var current = readDatasetContext();
        if (!current.sessionId) {
            return false;
        }
        if (uploadCheckIsActive()) {
            setText("social-network-dataset-switch-status",
                "上传检查正在进行；请先完成或取消检查，再切换数据。");
            return false;
        }
        var taskView = uploadState.uploadTaskView;
        var pendingPreview = taskView && taskView.preview;
        workspaceContextApi.resetDatasetContext();
        clearContextBoundViews();
        try {
            await loadShellDatasetSummary();
        } catch (error) {
            if (isStaleDatasetRequest(error)) {
                return false;
            }
            workspaceContextApi.setDatasetContext(current);
            clearContextBoundViews();
            await loadShellDatasetSummary().catch(function () {});
            uploadStatus("内置数据暂时无法加载，当前分析数据未切换。",
                "error");
            setText("social-network-dataset-switch-status",
                "内置数据暂时无法加载，当前分析数据未切换。");
            return false;
        }
        var sessionsToClose = [current];
        if (pendingPreview && pendingPreview.sessionId !== current.sessionId) {
            sessionsToClose.push({
                datasetId: pendingPreview.datasetId,
                datasetVersion: pendingPreview.datasetVersion,
                sessionId: pendingPreview.sessionId
            });
        }
        activeUploadContext = null;
        clearUploadTaskState();
        updateDatasetEntryState();
        var closeResults = await Promise.allSettled(
            sessionsToClose.filter(Boolean).map(closeUploadSession));
        uploadStatus(closeResults.some(function (result) {
            return result.status === "rejected";
        })
            ? "已切回内置数据；临时会话将在后续清理中关闭。"
            : "已切回内置数据，上传会话已结束。",
        closeResults.some(function (result) {
            return result.status === "rejected";
        }) ? "error" : "ready");
        setText("social-network-dataset-switch-status",
            closeResults.some(function (result) {
                return result.status === "rejected";
            })
                ? "已切回内置数据；临时会话未能立即关闭，将由会话清理机制处理。"
                : "已切回内置数据，临时会话已结束。");
        return true;
    }

    function formatDatasetPageCount(value) {
        return value === null || value === undefined
            ? "未提供"
            : formatNumber(value);
    }

    function renderDatasetPageDetails(details) {
        currentDatasetDetails = details;
        var context = readDatasetContext();
        var isBuiltin = !context.sessionId &&
            context.datasetId === BUILTIN_DATASET_CONTEXT.datasetId;

        setText("social-network-dataset-name", details.name || "未提供");
        setText("social-network-dataset-status", statusLabel(details.status));
        setText("social-network-dataset-source-summary",
            details.sourceDescription || "未提供");

        var notice = document.getElementById("social-network-dataset-notice");
        var notices = [];
        if (details.isTemporary) {
            notices.push("当前使用临时上传数据；刷新页面后不会恢复此分析会话。");
        }
        if (notice) {
            notice.textContent = notices.join(" ");
            notice.hidden = notices.length === 0;
        }

        setText("social-network-builtin-dataset-name",
            isBuiltin ? details.name : "智库社交网络数据集");
        setText("social-network-dataset-switch-description",
            isBuiltin
                ? "当前分析已使用内置数据。"
                : "切换后结束当前临时分析会话；需要时请重新上传。");
        var builtinCurrent = document.getElementById(
            "social-network-builtin-current"
        );
        var switchButton = document.getElementById(
            "social-network-dataset-switch-builtin"
        );
        var blocked = document.getElementById(
            "social-network-dataset-switch-blocked"
        );
        var checkActive = uploadCheckIsActive();
        if (builtinCurrent) {
            builtinCurrent.hidden = !isBuiltin;
        }
        if (switchButton) {
            switchButton.hidden = isBuiltin;
            switchButton.disabled = checkActive;
        }
        if (blocked) {
            blocked.hidden = isBuiltin || !checkActive;
        }

        var taskView = uploadState.uploadTaskView;
        var uploadStatePanel = document.getElementById(
            "social-network-dataset-upload-state"
        );
        var uploadMessage = document.getElementById(
            "social-network-dataset-upload-message"
        );
        var uploadLink = document.getElementById(
            "social-network-dataset-upload-link"
        );
        var uploadMessageText = "";
        var uploadLinkText = "";
        if (taskView) {
            var uploadLabels = {
                queued: ["上传检查已排队；当前分析数据尚未改变。", "继续检查"],
                processing: ["正在检查上传数据；当前分析数据尚未改变。", "继续检查"],
                cancelling: ["正在取消上传检查；当前分析数据尚未改变。", "查看检查状态"],
                ready: ["检查预览尚未激活；切回内置数据会一并结束该预览。", "查看预览"],
                failed: ["上传检查未通过；已选择的文件仍可修改。", "返回修改"],
                cancelled: ["上传检查已取消；已选择的文件仍保留。", "继续编辑"],
                expired: ["上传任务或预览已过期。", "重新选择文件"]
            };
            var uploadLabel = uploadLabels[taskView.status];
            if (uploadLabel) {
                uploadMessageText = uploadLabel[0];
                uploadLinkText = uploadLabel[1];
            }
        } else if (uploadFiles.length) {
            uploadMessageText = "已有 " + uploadFiles.length +
                " 个文件保留在当前页面会话中。";
            uploadLinkText = "继续编辑文件选择";
        }
        if (uploadStatePanel) {
            uploadStatePanel.hidden = !uploadMessageText;
        }
        if (uploadMessage) {
            uploadMessage.textContent = uploadMessageText;
        }
        if (uploadLink && uploadLinkText) {
            uploadLink.textContent = uploadLinkText;
        }

        var loading = document.getElementById("social-network-dataset-loading");
        var content = document.getElementById("social-network-dataset-content");
        var error = document.getElementById("social-network-dataset-error");
        if (loading) {
            loading.hidden = true;
        }
        if (error) {
            error.hidden = true;
        }
        if (content) {
            content.hidden = false;
        }
    }

    function isDataQualityTask(task) {
        return task === "data" || task === "quality";
    }

    function loadDatasetPage(scrollToReport) {
        initializeDatasetPageControls();
        var sequence = ++datasetPageRequestSequence;
        var reportSequence = ++qualityReportRequestSequence;
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        var loading = document.getElementById("social-network-dataset-loading");
        var content = document.getElementById("social-network-dataset-content");
        var errorPanel = document.getElementById("social-network-dataset-error");
        var reportMount = qualityReportMount();
        var reportExportButton = document.getElementById(
            "social-network-quality-report-export"
        );
        var overviewRefreshButton = document.getElementById(
            "social-network-overview-retry"
        );
        if (reportExportButton) {
            reportExportButton.hidden = true;
        }
        if (overviewRefreshButton) {
            overviewRefreshButton.hidden = true;
        }
        if (qualityReportContextKey !== contextKey) {
            qualityReportContextKey = contextKey;
            qualityReportDimension = "users";
            qualityReportWarningPage = 1;
            currentQualityReport = null;
        }
        if (loading) {
            loading.hidden = false;
            loading.textContent = "正在读取当前数据…";
        }
        if (content) {
            content.hidden = true;
        }
        if (errorPanel) {
            errorPanel.hidden = true;
        }
        if (reportMount) {
            reportMount.innerHTML =
                '<div class="social-network-quality-report-loading" role="status">' +
                "正在读取当前分析数据的质量摘要…</div>";
        }
        loadShellDatasetSummary().then(function (body) {
            var current = readDatasetContext();
            if (!isDataQualityTask(activeSocialNetworkTask) ||
                    sequence !== datasetPageRequestSequence ||
                    reportSequence !== qualityReportRequestSequence ||
                    !sameDatasetSelection(context, current)) {
                return;
            }
            qualityReportContextKey = datasetContextKey(current);
            var details = window.socialNetworkDatasetModel.fromResponse(
                body, current
            );
            renderDatasetPageDetails(details);
            currentQualityReport =
                socialNetworkQualityModel.reportFromResponse(body);
            renderQualityReportView(currentQualityReport, current, details);
            if (scrollToReport) {
                var section = document.getElementById(
                    "social-network-dataset-quality"
                );
                if (section) {
                    section.scrollIntoView({
                        block: "start",
                        behavior: "smooth"
                    });
                }
            }
        }).catch(function (requestError) {
            var current = readDatasetContext();
            if (isStaleDatasetRequest(requestError) ||
                    !isDataQualityTask(activeSocialNetworkTask) ||
                    sequence !== datasetPageRequestSequence ||
                    reportSequence !== qualityReportRequestSequence ||
                    !sameDatasetSelection(context, current)) {
                return;
            }
            if (loading) {
                loading.hidden = true;
            }
            if (errorPanel) {
                errorPanel.hidden = false;
            }
            currentQualityReport = null;
            if (reportMount) {
                reportMount.innerHTML =
                    '<div class="social-network-quality-report-error" role="alert">' +
                    '<p>暂时无法读取当前数据的质量报告。未提供或失败的摘要不会显示为零。</p>' +
                    '<button type="button" class="button button-secondary" ' +
                    'data-quality-report-retry>重试</button></div>';
                reportMount.querySelector("[data-quality-report-retry]")
                    .addEventListener("click", function () {
                        shellDatasetSummaryCache = null;
                        loadDatasetPage(scrollToReport);
                    });
            }
        });
    }

    function renderDatasetSourceDialog() {
        var details = currentDatasetDetails;
        if (!details) {
            return;
        }
        setText("social-network-source-dialog-description",
            details.sourceDescription || "未提供");
        setText("social-network-source-dialog-dataset-id",
            details.datasetId || "未提供");
        setText("social-network-source-dialog-time-range",
            details.timeRangeFrom || details.timeRangeTo
                ? (details.timeRangeFrom || "未提供") + " 至 " +
                    (details.timeRangeTo || "未提供")
                : "未提供");
        setText("social-network-source-dialog-file-count",
            details.fileCount === null
                ? "未提供"
                : formatNumber(details.fileCount) + " 个");
        var sessionRow = document.getElementById(
            "social-network-source-dialog-session-row"
        );
        if (sessionRow) {
            sessionRow.hidden = !details.sessionId;
        }
        setText("social-network-source-dialog-session-id",
            details.sessionId || "未提供");

        var list = document.getElementById("social-network-source-dialog-files");
        list.replaceChildren();
        if (!details.files || !details.files.length) {
            var empty = document.createElement("li");
            empty.textContent = "未提供文件明细";
            list.appendChild(empty);
            return;
        }
        var roleLabels = {
            users: "用户数据",
            relationships: "关注关系",
            tweets: "推文样本"
        };
        details.files.forEach(function (file) {
            var item = document.createElement("li");
            var name = document.createElement("strong");
            var metadata = document.createElement("span");
            name.textContent = file.logicalFileName || "未提供文件名";
            var role = file.role && roleLabels[file.role.toLowerCase()]
                ? roleLabels[file.role.toLowerCase()]
                : file.role || "未提供角色";
            var parts = [role];
            if (file.format) {
                parts.push(file.format);
            }
            if (file.encoding) {
                parts.push(file.encoding);
            }
            metadata.textContent = parts.join(" · ");
            item.appendChild(name);
            item.appendChild(metadata);
            list.appendChild(item);
        });
    }

    function closeDatasetDialog(dialogId) {
        var dialog = document.getElementById(dialogId);
        if (dialog && dialog.open) {
            dialog.close();
        }
    }

    function initializeDatasetPageControls() {
        if (datasetPageControlsBound) {
            return;
        }
        var sourceOpen = document.getElementById(
            "social-network-dataset-source-open"
        );
        var sourceDialog = document.getElementById(
            "social-network-dataset-source-dialog"
        );
        var sourceClose = document.getElementById(
            "social-network-dataset-source-close"
        );
        var sourceDone = document.getElementById(
            "social-network-dataset-source-done"
        );
        var switchOpen = document.getElementById(
            "social-network-dataset-switch-builtin"
        );
        var switchDialog = document.getElementById(
            "social-network-dataset-switch-dialog"
        );
        var switchConfirm = document.getElementById(
            "social-network-dataset-switch-confirm"
        );
        if (!sourceOpen || !sourceDialog || !switchOpen || !switchDialog) {
            return;
        }
        sourceOpen.addEventListener("click", function () {
            renderDatasetSourceDialog();
            sourceDialog.showModal();
        });
        [sourceClose, sourceDone].forEach(function (button) {
            button.addEventListener("click", function () {
                closeDatasetDialog("social-network-dataset-source-dialog");
            });
        });
        sourceDialog.addEventListener("close", function () {
            if (activeSocialNetworkTask === "data" && !sourceOpen.hidden) {
                sourceOpen.focus();
            }
        });

        switchOpen.addEventListener("click", function () {
            if (uploadCheckIsActive()) {
                renderDatasetPageDetails(currentDatasetDetails);
                return;
            }
            var taskView = uploadState.uploadTaskView;
            var pendingPreview = taskView && taskView.preview;
            var confirmation = "切换后将结束当前临时分析会话，不能再次直接切回该会话；需要时请重新上传。";
            if (pendingPreview && pendingPreview.sessionId &&
                    pendingPreview.sessionId !== readDatasetContext().sessionId) {
                confirmation += " 尚有未激活的检查预览，该预览会一并关闭。";
            }
            setText("social-network-dataset-switch-confirmation", confirmation);
            setText("social-network-dataset-switch-dialog-status", "");
            switchConfirm.disabled = false;
            switchDialog.showModal();
        });
        [
            document.getElementById("social-network-dataset-switch-close"),
            document.getElementById("social-network-dataset-switch-cancel")
        ].forEach(function (button) {
            button.addEventListener("click", function () {
                closeDatasetDialog("social-network-dataset-switch-dialog");
            });
        });
        switchDialog.addEventListener("close", function () {
            if (activeSocialNetworkTask === "data" &&
                    !switchOpen.hidden && !switchOpen.disabled) {
                switchOpen.focus();
            }
        });
        switchConfirm.addEventListener("click", async function () {
            switchConfirm.disabled = true;
            setText("social-network-dataset-switch-dialog-status",
                "正在切换到内置数据…");
            var switched = await switchToBuiltinDataset();
            if (switched) {
                closeDatasetDialog("social-network-dataset-switch-dialog");
                var heading = document.getElementById("social-network-dataset-name");
                if (heading) {
                    heading.focus();
                }
            } else {
                switchConfirm.disabled = false;
                setText("social-network-dataset-switch-dialog-status",
                    document.getElementById(
                        "social-network-dataset-switch-status"
                    ).textContent || "切换未完成，当前数据保持不变。");
            }
        });
        document.getElementById("social-network-dataset-retry")
            .addEventListener("click", loadDatasetPage);
        document.getElementById("social-network-dataset-name")
            .setAttribute("tabindex", "-1");
        datasetPageControlsBound = true;
    }

    function datasetContextKey(context) {
        return JSON.stringify([
            context.datasetId,
            context.datasetVersion || "",
            context.sessionId || ""
        ]);
    }

    function sameDatasetSelection(first, second) {
        return first.datasetId === second.datasetId &&
            first.sessionId === second.sessionId;
    }

    function loadShellDatasetSummary() {
        var context = readDatasetContext();
        var key = datasetContextKey(context);
        var cachedContext = shellDatasetSummaryCache &&
            shellDatasetSummaryCache.context;
        if (shellDatasetSummaryCache &&
                (shellDatasetSummaryCache.key === key ||
                    (sameDatasetSelection(cachedContext, context) &&
                        (!context.datasetVersion ||
                            context.datasetVersion ===
                                shellDatasetSummaryCache.version)))) {
            var cachedVersion = shellDatasetSummaryCache.version;
            if (!context.datasetVersion && !context.sessionId && cachedVersion) {
                context.datasetVersion = cachedVersion;
                workspaceContextApi.setDatasetContext(context);
            }
            return Promise.resolve(shellDatasetSummaryCache.body);
        }
        if (shellDatasetSummaryLookup &&
                shellDatasetSummaryLookup.key === key) {
            return shellDatasetSummaryLookup.promise;
        }

        var lookup = { key: key, context: context, promise: null };
        shellDatasetSummaryLookup = lookup;
        lookup.promise = request(buildDatasetUrl("")).then(function (body) {
            var current = readDatasetContext();
            var data = body.data || {};
            var version = data.version ||
                (body.meta && body.meta.datasetVersion);
            if (!sameDatasetSelection(lookup.context, current) ||
                    (lookup.context.datasetVersion &&
                        current.datasetVersion !== lookup.context.datasetVersion) ||
                    (lookup.context.datasetVersion && version &&
                        String(version) !== lookup.context.datasetVersion)) {
                throw staleDatasetRequestError();
            }

            var cacheContext = {
                datasetId: current.datasetId,
                datasetVersion: current.datasetVersion || version || null,
                sessionId: current.sessionId
            };
            shellDatasetSummaryCache = {
                key: datasetContextKey(cacheContext),
                context: cacheContext,
                version: version ? String(version) : null,
                body: body
            };
            if (!current.datasetVersion && !current.sessionId && version) {
                current.datasetVersion = String(version);
                workspaceContextApi.setDatasetContext(current);
            }
            return body;
        }).finally(function () {
            if (shellDatasetSummaryLookup === lookup) {
                shellDatasetSummaryLookup = null;
            }
        });
        return lookup.promise;
    }

    function loadShellDatasetVersion() {
        var context = readDatasetContext();
        if (context.datasetVersion || context.sessionId) {
            return Promise.resolve();
        }
        return loadShellDatasetSummary().catch(function () {
            // Keep the version explicitly unavailable when identity lookup fails.
        });
    }

    function overviewStateLabel(state) {
        var labels = {
            available: "可用",
            empty: "暂无样本",
            limited: "部分可用",
            unavailable: "暂不可用",
            failed: "加载失败",
            loading: "正在读取"
        };
        return labels[state] || "状态未提供";
    }

    function setOverviewReadiness(id, state, detail) {
        var stateElement = document.getElementById(
            "social-network-overview-" + id + "-state"
        );
        var detailElement = document.getElementById(
            "social-network-overview-" + id + "-detail"
        );
        var card = stateElement && stateElement.closest(
            ".social-network-overview-readiness-item"
        );
        if (stateElement) {
            stateElement.textContent = overviewStateLabel(state);
        }
        if (detailElement) {
            detailElement.textContent = detail;
        }
        if (card) {
            card.dataset.overviewState = state || "unavailable";
        }
    }

    function setOverviewMetric(id, value, formatter) {
        var element = document.getElementById(
            "social-network-overview-" + id
        );
        if (element) {
            element.textContent = value === null || value === undefined
                ? "未提供"
                : formatter(value);
            element.classList.toggle(
                "is-unavailable",
                value === null || value === undefined
            );
        }
    }

    function renderOverviewResults(networkBody, interactionBody, qualityBody) {
        var summary = socialNetworkOverviewModel.overviewFromResponses(
            networkBody,
            interactionBody,
            qualityBody
        );
        var network = summary.network;
        var interaction = summary.interaction;
        var quality = summary.quality;

        if (network) {
            var networkState = network.state || "unavailable";
            var networkDetail = networkState === "available"
                ? "结构指标已生成，可查看连通性与关系分布。"
                : networkState === "empty"
                    ? "当前完整规范化网络中没有可分析节点。"
                    : "服务端未提供网络可用状态。";
            setOverviewReadiness(
                "network",
                networkState,
                networkDetail
            );
            setText(
                "social-network-overview-network-scope",
                network.scope || "范围：全量规范化关系网络"
            );
            setOverviewMetric(
                "isolated-node-count",
                network.isolatedNodeCount,
                formatNumber
            );
            setOverviewMetric(
                "component-count",
                network.componentCount,
                formatNumber
            );
            setOverviewMetric("density", network.density, formatDensity);
            var largestComponent = network.largestComponentSize === null
                ? null
                : formatNumber(network.largestComponentSize) + " 个节点 · " +
                    (network.largestComponentRatio === null
                        ? "占比未提供"
                        : formatPercent(network.largestComponentRatio));
            setOverviewMetric(
                "largest-component",
                largestComponent,
                function (value) {
                    return value;
                }
            );
        } else {
            setOverviewReadiness(
                "network",
                "unavailable",
                "网络结构摘要暂时无法读取，可重试或进入关系分析。"
            );
            setText(
                "social-network-overview-network-scope",
                "网络结构摘要读取失败；不会将缺失结果显示为零。"
            );
            [
                "isolated-node-count",
                "component-count",
                "density",
                "largest-component"
            ].forEach(function (id) {
                setOverviewMetric(id, null, formatNumber);
            });
        }

        if (interaction) {
            var interactionState = interaction.state ||
                "unavailable";
            var sampleCount = interaction.sampleCount;
            var interactionDetail = interactionState === "available"
                ? (sampleCount === null
                    ? "服务端未提供互动分析样本数。"
                    : "当前纳入分析的去重推文样本 " +
                        formatNumber(sampleCount) + " 条。")
                : interactionState === "empty"
                    ? "当前没有可用于互动分析的推文样本。"
                    : "服务端未提供互动分析状态。";
            setOverviewReadiness(
                "interaction",
                interactionState,
                interactionDetail
            );
            setText(
                "social-network-overview-interaction-scope",
                interaction.scope ||
                    "范围：去重且纳入分析的推文样本"
            );
            [
                ["like-total", interaction.like],
                ["reply-total", interaction.reply],
                ["repost-total", interaction.repost]
            ].forEach(function (entry) {
                setOverviewMetric(
                    entry[0],
                    entry[1] && entry[1].total,
                    formatNumber
                );
            });
        } else {
            setOverviewReadiness(
                "interaction",
                "unavailable",
                "互动摘要暂时无法读取，可重试或进入互动分析。"
            );
            setText(
                "social-network-overview-interaction-scope",
                "互动摘要读取失败；不会将缺失结果显示为零。"
            );
            ["like-total", "reply-total", "repost-total"].forEach(
                function (id) {
                    setOverviewMetric(id, null, formatNumber);
                }
            );
        }

        if (quality) {
            var qualityState = quality.state || "unavailable";
            var qualityDetail;
            if (quality.issueCount === null &&
                    quality.capabilityImpactCount === null) {
                qualityDetail = "服务端未提供质量问题与能力影响计数。";
            } else if (quality.issueCount === 0 &&
                    quality.capabilityImpactCount === 0) {
                qualityDetail = "未报告质量问题或受影响的分析能力。";
            } else {
                qualityDetail = (quality.issueCount === null
                    ? "质量问题数未提供"
                    : formatNumber(quality.issueCount) + " 个质量问题项") +
                    " · " + (quality.capabilityImpactCount === null
                        ? "受影响能力数未提供"
                        : formatNumber(quality.capabilityImpactCount) +
                            " 项能力受影响");
            }
            setOverviewReadiness(
                "quality",
                qualityState,
                qualityDetail
            );
        } else {
            setOverviewReadiness(
                "quality",
                "unavailable",
                "质量摘要暂时无法读取，可重试或查看质量报告。"
            );
        }
    }

    function loadOverviewSummary() {
        var overview = document.getElementById(
            "social-network-overview-home"
        );
        if (!overview || activeSocialNetworkTask !== "overview") {
            return;
        }
        if (!overviewControlsBound) {
            document.getElementById(
                "social-network-overview-retry"
            ).addEventListener("click", loadOverviewSummary);
            overviewControlsBound = true;
        }
        var sequence = ++overviewRequestSequence;
        var context = readDatasetContext();
        ["network", "interaction", "quality"].forEach(function (id) {
            setOverviewReadiness(id, "loading", "正在读取当前数据摘要…");
        });
        setText(
            "social-network-overview-network-scope",
            "正在读取完整规范化网络摘要…"
        );
        setText(
            "social-network-overview-interaction-scope",
            "正在读取纳入分析的互动样本…"
        );
        ["isolated-node-count", "component-count", "density",
            "largest-component",
            "like-total", "reply-total", "repost-total"].forEach(
            function (id) {
                setOverviewMetric(id, null, formatNumber);
            }
        );
        Promise.allSettled([
            request(buildDatasetUrl("network/summary", null, context)),
            request(buildDatasetUrl("interactions/summary", null, context)),
            request(buildDatasetUrl("quality/summary", null, context))
        ]).then(function (results) {
            var current = readDatasetContext();
            if (sequence !== overviewRequestSequence ||
                    activeSocialNetworkTask !== "overview" ||
                    !sameDatasetContext(context, current)) {
                return;
            }
            renderOverviewResults(
                results[0].status === "fulfilled"
                    ? results[0].value
                    : null,
                results[1].status === "fulfilled"
                    ? results[1].value
                    : null,
                results[2].status === "fulfilled"
                    ? results[2].value
                    : null
            );
        });
    }

    function qualityReportHelpText(key) {
        var descriptions = {
            file_count: "参与本次数据质量检查的文件个数。",
            quality_issue_count:
                "本次检查生成的问题提示项数，不等于问题类型数或受影响原始记录数。",
            capability_impact_count:
                "因数据质量受到限制的分析功能种类数，不是问题数量。",
            user_id_coverage:
                "通过必要字段检查、可进入后续分析的用户数 ÷ 原始用户记录数；不代表资料完整。",
            relationship_inclusion_rate:
                "检查后保留的有效关注关系数 ÷ 关注关系候选记录数；不是用户之间的关注比例。",
            tweet_author_link_rate:
                "作者能匹配到用户资料的推文数 ÷ 原始推文记录数。",
            valid_text_rate:
                "规范化后含非空文本的推文数 ÷ 原始推文记录数；不代表文本内容适合主题分析。"
        };
        return descriptions[key] || null;
    }

    function qualityReportMetricLabelMarkup(entry) {
        var label = escapeHtml(entry.label);
        var description = qualityReportHelpText(entry.key);
        if (!description) {
            return label;
        }
        var tooltipId = "social-network-quality-help-" +
            entry.key.replace(/[^a-zA-Z0-9_-]/g, "-");
        return label + '<span class="social-network-quality-help">' +
            '<button class="social-network-quality-help-trigger" type="button" ' +
            'aria-label="' + label + '说明" aria-describedby="' +
            tooltipId + '">i</button>' +
            '<span class="social-network-scope-term-tooltip" id="' +
            tooltipId + '" role="tooltip"><strong>指标口径</strong>：<span>' +
            escapeHtml(description) + '</span></span></span>';
    }

    function positionQualityReportTooltip(trigger) {
        var tooltip = trigger.nextElementSibling;
        positionNetworkStructureTooltip(
            tooltip,
            trigger.getBoundingClientRect()
        );
    }

    function bindQualityReportTooltipPositioning(mount) {
        if (mount.getAttribute("data-quality-help-bound") === "true") {
            return;
        }
        mount.setAttribute("data-quality-help-bound", "true");
        mount.addEventListener("pointerover", function (event) {
            var trigger = event.target.closest(
                ".social-network-quality-help-trigger"
            );
            if (trigger && mount.contains(trigger)) {
                positionQualityReportTooltip(trigger);
            }
        });
        mount.addEventListener("focusin", function (event) {
            var trigger = event.target.closest(
                ".social-network-quality-help-trigger"
            );
            if (trigger && mount.contains(trigger)) {
                positionQualityReportTooltip(trigger);
            }
        });
        var repositionVisibleTooltips = function () {
            mount.querySelectorAll(
                ".social-network-quality-help-trigger"
            ).forEach(function (trigger) {
                if (trigger.matches(":hover") ||
                        trigger === document.activeElement) {
                    positionQualityReportTooltip(trigger);
                }
            });
        };
        window.addEventListener("resize", repositionVisibleTooltips);
        window.addEventListener("scroll", repositionVisibleTooltips, true);
    }

    function renderQualityReportSummaryEntries(quality, details) {
        var entries = socialNetworkQualityModel.summaryEntries(quality);
        var displayedDatasetCounts = {
            user_record_count: details && details.userCount,
            relationship_candidate_count: details &&
                details.relationshipCandidateCount,
            tweet_record_count: details && details.tweetRecordCount
        };
        entries = entries.filter(function (entry) {
            var displayedValue = displayedDatasetCounts[entry.key];
            if (displayedValue === null || displayedValue === undefined ||
                    entry.value === null || entry.value === undefined) {
                return true;
            }
            var displayedNumber = Number(displayedValue);
            var reportNumber = Number(entry.value);
            return !Number.isFinite(displayedNumber) ||
                !Number.isFinite(reportNumber) ||
                displayedNumber !== reportNumber;
        });
        var displayedEntries = [
            {
                label: "用户数",
                value: details && details.userCount,
                note: details && details.userCountBasis
            },
            {
                label: "关注关系记录",
                value: details && details.relationshipCandidateCount,
                note: "候选记录，不等于有效有向边"
            },
            {
                label: "推文记录",
                value: details && details.tweetRecordCount,
                note: "当前数据记录，不等于互动分析的有效样本"
            }
        ];
        var dataScaleKeys = [
            "user_record_count",
            "relationship_record_count",
            "relationship_candidate_count",
            "tweet_record_count"
        ];
        var qualitySummaryKeys = [
            "quality_issue_count",
            "capability_impact_count",
            "file_count"
        ];
        var dataScaleEntries = entries.filter(function (entry) {
            return dataScaleKeys.indexOf(entry.key) !== -1;
        });
        var qualityEntries = entries.filter(function (entry) {
            return qualitySummaryKeys.indexOf(entry.key) !== -1;
        });
        var assignedKeys = dataScaleEntries.concat(qualityEntries).map(
            function (entry) {
                return entry.key;
            }
        );
        var otherEntries = entries.filter(function (entry) {
            return assignedKeys.indexOf(entry.key) === -1;
        });
        qualityEntries = qualityEntries.concat(otherEntries);

        function entryMarkup(entry) {
            return '<div><dt>' + qualityReportMetricLabelMarkup(entry) +
                '</dt><dd>' +
                escapeHtml(entry.value === null || entry.value === undefined
                    ? "未提供"
                    : formatNumber(entry.value)) + '</dd>' +
                (!entry.known
                    ? '<small>服务端字段：' +
                        escapeHtml(entry.key) + '</small>'
                    : "") + '</div>';
        }

        function countMarkup(entry) {
            return '<div><dt>' + escapeHtml(entry.label) + '</dt><dd>' +
                escapeHtml(formatDatasetPageCount(entry.value)) + '</dd>' +
                (entry.note
                    ? '<small>' + escapeHtml(entry.note) + '</small>'
                    : "") + '</div>';
        }

        var dataScaleMarkup = displayedEntries.map(countMarkup).join("") +
            dataScaleEntries.map(entryMarkup).join("");
        var qualityMarkup = qualityEntries.length
            ? qualityEntries.map(entryMarkup).join("")
            : null;
        return '<section class="social-network-quality-report-block">' +
            '<h3>数据规模</h3><dl class="social-network-quality-report-summary">' +
            dataScaleMarkup + '</dl></section>' +
            '<section class="social-network-quality-report-block">' +
            '<h3>质量概况</h3>' +
            (qualityMarkup
                ? '<dl class="social-network-quality-report-summary">' +
                    qualityMarkup + '</dl>'
                : '<p class="social-network-quality-report-empty">' +
                    "服务端未提供额外质量汇总计数。" + '</p>') +
            '</section>';
    }

    function renderQualityReportCoverage(quality) {
        var entries = socialNetworkQualityModel.coverageEntries(quality);
        if (!entries.length) {
            return '<p class="social-network-quality-report-empty">' +
                "服务端未提供覆盖率摘要。</p>";
        }
        return '<dl class="social-network-quality-report-coverage">' +
            entries.map(function (entry) {
                var value = typeof entry.value === "number" &&
                    Number.isFinite(entry.value)
                    ? formatPercent(entry.value)
                    : "未提供";
                return '<div><dt>' + qualityReportMetricLabelMarkup(entry) +
                    '</dt><dd>' +
                    escapeHtml(value) + '</dd>' +
                    (!entry.known
                        ? '<small>服务端字段：' + escapeHtml(entry.key) + '</small>'
                        : "") + '</div>';
            }).join("") + '</dl>';
    }

    function renderQualityWarningList(report, dimensionId) {
        var allWarnings = report.warnings;
        var warnings = socialNetworkQualityModel.warningsForDimension(
            allWarnings, dimensionId
        );
        var page = socialNetworkQualityModel.paginateWarnings(
            warnings, qualityReportWarningPage, 10
        );
        qualityReportWarningPage = page.page;
        var content;
        if (!warnings.length) {
            content = '<p class="social-network-quality-report-empty">' +
                "当前响应在此作用范围下没有聚合 warning/info 项。";
            if (dimensionId === "other") {
                content += "文件级及未识别作用范围的提示归入此分类。";
            }
            content += "这不表示所有原始记录均无错误。</p>";
        } else {
            content = '<p class="social-network-quality-report-note">' +
                "当前数据摘要响应返回 " + formatNumber(allWarnings.length) +
                " 条聚合 warnings；本分类匹配 " +
                formatNumber(page.returnedCount) + " 条，当前显示 " +
                formatNumber(page.start) + "–" + formatNumber(page.end) +
                "。以下是已返回聚合项的本地浏览，不代表服务端全量分页或逐条定位。</p>" +
                '<ul class="social-network-quality-report-warnings">' +
                page.items.map(function (warning) {
                    var code = warning.code || "质量提示";
                    var message = warning.message || "服务端未提供说明。";
                    var count = warning.count === null ||
                        warning.count === undefined
                        ? "未提供"
                        : formatNumber(warning.count);
                    var scope = warning.affectedScope || "未提供";
                    return '<li><div class="social-network-quality-report-warning-head">' +
                        '<strong>' + escapeHtml(code) + '</strong><span>' +
                        escapeHtml(socialNetworkQualityModel.severityLabel(
                            warning.severity)) + '</span></div><p>' +
                        escapeHtml(message) + '</p><small>作用范围：' +
                        escapeHtml(scope) + ' · 服务端聚合计数：' +
                        escapeHtml(count) + '</small></li>';
                }).join("") + '</ul>';
        }
        if (page.pageCount > 1) {
            content += '<div class="social-network-quality-report-pagination" ' +
                'aria-label="已返回聚合提示的本地分页">' +
                '<button type="button" data-quality-page="' +
                (page.page - 1) + '"' +
                (page.page <= 1 ? " disabled" : "") +
                '>上一页</button><span>第 ' + page.page + ' / ' +
                page.pageCount + ' 页</span><button type="button" ' +
                'data-quality-page="' + (page.page + 1) + '"' +
                (page.page >= page.pageCount ? " disabled" : "") +
                '>下一页</button></div>';
        }
        return content;
    }

    function renderQualityReportView(report, context, details) {
        var mount = document.getElementById("social-network-quality-report");
        if (!mount) {
            return;
        }
        var quality = report.quality || {};
        var categories = socialNetworkQualityModel.categories();
        var selected = categories.some(function (item) {
            return item.id === qualityReportDimension;
        }) ? qualityReportDimension : "users";
        qualityReportDimension = selected;
        var selectedCategory = categories.find(function (item) {
            return item.id === selected;
        });
        var metricEntries = socialNetworkQualityModel.metricEntries(
            quality, selected
        );
        var categoryContent = selected === "other"
            ? '<p class="social-network-quality-report-note">' +
                "此分类汇集文件级或服务端未识别作用范围的聚合提示；不包含上传预览中的逐行定位。</p>"
            : metricEntries.length
                ? '<dl class="social-network-quality-report-metrics">' +
                    metricEntries.map(function (entry) {
                        var value = entry.value === null ||
                            entry.value === undefined
                            ? "未提供"
                            : typeof entry.value === "number"
                                ? formatNumber(entry.value)
                                : String(entry.value);
                        return '<div><dt>' + escapeHtml(entry.label) +
                            '</dt><dd>' + escapeHtml(value) + '</dd>' +
                            (!entry.known
                                ? '<small>服务端字段：' +
                                    escapeHtml(entry.key) + '</small>'
                                : "") + '</div>';
                    }).join("") + '</dl>'
                : '<p class="social-network-quality-report-empty">' +
                    "服务端未返回此维度的质量计数。</p>";
        categoryContent += '<section class="social-network-quality-report-warning-section" ' +
            'aria-labelledby="social-network-quality-warning-title">' +
            '<h3 id="social-network-quality-warning-title">' +
            escapeHtml(selectedCategory.label) + "聚合提示</h3>" +
            renderQualityWarningList(report, selected) + '</section>';

        mount.innerHTML =
            '<div class="social-network-quality-report-overview">' +
            renderQualityReportSummaryEntries(quality, details) +
            '<section class="social-network-quality-report-block" ' +
            'aria-labelledby="social-network-quality-coverage-title">' +
            '<h3 id="social-network-quality-coverage-title">覆盖摘要</h3>' +
            renderQualityReportCoverage(quality) + '</section></div>' +
            '<div class="social-network-quality-report-dimensions" ' +
            'role="group" aria-label="质量报告分类">' +
            categories.map(function (item) {
                return '<button type="button" data-quality-dimension="' +
                    escapeHtml(item.id) + '" aria-pressed="' +
                    (item.id === selected ? "true" : "false") + '">' +
                    escapeHtml(item.label) + '</button>';
            }).join("") + '</div>' +
            '<section class="social-network-quality-report-block" ' +
            'aria-labelledby="social-network-quality-dimension-title">' +
            '<h3 id="social-network-quality-dimension-title">' +
            escapeHtml(selectedCategory.label) + "质量统计</h3>" +
            '<p class="social-network-quality-report-note">' +
            "仅展示服务端当前响应中的计数；零值保留，未提供的字段不推断。未知字段按原始键名显示。</p>" +
            categoryContent + '</section>';

        bindQualityReportTooltipPositioning(mount);
        var exportButton = document.getElementById(
            "social-network-quality-report-export"
        );
        var refreshButton = document.getElementById(
            "social-network-overview-retry"
        );
        initializeModuleExportControls();
        if (exportButton) {
            exportButton.hidden = false;
            exportButton.onclick = function () {
                openModuleExportDialog("quality", exportButton);
            };
        }
        if (refreshButton) {
            refreshButton.hidden = false;
            refreshButton.onclick = loadDatasetPage;
        }
        mount.querySelectorAll("[data-quality-dimension]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    qualityReportDimension = button.getAttribute(
                        "data-quality-dimension"
                    );
                    qualityReportWarningPage = 1;
                    renderQualityReportView(report, context, details);
                    var selectedButton = mount.querySelector(
                        '[data-quality-dimension="' +
                        qualityReportDimension + '"]'
                    );
                    if (selectedButton) {
                        selectedButton.focus();
                    }
                });
            });
        mount.querySelectorAll("[data-quality-page]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    qualityReportWarningPage = Number(button.getAttribute(
                        "data-quality-page"
                    ));
                    renderQualityReportView(report, context, details);
                    var pageButton = mount.querySelector(
                        '[data-quality-page="' +
                        qualityReportWarningPage + '"]'
                    );
                    if (pageButton) {
                        pageButton.focus();
                    }
                });
            });
    }

    function qualityReportMount() {
        return document.getElementById("social-network-quality-report");
    }

    function renderNetworkStructureMetricValue(summary, entry) {
        if (entry.key === "largestWeaklyConnectedComponentRatio") {
            var component = networkStructureModel.largestComponent(summary);
            var size = component.sizeState === "value"
                ? formatNumber(component.size)
                : component.sizeState === "unavailable"
                    ? "不可计算"
                    : "未提供";
            var total = component.totalState === "value"
                ? formatNumber(component.total)
                : component.totalState === "unavailable"
                    ? "不可计算"
                    : "未提供";
            var ratio = component.ratioState === "value"
                ? formatPercent(component.ratio)
                : component.ratioState === "unavailable"
                    ? "占比不可计算"
                    : "占比未提供";
            return size + " / " + total + " 个节点 · " + ratio;
        }
        if (entry.state === "not-provided") {
            return "未提供";
        }
        if (entry.state === "unavailable") {
            return "不可计算";
        }
        if (entry.format === "count") {
            return formatNumber(entry.value);
        }
        if (entry.format === "ratio") {
            return formatPercent(entry.value);
        }
        if (entry.format === "density") {
            return formatDensity(entry.value);
        }
        return formatDecimal(entry.value);
    }

    function renderNetworkStructureCount(data, key) {
        var state = networkStructureModel.valueState(data, key);
        if (state === "not-provided") {
            return "未提供";
        }
        if (state === "unavailable") {
            return "不可计算";
        }
        return formatNumber(data[key]);
    }

    function networkStructureTerm(id, label, explanation) {
        var tooltipId = "social-network-scope-term-" + id;
        return '<span class="social-network-scope-term">' +
            '<button class="social-network-scope-term-trigger" type="button" ' +
            'data-network-scope-term aria-label="解释：' +
            escapeHtml(label) + '" aria-describedby="' + tooltipId +
            '" aria-expanded="false">i</button>' +
            '<span class="social-network-scope-term-tooltip" id="' +
            tooltipId + '" role="tooltip"><strong>' +
            escapeHtml(label) + '</strong><span>：' +
            escapeHtml(explanation) + '</span></span></span>';
    }

    function positionNetworkStructureTooltip(tooltip, anchorRect) {
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
        var top = above >= margin
            ? above
            : below <= maxTop
                ? below
                : Math.min(maxTop, Math.max(margin, above));
        tooltip.style.left = Math.round(left) + "px";
        tooltip.style.top = Math.round(top) + "px";
    }

    function repositionVisibleNetworkStructureTooltips() {
        document.querySelectorAll(
            ".social-network-scope-term-trigger:hover, " +
            ".social-network-scope-term-trigger:focus, " +
            ".social-network-scope-term.is-open " +
            ".social-network-scope-term-trigger"
        ).forEach(function (trigger) {
            positionNetworkStructureTooltip(
                trigger.nextElementSibling,
                trigger.getBoundingClientRect()
            );
        });
        document.querySelectorAll(
            ".social-network-network-structure-core-bar-hit-area:hover, " +
            ".social-network-network-structure-core-bar-hit-area:focus"
        ).forEach(function (bar) {
            var tooltip = bar.closest(
                ".social-network-network-structure-core-chart-frame"
            );
            if (tooltip) {
                positionNetworkStructureTooltip(
                    tooltip.querySelector("[data-kcore-chart-tooltip]"),
                    bar.getBoundingClientRect()
                );
            }
        });
    }

    function renderNetworkStructureScope(summary, context, includeExternal) {
        var data = summary.data || {};
        var impacts = networkStructureModel.qualityImpactItems(summary);
        var qualityStatus = networkStructureModel.qualityStatusLabel(
            summary.qualityStatus
        );
        var networkStatus = networkStructureModel.networkStateLabel(data.state);

        return '<details class="social-network-network-structure-details">' +
            '<summary><span><strong>数据范围与计算口径</strong>' +
            '<small>数据版本、账号范围和关系方向</small></span>' +
            '<span class="social-network-network-structure-state">' +
            '网络：' + escapeHtml(networkStatus) + ' · 数据质量：' +
            escapeHtml(qualityStatus) + '</span></summary>' +
            '<div class="social-network-network-structure-details-body">' +
            '<div class="social-network-network-structure-scope-grid">' +
            '<section><h4>纳入哪些账号</h4><p>完整范围包括有效账号资料、' +
            '有效关系中出现的资料外账号；没有关注关系的有效资料账号也保留。' +
            '外部节点只表示缺少对应账号资料，不代表账号无效。</p></section>' +
            '<section><h4>关注关系如何处理</h4><p>箭头 A → B 表示 A 关注 B。' +
            '重复关系合并，自关注和无效关系不进入有效关系集合。当前是否纳入外部账号：' +
            escapeHtml(includeExternal) + '。</p></section>' +
            '<section><h4>各项指标如何计算</h4><p>关注数、被关注数、网络密度、' +
            '互惠率和网络位置估计 ' +
            networkStructureTerm(
                "pagerank",
                "PageRank",
                "根据当前有方向的关注关系计算网络中的相对结构位置，不等同于现实影响力。"
            ) +
            '保留关注方向。连接组统计会忽略方向 ' +
            networkStructureTerm(
                "weak-components",
                "弱连通分量",
                "暂时忽略关注箭头后，能沿关系路径连在一起的一组账号；没有关系的账号各自算一组。"
            ) +
            '。聚类、路径、核心层级和介数会忽略方向，并把互相关注合并为一条关系 ' +
            networkStructureTerm(
                "undirected-projection",
                "无向无权简单投影",
                "不区分关系方向、合并互相关注并去重后得到的网络；不表示真实传播路径。"
            ) +
            '。</p></section></div>' +
            '<div class="social-network-network-structure-versions" ' +
            'aria-label="当前数据与算法版本">' +
            '<span><b>数据集</b><code>' +
            escapeHtml(summary.datasetId || context.datasetId || "未提供") +
            '</code></span><span><b>数据版本</b><code>' +
            escapeHtml(summary.datasetVersion ||
                context.datasetVersion || "未提供") +
            '</code></span><span><b>整理规则</b><code>' +
            escapeHtml(summary.normalizationVersion || "未提供") +
            '</code></span><span><b>网络指标</b><code>' +
            escapeHtml(summary.metricVersion || "未提供") +
            '</code></span><span><b>PageRank</b><code>' +
            escapeHtml(summary.pagerankVersion || "未提供") +
            '</code></span></div>' +
            '<section class="social-network-network-structure-quality" ' +
            'aria-labelledby="social-network-network-structure-quality-title">' +
            '<div><h4 id="social-network-network-structure-quality-title">' +
            '数据质量会影响哪些结果</h4><span>数据状态：' +
            escapeHtml(qualityStatus) + '</span></div>' +
            '<ul>' + impacts.map(function (item) {
                return '<li><strong>' + escapeHtml(item.label) +
                    '</strong><p>' + escapeHtml(item.text) + '</p></li>';
            }).join("") + '</ul>' +
            '<p class="social-network-network-structure-quality-note">' +
            '重复关系合并和自关注关系排除属于固定计算规则，计数已反映在上方有效关系数中；推文、互动和文本质量提示不用于解释本页网络指标。</p>' +
            '<a href="#/social-network/data/quality">查看完整数据质量报告</a>' +
            '</section></div></details>';
    }

    function bindNetworkStructureScopeTerms(mount) {
        if (mount.getAttribute("data-scope-term-controls") === "bound") {
            return;
        }
        mount.setAttribute("data-scope-term-controls", "bound");
        if (!networkStructureTooltipPositioningBound) {
            networkStructureTooltipPositioningBound = true;
            window.addEventListener(
                "resize",
                repositionVisibleNetworkStructureTooltips
            );
            window.addEventListener(
                "scroll",
                repositionVisibleNetworkStructureTooltips,
                true
            );
        }
        var positionTriggerTooltip = function (event) {
            var trigger = event.target.closest(
                "[data-network-scope-term]"
            );
            if (trigger) {
                positionNetworkStructureTooltip(
                    trigger.nextElementSibling,
                    trigger.getBoundingClientRect()
                );
            }
        };
        mount.addEventListener("mouseover", positionTriggerTooltip);
        mount.addEventListener("focusin", positionTriggerTooltip);
        mount.addEventListener("click", function (event) {
            var trigger = event.target.closest("[data-network-scope-term]");
            mount.querySelectorAll("[data-network-scope-term]")
                .forEach(function (item) {
                    if (item !== trigger) {
                        item.setAttribute("aria-expanded", "false");
                        item.parentElement.classList.remove("is-open");
                    }
                });
            if (!trigger) {
                return;
            }
            var expanded = trigger.getAttribute("aria-expanded") === "true";
            trigger.setAttribute("aria-expanded", String(!expanded));
            trigger.parentElement.classList.toggle("is-open", !expanded);
            if (!expanded) {
                positionNetworkStructureTooltip(
                    trigger.nextElementSibling,
                    trigger.getBoundingClientRect()
                );
            }
        });
        mount.addEventListener("keydown", function (event) {
            if (event.key !== "Escape") {
                return;
            }
            var openTerm = mount.querySelector(
                '[data-network-scope-term][aria-expanded="true"]'
            );
            if (openTerm) {
                openTerm.setAttribute("aria-expanded", "false");
                openTerm.parentElement.classList.remove("is-open");
                openTerm.focus();
                event.stopPropagation();
            }
        });
    }

    function renderKCoreChart(rows, mode) {
        if (!rows.length) {
            return "";
        }
        var width = Math.max(760, rows.length * 20);
        var height = 190;
        var baseline = 150;
        var plotHeight = 124;
        var slotWidth = width / rows.length;
        var barWidth = Math.max(2, Math.min(12, slotWidth * 0.62));
        var labelStep = Math.max(1, Math.ceil(rows.length / 12));
        var tooltipId =
            "social-network-network-structure-core-chart-tooltip";
        var bars = rows.map(function (row, index) {
            var ratio = Number(row.ratio);
            var barHeight = Number.isFinite(ratio)
                ? Math.max(0, Math.min(1, ratio)) * plotHeight
                : 0;
            var x = index * slotWidth + (slotWidth - barWidth) / 2;
            var label = mode === "cumulative"
                ? "core≥" + row.coreNumber
                : "core=" + row.coreNumber;
            var title = label + "：" + formatNumber(row.count) + " / " +
                formatNumber(row.sampleSize) + " 个账号（" +
                formatPercent(row.ratio) + "）";
            var slotX = index * slotWidth;
            var tick = index % labelStep === 0 ||
                index === rows.length - 1
                ? '<text class="social-network-network-structure-core-chart-label" ' +
                    'x="' + (index * slotWidth + slotWidth / 2) +
                    '" y="174" text-anchor="middle">' +
                    escapeHtml(String(row.coreNumber)) + '</text>'
                : "";
            return '<g><rect class="' +
                'social-network-network-structure-core-bar-hit-area" ' +
                'data-kcore-chart-bar tabindex="0" focusable="true" ' +
                'role="img" aria-label="' + escapeHtml(title) +
                '" aria-describedby="' + tooltipId + '" x="' + slotX +
                '" y="' + (baseline - plotHeight) + '" width="' +
                slotWidth + '" height="' + plotHeight + '"><title>' +
                escapeHtml(title) + '</title></rect><rect ' +
                'class="social-network-network-structure-core-bar" x="' +
                x + '" y="' + (baseline - barHeight) + '" width="' +
                barWidth + '" height="' + barHeight +
                '" rx="2" pointer-events="none"></rect>' +
                tick + '</g>';
        }).join("");
        return '<div class="social-network-network-structure-core-chart-scroll">' +
            '<svg class="social-network-network-structure-core-chart" ' +
            'viewBox="0 0 ' + width + " " + height +
            '" preserveAspectRatio="none" role="img" aria-label="' +
            (mode === "cumulative"
                ? "各 core 层级及以上账号数占全网比例"
                : "各 core 层级账号数占全网比例") +
            '" style="min-width:' + width + 'px">' +
            '<line class="social-network-network-structure-core-chart-axis" ' +
            'x1="0" y1="' + baseline + '" x2="' + width + '" y2="' +
            baseline + '"></line>' + bars + '</svg></div>';
    }

    function renderKCoreNodeEvidence(summary) {
        if (networkStructureSelectedCore === null) {
            return '<p class="social-network-network-structure-core-selection-prompt" ' +
                'role="status">选择上方一个层级，即可查看该层账号证据。</p>';
        }
        var selectedNodes = networkStructureModel.kCoreEvidenceNodes(
            summary,
            networkStructureSelectedCore,
            networkStructureKCoreMode
        );
        var selectedCoreRow = networkStructureModel.kCoreRows(
            summary,
            networkStructureKCoreMode
        ).find(function (row) {
            return row.coreNumber === networkStructureSelectedCore;
        });
        if (!selectedCoreRow) {
            return '<p class="social-network-network-structure-core-selection-prompt" ' +
                'role="status">所选层级不在当前完整分布中，请重新选择。</p>';
        }
        var query = networkStructureNodeQuery.trim().toLocaleLowerCase();
        var matchingNodes = selectedNodes.filter(function (node) {
            var profile = node.profile || {};
            return !query || [
                node.nodeId,
                profile.displayName,
                profile.username
            ].some(function (value) {
                return String(value || "").toLocaleLowerCase().indexOf(query) >= 0;
            });
        });
        var pageCount = Math.max(
            1,
            Math.ceil(matchingNodes.length / 20)
        );
        networkStructureNodePage = Math.max(
            1,
            Math.min(networkStructureNodePage, pageCount)
        );
        var start = (networkStructureNodePage - 1) * 20;
        var visibleNodes = matchingNodes.slice(start, start + 20);
        var rangeStart = matchingNodes.length ? start + 1 : 0;
        var rangeEnd = Math.min(start + 20, matchingNodes.length);
        var selectedLabel = networkStructureKCoreMode === "cumulative"
            ? "core≥" + networkStructureSelectedCore
            : "core=" + networkStructureSelectedCore;

        return '<section class="social-network-network-structure-core-evidence" ' +
            'aria-labelledby="social-network-network-structure-core-evidence-title">' +
            '<div class="social-network-network-structure-section-head">' +
            '<div><h4 id="social-network-network-structure-core-evidence-title">' +
            escapeHtml(selectedLabel) + ' · 账号证据</h4><p>该范围共 ' +
            formatNumber(selectedCoreRow.count) + ' 个账号 / 全网 ' +
            formatNumber(summary.kCoreDistribution.sampleSize) +
            ' 个账号；账号资料仅用于识别，不参与核心层计算。</p></div>' +
            '<button class="button button-secondary" type="button" ' +
            'data-kcore-close aria-label="收起账号证据">收起证据</button>' +
            '</div><form class="social-network-network-structure-core-search" ' +
            'data-kcore-search-form role="search">' +
            '<label for="social-network-network-structure-core-query">按账号 ID、用户名或显示名称查找</label>' +
            '<div><input id="social-network-network-structure-core-query" ' +
            'type="search" name="query" value="' +
            escapeHtml(networkStructureNodeQuery) +
            '" autocomplete="off"><button class="button button-secondary" ' +
            'type="submit">查找</button><button class="button button-secondary" ' +
            'type="button" data-kcore-clear-query>清除</button></div></form>' +
            '<p class="social-network-network-structure-core-results-count" ' +
            'role="status">匹配 ' + formatNumber(matchingNodes.length) +
            ' 个账号，当前显示 ' + formatNumber(rangeStart) + "–" +
            formatNumber(rangeEnd) + "。</p>" +
            (matchingNodes.length
                ? '<div class="social-network-network-structure-core-table-wrap">' +
                    '<table class="social-network-network-structure-core-node-table">' +
                    '<thead><tr><th>账号 ID</th><th>显示名称</th><th>用户名</th>' +
                    '<th>节点类型</th><th>core</th><th>详情</th></tr></thead><tbody>' +
                    visibleNodes.map(function (node) {
                        var profile = node.profile || {};
                        return '<tr><td><code>' +
                            escapeHtml(node.nodeId) + '</code></td><td>' +
                            escapeHtml(profile.displayName || "未提供") +
                            '</td><td>' +
                            escapeHtml(profile.username || "未提供") +
                            '</td><td>' +
                            (node.nodeType === "external"
                                ? "外部账号"
                                : "资料账号") + '</td><td>' +
                            formatNumber(node.coreNumber) + '</td><td>' +
                            (node.nodeType === "profile"
                                ? '<button type="button" class="button button-secondary" ' +
                                    'data-kcore-user-detail="' +
                                    escapeHtml(node.nodeId) +
                                    '">查看详情</button>'
                                : "无资料") +
                            '</td></tr>';
                    }).join("") + '</tbody></table></div>'
                : '<p class="social-network-network-structure-core-selection-prompt" ' +
                    'role="status">没有匹配的账号。</p>') +
            '<nav class="social-network-network-structure-core-pagination" ' +
            'aria-label="核心层账号证据分页"><span>第 ' +
            formatNumber(networkStructureNodePage) + " / " +
            formatNumber(pageCount) + " 页</span>" +
            '<button class="button button-secondary" type="button" ' +
            'data-kcore-page="previous" ' +
            (networkStructureNodePage <= 1 ? "disabled" : "") +
            '>上一页</button><button class="button button-secondary" ' +
            'type="button" data-kcore-page="next" ' +
            (networkStructureNodePage >= pageCount ? "disabled" : "") +
            '>下一页</button></nav></section>';
    }

    function renderNetworkStructureConclusion(summary) {
        var data = summary.data || {};
        var nodeState = networkStructureModel.valueState(data, "nodeCount");
        var edgeState = networkStructureModel.valueState(data, "edgeCount");
        var isolateState = networkStructureModel.valueState(
            data,
            "isolatedNodeCount"
        );
        var nodeCount = nodeState === "value" ? Number(data.nodeCount) : null;
        var edgeCount = edgeState === "value" ? Number(data.edgeCount) : null;
        var conclusion;
        var evidence = [];
        var explanation =
            "关系区域按忽略关注方向后的有效关系链划分；不表示组内账号彼此直接关注或发生了互动。";

        if (nodeState !== "value") {
            conclusion = "节点统计暂不可用，当前无法判断网络连接结构。";
            evidence.push("节点范围统计未提供");
        } else if (nodeCount === 0) {
            conclusion = "当前数据没有有效网络节点，无法判断连接结构。";
            evidence.push("网络节点数 0");
        } else if (edgeState === "value" && edgeCount === 0) {
            conclusion = "当前有有效节点，但没有有效关系，暂时无法判断群体连接结构。";
            evidence.push("网络节点 " + formatNumber(nodeCount));
            evidence.push("有效有向关系 0");
        } else {
            var component = networkStructureModel.largestComponent(summary);
            if (component.ratioState === "value" &&
                    component.sizeState === "value" &&
                    component.totalState === "value") {
                conclusion = "最大关系区域覆盖 " +
                    formatPercent(component.ratio) + " 的节点；" +
                    (isolateState === "value"
                        ? formatNumber(data.isolatedNodeCount) +
                            " 个节点没有有效入边或出边。"
                        : "孤立节点数量暂不可用。");
                evidence.push("最大关系区域 " +
                    formatNumber(component.size) + " / " +
                    formatNumber(component.total) + " 个节点（" +
                    formatPercent(component.ratio) + "）");
            } else {
                conclusion = "当前网络包含有效节点和关系，但连接区域占比暂不可用。";
            }
            if (nodeState === "value") {
                evidence.push("网络节点 " + formatNumber(nodeCount));
            }
            if (edgeState === "value") {
                evidence.push("有效有向关系 " + formatNumber(edgeCount));
            }
            if (networkStructureModel.valueState(
                    data,
                    "weaklyConnectedComponentCount"
                ) === "value") {
                evidence.push("关系区域 " + formatNumber(
                    data.weaklyConnectedComponentCount
                ));
            }
            if (isolateState === "value") {
                evidence.push("孤立节点 " +
                    formatNumber(data.isolatedNodeCount));
            }
        }

        return '<section class="social-network-network-structure-conclusion" ' +
            'aria-labelledby="social-network-network-structure-conclusion-title">' +
            '<div class="social-network-network-structure-conclusion-question">' +
            '<span>问题</span><h3 id="social-network-network-structure-conclusion-title">' +
            "这张网络的连接结构是什么样？</h3></div>" +
            '<div><span class="social-network-network-structure-conclusion-label">' +
            "结论</span><p>" + escapeHtml(conclusion) + '</p></div>' +
            '<div><span class="social-network-network-structure-conclusion-label">' +
            "证据</span><p>" + escapeHtml(evidence.join(" · ")) +
            '</p><small>' + escapeHtml(explanation) +
            '</small></div></section>';
    }

    function renderNetworkStructureEvidence(summary) {
        if (summary.kCoreLoadState !== "available") {
            return '<section class="social-network-network-structure-core-panel">' +
                '<div class="social-network-network-structure-error" role="alert"><p>' +
                escapeHtml(summary.kCoreDistribution.reason ||
                    "核心层完整分布暂不可用。") +
                '未使用排行前 20 名估算全体分布。</p>' +
                '<button type="button" class="button button-secondary" ' +
                'data-kcore-retry>重试读取</button></div></section>';
        }
        var distribution = summary.kCoreDistribution;
        if (distribution.state === "empty" || distribution.sampleSize === 0) {
            return '<section class="social-network-network-structure-core-panel">' +
                '<h3>关系圈层分布</h3><p class="social-network-network-structure-notice" ' +
                'role="status">当前完整网络没有节点，因此没有可展示的核心层级。</p></section>';
        }
        var rows = networkStructureModel.kCoreRows(
            summary,
            networkStructureKCoreMode
        );
        var mode = networkStructureKCoreMode;
        var term = networkStructureTerm(
            "k-core",
            "k-core",
            "忽略关注方向后，按账号在较紧密连接结构中的嵌入层级统计。core=k 是恰好属于该层的账号；core≥k 是该层及更高层的累计账号。层级不是自然的核心/边缘分界，也不代表社会地位。"
        );

        return '<section class="social-network-network-structure-core-panel" ' +
            'aria-labelledby="social-network-network-structure-core-title">' +
            '<div class="social-network-network-structure-section-head">' +
            '<div><h3 id="social-network-network-structure-core-title">' +
            '关系圈层分布 ' + term + '</h3><p>完整总体 ' +
            formatNumber(distribution.sampleSize) + ' 个账号 · 最高 core number ' +
            formatNumber(distribution.maxCoreNumber) + ' · 最高层账号数 ' +
            formatNumber(distribution.highestCoreCount) + ' · 算法版本 ' +
            escapeHtml(distribution.algorithmVersion || "未提供") +
            ' · ' + formatNumber(distribution.shells.length) +
            ' 个层级（包含 core=0）</p></div>' +
            '<div class="social-network-network-structure-core-mode" ' +
            'role="group" aria-label="核心层统计口径">' +
            '<button type="button" data-kcore-mode="exact" aria-pressed="' +
            (mode === "exact" ? "true" : "false") +
            '">每层账号数</button><button type="button" ' +
            'data-kcore-mode="cumulative" aria-pressed="' +
            (mode === "cumulative" ? "true" : "false") +
            '">该层及以上</button></div></div>' +
            '<p class="social-network-network-structure-core-definition">' +
            (mode === "cumulative"
                ? '当前显示 core≥k：core number 不低于所选层级的累计账号数。'
                : '当前显示 core=k：恰好属于该层级的账号数。') +
            '占比和柱形长度均以完整总体 ' +
            formatNumber(distribution.sampleSize) + ' 为分母。</p>' +
            '<div class="social-network-network-structure-core-chart-frame">' +
            '<div class="social-network-network-structure-core-chart-legend">' +
            '<span><i aria-hidden="true"></i>占全网账号比例 · 悬停或聚焦柱条查看数值</span></div>' +
            renderKCoreChart(rows, mode) +
            '<div class="social-network-network-structure-core-chart-tooltip" ' +
            'id="social-network-network-structure-core-chart-tooltip" ' +
            'data-kcore-chart-tooltip role="tooltip" hidden></div></div>' +
            '<div class="social-network-network-structure-core-table-wrap">' +
            '<table class="social-network-network-structure-core-table">' +
            '<caption>' +
            (mode === "cumulative"
                ? "每个核心层级及以上的全体账号分布"
                : "各核心层级的全体账号分布") +
            '；分母均为 ' + formatNumber(distribution.sampleSize) +
            ' 个账号</caption><thead><tr><th>层级</th><th>账号数 / 总体</th>' +
            '<th>占全网</th><th>账号证据</th></tr></thead><tbody>' +
            rows.map(function (row) {
                var selected = networkStructureSelectedCore === row.coreNumber;
                var title = row.label + "：" + formatNumber(row.count) + " / " +
                    formatNumber(row.sampleSize) + " 个账号（" +
                    formatPercent(row.ratio) + "）";
                return '<tr' + (selected ? ' class="is-selected"' : "") +
                    '><th scope="row"><span>' +
                    escapeHtml(row.label) + '</span></th><td>' +
                    formatNumber(row.count) + " / " +
                    formatNumber(row.sampleSize) + '</td><td>' +
                    formatPercent(row.ratio) + '</td><td><button type="button" ' +
                    'class="button button-secondary" data-kcore-select="' +
                    row.coreNumber + '" aria-pressed="' +
                    (selected ? "true" : "false") + '" title="' +
                    escapeHtml(title) + '">查看账号</button></td></tr>';
            }).join("") + '</tbody></table></div>' +
            renderKCoreNodeEvidence(summary) +
            '</section>';
    }

    function bindNetworkStructureEvidence(mount, summary, context) {
        mount.querySelectorAll("[data-network-structure-view]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    networkStructureView = button.getAttribute(
                        "data-network-structure-view"
                    );
                    renderNetworkStructureView(summary, context);
                    var selected = mount.querySelector(
                        '[data-network-structure-view="' +
                        networkStructureView + '"]'
                    );
                    if (selected) {
                        selected.focus();
                    }
                });
            });
        mount.querySelectorAll("[data-kcore-mode]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    networkStructureKCoreMode = button.getAttribute(
                        "data-kcore-mode"
                    );
                    networkStructureNodePage = 1;
                    renderNetworkStructureView(summary, context);
                    mount.querySelector('[data-kcore-mode="' +
                        networkStructureKCoreMode + '"]').focus();
                });
            });
        mount.querySelectorAll("[data-kcore-select]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    var selectedCore = Number(
                        button.getAttribute("data-kcore-select")
                    );
                    networkStructureSelectedCore =
                        networkStructureSelectedCore === selectedCore
                            ? null
                            : selectedCore;
                    networkStructureNodeQuery = "";
                    networkStructureNodePage = 1;
                    renderNetworkStructureView(summary, context);
                    var selection = mount.querySelector(
                        '[data-kcore-select="' + selectedCore + '"]'
                    );
                    if (selection) {
                        selection.focus();
                    }
                });
            });
        var closeEvidence = mount.querySelector("[data-kcore-close]");
        if (closeEvidence) {
            closeEvidence.addEventListener("click", function () {
                var selectedCore = networkStructureSelectedCore;
                networkStructureSelectedCore = null;
                networkStructureNodeQuery = "";
                networkStructureNodePage = 1;
                renderNetworkStructureView(summary, context);
                var selection = mount.querySelector(
                    '[data-kcore-select="' + selectedCore + '"]'
                );
                if (selection) {
                    selection.focus();
                }
            });
        }
        var chartFrame = mount.querySelector(
            ".social-network-network-structure-core-chart-frame"
        );
        if (chartFrame) {
            var chartTooltip = chartFrame.querySelector(
                "[data-kcore-chart-tooltip]"
            );
            var activeChartBar = null;
            var showChartTooltip = function (bar) {
                if (!bar || !chartTooltip) {
                    return;
                }
                activeChartBar = bar;
                chartTooltip.textContent = bar.getAttribute("aria-label") || "";
                chartTooltip.hidden = false;
                positionNetworkStructureTooltip(
                    chartTooltip,
                    bar.getBoundingClientRect()
                );
            };
            var hideChartTooltip = function (bar) {
                if (bar && activeChartBar !== bar) {
                    return;
                }
                activeChartBar = null;
                if (chartTooltip) {
                    chartTooltip.hidden = true;
                }
            };
            chartFrame.addEventListener("mouseover", function (event) {
                var bar = event.target.closest("[data-kcore-chart-bar]");
                if (bar) {
                    showChartTooltip(bar);
                }
            });
            chartFrame.addEventListener("mouseout", function (event) {
                var bar = event.target.closest("[data-kcore-chart-bar]");
                if (bar && !bar.contains(event.relatedTarget)) {
                    hideChartTooltip(bar);
                }
            });
            chartFrame.addEventListener("focusin", function (event) {
                var bar = event.target.closest("[data-kcore-chart-bar]");
                if (bar) {
                    showChartTooltip(bar);
                }
            });
            chartFrame.addEventListener("focusout", function (event) {
                var bar = event.target.closest("[data-kcore-chart-bar]");
                if (bar && !bar.contains(event.relatedTarget)) {
                    hideChartTooltip(bar);
                }
            });
        }
        var searchForm = mount.querySelector("[data-kcore-search-form]");
        if (searchForm) {
            searchForm.addEventListener("submit", function (event) {
                event.preventDefault();
                networkStructureNodeQuery = searchForm.querySelector(
                    'input[name="query"]'
                ).value.trim();
                networkStructureNodePage = 1;
                renderNetworkStructureView(summary, context);
                var search = mount.querySelector(
                    "#social-network-network-structure-core-query"
                );
                if (search) {
                    search.focus();
                }
            });
        }
        var clearQuery = mount.querySelector("[data-kcore-clear-query]");
        if (clearQuery) {
            clearQuery.addEventListener("click", function () {
                networkStructureNodeQuery = "";
                networkStructureNodePage = 1;
                renderNetworkStructureView(summary, context);
                var search = mount.querySelector(
                    "#social-network-network-structure-core-query"
                );
                if (search) {
                    search.focus();
                }
            });
        }
        mount.querySelectorAll("[data-kcore-page]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    networkStructureNodePage +=
                        button.getAttribute("data-kcore-page") === "next"
                            ? 1
                            : -1;
                    renderNetworkStructureView(summary, context);
                    var nextButton = mount.querySelector(
                        '[data-kcore-page="' +
                        button.getAttribute("data-kcore-page") + '"]'
                    );
                    if (nextButton && !nextButton.disabled) {
                        nextButton.focus();
                    }
                });
            });
        mount.querySelectorAll("[data-kcore-user-detail]")
            .forEach(function (button) {
                button.addEventListener("click", function (event) {
                    if (typeof window.openSocialNetworkUserDetail ===
                            "function") {
                        window.openSocialNetworkUserDetail(
                            button.getAttribute("data-kcore-user-detail"),
                            null,
                            event.currentTarget
                        );
                    }
                });
            });
        var retry = mount.querySelector("[data-kcore-retry]");
        if (retry) {
            retry.addEventListener("click", function () {
                currentNetworkStructure = null;
                loadNetworkStructure();
            });
        }
    }

    function renderNetworkStructureView(summary, context) {
        var mount = document.getElementById(
            "social-network-network-structure"
        );
        if (!mount) {
            return;
        }
        var data = summary.data;
        var entries = networkStructureModel.categories().reduce(
            function (allEntries, category) {
                return allEntries.concat(
                    networkStructureModel.metricEntries(summary, category.id)
                );
            },
            []
        );
        var pageRankState = networkStructureModel.pageRankState(summary);
        var pageRankLabels = {
            converged: "已收敛",
            "not-converged": "未收敛",
            "not-provided": "收敛状态未提供"
        };
        var pageRankClass = pageRankState === "converged"
            ? "is-converged"
            : pageRankState === "not-converged"
                ? "is-not-converged"
                : "is-not-provided";
        var iterationsState = networkStructureModel.pageRankValueState(
            summary,
            "iterations"
        );
        var deltaState = networkStructureModel.pageRankValueState(
            summary,
            "finalDelta"
        );
        var sumState = networkStructureModel.pageRankValueState(
            summary,
            "sum"
        );
        var includeExternal = data.includeExternalNodes === true
            ? "纳入外部节点"
            : data.includeExternalNodes === false
                ? "未纳入外部节点"
                : "外部节点口径未提供";
        var selfLoopRule = data.excludeSelfLoops === true
            ? "排除自环"
            : data.excludeSelfLoops === false
                ? "包含自环"
                : "自环口径未提供";
        var scope = data.scope || "完整规范化网络";
        var pageRankNotice = pageRankState === "not-converged"
            ? '<p class="social-network-network-structure-warning" role="note">' +
                "PageRank 未收敛，不应作为正常结构位置结论；其他网络结构指标仍可查看。</p>"
            : "";
        var viewToggle =
            '<div class="social-network-network-structure-views" ' +
            'role="group" aria-label="网络结构视图切换">' +
            '<button type="button" data-network-structure-view="overview" ' +
            'aria-pressed="' +
            (networkStructureView === "overview" ? "true" : "false") +
            '">指标概览</button><button type="button" ' +
            'data-network-structure-view="evidence" aria-pressed="' +
            (networkStructureView === "evidence" ? "true" : "false") +
            '">全网证据</button></div>';

        mount.innerHTML =
            '<header class="social-network-network-structure-header">' +
            '<div><p class="social-network-network-structure-eyebrow">NETWORK SCOPE</p>' +
            '<h2>完整规范化网络</h2><p>计数覆盖资料用户、有效关系端点及没有关系的有效用户；' +
            "不受其他页面关系图展示上限影响。</p></div>" +
            '<button class="button button-secondary" type="button" ' +
            'data-network-structure-export>导出网络摘要</button></header>' +
            '<section class="social-network-network-structure-scope" ' +
            'aria-label="完整网络范围计数">' +
            '<div><span>网络节点数</span><strong>' +
            escapeHtml(renderNetworkStructureCount(data, "nodeCount")) +
            '</strong></div><div><span>有效有向关系数</span><strong>' +
            escapeHtml(renderNetworkStructureCount(data, "edgeCount")) +
            '</strong></div><div class="social-network-network-structure-composition">' +
            '<span>节点构成</span><strong><span>资料用户 ' +
            escapeHtml(renderNetworkStructureCount(data, "profileUserCount")) +
            '</span><span>外部节点 ' +
            escapeHtml(renderNetworkStructureCount(data, "externalNodeCount")) +
            '</span></strong></div></section>' +
            '<p class="social-network-network-structure-scope-note">' +
            escapeHtml(scope) + " · " + escapeHtml(includeExternal) + " · " +
            escapeHtml(selfLoopRule) +
            " · 有效关系为去重、排除非法关系后的有向边。</p>" +
            renderNetworkStructureScope(summary, context, includeExternal) +
            viewToggle +
            (networkStructureView === "evidence"
                ? renderNetworkStructureEvidence(summary)
                : renderNetworkStructureConclusion(summary) +
            '<section class="social-network-network-structure-metrics" ' +
            'aria-label="网络结构指标">' +
            '<div class="social-network-network-structure-section-head">' +
            '<h3>网络结构指标</h3>' +
            '<button class="social-network-network-structure-method" type="button" ' +
            'data-network-structure-method>查看指标口径</button></div>' +
            '<div class="social-network-network-structure-metric-grid">' +
            entries.map(function (entry) {
                return '<article class="social-network-network-structure-metric">' +
                    '<div class="social-network-network-structure-metric-heading">' +
                    '<h4>' + escapeHtml(entry.label) + '</h4>' +
                    networkStructureTerm(
                        "metric-" + entry.key,
                        entry.label,
                        entry.meaning
                    ) + '</div><strong>' +
                    escapeHtml(renderNetworkStructureMetricValue(summary, entry)) +
                    '</strong></article>';
            }).join("") + '</div></section>' +
            '<section class="social-network-network-structure-pagerank" ' +
            'aria-label="网络位置估算状态">' +
            '<div><span>网络位置估算状态 ' +
            networkStructureTerm(
                "pagerank-status",
                "PageRank（网络位置估算）",
                "依据关注关系估算账号在网络中的相对位置；这不是现实影响力。"
            ) +
            '</span><strong class="' +
            pageRankClass + '">' + escapeHtml(pageRankLabels[pageRankState]) +
            '</strong><small>描述网络中的结构位置，不等同于社会影响力。</small></div>' +
            '<details><summary>查看计算详情</summary><p>迭代轮次 ' +
            escapeHtml(iterationsState === "value"
                ? formatNumber(summary.pageRank.iterations)
                : iterationsState === "unavailable" ? "不可用" : "未提供") +
            " · 最终变化量 " +
            escapeHtml(deltaState === "value"
                ? formatDensity(summary.pageRank.finalDelta)
                : deltaState === "unavailable" ? "不可用" : "未提供") +
            " · PageRank 总和 " +
            escapeHtml(sumState === "value"
                ? formatDecimal(summary.pageRank.sum)
                : sumState === "unavailable" ? "不可用" : "未提供") +
            '</p><p>网络指标版本：' +
            escapeHtml(summary.metricVersion || "未提供") +
            " · PageRank 版本：" +
            escapeHtml(summary.pagerankVersion || "未提供") +
            " · 阻尼系数 0.85 · 收敛容差 1e-8 · 最多 100 轮。</p></details>" +
            pageRankNotice + '</section>' +
            '<dialog class="social-network-network-structure-dialog" ' +
            'aria-labelledby="social-network-network-structure-dialog-title">' +
            '<form method="dialog"><h3 id="social-network-network-structure-dialog-title" tabindex="-1">' +
            "网络指标口径</h3>" +
            '<p>范围：' + escapeHtml(scope) + " · " +
            escapeHtml(includeExternal) + " · " + escapeHtml(selfLoopRule) +
            '</p><ul>' + entries.map(function (entry) {
                return '<li><strong>' + escapeHtml(entry.label) + '</strong><p>' +
                    escapeHtml(entry.description) + '</p></li>';
            }).join("") +
            '</ul><p>并列最大弱连通分量的平均路径长度，按各最大分量内节点对距离总和及节点对数量汇总；' +
            "不将并列分量误作一个可互相到达的分量。</p>" +
            '<p>网络指标版本：' + escapeHtml(summary.metricVersion || "未提供") +
            " · PageRank 版本：" +
            escapeHtml(summary.pagerankVersion || "未提供") +
            " · PageRank 阻尼系数 0.85、收敛容差 1e-8、最多 100 轮，均由服务端计算。</p>" +
            '<button class="button button-secondary" type="submit">关闭</button>' +
            '</form></dialog>');

        bindNetworkStructureScopeTerms(mount);
        bindNetworkStructureEvidence(mount, summary, context);
        var exportButton = mount.querySelector(
            "[data-network-structure-export]"
        );
        initializeModuleExportControls();
        exportButton.addEventListener("click", function () {
            openModuleExportDialog("network", exportButton);
        });
        var methodButton = mount.querySelector(
            "[data-network-structure-method]"
        );
        if (methodButton) {
            methodButton.addEventListener("click", function () {
                var dialog = mount.querySelector(
                    ".social-network-network-structure-dialog"
                );
                if (dialog && typeof dialog.showModal === "function") {
                    dialog.showModal();
                    var dialogTitle = dialog.querySelector("h3");
                    if (dialogTitle) {
                        dialogTitle.focus({ preventScroll: true });
                    }
                    dialog.scrollTop = 0;
                }
            });
        }
    }

    function networkStructureMount(taskCard) {
        var mount = document.getElementById(
            "social-network-network-structure"
        );
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-network-structure";
            mount.className = "social-network-network-structure";
            taskCard.appendChild(mount);
        }
        return mount;
    }

    function loadNetworkStructure(taskCard) {
        var card = taskCard || document.querySelector(
            "#page-social-network-task .social-network-task-card"
        );
        if (!card || !networkStructureModel) {
            return;
        }
        var mount = networkStructureMount(card);
        card.classList.add("is-network-structure");
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        if (networkStructureContextKey !== contextKey) {
            networkStructureContextKey = contextKey;
            networkStructureView = "overview";
            networkStructureKCoreMode = "exact";
            networkStructureSelectedCore = null;
            networkStructureNodeQuery = "";
            networkStructureNodePage = 1;
            currentNetworkStructure = null;
        }
        if (currentNetworkStructure &&
                networkStructureContextKey === contextKey) {
            renderNetworkStructureView(currentNetworkStructure, context);
            return;
        }
        if (networkStructureLookup &&
                sameDatasetContext(networkStructureLookup.context, context)) {
            return;
        }
        var sequence = ++networkStructureRequestSequence;
        mount.innerHTML =
            '<p class="social-network-network-structure-loading" role="status">' +
            "正在读取当前网络结构和完整核心层分布…</p>";
        var lookup = {
            context: context,
            sequence: sequence
        };
        networkStructureLookup = lookup;
        Promise.all([
            request(buildDatasetUrl("network/summary", null, context)),
            request(buildDatasetUrl("quality/summary", null, context))
                .catch(function () { return null; }),
            request(buildDatasetUrl("network/structure-roles", {
                limit: 1
            }, context)).catch(function () { return null; })
        ]).then(function (responses) {
                var body = responses[0];
                var qualityBody = responses[1];
                var structureBody = responses[2];
                var current = readDatasetContext();
                if (activeSocialNetworkTask !== "relations" ||
                        sequence !== networkStructureRequestSequence ||
                        !sameDatasetContext(context, current)) {
                    return;
                }
                currentNetworkStructure =
                    networkStructureModel.fromResponse(
                        body,
                        qualityBody,
                        structureBody
                    );
                renderNetworkStructureView(currentNetworkStructure, current);
                setText("social-network-task-status", "当前网络结构摘要");
                setText("social-network-task-message",
                    "显示完整规范化网络范围；分类切换仅改变指标视图，不会重新计算。");
            }).catch(function (error) {
                var current = readDatasetContext();
                if (isStaleDatasetRequest(error) ||
                        activeSocialNetworkTask !== "relations" ||
                        sequence !== networkStructureRequestSequence ||
                        !sameDatasetContext(context, current)) {
                    return;
                }
                currentNetworkStructure = null;
                mount.innerHTML =
                    '<div class="social-network-network-structure-error" ' +
                    'role="alert"><p>暂时无法读取当前数据的网络结构摘要。' +
                    "缺失或失败的数据不会显示为零。</p>" +
                    '<button class="button button-secondary" type="button" ' +
                    'data-network-structure-retry>重试</button></div>';
                mount.querySelector("[data-network-structure-retry]")
                    .addEventListener("click", function () {
                        loadNetworkStructure(card);
                    });
                setText("social-network-task-status", "网络结构摘要读取失败");
                setText("social-network-task-message",
                    "当前数据上下文未改变，可重试读取完整网络摘要。");
                if (typeof console !== "undefined" && console.error) {
                    console.error(
                        "[social-network] network structure failed",
                        error
                    );
                }
            }).finally(function () {
                if (networkStructureLookup === lookup) {
                    networkStructureLookup = null;
                }
            });
    }

    function rankingTaskMount(taskCard) {
        var mount = document.getElementById("social-network-node-ranking");
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-node-ranking";
            mount.className = "social-network-node-ranking";
            taskCard.appendChild(mount);
        }
        return mount;
    }

    function rankingTaskSelectionHtml(selectedMetric) {
        var groups = [
            { id: "network", label: "关系网络" },
            { id: "profile", label: "账号资料" },
            { id: "structure", label: "网络位置参考" }
        ];
        return groups.map(function (group) {
            var options = socialNetworkRankingModel.metrics().filter(
                function (entry) {
                    return entry.group === group.id;
                }
            );
            return '<optgroup label="' + escapeHtml(group.label) + '">' +
                options.map(function (entry) {
                    return '<option value="' + escapeHtml(entry.id) + '"' +
                        (entry.id === selectedMetric ? " selected" : "") + ">" +
                        escapeHtml(entry.label) + "</option>";
                }).join("") + "</optgroup>";
        }).join("");
    }

    function ensureRankingTaskView(taskCard, selectedMetric) {
        var mount = rankingTaskMount(taskCard);
        if (mount.querySelector("#social-network-node-ranking-metric")) {
            return mount;
        }
        mount.innerHTML =
            '<header class="social-network-node-ranking-header">' +
            '<div><p class="social-network-node-ranking-eyebrow">RANKING RESULTS</p>' +
            "<h2>排序结果</h2>" +
            "<p>按关系、账号资料或网络位置查看前列账号。每种角度回答的问题不同，排名不代表内容热度或现实影响力。</p></div>" +
            '<div class="social-network-node-ranking-header-actions">' +
            '<button class="button button-secondary" type="button" ' +
            'data-ranking-task-export>导出当前排行</button></div></header>' +
            '<div class="social-network-node-ranking-views" role="group" ' +
            'aria-label="节点排行视图">' +
            '<button type="button" data-ranking-task-view="single" ' +
            'aria-pressed="' + (rankingTaskView === "single") +
            '">查看一种角度</button><button type="button" ' +
            'data-ranking-task-view="comparison" aria-pressed="' +
            (rankingTaskView === "comparison") +
            '">比较不同角度</button></div>' +
            '<section data-ranking-task-single-view>' +
            '<div class="social-network-node-ranking-controls">' +
            '<label for="social-network-node-ranking-metric">选择排序角度' +
            '<select id="social-network-node-ranking-metric">' +
            rankingTaskSelectionHtml(selectedMetric) +
            "</select></label>" +
            '<button class="button button-secondary" type="button" ' +
            'data-ranking-task-refresh>更新排行</button></div>' +
            '<p class="social-network-node-ranking-note" ' +
            'data-ranking-task-note aria-live="polite"></p>' +
            '<p class="social-network-node-ranking-count" ' +
            'data-ranking-task-count></p>' +
            '<div class="social-network-node-ranking-table" ' +
            'data-ranking-task-table role="region" aria-label="当前指标 Top 20 排行" ' +
            'tabindex="0"></div>' +
            "</section>" +
            '<section class="social-network-cross-metric-view" ' +
            'data-ranking-task-comparison-view hidden ' +
            'aria-labelledby="social-network-cross-metric-title">' +
            '<div class="social-network-cross-metric-heading">' +
            '<div><h3 id="social-network-cross-metric-title">不同角度的结果是否一致？</h3>' +
            '<p>分别看整体名次是否接近、前列账号是否相同；两项结果都不代表账号影响力或因果关系。</p></div>' +
            '<p data-ranking-cross-metric-summary aria-live="polite"></p></div>' +
            '<div class="social-network-cross-metric-grid">' +
            '<section class="social-network-cross-metric-panel">' +
            '<h4>整体名次有多接近</h4><p>比较两种角度给账号排出的先后顺序。数值越接近 1，排序越相似；接近 0 表示没有明显同向关系；负值表示顺序大致相反。</p>' +
            '<div class="social-network-cross-metric-table-wrap" ' +
            'data-ranking-spearman-table role="region" ' +
            'aria-label="不同排序角度的整体接近程度"></div></section>' +
            '<section class="social-network-cross-metric-panel">' +
            '<h4>前列账号有多少相同</h4>' +
            '<p>分别比较每种角度的前 20 名；如果第 20 名有并列，相关账号会一并纳入，所以名单可能超过 20 人。重合比例表示共同入选账号占两份名单合并后总人数的比例。</p>' +
            '<div class="social-network-cross-metric-table-wrap" ' +
            'data-ranking-top20-table role="region" ' +
            'aria-label="不同角度前列账号的重合情况"></div></section></div>' +
            '<details class="social-network-cross-metric-evidence">' +
            '<summary>查看这些账号在其他角度下的名次</summary>' +
            '<p>每项显示该角度的数值，下方小字是它在全部账号中的名次；这里只列出本次对照涉及的账号。</p>' +
            '<p data-ranking-node-evidence-state role="status"></p>' +
            '<div class="social-network-cross-metric-table-wrap" ' +
            'data-ranking-node-evidence role="region" ' +
            'aria-label="账号在不同排序角度下的结果"></div></details></section>';

        var metricControl = mount.querySelector(
            "#social-network-node-ranking-metric");
        metricControl.addEventListener("change", function () {
            workspaceState.setCondition("ranking", { metric: this.value });
            loadRankingTask(taskCard);
        });
        mount.querySelector("[data-ranking-task-refresh]")
            .addEventListener("click", function () {
                loadRankingTask(taskCard);
            });
        mount.querySelector("[data-ranking-task-export]")
            .addEventListener("click", function () {
                openModuleExportDialog(
                    "users",
                    mount.querySelector("[data-ranking-task-export]"));
            });
        mount.querySelectorAll("[data-ranking-task-view]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    rankingTaskView = button.getAttribute(
                        "data-ranking-task-view"
                    );
                    loadRankingTask(taskCard);
                });
            });
        setRankingTaskViewVisibility(mount);
        return mount;
    }

    function setRankingTaskViewVisibility(mount) {
        var comparison = rankingTaskView === "comparison";
        var singleView = mount.querySelector("[data-ranking-task-single-view]");
        var comparisonView = mount.querySelector(
            "[data-ranking-task-comparison-view]");
        if (singleView) {
            singleView.hidden = comparison;
        }
        if (comparisonView) {
            comparisonView.hidden = !comparison;
        }
        mount.querySelectorAll("[data-ranking-task-view]")
            .forEach(function (button) {
                button.setAttribute(
                    "aria-pressed",
                    String(button.getAttribute("data-ranking-task-view") ===
                        rankingTaskView)
                );
            });
    }

    function rankingTaskIsCurrent(contextKey, metric, sequence, view) {
        return activeSocialNetworkTask === "ranking" &&
            contextKey === rankingTaskContextKey &&
            contextKey === datasetContextKey(readDatasetContext()) &&
            sequence === rankingTaskRequestSequence &&
            rankingTaskView === view &&
            (view === "comparison" ||
                workspaceState.getCondition("ranking").metric === metric);
    }

    function renderRankingTaskLoading(mount, metric) {
        var definition = socialNetworkRankingModel.metric(metric);
        mount.querySelector("#social-network-node-ranking-metric").value = metric;
        mount.querySelector("[data-ranking-task-note]").textContent =
            definition ? definition.description : "";
        mount.querySelector("[data-ranking-task-count]").textContent =
            "正在按「" + (definition ? definition.label : "所选角度") +
            "」整理账号顺序…";
        mount.querySelector("[data-ranking-task-table]").innerHTML =
            '<div class="social-network-network-structure-loading" role="status">' +
            "正在更新列表…</div>";
        setText("social-network-task-status", "正在读取节点排行");
        setText("social-network-task-message",
            "正在按所选角度更新账号顺序。");
    }

    function renderRankingTaskResult(result) {
        var mount = document.getElementById("social-network-node-ranking");
        if (!mount) {
            return;
        }
        var note = mount.querySelector("[data-ranking-task-note]");
        var count = mount.querySelector("[data-ranking-task-count]");
        var table = mount.querySelector("[data-ranking-task-table]");
        var definition = result.definition;
        note.textContent = definition.description;

        if (result.metric === "k_core" || result.metric === "betweenness") {
            count.textContent = "范围：完整关系网络，共 " +
                formatNumber(result.nodeCount) +
                " 个账号或关系节点；列表展示其中排名靠前的对象。";
        } else {
            count.textContent = "本次有 " + formatNumber(result.totalCount) +
                " 个账号或关系节点可参与排序；页面最多展示前 20 项。";
        }

        if (result.state === "unavailable") {
            table.innerHTML =
                '<div class="social-network-network-structure-warning" ' +
                'role="status">' + escapeHtml(rankingReasonLabel(
                    result.reason)) + "</div>";
            setText("social-network-task-status", definition.label + "暂不可用");
            setText("social-network-task-message",
                "可改选其他排序角度，或检查数据后重试。");
            return;
        }
        if (result.state === "empty" || !result.rows.length) {
            table.innerHTML =
                '<div class="social-network-empty" role="status">' +
                escapeHtml(rankingReasonLabel(result.reason)) +
                "</div>";
            setText("social-network-task-status", definition.label + "暂无结果");
            setText("social-network-task-message",
                "没有可用对象时不生成虚假的排行名次。");
            return;
        }

        var rows = result.rows.map(function (item, index) {
            var external = item.nodeType === "external" ||
                item.status === "external";
            var name = external
                ? "尚未采集资料的关系节点"
                : item.displayName || item.username || "未命名账号";
            var stateLabel = external
                ? '<span class="social-network-node-ranking-external">' +
                    "未采集</span>"
                : "已采集";
            var value = result.metric === "k_core"
                ? formatNumber(item.value)
                : formatDecimal(item.value);
            var username = item.username && item.username !== name
                ? "<small>@" + escapeHtml(item.username) + "</small>"
                : "";
            return "<tr><td>" + escapeHtml(formatNumber(item.rank)) +
                "</td><td><strong>" + escapeHtml(name) + "</strong>" +
                username + "</td><td>" + escapeHtml(value) + "</td><td>" +
                stateLabel + '</td><td><button type="button" ' +
                'class="social-network-user-detail-action" ' +
                'data-ranking-detail-index="' + escapeHtml(index) + '">' +
                "查看资料</button> " +
                '<button type="button" class="social-network-user-relations-action" ' +
                'data-ranking-relationship-index="' + escapeHtml(index) + '">' +
                "查看关系</button></td></tr>";
        }).join("");
        var cappedNote = result.truncated
            ? '<p class="social-network-node-ranking-actions-note">' +
                "当前只展示前 20 项；上方说明的是完整计算范围。" +
                "</p>"
            : "";
        table.innerHTML =
            "<table><thead><tr><th>排名</th><th>账号或关系节点</th>" +
            "<th>结果</th><th>资料</th><th>可查看</th></tr></thead><tbody>" +
            rows + "</tbody></table>" + cappedNote;
        table.querySelectorAll("[data-ranking-detail-index]").forEach(function (button) {
            var item = result.rows[Number(button.getAttribute(
                "data-ranking-detail-index"))];
            if (!item) {
                return;
            }
            button.addEventListener("click", function () {
                window.openSocialNetworkUserDetail(item.nodeId, {
                    metric: result.metric,
                    metricLabel: definition.label,
                    rank: item.rank,
                    value: item.value,
                    scope: result.scope || "",
                    metricType: result.metricType || ""
                }, button);
            });
        });
        table.querySelectorAll("[data-ranking-relationship-index]")
            .forEach(function (button) {
                var item = result.rows[Number(button.getAttribute(
                    "data-ranking-relationship-index"))];
                if (item && item.nodeId) {
                    button.addEventListener("click", function () {
                        openUserRelationsAtCenter(item.nodeId, button);
                    });
                }
            });
        setText("social-network-task-status", definition.label + " · 前 20 名");
        setText("social-network-task-message",
            "当前按「" + definition.label + "」排序，展示 " +
            formatNumber(result.rows.length) + " 项；不同角度分开排列，不合成总分。");
    }

    function rankingReasonLabel(reason) {
        var text = String(reason || "");
        if (/节点数不足|账号数量不足|至少.*账号/.test(text)) {
            return "当前可参与分析的账号较少，暂时无法计算这项排序。";
        }
        if (/没有关系|无边|关系数量不足/.test(text)) {
            return "当前数据中的关系数量不足，暂时无法计算这项排序。";
        }
        if (/数据集版本|数据快照|snapshot|数据已变化/i.test(text)) {
            return "数据在分析期间发生变化，请刷新后重试。";
        }
        if (!text || /空数据|没有节点|没有可用数据/.test(text)) {
            return "这份数据中没有可参与排序的对象。";
        }
        return "当前数据暂不满足这项排序的计算条件，请检查数据后重试。";
    }

    function crossMetricStateLabel(state) {
        var labels = {
            available: "已完成比较",
            empty: "没有可比较的账号",
            limited: "部分结果无法计算",
            unavailable: "暂时无法比较",
            failed: "读取失败",
            mismatch: "数据已更新，请重新加载"
        };
        return labels[state] || "暂时无法比较";
    }

    function crossMetricReasonLabel(reason) {
        var text = String(reason || "");
        if (/零阈值|Jaccard/i.test(text)) {
            return "其中一份名单没有可比较的分值，暂时无法计算重合比例。";
        }
        if (/无变异/.test(text)) {
            return "其中一项没有分值差异，暂时无法比较整体名次。";
        }
        if (/数据集版本不一致|数据快照|snapshot/i.test(text)) {
            return "数据在对照期间发生变化，请刷新后重试。";
        }
        return "这部分对照暂时无法完成，请刷新后重试。";
    }

    function renderCrossMetricLoading(mount) {
        mount.querySelector("[data-ranking-cross-metric-summary]")
            .textContent = "正在比较当前数据中的不同排序角度…";
        mount.querySelector("[data-ranking-spearman-table]").innerHTML =
            '<p class="social-network-network-structure-loading" role="status">' +
            "正在比较整体名次…</p>";
        mount.querySelector("[data-ranking-top20-table]").innerHTML =
            '<p class="social-network-network-structure-loading" role="status">' +
            "正在比较前列账号…</p>";
        mount.querySelector("[data-ranking-node-evidence-state]")
            .textContent = "正在读取这些账号在其他角度下的名次。";
        mount.querySelector("[data-ranking-node-evidence]").innerHTML = "";
        setText("social-network-task-status", "正在比较不同角度");
        setText("social-network-task-message",
            "正在查看整体排名顺序和前列账号是否一致。");
    }

    function renderCrossMetricFailure(mount, message) {
        var evidenceMessage = crossMetricReasonLabel(message) ||
            "暂时无法读取当前数据的比较结果，请刷新后重试。";
        var notice = '<p class="social-network-network-structure-error" ' +
            'role="alert">' + escapeHtml(evidenceMessage) + "</p>";
        mount.querySelector("[data-ranking-cross-metric-summary]")
            .textContent = "暂时无法比较不同角度：" +
                evidenceMessage;
        mount.querySelector("[data-ranking-spearman-table]").innerHTML = notice;
        mount.querySelector("[data-ranking-top20-table]").innerHTML = notice;
        mount.querySelector("[data-ranking-node-evidence-state]")
            .textContent = "数据已变化，本次不展示旧账号名次。";
        mount.querySelector("[data-ranking-node-evidence]").innerHTML =
            '<p class="social-network-empty" role="status">' +
            escapeHtml(evidenceMessage) + "</p>";
        setText("social-network-task-status", "暂时无法比较不同角度");
        setText("social-network-task-message",
            "请检查数据后重试，或切回单一排序角度。");
    }

    function renderRankingCrossMetricResult(mount, result) {
        var summary = mount.querySelector(
            "[data-ranking-cross-metric-summary]");
        var spearman = mount.querySelector("[data-ranking-spearman-table]");
        var top20 = mount.querySelector("[data-ranking-top20-table]");
        var nodeState = mount.querySelector(
            "[data-ranking-node-evidence-state]");
        var nodeTable = mount.querySelector("[data-ranking-node-evidence]");
        if (result.state === "mismatch") {
            renderCrossMetricFailure(mount, result.reason);
            return;
        }
        summary.textContent = "本次比较覆盖 " +
            formatNumber(result.populationSize) +
            " 个账号。整体名次看排序是否接近，前列名单看入选账号是否相同。" +
            (result.state !== "available"
                ? " " + crossMetricStateLabel(result.state) +
                    (result.reason
                        ? "：" + crossMetricReasonLabel(result.reason)
                        : "。")
                : "");

        if (result.spearmanPairs.length) {
            spearman.innerHTML =
                '<table class="social-network-cross-metric-table">' +
                '<thead><tr><th>比较角度</th><th>排序接近程度</th>' +
                '<th>参与账号数</th><th>结果说明</th></tr></thead><tbody>' +
                result.spearmanPairs.map(function (pair) {
                    var rho = Number.isFinite(pair.rho)
                        ? Number(pair.rho).toLocaleString("zh-CN", {
                            maximumFractionDigits: 2
                        })
                        : "暂无结果";
                    var pairNote = pair.state === "available"
                        ? "已完成比较"
                        : "暂无法比较" +
                            (pair.reason
                                ? "：" + crossMetricReasonLabel(pair.reason)
                                : "");
                    return "<tr><td>" + escapeHtml(pair.metricALabel) + " × " +
                        escapeHtml(pair.metricBLabel) + "</td><td>" +
                        escapeHtml(rho) + "</td><td>" +
                        formatNumber(pair.sampleSize) + "</td><td>" +
                        escapeHtml(pairNote) + "</td></tr>";
                }).join("") + "</tbody></table>";
        } else {
            spearman.innerHTML =
                '<p class="social-network-empty" role="status">' +
                escapeHtml(result.reason
                    ? crossMetricReasonLabel(result.reason)
                    : "当前没有可比较的账号组合。") +
                "</p>";
        }

        if (result.top20Comparisons.length) {
            top20.innerHTML =
                '<table class="social-network-cross-metric-table">' +
                '<thead><tr><th>比较角度</th><th>各自入选账号</th>' +
                '<th>共同账号</th><th>重合比例</th>' +
                '<th>补充说明</th></tr></thead><tbody>' +
                result.top20Comparisons.map(function (comparison) {
                    var jaccard = Number.isFinite(comparison.jaccard)
                        ? (Number(comparison.jaccard) * 100)
                            .toLocaleString("zh-CN", {
                                maximumFractionDigits: 1
                            }) + "%"
                        : "暂不可算";
                    var notes = [];
                    if (comparison.tieExpandedA === true) {
                        notes.push(comparison.metricALabel +
                            "的入选边界有并列，已一并纳入");
                    }
                    if (comparison.tieExpandedB === true) {
                        notes.push(comparison.metricBLabel +
                            "的入选边界有并列，已一并纳入");
                    }
                    if (comparison.state !== "available") {
                        notes.push(crossMetricStateLabel(comparison.state) +
                            (comparison.reason
                                ? "：" +
                                    crossMetricReasonLabel(comparison.reason)
                                : ""));
                    }
                    return "<tr><td>" +
                        escapeHtml(comparison.metricALabel) + " × " +
                        escapeHtml(comparison.metricBLabel) + "</td><td>" +
                        escapeHtml(comparison.metricALabel) + "：" +
                        formatNumber(comparison.setSizeA) + " 名<br>" +
                        escapeHtml(comparison.metricBLabel) + "：" +
                        formatNumber(comparison.setSizeB) + " 名</td><td>" +
                        formatNumber(comparison.intersectionCount) +
                        "</td><td>" + escapeHtml(jaccard) + "</td><td>" +
                        escapeHtml(notes.length
                            ? notes.join("；")
                            : "两种角度均有结果") + "</td></tr>";
                }).join("") + "</tbody></table>";
        } else {
            top20.innerHTML =
                '<p class="social-network-empty" role="status">' +
                escapeHtml(result.reason
                    ? crossMetricReasonLabel(result.reason)
                    : "当前没有可比较的前列账号名单。") +
                "</p>";
        }

        nodeState.textContent = result.nodeEvidence.length
            ? "列表只包含本次对照涉及的账号；未显示某项结果表示该角度没有返回可用名次。"
            : crossMetricReasonLabel(result.nodeEvidenceReason) ||
                "当前没有可展示的账号名次。";
        if (!result.nodeEvidence.length) {
            nodeTable.innerHTML =
                '<p class="social-network-empty" role="status">' +
                escapeHtml(nodeState.textContent) + "</p>";
        } else {
            var metricColumns = [
                { key: "indegree", label: "被关注关系数" },
                { key: "outdegree", label: "主动关注关系数" },
                { key: "pageRank", label: "网络位置估算" },
                { key: "kCore", label: "紧密圈层位置" },
                { key: "normalizedBetweenness", label: "群体间连接位置" }
            ];
            nodeTable.innerHTML =
                '<table class="social-network-cross-metric-table ' +
                'social-network-cross-metric-node-table">' +
                "<thead><tr><th>账号</th>" +
                metricColumns.map(function (column) {
                    return "<th>" + escapeHtml(column.label) +
                        "</th>";
                }).join("") +
                "<th>资料</th></tr></thead><tbody>" +
                result.nodeEvidence.map(function (node, index) {
                    var profile = node.profile || {};
                    var profileName = profile.displayName ||
                        profile.username || "未命名账号";
                    var cells = metricColumns.map(function (column) {
                        var item = node.metrics.find(function (entry) {
                            return entry.metricKey === column.key;
                        });
                        if (!item) {
                            return "<td>暂无结果</td>";
                        }
                        var value = column.key === "kCore"
                            ? formatNumber(item.value)
                            : formatDecimal(item.value);
                        return "<td>" + escapeHtml(value) +
                            "<small>名次 " +
                            escapeHtml(formatNumber(item.rank)) +
                            "</small></td>";
                    }).join("");
                    var detail = node.nodeType === "profile" &&
                            node.profileState === "available"
                        ? '<button type="button" ' +
                            'data-cross-metric-detail="' +
                            escapeHtml(index) + '">查看账号资料</button>'
                        : "未采集";
                    return "<tr><td><strong>" +
                        escapeHtml(profileName) + "</strong>" +
                        (profile.username && profile.username !== profileName
                            ? "<small>@" + escapeHtml(profile.username) +
                                "</small>"
                            : "") + "</td>" +
                        cells + "<td>" + detail + "</td></tr>";
                }).join("") + "</tbody></table>";
            nodeTable.querySelectorAll("[data-cross-metric-detail]")
                .forEach(function (button) {
                    var node = result.nodeEvidence[Number(
                        button.getAttribute("data-cross-metric-detail"))];
                    if (node) {
                        button.addEventListener("click", function () {
                            window.openSocialNetworkUserDetail(
                                node.nodeId,
                                null,
                                button
                            );
                        });
                    }
                });
        }
        setText("social-network-task-status",
            "不同角度比较 · " + crossMetricStateLabel(result.state));
        setText("social-network-task-message",
            "这里分别比较整体名次接近程度和前列账号重合情况，不合成总分。");
    }

    async function loadRankingTask(taskCard) {
        var card = taskCard || document.querySelector(
            "#page-social-network-task .social-network-task-card");
        if (!card || !socialNetworkRankingModel) {
            return;
        }
        var currentCondition = workspaceState.getCondition("ranking");
        var metric = socialNetworkRankingModel.metric(currentCondition.metric)
            ? currentCondition.metric
            : "indegree";
        if (metric !== currentCondition.metric) {
            workspaceState.setCondition("ranking", { metric: metric });
        }
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        if (rankingTaskContextKey !== contextKey) {
            rankingTaskContextKey = contextKey;
            rankingTaskView = "single";
            rankingTaskRolesCache = null;
            rankingTaskRolesLookup = null;
            rankingTaskRequestSequence += 1;
        }
        var mount = ensureRankingTaskView(card, metric);
        setRankingTaskViewVisibility(mount);
        var sequence = ++rankingTaskRequestSequence;
        var requestView = rankingTaskView;
        if (requestView === "comparison") {
            renderCrossMetricLoading(mount);
        } else {
            renderRankingTaskLoading(mount, metric);
        }

        if (requestView === "comparison" ||
                socialNetworkRankingModel.metric(metric).group === "structure") {
            try {
                var rolesBody;
                if (rankingTaskRolesCache &&
                        rankingTaskRolesCache.contextKey === contextKey) {
                    rolesBody = rankingTaskRolesCache.body;
                } else {
                    var lookup = rankingTaskRolesLookup;
                    if (!lookup || lookup.contextKey !== contextKey) {
                        lookup = { contextKey: contextKey, promise: null };
                        lookup.promise = request(buildDatasetUrl(
                            "network/structure-roles",
                            { limit: 20 },
                            context
                        )).then(function (body) {
                            if (rankingTaskRolesLookup === lookup &&
                                    rankingTaskContextKey === contextKey) {
                                rankingTaskRolesCache = {
                                    contextKey: contextKey,
                                    body: body
                                };
                            }
                            return body;
                        }).finally(function () {
                            if (rankingTaskRolesLookup === lookup) {
                                rankingTaskRolesLookup = null;
                            }
                        });
                        rankingTaskRolesLookup = lookup;
                    }
                    rolesBody = await lookup.promise;
                }
                if (!rankingTaskIsCurrent(
                        contextKey,
                        metric,
                        sequence,
                        requestView
                )) {
                    return;
                }
                if (requestView === "comparison") {
                    renderRankingCrossMetricResult(
                        mount,
                        socialNetworkRankingModel.fromCrossMetricResponse(
                            rolesBody,
                            context
                        )
                    );
                    return;
                }
                var activeMetric = workspaceState.getCondition("ranking").metric;
                if (socialNetworkRankingModel.metric(activeMetric).group !==
                        "structure") {
                    return;
                }
                renderRankingTaskResult(
                    socialNetworkRankingModel.fromStructureRolesResponse(
                        rolesBody,
                        activeMetric
                    )
                );
            } catch (error) {
                if (!rankingTaskIsCurrent(
                        contextKey,
                        metric,
                        sequence,
                        requestView
                )) {
                    return;
                }
                if (requestView === "comparison") {
                    renderCrossMetricFailure(mount, error.message);
                } else {
                    mount.querySelector("[data-ranking-task-table]").innerHTML =
                        '<div class="social-network-network-structure-error" ' +
                        'role="alert">暂时无法读取结构角色排行。请使用“刷新排行”重试。</div>';
                    setText("social-network-task-status", "结构角色排行读取失败");
                    setText("social-network-task-message",
                        error.message || "当前数据上下文未改变，可重试读取。");
                }
            }
            return;
        }

        try {
            var lookup = rankingTaskOrdinaryLookup;
            if (!lookup || lookup.contextKey !== contextKey ||
                    lookup.metric !== metric) {
                lookup = {
                    contextKey: contextKey,
                    metric: metric,
                    promise: null
                };
                lookup.promise = request(buildDatasetUrl("rankings", {
                    metric: metric,
                    limit: 20
                }, context)).finally(function () {
                    if (rankingTaskOrdinaryLookup === lookup) {
                        rankingTaskOrdinaryLookup = null;
                    }
                });
                rankingTaskOrdinaryLookup = lookup;
            }
            var result = await lookup.promise;
            if (!rankingTaskIsCurrent(
                    contextKey,
                    metric,
                    sequence,
                    requestView
            )) {
                return;
            }
            renderRankingTaskResult(
                socialNetworkRankingModel.fromRankingResponse(result, metric)
            );
        } catch (error) {
            if (!rankingTaskIsCurrent(
                    contextKey,
                    metric,
                    sequence,
                    requestView
            )) {
                return;
            }
            mount.querySelector("[data-ranking-task-table]").innerHTML =
                '<div class="social-network-network-structure-error" ' +
                'role="alert">暂时无法读取当前指标排行。请使用“刷新排行”重试。</div>';
            setText("social-network-task-status", "节点排行读取失败");
            setText("social-network-task-message",
                error.message || "当前数据上下文未改变，可重试读取。");
        }
    }

    function userRelationsTaskMount(taskCard) {
        var mount = document.getElementById("social-network-user-relations");
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-user-relations";
            mount.className = "social-network-user-relations";
            taskCard.appendChild(mount);
        }
        return mount;
    }

    function userRelationsRequestIsCurrent(sequence, contextKey, requestType) {
        var currentSequence = requestType === "search"
            ? userRelationsSearchSequence
            : userRelationsLocalSequence;
        return activeSocialNetworkTask === "user-relations" &&
            sequence === currentSequence &&
            contextKey === userRelationsContextKey &&
            contextKey === datasetContextKey(readDatasetContext());
    }

    function ensureUserRelationsTaskView(taskCard) {
        var mount = userRelationsTaskMount(taskCard);
        if (mount.querySelector("#social-network-user-relations-query")) {
            return mount;
        }
        mount.innerHTML =
            '<header class="social-network-user-relations-header">' +
            '<div><p class="social-network-user-relations-eyebrow">RELATIONSHIP EXPLORER</p>' +
            "<h2>从一个中心用户查看局部关系</h2>" +
            "<p>先查找并明确选择中心用户，再按当前条件读取真实关注节点与有向关系。</p>" +
            "</div></header>" +
            '<section class="social-network-user-relations-search" ' +
            'aria-labelledby="social-network-user-relations-search-title">' +
            '<div class="social-network-user-relations-section-head">' +
            '<div><h3 id="social-network-user-relations-search-title">查找资料用户</h3>' +
            "<p>按用户 ID、用户名或显示名称搜索；外部节点不在此搜索范围内。</p></div>" +
            "</div>" +
            '<div class="social-network-user-relations-search-controls">' +
            '<label for="social-network-user-relations-query">用户关键词</label>' +
            '<input id="social-network-user-relations-query" type="search" ' +
            'autocomplete="off" placeholder="输入 ID、用户名或显示名称">' +
            '<button class="button button-primary" type="button" ' +
            'data-user-relations-search>查找用户</button></div>' +
            '<p class="social-network-user-relations-search-status" ' +
            'data-user-relations-search-status role="status" aria-live="polite">' +
            "请输入关键词并显式查找；空关键词不会列出全部用户。</p>" +
            '<div class="social-network-user-relations-candidates" ' +
            'data-user-relations-candidates></div></section>' +
            '<section class="social-network-user-relations-center" ' +
            'data-user-relations-center-section hidden>' +
            '<div class="social-network-user-relations-section-head">' +
            '<div><h3>中心用户</h3><p data-user-relations-center-label></p></div>' +
            '<button class="button button-secondary" type="button" ' +
            'data-user-relations-clear-center>更换用户</button></div>' +
            '<div class="social-network-user-relations-controls">' +
            '<div class="social-network-user-relations-control">' +
            '<div class="social-network-user-relations-control-label">' +
            '<label for="social-network-user-relations-depth">邻居层级</label>' +
            networkStructureTerm(
                "user-relations-neighbor-level",
                "一阶 / 二阶邻居",
                "一阶是中心用户直接关注或被关注的账号；二阶是沿这些账号再向外延伸一层的账号。查找时不区分关注方向。"
            ) + '</div><select id="social-network-user-relations-depth" ' +
            'data-user-relations-depth>' +
            '<option value="1">一阶邻居</option><option value="2">二阶邻居</option>' +
            '</select></div>' +
            '<div class="social-network-user-relations-control">' +
            '<div class="social-network-user-relations-control-label">' +
            '<label for="social-network-user-relations-top-k">邻居上限</label>' +
            networkStructureTerm(
                "user-relations-neighbor-limit",
                "邻居上限",
                "最多纳入多少个周边账号；超过上限时，按其在关注网络中的连接位置排序后取前列。中心用户不计入此上限。"
            ) + '</div><select id="social-network-user-relations-top-k" ' +
            'data-user-relations-top-k>' +
            '<option value="20">20</option><option value="30">30</option>' +
            '<option value="50">50</option></select></div>' +
            '<label class="social-network-user-relations-checkbox">' +
            '<input type="checkbox" data-user-relations-include-external>' +
            "<span>包含外部节点</span></label>" +
            '<button class="button button-primary" type="button" ' +
            'data-user-relations-submit>更新关系</button></div>' +
            '<p class="social-network-user-relations-draft" ' +
            'data-user-relations-draft role="status" aria-live="polite" hidden></p>' +
            '<section class="social-network-user-relations-results" ' +
            'data-user-relations-results hidden>' +
            '<div class="social-network-user-relations-section-head">' +
            '<div><h3>本次关系结果</h3>' +
            '<p data-user-relations-result-summary></p></div></div>' +
            '<p class="social-network-user-relations-scope-note" ' +
            'data-user-relations-range-note></p>' +
            '<p class="social-network-user-relations-warning" ' +
            'data-user-relations-truncation hidden></p>' +
            '<div class="social-network-user-relations-result-toolbar">' +
            '<div class="social-network-user-relations-result-tabs" ' +
            'role="group" aria-label="局部关系结果类别">' +
            '<button type="button" data-user-relations-category="graph" ' +
            'aria-pressed="true">局部图</button>' +
            '<button type="button" data-user-relations-category="nodes" ' +
            'aria-pressed="false">节点列表</button>' +
            '<button type="button" data-user-relations-category="edges" ' +
            'aria-pressed="false">关系列表</button></div>' +
            '<div class="social-network-user-relations-chart-toolbar" ' +
            'role="group" aria-label="局部图视图操作">' +
            '<button class="button button-secondary" type="button" ' +
            'data-user-relations-chart-action="zoom-in" aria-label="放大局部图">放大</button>' +
            '<button class="button button-secondary" type="button" ' +
            'data-user-relations-chart-action="zoom-out" aria-label="缩小局部图">缩小</button>' +
            '<button class="button button-secondary" type="button" ' +
            'data-user-relations-chart-action="reset" aria-label="重置局部图视图">重置视图</button></div></div>' +
            '<div class="social-network-user-relations-list" ' +
            'data-user-relations-list role="region" aria-label="局部关系结果" ' +
            'aria-live="polite"></div>' +
            '<div class="social-network-user-relations-pagination" ' +
            'data-user-relations-pagination></div></section></section>';

        bindNetworkStructureScopeTerms(mount);
        var queryInput = mount.querySelector("#social-network-user-relations-query");
        queryInput.addEventListener("input", function () {
            workspaceState.setCondition("userSearch", { query: queryInput.value });
            var searchStatus = mount.querySelector(
                "[data-user-relations-search-status]");
            searchStatus.textContent = "关键词已修改；点击“查找用户”提交搜索。";
            mount.querySelector("[data-user-relations-candidates]").innerHTML =
                '<div class="social-network-empty">提交当前关键词后显示候选；上次候选不会与新关键词混用。</div>';
        });
        queryInput.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                searchUserRelationsCandidates(mount);
            }
        });
        mount.querySelector("[data-user-relations-search]")
            .addEventListener("click", function () {
                searchUserRelationsCandidates(mount);
            });
        mount.querySelector("[data-user-relations-clear-center]")
            .addEventListener("click", function () {
                workspaceState.setCondition("localNetwork", {
                    centerNodeId: ""
                });
                renderUserRelationsTask(mount);
                queryInput.focus();
            });
        mount.querySelectorAll("[data-user-relations-depth], " +
                "[data-user-relations-top-k], " +
                "[data-user-relations-include-external]")
            .forEach(function (control) {
                control.addEventListener("change", function () {
                    updateUserRelationsDraft(mount);
                });
            });
        mount.querySelector("[data-user-relations-submit]")
            .addEventListener("click", function () {
                loadUserRelationsLocal(mount);
            });
        mount.querySelectorAll("[data-user-relations-category]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    userRelationsResultCategory = button.getAttribute(
                        "data-user-relations-category");
                    userRelationsNodePage = 1;
                    renderUserRelationsLocalResult();
                });
            });
        var resultToolbar = mount.querySelector(
            ".social-network-user-relations-result-toolbar");
        resultToolbar.addEventListener("click", function (event) {
            var button = event.target.closest(
                "[data-user-relations-chart-action]");
            if (!button || !userRelationsLocalChart) {
                return;
            }
            var action = button.getAttribute(
                "data-user-relations-chart-action");
            if (action === "reset") {
                userRelationsLocalChart.setOption(
                    userRelationsChartInitialOption,
                    true
                );
                return;
            }
            userRelationsLocalChart.dispatchAction({
                type: "graphRoam",
                seriesIndex: 0,
                zoom: action === "zoom-in" ? 1.2 : 1 / 1.2,
                originX: userRelationsLocalChart.getWidth() / 2,
                originY: userRelationsLocalChart.getHeight() / 2
            });
        });
        renderUserRelationsTask(mount);
        return mount;
    }

    function renderUserRelationsCandidates(body, query) {
        var mount = document.getElementById("social-network-user-relations");
        if (!mount) {
            return;
        }
        var container = mount.querySelector("[data-user-relations-candidates]");
        var items = ((body || {}).data || {}).items || [];
        var searchStatus = mount.querySelector(
            "[data-user-relations-search-status]");
        if (!items.length) {
            searchStatus.textContent = "本次返回 0 个候选，最多返回 20 项；这不是全网命中总数。";
            container.innerHTML =
                '<div class="social-network-empty">没有匹配的资料用户。外部节点不会出现在此搜索中。</div>';
            return;
        }
        searchStatus.textContent = "本次返回 " + formatNumber(items.length) +
            " 个候选，最多返回 20 项；不代表全网命中总数。搜索范围：用户 ID、用户名、显示名称。";
        container.innerHTML =
            '<p class="social-network-user-relations-candidate-query">搜索词：' +
            escapeHtml(query) + "</p><ul>" +
            items.slice(0, 20).map(function (item) {
                var userId = String(item.userId || "");
                var label = item.displayName || item.username || userId;
                return "<li><div><strong>" + escapeHtml(label) +
                    "</strong><small>资料用户 · " +
                    escapeHtml(item.username || "未提供用户名") + " · ID " +
                    escapeHtml(userId) + "</small></div>" +
                    '<button class="button button-secondary" type="button" ' +
                    'data-user-relations-select="' + escapeHtml(userId) +
                    '">设为中心</button></li>';
            }).join("") + "</ul>";
        container.querySelectorAll("[data-user-relations-select]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    selectUserRelationsCenter(
                        button.getAttribute("data-user-relations-select"),
                        button,
                        false);
                });
            });
    }

    async function searchUserRelationsCandidates(mount) {
        var query = String(workspaceState.getCondition("userSearch").query || "").trim();
        var status = mount.querySelector("[data-user-relations-search-status]");
        if (!query) {
            status.textContent = "请输入非空关键词；空关键词不会列出全部用户。";
            mount.querySelector("[data-user-relations-candidates]").innerHTML =
                '<div class="social-network-empty">尚未发起搜索。</div>';
            return;
        }
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        var sequence = ++userRelationsSearchSequence;
        var requestParameters = { query: query, limit: 20 };
        var requestToken = beginTaskRequest("userSearch", requestParameters);
        userRelationsSearchResult = null;
        status.textContent = "正在查找资料用户…";
        mount.querySelector("[data-user-relations-candidates]").innerHTML =
            '<div class="social-network-network-structure-loading" role="status">正在读取候选…</div>';
        try {
            var body = await request(buildDatasetUrl("users/search", {
                query: query,
                limit: 20
            }, context));
            requireCurrentTaskRequest(requestToken, requestParameters);
            if (!userRelationsRequestIsCurrent(sequence, contextKey, "search")) {
                return;
            }
            userRelationsSearchResult = {
                contextKey: contextKey,
                query: query,
                body: body
            };
            renderUserRelationsCandidates(body, query);
        } catch (error) {
            if (isStaleDatasetRequest(error) ||
                    !userRelationsRequestIsCurrent(sequence, contextKey, "search")) {
                return;
            }
            status.textContent = "用户搜索暂时不可用；输入内容和已成功的关系结果均已保留。";
            mount.querySelector("[data-user-relations-candidates]").innerHTML =
                '<div class="social-network-network-structure-error" role="alert">' +
                "无法读取搜索候选，请检查当前数据后重试。</div>";
        }
    }

    function updateUserRelationsDraft(mount) {
        var depth = mount.querySelector("[data-user-relations-depth]").value;
        var topK = mount.querySelector("[data-user-relations-top-k]").value;
        var includeExternal = mount.querySelector(
            "[data-user-relations-include-external]").checked;
        workspaceState.setCondition("localNetwork", {
            depth: depth,
            topK: topK,
            includeExternal: includeExternal
        });
        var conditions = workspaceState.getCondition("localNetwork");
        var centerId = String(conditions.centerNodeId || "");
        var result = userRelationsLocalResult;
        var resultMatches = result &&
            result.contextKey === datasetContextKey(readDatasetContext()) &&
            result.parameters.centerNodeId === centerId &&
            String(result.parameters.depth) === String(conditions.depth) &&
            String(result.parameters.topK) === String(conditions.topK) &&
            result.parameters.includeExternal === conditions.includeExternal;
        var draft = mount.querySelector("[data-user-relations-draft]");
        mount.querySelector("[data-user-relations-submit]").disabled = !centerId;
        if (!centerId) {
            draft.textContent = "选择中心用户后，才能提交局部关系查询。";
        } else if (resultMatches) {
            draft.textContent = "";
        } else {
            draft.textContent =
                "条件已修改或中心用户尚未查询；下方若有结果，仍对应上一次成功提交。";
        }
        draft.hidden = !draft.textContent;
    }

    function renderUserRelationsTask(mount) {
        if (!mount) {
            return;
        }
        var contextKey = datasetContextKey(readDatasetContext());
        var searchCondition = workspaceState.getCondition("userSearch");
        var localCondition = workspaceState.getCondition("localNetwork");
        var queryInput = mount.querySelector("#social-network-user-relations-query");
        queryInput.value = searchCondition.query || "";
        mount.querySelector("[data-user-relations-depth]").value =
            String(localCondition.depth || "1");
        mount.querySelector("[data-user-relations-top-k]").value =
            String(localCondition.topK || "30");
        mount.querySelector("[data-user-relations-include-external]").checked =
            localCondition.includeExternal !== false;
        var centerSection = mount.querySelector(
            "[data-user-relations-center-section]");
        var centerId = String(localCondition.centerNodeId || "");
        centerSection.hidden = !centerId;
        if (centerId) {
            var currentResult = userRelationsLocalResult &&
                userRelationsLocalResult.contextKey === contextKey
                ? userRelationsLocalResult.body.data
                : null;
            var center = currentResult && (currentResult.nodes || []).find(
                function (node) {
                    return String(node.id) === centerId;
                });
            mount.querySelector("[data-user-relations-center-label]")
                .textContent = (center
                    ? center.displayName || center.username || center.id
                    : "ID " + centerId) + " · ID " + centerId;
        }
        var searchResult = userRelationsSearchResult;
        if (searchResult && searchResult.contextKey === contextKey &&
                searchResult.query === String(searchCondition.query || "").trim()) {
            renderUserRelationsCandidates(searchResult.body, searchResult.query);
        } else {
            mount.querySelector("[data-user-relations-search-status]").textContent =
                queryInput.value.trim()
                    ? "关键词已填写；点击“查找用户”提交搜索。"
                    : "请输入关键词并显式查找；空关键词不会列出全部用户。";
            mount.querySelector("[data-user-relations-candidates]").innerHTML =
                '<div class="social-network-empty">提交关键词后显示候选；外部节点请从排行或关系列表进入。</div>';
        }
        var localResult = userRelationsLocalResult;
        if (localResult && localResult.contextKey === contextKey) {
            renderUserRelationsLocalResult();
        } else {
            mount.querySelector("[data-user-relations-results]").hidden = true;
        }
        updateUserRelationsDraft(mount);
    }

    function selectUserRelationsCenter(userId, opener, useDefaults) {
        var centerNodeId = String(userId || "").trim();
        if (!centerNodeId) {
            return;
        }
        var patch = { centerNodeId: centerNodeId };
        if (useDefaults) {
            patch.depth = "1";
            patch.topK = "30";
            patch.includeExternal = true;
        }
        workspaceState.setCondition("localNetwork", patch);
        var taskCard = document.querySelector(
            "#page-social-network-task .social-network-task-card");
        var mount = taskCard && ensureUserRelationsTaskView(taskCard);
        if (mount) {
            renderUserRelationsTask(mount);
            loadUserRelationsLocal(mount);
        }
    }

    function openUserRelationsAtCenter(userId, opener) {
        var centerNodeId = String(userId || "").trim();
        if (!centerNodeId) {
            return;
        }
        workspaceState.setCondition("localNetwork", {
            centerNodeId: centerNodeId,
            depth: "1",
            topK: "30",
            includeExternal: true
        });
        if (activeSocialNetworkTask === "user-relations") {
            selectUserRelationsCenter(centerNodeId, opener, false);
            return;
        }
        window.location.hash = "#/social-network/relations/user";
    }

    function resetUserRelationsForDatasetChange() {
        userRelationsSearchSequence += 1;
        userRelationsLocalSequence += 1;
        workspaceState.invalidateRequest("userSearch");
        workspaceState.invalidateRequest("localNetwork");
        disposeUserRelationsLocalChart();
        userRelationsContextKey = datasetContextKey(readDatasetContext());
        userRelationsSearchResult = null;
        userRelationsLocalResult = null;
        userRelationsNodePage = 1;
        workspaceState.setCondition("localNetwork", { centerNodeId: "" });
    }

    function disposeUserRelationsLocalChart() {
        if (userRelationsChartResizeObserver) {
            userRelationsChartResizeObserver.disconnect();
            userRelationsChartResizeObserver = null;
        }
        if (userRelationsChartResizeHandler) {
            window.removeEventListener("resize", userRelationsChartResizeHandler);
            userRelationsChartResizeHandler = null;
        }
        if (userRelationsLocalChart) {
            userRelationsLocalChart.dispose();
            userRelationsLocalChart = null;
        }
        userRelationsChartInitialOption = null;
    }

    function userRelationsNodeIsExternal(node) {
        return String(node.nodeType || node.status || "")
            .toLowerCase().indexOf("external") >= 0;
    }

    function userRelationsGraphOption(data, nodes, edges) {
        var centerId = String(data.centerNodeId || "");
        var nodeById = Object.create(null);
        nodes.forEach(function (node) {
            nodeById[String(node.id)] = node;
        });
        var directedPairs = Object.create(null);
        edges.forEach(function (edge) {
            directedPairs[String(edge.source) + "\u0000" +
                String(edge.target)] = true;
        });
        return {
            animation: false,
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    var node = params.data || {};
                    var id = String(node.id || "");
                    var label = node.displayName || node.username || id;
                    var type = userRelationsNodeIsExternal(node)
                        ? "外部节点 · 资料未采集"
                        : "资料用户";
                    return escapeHtml(label) + "<br>ID " +
                        escapeHtml(id) + "<br>" + type;
                }
            },
            series: [{
                type: "graph",
                layout: "force",
                roam: true,
                draggable: true,
                symbol: "circle",
                symbolSize: 18,
                data: nodes.map(function (node) {
                    var id = String(node.id || "");
                    var external = userRelationsNodeIsExternal(node);
                    var isCenter = id === centerId;
                    var name = String(node.displayName || node.username || id);
                    return {
                        id: id,
                        name: name,
                        displayName: name,
                        username: node.username || "",
                        nodeType: node.nodeType || node.status || "profile",
                        category: external ? 1 : 0,
                        label: isCenter ? "中心 · " + name : name,
                        itemStyle: {
                            color: isCenter
                                ? "#C86B43"
                                : external ? "#C7C3BB" : "#718077",
                            borderColor: isCenter ? "#8F4328" : "#FBFAF7",
                            borderWidth: isCenter ? 3 : 2
                        }
                    };
                }),
                links: edges.map(function (edge) {
                    var source = String(edge.source || "");
                    var target = String(edge.target || "");
                    var reciprocal = Boolean(directedPairs[
                        target + "\u0000" + source]);
                    var sourceNode = nodeById[source];
                    var targetNode = nodeById[target];
                    return {
                        source: source,
                        target: target,
                        relationType: edge.relationType || "following",
                        lineStyle: {
                            color: "rgba(66, 75, 70, 0.34)",
                            width: 1,
                            curveness: reciprocal && source.localeCompare(target) < 0
                                ? 0.18
                                : reciprocal ? -0.18 : 0
                        },
                        emphasis: {
                            lineStyle: {
                                color: "#C86B43",
                                width: 2
                            }
                        },
                        sourceType: sourceNode && sourceNode.nodeType,
                        targetType: targetNode && targetNode.nodeType
                    };
                }),
                categories: [
                    { name: "资料用户" },
                    { name: "外部节点 · 资料未采集" }
                ],
                edgeSymbol: ["none", "arrow"],
                edgeSymbolSize: [0, 9],
                edgeLabel: { show: false },
                label: {
                    show: nodes.length <= 24,
                    position: "right",
                    distance: 7,
                    color: "#454B47",
                    fontSize: 11,
                    formatter: function (params) {
                        var name = String(params.data.name || "");
                        return name.length > 24
                            ? name.slice(0, 23) + "…"
                            : name;
                    }
                },
                emphasis: {
                    focus: "adjacency",
                    label: { show: true, fontWeight: 700 }
                },
                lineStyle: {
                    opacity: 0.72
                },
                force: {
                    initLayout: "circular",
                    repulsion: 180,
                    edgeLength: 105,
                    gravity: 0.08,
                    layoutAnimation: false
                },
                animation: false
            }]
        };
    }

    function showUserRelationsGraphFallback(message) {
        var mount = document.getElementById("social-network-user-relations");
        var list = mount && mount.querySelector("[data-user-relations-list]");
        if (!list) {
            return;
        }
        var chartToolbar = mount.querySelector(
            ".social-network-user-relations-chart-toolbar");
        if (chartToolbar) {
            chartToolbar.hidden = true;
        }
        list.innerHTML =
            '<div class="social-network-user-relations-graph-error" role="alert">' +
            "<p>" + escapeHtml(message) + "</p>" +
            '<button class="button button-secondary" type="button" ' +
            'data-user-relations-graph-fallback>切换到节点列表</button></div>';
        list.querySelector("[data-user-relations-graph-fallback]")
            .addEventListener("click", function () {
                userRelationsResultCategory = "nodes";
                renderUserRelationsLocalResult();
            });
    }

    function mountUserRelationsLocalChart(data, nodes, edges) {
        if (!nodes.length) {
            var emptyList = document.querySelector(
                "#social-network-user-relations [data-user-relations-list]");
            emptyList.innerHTML =
                '<div class="social-network-empty">本次局部查询没有返回可绘制节点。</div>';
            return;
        }
        if (typeof echarts === "undefined") {
            showUserRelationsGraphFallback(
                "局部图组件不可用；节点与关系列表仍可正常查看。");
            return;
        }
        var mount = document.getElementById("social-network-user-relations");
        var chartElement = mount && mount.querySelector(
            "[data-user-relations-chart]");
        if (!chartElement) {
            return;
        }
        try {
            userRelationsChartInitialOption =
                userRelationsGraphOption(data, nodes, edges);
            userRelationsLocalChart = echarts.init(
                chartElement,
                null,
                { renderer: "canvas" }
            );
            userRelationsLocalChart.setOption(
                userRelationsChartInitialOption,
                true
            );
            userRelationsLocalChart.on("click", function (params) {
                if (params.dataType !== "node" || !params.data ||
                        !params.data.id) {
                    return;
                }
                window.openSocialNetworkUserDetail(
                    String(params.data.id),
                    null,
                    chartElement
                );
            });
            if (typeof ResizeObserver !== "undefined") {
                userRelationsChartResizeObserver = new ResizeObserver(function () {
                    if (userRelationsLocalChart &&
                            chartElement.getBoundingClientRect().width > 0) {
                        userRelationsLocalChart.resize();
                    }
                });
                userRelationsChartResizeObserver.observe(chartElement);
            } else {
                userRelationsChartResizeHandler = function () {
                    if (userRelationsLocalChart &&
                            chartElement.getBoundingClientRect().width > 0) {
                        userRelationsLocalChart.resize();
                    }
                };
                window.addEventListener(
                    "resize",
                    userRelationsChartResizeHandler
                );
            }
        } catch (error) {
            disposeUserRelationsLocalChart();
            showUserRelationsGraphFallback(
                "局部图渲染失败；节点与关系列表仍可正常查看。");
        }
    }

    function renderUserRelationsLocalResult() {
        var mount = document.getElementById("social-network-user-relations");
        var result = userRelationsLocalResult;
        if (!mount || !result ||
                result.contextKey !== datasetContextKey(readDatasetContext())) {
            return;
        }
        var data = result.body.data || {};
        var nodes = Array.isArray(data.nodes) ? data.nodes : [];
        var edges = Array.isArray(data.edges) ? data.edges : [];
        var centerId = String(data.centerNodeId || result.parameters.centerNodeId);
        var nodeNames = Object.create(null);
        nodes.forEach(function (node) {
            var nodeId = String(node.id || "");
            nodeNames[nodeId] = node.displayName || node.username || nodeId;
        });
        var centerNode = nodes.find(function (node) {
            return String(node.id) === centerId;
        });
        var depth = Number(data.depth || result.parameters.depth);
        var topK = Number(data.topK || result.parameters.topK);
        var includesExternal = data.includeExternalNodes !== undefined
            ? data.includeExternalNodes === true
            : result.parameters.includeExternal;
        mount.querySelector("[data-user-relations-results]").hidden = false;
        mount.querySelector("[data-user-relations-result-summary]").textContent =
            "中心：" + (centerNode
                ? centerNode.displayName || centerNode.username || centerId
                : centerId) + " · " + centerId + " · " +
            (depth === 2 ? "二阶" : "一阶") + " · 邻居上限 " + topK +
            "（不含中心） · " +
            (includesExternal ? "包含外部节点" : "排除外部节点");
        var rangeNote = "本次返回 " + formatNumber(nodes.length) +
            " 个节点" + networkStructureTerm(
                "user-relations-node",
                "节点",
                "关系图中的一个圆点，代表一个账号；中心用户、资料用户和未采集资料的外部账号都算节点。"
            ) + "（含中心）和 " + formatNumber(edges.length) +
            " 条有向边" + networkStructureTerm(
                "user-relations-directed-edge",
                "有向边",
                "表示一条有方向的关注关系。A → B 表示 A 关注 B；如果 B 也关注 A，则另有一条反方向关系。"
            ) + "。查找周边账号时不区分关注方向；图中箭头仍表示实际关注方向。" +
            "候选账号按其在关注网络中的连接位置排序，并受所选邻居上限限制，不代表全部邻居。";
        if (centerNode && String(centerNode.nodeType || centerNode.status || "")
                .toLowerCase().indexOf("external") >= 0 && !includesExternal) {
            rangeNote += " 当前中心为外部节点；按接口规则，即使关闭外部节点选项也会保留中心。";
        }
        mount.querySelector("[data-user-relations-range-note]").innerHTML =
            rangeNote;
        var truncation = mount.querySelector("[data-user-relations-truncation]");
        if (data.truncated === true) {
            var limits = data.limits || {};
            truncation.hidden = false;
            truncation.textContent =
                (data.truncationReason || "本次返回结果达到服务端硬上限。") +
                " 服务端硬上限：" +
                formatNumber(limits.maxNodes === undefined ? 100 : limits.maxNodes) +
                " 个节点、" +
                formatNumber(limits.maxEdges === undefined ? 500 : limits.maxEdges) +
                " 条有向边。";
        } else {
            truncation.hidden = true;
            truncation.textContent = "";
        }
        mount.querySelectorAll("[data-user-relations-category]")
            .forEach(function (button) {
                button.setAttribute("aria-pressed",
                    button.getAttribute("data-user-relations-category") ===
                        userRelationsResultCategory ? "true" : "false");
            });
        mount.querySelector(
            ".social-network-user-relations-chart-toolbar").hidden =
            userRelationsResultCategory !== "graph" || !nodes.length;

        var list = mount.querySelector("[data-user-relations-list]");
        var pagination = mount.querySelector(
            "[data-user-relations-pagination]");
        disposeUserRelationsLocalChart();
        pagination.innerHTML = "";
        if (userRelationsResultCategory === "graph") {
            list.innerHTML =
                '<div class="social-network-user-relations-chart-panel">' +
                '<div class="social-network-user-relations-chart-legend" ' +
                'aria-label="局部图图例"><span><i class="is-center"></i>中心</span>' +
                '<span><i class="is-profile"></i>资料用户</span>' +
                '<span><i class="is-external"></i>外部节点 · 资料未采集</span>' +
                '<span><b aria-hidden="true">→</b>真实关注方向</span></div>' +
                '<div class="social-network-user-relations-chart" ' +
                'data-user-relations-chart role="img" tabindex="0" ' +
                'aria-label="当前局部关注关系图；可缩放或拖动画布，节点列表提供键盘详情入口"></div>' +
                (edges.length
                    ? ""
                    : '<p class="social-network-user-relations-chart-note">' +
                        "本次查询没有显示关系。邻居层级、数量上限或外部账号筛选都会影响结果；" +
                        "不能据此判断该账号在完整网络中没有关系。</p>") +
                "</div>";
            mountUserRelationsLocalChart(data, nodes, edges);
            return;
        }
        if (userRelationsResultCategory === "edges") {
            var visibleEdges = edges.slice(0, 20);
            list.innerHTML = edges.length
                ? '<p class="social-network-user-relations-list-note">本次返回 ' +
                    escapeHtml(formatNumber(edges.length)) +
                    " 条有向边；此列表最多展示前 20 条，局部图展示本次响应中的全部边。</p>" +
                    '<ol class="social-network-user-relations-edges">' +
                    visibleEdges.map(function (edge) {
                        var source = String(edge.source || "");
                        var target = String(edge.target || "");
                        return "<li><span>" + escapeHtml(nodeNames[source] || source) +
                            "</span><code>" + escapeHtml(source) + "</code>" +
                            '<b aria-label="关注方向">→</b><span>' +
                            escapeHtml(nodeNames[target] || target) +
                            "</span><code>" + escapeHtml(target) + "</code></li>";
                    }).join("") + "</ol>"
                : '<div class="social-network-empty">本次范围没有返回有向关系；节点结果仍保留。</div>';
            pagination.textContent = edges.length > 20
                ? "关系列表显示 20 / " + formatNumber(edges.length) + " 条；节点数与列表条数口径不同。"
                : "";
            return;
        }

        var pageSize = 10;
        var pageCount = Math.max(1, Math.ceil(nodes.length / pageSize));
        userRelationsNodePage = Math.min(userRelationsNodePage, pageCount);
        var start = (userRelationsNodePage - 1) * pageSize;
        var pageNodes = nodes.slice(start, start + pageSize);
        list.innerHTML = nodes.length
            ? '<p class="social-network-user-relations-list-note">本次返回 ' +
                escapeHtml(formatNumber(nodes.length)) +
                " 个节点（含中心）；以下分页只浏览本次返回集合，不代表后端全网分页。</p>" +
                '<div class="social-network-user-relations-nodes">' +
                pageNodes.map(function (node) {
                    var nodeId = String(node.id || "");
                    var external = String(node.nodeType || node.status || "")
                        .toLowerCase().indexOf("external") >= 0;
                    var label = external
                        ? "外部节点 · 资料未采集"
                        : node.displayName || node.username || nodeId;
                    return '<article class="social-network-user-relations-node">' +
                        "<div><strong>" + escapeHtml(label) + "</strong>" +
                        "<small>" + (external ? "外部节点" : "资料用户") +
                        " · ID " + escapeHtml(nodeId) + "</small></div>" +
                        "<p>数据内被关注数（入度） " +
                        escapeHtml(formatNumber(node.inDegree)) +
                        " · 数据内关注数（出度） " +
                        escapeHtml(formatNumber(node.outDegree)) + "</p>" +
                        '<div class="social-network-user-relations-node-actions">' +
                        '<button class="button button-secondary" type="button" ' +
                        'data-user-relations-detail="' + escapeHtml(nodeId) +
                        '">查看详情</button>' +
                        '<button class="button button-secondary" type="button" ' +
                        'data-user-relations-use-center="' + escapeHtml(nodeId) +
                        '">以此为中心</button></div></article>';
                }).join("") + "</div>"
            : '<div class="social-network-empty">本次没有返回节点。</div>';
        pagination.innerHTML = nodes.length > pageSize
            ? '<button class="button button-secondary" type="button" ' +
                'data-user-relations-page="' + (userRelationsNodePage - 1) +
                '" ' + (userRelationsNodePage <= 1 ? "disabled" : "") +
                ">上一页</button><span>本次返回集合 " +
                escapeHtml(formatNumber(userRelationsNodePage)) + " / " +
                escapeHtml(formatNumber(pageCount)) + " 页</span>" +
                '<button class="button button-secondary" type="button" ' +
                'data-user-relations-page="' + (userRelationsNodePage + 1) +
                '" ' + (userRelationsNodePage >= pageCount ? "disabled" : "") +
                ">下一页</button>"
            : "";
        list.querySelectorAll("[data-user-relations-detail]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    window.openSocialNetworkUserDetail(
                        button.getAttribute("data-user-relations-detail"),
                        null,
                        button);
                });
            });
        list.querySelectorAll("[data-user-relations-use-center]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    selectUserRelationsCenter(
                        button.getAttribute("data-user-relations-use-center"),
                        button,
                        false);
                });
            });
        pagination.querySelectorAll("[data-user-relations-page]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    userRelationsNodePage = Number(button.getAttribute(
                        "data-user-relations-page"));
                    renderUserRelationsLocalResult();
                });
            });
    }

    async function loadUserRelationsLocal(mount) {
        var conditions = workspaceState.getCondition("localNetwork");
        var centerNodeId = String(conditions.centerNodeId || "").trim();
        if (!centerNodeId) {
            return;
        }
        var context = readDatasetContext();
        var contextKey = datasetContextKey(context);
        var parameters = {
            centerNodeId: centerNodeId,
            depth: String(conditions.depth || "1"),
            topK: String(conditions.topK || "30"),
            includeExternal: conditions.includeExternal !== false
        };
        var requestParameters = {
            centerNodeId: parameters.centerNodeId,
            depth: parameters.depth,
            topK: parameters.topK,
            includeExternal: parameters.includeExternal
        };
        var sequence = ++userRelationsLocalSequence;
        var requestToken = beginTaskRequest("localNetwork", requestParameters);
        var previousError = mount.querySelector("[data-user-relations-query-error]");
        if (previousError) {
            previousError.remove();
        }
        var draft = mount.querySelector("[data-user-relations-draft]");
        draft.textContent =
            "正在读取新提交的条件；若下方已有结果，它仍标注上一次成功范围。";
        draft.hidden = false;
        try {
            var body = await request(buildDatasetUrl("network/local", {
                centerNodeId: parameters.centerNodeId,
                depth: parameters.depth,
                topK: parameters.topK,
                includeExternalNodes: parameters.includeExternal
            }, context));
            requireCurrentTaskRequest(requestToken, requestParameters);
            if (!userRelationsRequestIsCurrent(sequence, contextKey, "local")) {
                return;
            }
            userRelationsLocalResult = {
                contextKey: contextKey,
                parameters: parameters,
                body: body
            };
            userRelationsNodePage = 1;
            renderUserRelationsTask(mount);
            renderUserRelationsLocalResult();
            setText("social-network-task-status",
                "已读取 " + formatNumber((body.data || {}).nodes.length) +
                " 个局部节点");
            setText("social-network-task-message",
                "显示本次局部查询返回的账号与关注关系；展示数量受邻居上限影响，不代表完整网络。");
        } catch (error) {
            if (isStaleDatasetRequest(error) ||
                    !userRelationsRequestIsCurrent(sequence, contextKey, "local")) {
                return;
            }
            var draft = mount.querySelector("[data-user-relations-draft]");
            draft.textContent =
                "本次关系查询失败；如有结果，仍保留并标注上一次成功条件。";
            draft.hidden = false;
            var results = mount.querySelector("[data-user-relations-results]");
            if (!userRelationsLocalResult ||
                    userRelationsLocalResult.contextKey !== contextKey) {
                results.hidden = true;
            }
            var status = document.createElement("p");
            status.className = "social-network-user-relations-error";
            status.setAttribute("role", "alert");
            status.textContent =
                "无法读取所选中心用户的局部关系；请检查中心 ID 或当前数据后重试。";
            var existing = mount.querySelector(
                "[data-user-relations-query-error]");
            if (existing) {
                existing.remove();
            }
            status.setAttribute("data-user-relations-query-error", "");
            mount.querySelector("[data-user-relations-center-section]")
                .appendChild(status);
        }
    }

    function handleSocialNetworkDatasetContextChanged() {
        resetUserRelationsForDatasetChange();
        if (document.body.classList.contains("social-network-shell-active")) {
            if (activeSocialNetworkTask === "overview") {
                loadOverviewSummary();
            } else if (isDataQualityTask(activeSocialNetworkTask)) {
                loadDatasetPage(activeSocialNetworkTask === "quality");
            } else if (activeSocialNetworkTask === "relations") {
                loadNetworkStructure();
            } else if (activeSocialNetworkTask === "ranking") {
                loadRankingTask();
            } else if (activeSocialNetworkTask === "interaction") {
                workspaceState.invalidateRequest("interaction");
                interactionTaskResultCache = null;
                var interactionTaskCard = document.querySelector(
                    "#page-social-network-task .social-network-task-card");
                if (interactionTaskCard) {
                    startInteractionTaskPage(interactionTaskCard);
                }
            } else if (activeSocialNetworkTask === "topic") {
                clearTopicTaskForDatasetChange();
                var topicTaskCard = document.querySelector(
                    "#page-social-network-task .social-network-task-card");
                if (topicTaskCard) {
                    var topicMount = document.getElementById(
                        "social-network-topic-task");
                    if (topicMount) {
                        topicMount.remove();
                    }
                    startTopicTaskPage(topicTaskCard);
                }
            } else if (activeSocialNetworkTask === "user-relations") {
                var taskCard = document.querySelector(
                    "#page-social-network-task .social-network-task-card");
                if (taskCard) {
                    var mount = ensureUserRelationsTaskView(taskCard);
                    renderUserRelationsTask(mount);
                }
                setText("social-network-task-status", "数据已切换，请重新选择中心用户");
                setText("social-network-task-message",
                    "旧数据集的搜索候选与局部关系结果已清除；当前数据不会沿用旧结果。");
                loadShellDatasetVersion();
            } else {
                loadShellDatasetVersion();
            }
        }
    }

    window.addEventListener(
        "social-network-dataset-context-changed",
        handleSocialNetworkDatasetContextChanged
    );

    function setTaskUploadStatus(message, state) {
        var status = document.getElementById("social-network-task-upload-status");
        if (!status) {
            return;
        }
        status.textContent = message;
        status.className = "social-network-upload-status" +
            (state ? " is-" + state : "");
    }

    function renderTaskUploadFiles() {
        var container = document.getElementById("social-network-task-upload-files");
        if (!container) {
            return;
        }
        container.innerHTML = uploadFiles.map(function (entry) {
            var file = entry.file;
            var issue = uploadFileIssue(file);
            return '<div class="social-network-upload-file-row">' +
                '<div class="social-network-upload-file-meta">' +
                '<strong title="' + escapeHtml(file.name) + '">' +
                escapeHtml(file.name) + '</strong>' +
                '<small>' + formatUploadBytes(file.size) + '</small>' +
                (issue ? '<span class="social-network-upload-file-error">' +
                    escapeHtml(issue) + '</span>' : '') +
                '</div>' +
                '<select class="social-network-upload-role" data-task-upload-file-id="' +
                escapeHtml(entry.partId) + '" aria-label="选择 ' +
                escapeHtml(file.name) + ' 的数据角色">' +
                '<option value="">选择数据角色</option>' +
                '<option value="users"' + (entry.role === "users" ? " selected" : "") +
                    '>用户 users</option>' +
                '<option value="relationships"' +
                    (entry.role === "relationships" ? " selected" : "") +
                    '>关注关系 relationships</option>' +
                '<option value="tweets"' + (entry.role === "tweets" ? " selected" : "") +
                    '>推文 tweets</option>' +
                '</select>' +
                '<button class="social-network-upload-file-remove" type="button" ' +
                'data-task-upload-remove-id="' + escapeHtml(entry.partId) +
                '">移除</button>' +
                '</div>';
        }).join("");
        container.querySelectorAll("[data-task-upload-remove-id]")
            .forEach(function (button) {
                button.addEventListener("click", function () {
                    var partId = button.getAttribute("data-task-upload-remove-id");
                    workspaceState.replaceUploadFiles(uploadFiles.filter(function (entry) {
                        return entry.partId !== partId;
                    }));
                    renderTaskUploadFiles();
                    updateUploadControls();
                    var issue = uploadSelectionIssue();
                    setTaskUploadStatus(uploadFiles.length
                        ? (issue || "文件选择已更新，可提交上传检查。")
                        : "文件已移除；来源说明已保留，可继续添加文件。",
                    issue && uploadFiles.length ? "error" : "");
                });
            });
        container.querySelectorAll("[data-task-upload-file-id]")
            .forEach(function (select) {
                select.addEventListener("change", function () {
                    var partId = select.getAttribute("data-task-upload-file-id");
                    var entry = uploadFiles.find(function (item) {
                        return item.partId === partId;
                    });
                    if (entry) {
                        entry.role = select.value;
                    }
                    updateUploadControls();
                    var issue = uploadSelectionIssue();
                    setTaskUploadStatus(issue ||
                        "文件角色已更新，可提交上传检查。",
                    issue ? "error" : "");
                });
            });
    }

    function addTaskUploadFiles(fileList) {
        var additions = Array.prototype.slice.call(fileList || []);
        if (!additions.length) {
            return;
        }
        if (uploadFiles.length + additions.length > MAX_UPLOAD_FILES) {
            setTaskUploadStatus("单次最多选择 10 个文件；本次选择未添加。", "error");
            return;
        }
        additions.forEach(function (file, index) {
            uploadFiles.push({
                partId: "part-" + Date.now() + "-" + uploadFiles.length + "-" + index,
                file: file,
                role: ""
            });
        });
        renderTaskUploadFiles();
        updateUploadControls();
        var issue = uploadSelectionIssue();
        setTaskUploadStatus(issue || "文件已添加。请为每个文件指定数据角色。",
            issue ? "error" : "");
    }

    function renderTaskUploadSelection(taskCard) {
        var mount = document.getElementById("social-network-task-upload-content");
        if (!mount) {
            mount = document.createElement("div");
            mount.id = "social-network-task-upload-content";
            mount.className = "social-network-task-upload";
            taskCard.appendChild(mount);
        }
        mount.innerHTML =
            '<div class="social-network-upload-contract">' +
            '<strong>文件要求</strong>' +
            '<span>users · relationships · tweets</span>' +
            '<small>支持 CSV 或 JSONL（UTF-8，可含 BOM）；最多 10 个文件，同一角色可添加多个。' +
            '单文件 128 MiB、单次请求 160 MiB 为设计上限，未进行超大文件压力验收。</small>' +
            '</div>' +
            '<label class="social-network-upload-file-picker" ' +
            'for="social-network-task-upload-input">' +
            '<span>选择数据文件</span>' +
            '<small>请选择 1–10 个 .csv 或 .jsonl 文件</small>' +
            '<input id="social-network-task-upload-input" type="file" multiple ' +
            'accept=".csv,.jsonl,text/csv,application/x-ndjson">' +
            '</label>' +
            '<div id="social-network-task-upload-files" ' +
            'class="social-network-upload-files" aria-live="polite"></div>' +
            '<label class="social-network-upload-source-label" ' +
            'for="social-network-task-upload-source">' +
            '来源说明 <span>可留空</span>' +
            '<textarea id="social-network-task-upload-source" rows="2" maxlength="1000" ' +
            'placeholder="例如：数据来源、采集时间范围或整理说明"></textarea>' +
            '</label>' +
            '<div class="social-network-upload-responsibility" role="note">' +
            '<strong>使用边界</strong>' +
            '<p>请确认你有权在当前分析会话中使用这些数据。平台不核验数据来源或授权；' +
            '上传数据仅用于当前分析会话，不会永久保存。展示此提示即可继续，无需勾选确认。</p>' +
            '</div>' +
            '<p id="social-network-task-upload-status" class="social-network-upload-status" ' +
            'role="status" aria-live="polite">选择文件后，可为每个文件指定数据角色。' +
            '来源说明为可选项；获得任务编号后才能取消检查。</p>' +
            '<div class="social-network-upload-actions">' +
            '<button id="social-network-task-upload-submit" class="button button-primary" ' +
            'type="button" disabled ' +
            'aria-describedby="social-network-task-upload-status">' +
            '上传并检查</button>' +
            '<button id="social-network-task-upload-cancel" class="button button-secondary" ' +
            'type="button" hidden>取消检查</button>' +
            '<button id="social-network-task-upload-retry" class="button button-secondary" ' +
            'type="button" hidden>重试读取状态</button>' +
            '</div>';

        var fileInput = document.getElementById("social-network-task-upload-input");
        fileInput.addEventListener("change", function () {
            addTaskUploadFiles(fileInput.files);
            fileInput.value = "";
        });
        var sourceInput = document.getElementById("social-network-task-upload-source");
        sourceInput.value = uploadState.uploadDraft.sourceDescription || "";
        sourceInput.addEventListener("input", function () {
            uploadState.uploadDraft.sourceDescription = sourceInput.value;
        });
        document.getElementById("social-network-task-upload-submit")
            .addEventListener("click", submitUpload);
        document.getElementById("social-network-task-upload-cancel")
            .addEventListener("click", cancelUploadTask);
        document.getElementById("social-network-task-upload-retry")
            .addEventListener("click", function () {
                uploadPollFailure = null;
                uploadPollRetryCount = 0;
                uploadStatus("正在重新读取检查状态…", "busy");
                updateUploadControls();
                pollUploadTask();
            });
        renderTaskUploadFiles();
        if (uploadSubmitting) {
            uploadStatus("正在安全接收文件…", "busy");
        } else if (uploadState.uploadTaskView) {
            if (["ready", "failed"].indexOf(
                    uploadState.uploadTaskView.status) >= 0) {
                renderTaskUploadResult(uploadState.uploadTaskView);
            } else {
                renderTaskUploadState(uploadState.uploadTaskView);
            }
        }
        updateUploadControls();
    }

    window.onSocialNetworkTaskPageShown = function (task) {
        var nextTask = task || "";
        if (activeSocialNetworkTask === "groups" && nextTask !== "groups" &&
                window.socialNetworkGroupsPage) {
            window.socialNetworkGroupsPage.onHidden();
        }
        if (activeSocialNetworkTask === "user-relations" &&
                nextTask !== "user-relations") {
            userRelationsSearchSequence += 1;
            userRelationsLocalSequence += 1;
            workspaceState.invalidateRequest("userSearch");
            workspaceState.invalidateRequest("localNetwork");
        }
        if (activeSocialNetworkTask === "interaction" &&
                nextTask !== "interaction") {
            workspaceState.invalidateRequest("interaction");
            interactionTaskIdentitySequence += 1;
            interactionTaskIdentityPending = false;
        }
        if (activeSocialNetworkTask === "topic" && nextTask !== "topic") {
            workspaceState.invalidateRequest("topic");
            topicTaskIdentitySequence += 1;
            topicTaskIdentityPending = false;
            [
                "social-network-topic-detail-dialog",
                "social-network-topic-rules-dialog"
            ].forEach(function (id) {
                var dialog = document.getElementById(id);
                if (dialog && dialog.open) {
                    dialog.close();
                }
            });
            if (activeModuleExportType === "topic") {
                var topicExportDialog = document.getElementById(
                    "social-network-module-export-dialog");
                if (topicExportDialog && topicExportDialog.open) {
                    topicExportDialog.close();
                }
            }
        }
        if (activeSocialNetworkTask !== nextTask) {
            closeSocialUserDrawer(false);
        }
        activeSocialNetworkTask = nextTask;
        var routeTable = window.socialNetworkRouteTable;
        var taskDetails = routeTable ? routeTable.getTask(task) : null;
        var title = document.getElementById("social-network-task-title");
        var taskEyebrow = document.getElementById(
            "social-network-task-eyebrow"
        );
        var status = document.getElementById("social-network-task-status");
        var message = document.getElementById("social-network-task-message");
        var returnLink = document.getElementById("social-network-task-return");
        var overview = document.getElementById("social-network-overview-home");
        var contentTabs = document.getElementById(
            "social-network-content-tabs"
        );
        var relationsTabs = document.getElementById(
            "social-network-relations-tabs"
        );
        var groupPage = document.getElementById(
            "social-network-group-page"
        );
        var datasetPage = document.getElementById("social-network-dataset-page");
        var taskCard = document.querySelector(
            "#page-social-network-task .social-network-task-card"
        );
        if (!title || !status || !message || !returnLink) {
            return;
        }
        var qualityReportExportButton = document.getElementById(
            "social-network-quality-report-export"
        );
        var overviewRefreshButton = document.getElementById(
            "social-network-overview-retry"
        );
        if (qualityReportExportButton) {
            qualityReportExportButton.hidden = true;
        }
        if (overviewRefreshButton) {
            overviewRefreshButton.hidden = true;
        }
        if (task !== "overview") {
            overviewRequestSequence += 1;
        }
        if (overview) {
            overview.hidden = task !== "overview";
        }
        if (datasetPage) {
            datasetPage.hidden = !isDataQualityTask(task);
        }
        if (groupPage) {
            groupPage.hidden = task !== "groups";
        }
        if (contentTabs) {
            var contentPathByTask = {
                interaction: "#/social-network/content",
                topic: "#/social-network/content/topic"
            };
            var currentContentPath = contentPathByTask[task] || "";
            contentTabs.hidden = !currentContentPath;
            contentTabs.querySelectorAll("a").forEach(function (link) {
                if (link.getAttribute("href") === currentContentPath) {
                    link.setAttribute("aria-current", "page");
                } else {
                    link.removeAttribute("aria-current");
                }
            });
        }
        if (relationsTabs) {
            var relationsPathByTask = {
                relations: "#/social-network/relations",
                ranking: "#/social-network/relations/ranking",
                "user-relations": "#/social-network/relations/user",
                groups: "#/social-network/relations/groups"
            };
            var currentRelationsPath = relationsPathByTask[task] || "";
            relationsTabs.hidden = !currentRelationsPath;
            relationsTabs.querySelectorAll("a").forEach(function (link) {
                if (link.getAttribute("href") === currentRelationsPath) {
                    link.setAttribute("aria-current", "page");
                } else {
                    link.removeAttribute("aria-current");
                }
            });
        }
        if (taskCard) {
            taskCard.hidden = task === "overview" ||
                isDataQualityTask(task) || task === "groups";
            taskCard.classList.toggle(
                "is-network-structure",
                task === "relations"
            );
            taskCard.classList.toggle("is-node-ranking", task === "ranking");
            taskCard.classList.toggle(
                "is-user-relations",
                task === "user-relations"
            );
            taskCard.classList.toggle(
                "is-interaction-analysis",
                task === "interaction"
            );
            taskCard.classList.toggle("is-topic-analysis", task === "topic");
        }
        if (!isDataQualityTask(task)) {
            qualityReportRequestSequence += 1;
            var qualityReport = document.getElementById(
                "social-network-quality-report"
            );
            if (qualityReport) {
                qualityReport.replaceChildren();
            }
        }
        if (task !== "relations") {
            networkStructureRequestSequence += 1;
            networkStructureLookup = null;
            var networkStructureMountElement = document.getElementById(
                "social-network-network-structure"
            );
            if (networkStructureMountElement) {
                networkStructureMountElement.remove();
            }
        }
        if (task !== "ranking") {
            rankingTaskRequestSequence += 1;
            var rankingTaskMountElement = document.getElementById(
                "social-network-node-ranking"
            );
            if (rankingTaskMountElement) {
                rankingTaskMountElement.remove();
            }
        }
        if (task !== "user-relations") {
            var userRelationsMountElement = document.getElementById(
                "social-network-user-relations");
            disposeUserRelationsLocalChart();
            if (userRelationsMountElement) {
                userRelationsMountElement.remove();
            }
        }
        if (task !== "interaction") {
            var interactionTaskMountElement = document.getElementById(
                "social-network-interaction-task");
            if (interactionTaskMountElement) {
                interactionTaskMountElement.remove();
            }
        }
        if (task !== "topic") {
            var topicTaskMountElement = document.getElementById(
                "social-network-topic-task");
            if (topicTaskMountElement) {
                topicTaskMountElement.remove();
            }
        }
        title.textContent = taskDetails
            ? taskDetails.title
            : "社交网络分析";
        if (taskEyebrow) {
            var taskEyebrows = {
                overview: "ANALYSIS OVERVIEW",
                relations: "NETWORK STRUCTURE",
                ranking: "NODE RANKING",
                "user-relations": "USER RELATIONS",
                groups: "GROUPS & COMMUNITIES",
                interaction: "INTERACTION ANALYSIS",
                topic: "TOPIC ANALYSIS",
                data: "DATA & QUALITY",
                quality: "DATA & QUALITY",
                upload: "DATA UPLOAD"
            };
            taskEyebrow.textContent = taskEyebrows[task] ||
                "社交网络分析工作台";
        }
        if (task === "overview") {
            loadOverviewSummary();
            return;
        }
        if (isDataQualityTask(task)) {
            loadDatasetPage(task === "quality");
            return;
        }
        var taskUploadContent = document.getElementById(
            "social-network-task-upload-content");
        if (task !== "upload" && taskUploadContent) {
            taskUploadContent.remove();
        }
        if (task === "upload") {
            loadShellDatasetVersion();
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network/data";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回当前数据";
            renderTaskUploadSelection(taskCard);
            status.textContent = uploadSubmitting
                ? "正在接收文件"
                : uploadState.uploadTaskView
                    ? uploadState.uploadTaskView.status === "ready"
                        ? "检查结果待确认"
                        : uploadState.uploadTaskView.status === "failed"
                            ? "检查未通过"
                            : "上传任务：" + uploadState.uploadTaskView.status
                    : "文件选择阶段";
            message.textContent = uploadSubmitting
                ? "文件正在安全接收；取得任务编号后才可取消。"
                : uploadState.uploadTaskView &&
                    ["ready", "failed"].indexOf(
                        uploadState.uploadTaskView.status) >= 0
                    ? "检查结果与当前分析数据保持分离；可查看问题、返回修改或明确确认使用。"
                    : uploadState.uploadTaskView
                    ? "当前任务状态保留在工作台中；返回页面不会重新提交文件。"
                    : "整理 CSV 或 JSONL 文件并为每个文件指定角色，然后显式提交上传检查。";
            if (uploadState.uploadTaskId &&
                    uploadState.uploadTaskView &&
                    taskUploadStatusIsActive(uploadState.uploadTaskView.status) &&
                    !uploadPollFailure) {
                scheduleUploadTaskPoll(0);
            }
            return;
        }
        if (task === "relations") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回分析概览";
            status.textContent = "正在读取网络结构摘要";
            message.textContent =
                "展示完整规范化网络范围；指标类别切换不会重新计算。";
            loadNetworkStructure(taskCard);
            return;
        }
        if (task === "ranking") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回分析概览";
            status.textContent = "正在读取节点排行";
            message.textContent =
                "一个指标、一张 Top 20 表；名次和完整计算范围采用服务端口径。";
            loadRankingTask(taskCard);
            return;
        }
        if (task === "interaction") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回分析概览";
            startInteractionTaskPage(taskCard);
            return;
        }
        if (task === "topic") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回分析概览";
            startTopicTaskPage(taskCard);
            return;
        }
        if (task === "user-relations") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回分析概览";
            var contextKey = datasetContextKey(readDatasetContext());
            userRelationsContextKey = contextKey;
            var userRelationsMount = ensureUserRelationsTaskView(taskCard);
            renderUserRelationsTask(userRelationsMount);
            var localCondition = workspaceState.getCondition("localNetwork");
            var existingLocalResult = userRelationsLocalResult;
            var hasCurrentLocalResult = existingLocalResult &&
                existingLocalResult.contextKey === contextKey &&
                existingLocalResult.parameters.centerNodeId ===
                    String(localCondition.centerNodeId || "") &&
                String(existingLocalResult.parameters.depth) ===
                    String(localCondition.depth) &&
                String(existingLocalResult.parameters.topK) ===
                    String(localCondition.topK) &&
                existingLocalResult.parameters.includeExternal ===
                    (localCondition.includeExternal !== false);
            if (localCondition.centerNodeId && !hasCurrentLocalResult) {
                loadUserRelationsLocal(userRelationsMount);
            } else {
                setText("social-network-task-status",
                    localCondition.centerNodeId
                        ? "显示上次成功的局部关系结果"
                        : "等待选择中心用户");
                setText("social-network-task-message",
                    "搜索只匹配资料用户；外部中心可从排行、详情或本次节点列表进入。");
            }
            return;
        }
        if (task === "groups") {
            returnLink.href = taskDetails
                ? taskDetails.returnHref
                : "#/social-network/relations";
            returnLink.textContent = taskDetails
                ? taskDetails.returnLabel
                : "返回关系与节点";
            status.textContent = "正在读取团体与社群分析状态";
            message.textContent =
                "只有点击开始计算才会启动分析；页面切换只查询已有状态。";
            if (window.socialNetworkGroupsPage) {
                window.socialNetworkGroupsPage.onShown();
            }
            return;
        }
        loadShellDatasetVersion();
        status.textContent = "未识别的社交网络任务";
        message.textContent = "当前地址未对应已登记的社交网络任务，请返回分析概览。";
        returnLink.href = taskDetails
            ? taskDetails.returnHref
            : "#/social-network";
        returnLink.textContent = taskDetails
            ? taskDetails.returnLabel
            : "返回分析概览";
    };

})();
