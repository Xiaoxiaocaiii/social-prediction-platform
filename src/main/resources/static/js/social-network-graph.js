(function () {
    "use strict";

    var canvas = document.getElementById("social-network-graph-canvas");
    if (!canvas) {
        return;
    }

    var context = canvas.getContext("2d");
    var shell = document.getElementById("social-network-graph-canvas-shell");
    var loading = document.getElementById("social-network-graph-loading");
    var status = document.getElementById("social-network-graph-status");
    var tooltip = document.getElementById("social-network-graph-tooltip");
    // #region debug-point A:reporter
    function reportGraphDebug(hypothesisId, location, message, data) {
        window.fetch("http://127.0.0.1:7777/event", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                sessionId: "graph-render-lag",
                runId: "interaction-pre-fix",
                hypothesisId: hypothesisId,
                location: location,
                msg: "[DEBUG] " + message,
                data: data,
                ts: Date.now()
            })
        }).catch(function () {});
    }
    var debugDrawCount = 0;
    var debugInteraction = null;
    var debugInteractionTimer = null;

    function finishDebugInteraction(reason) {
        if (!debugInteraction) {
            return;
        }
        if (debugInteractionTimer !== null) {
            window.clearTimeout(debugInteractionTimer);
            debugInteractionTimer = null;
        }
        var interaction = debugInteraction;
        debugInteraction = null;
        reportGraphDebug("F", "interaction",
            interaction.type + " interaction completed", {
                endReason: reason,
                durationMs: Number(
                    (performance.now() - interaction.startedAt).toFixed(2)),
                inputEventCount: interaction.inputEventCount,
                drawRequestCount: interaction.drawRequestCount,
                coalescedDrawRequests: interaction.coalescedDrawRequests,
                renderedFrameCount: interaction.renderedFrameCount,
                totalDrawDurationMs: Number(
                    interaction.totalDrawDurationMs.toFixed(2)),
                maxDrawDurationMs: Number(
                    interaction.maxDrawDurationMs.toFixed(2)),
                avgEdgePassDurationMs: Number((
                    interaction.edgePassDurationMs /
                    Math.max(1, interaction.renderedFrameCount)).toFixed(2)),
                maxEdgePassDurationMs: Number(
                    interaction.maxEdgePassDurationMs.toFixed(2)),
                avgArrowPaintDurationMs: Number((
                    interaction.arrowPaintDurationMs /
                    Math.max(1, interaction.renderedFrameCount)).toFixed(2)),
                maxArrowPaintDurationMs: Number(
                    interaction.maxArrowPaintDurationMs.toFixed(2)),
                totalArrowheadCount: interaction.totalArrowheadCount,
                maxArrowheadsPerFrame: interaction.maxArrowheadsPerFrame,
                maxFrameIntervalMs: Number(
                    interaction.maxFrameIntervalMs.toFixed(2)),
                maxInputToDrawMs: Number(
                    interaction.maxInputToDrawMs.toFixed(2)),
                nodeCount: nodes.length,
                edgeCount: edges.length
            });
    }

    function recordDebugInteractionInput(type) {
        var now = performance.now();
        if (debugInteraction && debugInteraction.type !== type) {
            finishDebugInteraction("interrupted");
        }
        if (debugInteraction && now - debugInteraction.lastInputAt > 300) {
            finishDebugInteraction("idle");
        }
        if (!debugInteraction) {
            debugInteraction = {
                type: type,
                startedAt: now,
                lastInputAt: now,
                lastFrameAt: null,
                inputEventCount: 0,
                drawRequestCount: 0,
                coalescedDrawRequests: 0,
                renderedFrameCount: 0,
                totalDrawDurationMs: 0,
                maxDrawDurationMs: 0,
                edgePassDurationMs: 0,
                maxEdgePassDurationMs: 0,
                arrowPaintDurationMs: 0,
                maxArrowPaintDurationMs: 0,
                totalArrowheadCount: 0,
                maxArrowheadsPerFrame: 0,
                maxFrameIntervalMs: 0,
                maxFrameIntervalMs: 0,
                maxInputToDrawMs: 0
            };
        }
        debugInteraction.inputEventCount += 1;
        debugInteraction.lastInputAt = now;
        if (debugInteractionTimer !== null) {
            window.clearTimeout(debugInteractionTimer);
        }
        debugInteractionTimer = window.setTimeout(function () {
            finishDebugInteraction("idle");
        }, 350);
    }
    // #endregion
    // #region debug-point E:longtask
    if ("PerformanceObserver" in window) {
        try {
            new PerformanceObserver(function (list) {
                if (!document.getElementById("page-social-network-graph")
                        .classList.contains("active")) {
                    return;
                }
                list.getEntries().forEach(function (entry) {
                    reportGraphDebug("E", "PerformanceObserver",
                        "graph-page main-thread long task", {
                            durationMs: Number(entry.duration.toFixed(2)),
                            startTimeMs: Number(entry.startTime.toFixed(2)),
                            attributionCount: entry.attribution
                                ? entry.attribution.length
                                : 0,
                            interactionType: debugInteraction
                                ? debugInteraction.type
                                : null
                        });
                });
            }).observe({ entryTypes: ["longtask"] });
        } catch (error) {
            reportGraphDebug("E", "PerformanceObserver",
                "long-task observer unavailable", {
                    message: error && error.message
                });
        }
    } else {
        reportGraphDebug("E", "PerformanceObserver",
            "long-task observer unsupported", {});
    }
    // #endregion
    var nodes = [];
    var edges = [];
    var communities = [];
    var communityCatalog = [];
    var graphCommunityFingerprint = null;
    var graphDatasetContextIdentity = "";
    var communityPairsFingerprint = null;
    var communityPairs = [];
    var communityPairsLoaded = false;
    var evidenceMode = "members";
    var evidenceCommunityId = "";
    var evidencePair = null;
    var evidenceOffset = 0;
    var evidenceRequestSequence = 0;
    var evidenceLoading = false;
    var evidencePage = null;
    var activeCommunityFilter = "all";
    var activeCommunityId = "";
    var nodeById = new Map();
    var communityColorById = new Map();
    var communityMembers = new Map();
    var positions = new Map();
    var selectedId = null;
    var hoveredId = null;
    var transform = { scale: 1, x: 0, y: 0 };
    var canvasWidth = 0;
    var canvasHeight = 0;
    var loaded = false;
    var initialized = false;
    var graphPageVisible = false;
    var loadingGraph = false;
    var graphLoadSequence = 0;
    var graphDatasetVersion = null;
    var pendingBridgeGraphFocus = null;
    var graphTruncated = false;
    var truncationReason = "";
    var communitiesVisible = true;
    var layoutWorker = null;
    var pointerAction = null;
    var pendingDrawFrame = null;
    var hasInteracted = false;
    var colors = {
        canvas: "#FBFAF7",
        edge: "rgba(91, 96, 100, 0.14)",
        edgeSelected: "rgba(200, 107, 67, 0.76)",
        profile: "#788C80",
        external: "#B5B2AA",
        selected: "#C86B43",
        text: "#25282C",
        communityPalette: [
            "#AFC4B4", "#D4B99C", "#AEBFD3", "#D3B2AE", "#C3BD91",
            "#B9AFD0", "#A7C7C4", "#D1B78D", "#B2BCC9", "#C5ADA0",
            "#AFC08F", "#C7B9B0", "#A7B6A5", "#D2AAA0", "#B0B1CF",
            "#B7C6D6", "#D0C09F", "#B6C3AE", "#C3A9B5", "#AFC8BC"
        ]
    };

    function setStatus(message, state) {
        status.textContent = message;
        status.className = "social-network-graph-status" +
            (state ? " is-" + state : "");
    }

    function formatNumber(value) {
        return value === null || value === undefined
            ? "-"
            : Number(value).toLocaleString("zh-CN");
    }

    function nodeLabel(node) {
        return node.displayName || node.username || node.id;
    }

    function stableCommunityColorIndex(communityId) {
        var hash = 2166136261;
        var value = String(communityId || "");
        for (var index = 0; index < value.length; index += 1) {
            hash = Math.imul(hash ^ value.charCodeAt(index), 16777619);
        }
        return (hash >>> 0) % colors.communityPalette.length;
    }

    function communityColor(communityId) {
        if (!communityId) {
            return colors.profile;
        }
        if (!communityColorById.has(communityId)) {
            communityColorById.set(
                communityId,
                colors.communityPalette[stableCommunityColorIndex(communityId)]);
        }
        return communityColorById.get(communityId);
    }

    function updateNodeLegendLabels(hasCommunityResult) {
        var profileLegend =
            document.getElementById("social-network-graph-profile-legend");
        var externalLegend =
            document.getElementById("social-network-graph-external-legend");
        if (profileLegend && profileLegend.lastChild.nodeType === Node.TEXT_NODE) {
            profileLegend.lastChild.nodeValue = hasCommunityResult
                ? "未归类用户节点"
                : "用户节点（暂未按社群着色）";
        }
        if (externalLegend && externalLegend.lastChild.nodeType === Node.TEXT_NODE) {
            externalLegend.lastChild.nodeValue = hasCommunityResult
                ? "未归类外部节点（资料未采集）"
                : "外部节点（资料未采集）";
        }
    }

    function updateCommunityToggleLabel(status, hasCommunityResult) {
        var label = document.getElementById(
            "social-network-graph-community-toggle-label");
        if (!label) {
            return;
        }
        if (hasCommunityResult) {
            label.textContent = "显示社群圈";
        } else if (status === "running") {
            label.textContent = "显示社群圈（分析中）";
        } else if (status === "completed_unconverged") {
            label.textContent = "显示社群圈（未收敛）";
        } else if (status === "failed" || status === "completed") {
            label.textContent = "显示社群圈（结果不可用）";
        } else {
            label.textContent = "显示社群圈（分析未运行）";
        }
    }

    function withAlpha(hexColor, alpha) {
        var value = hexColor.replace("#", "");
        var red = parseInt(value.slice(0, 2), 16);
        var green = parseInt(value.slice(2, 4), 16);
        var blue = parseInt(value.slice(4, 6), 16);
        return "rgba(" + red + ", " + green + ", " + blue + ", " + alpha + ")";
    }

    function canvasPoint(event) {
        var bounds = canvas.getBoundingClientRect();
        return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
    }

    function screenPoint(position) {
        return {
            x: position.x * transform.scale + transform.x,
            y: position.y * transform.scale + transform.y
        };
    }

    function worldPoint(point) {
        return {
            x: (point.x - transform.x) / transform.scale,
            y: (point.y - transform.y) / transform.scale
        };
    }

    function nodeAt(point) {
        var closest = null;
        var closestDistance = 14;
        nodes.forEach(function (node) {
            var position = positions.get(node.id);
            if (!position) {
                return;
            }
            var screen = screenPoint(position);
            var dx = screen.x - point.x;
            var dy = screen.y - point.y;
            var distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < closestDistance) {
                closest = node;
                closestDistance = distance;
            }
        });
        return closest;
    }

    function drawEdges(focused) {
        var debugEdgePassStartedAt = performance.now();
        var arrows = [];
        var showArrows = focused || transform.scale >= 1.15;
        context.beginPath();
        edges.forEach(function (edge) {
            var isFocused = edge.source === selectedId || edge.target === selectedId;
            if (isFocused !== focused) {
                return;
            }
            var sourcePosition = positions.get(edge.source);
            var targetPosition = positions.get(edge.target);
            if (!sourcePosition || !targetPosition) {
                return;
            }
            var source = screenPoint(sourcePosition);
            var target = screenPoint(targetPosition);
            var edgeMargin = 5;
            if (Math.max(source.x, target.x) < -edgeMargin ||
                    Math.min(source.x, target.x) > canvasWidth + edgeMargin ||
                    Math.max(source.y, target.y) < -edgeMargin ||
                    Math.min(source.y, target.y) > canvasHeight + edgeMargin) {
                return;
            }
            var dx = target.x - source.x;
            var dy = target.y - source.y;
            var length = Math.sqrt(dx * dx + dy * dy);
            if (length < 1) {
                return;
            }
            var start = {
                x: source.x + dx / length * 5,
                y: source.y + dy / length * 5
            };
            var end = {
                x: target.x - dx / length * 5,
                y: target.y - dy / length * 5
            };
            context.moveTo(start.x, start.y);
            context.lineTo(end.x, end.y);
            if (showArrows) {
                var angle = Math.atan2(dy, dx);
                arrows.push({
                    x: end.x - Math.cos(angle) * 2,
                    y: end.y - Math.sin(angle) * 2,
                    angle: angle
                });
            }
        });
        context.strokeStyle = focused ? colors.edgeSelected : colors.edge;
        context.lineWidth = focused ? 1.35 : 0.7;
        context.stroke();
        var debugEdgePassDuration = performance.now() - debugEdgePassStartedAt;
        var debugArrowPaintStartedAt = performance.now();
        if (arrows.length) {
            context.beginPath();
            arrows.forEach(function (arrow) {
                var size = 4;
                context.moveTo(arrow.x, arrow.y);
                context.lineTo(
                    arrow.x - Math.cos(arrow.angle - 0.5) * size,
                    arrow.y - Math.sin(arrow.angle - 0.5) * size);
                context.lineTo(
                    arrow.x - Math.cos(arrow.angle + 0.5) * size,
                    arrow.y - Math.sin(arrow.angle + 0.5) * size);
                context.closePath();
            });
            context.fillStyle = focused ? colors.edgeSelected : colors.edge;
            context.fill();
        }
        if (debugInteraction) {
            var debugArrowPaintDuration =
                performance.now() - debugArrowPaintStartedAt;
            debugInteraction.edgePassDurationMs += debugEdgePassDuration;
            debugInteraction.maxEdgePassDurationMs = Math.max(
                debugInteraction.maxEdgePassDurationMs, debugEdgePassDuration);
            debugInteraction.arrowPaintDurationMs += debugArrowPaintDuration;
            debugInteraction.maxArrowPaintDurationMs = Math.max(
                debugInteraction.maxArrowPaintDurationMs,
                debugArrowPaintDuration);
            debugInteraction.totalArrowheadCount += arrows.length;
            debugInteraction.maxArrowheadsPerFrame = Math.max(
                debugInteraction.maxArrowheadsPerFrame, arrows.length);
        }
    }

    function drawCommunities() {
        if (!communitiesVisible) {
            return;
        }
        var circles = communities.map(function (community) {
            var members = communityMembers.get(community.id) || [];
            var memberPositions = members.map(function (node) {
                return positions.get(node.id);
            }).filter(Boolean);
            if (!memberPositions.length) {
                return null;
            }
            var center = memberPositions.reduce(function (total, position) {
                total.x += position.x;
                total.y += position.y;
                return total;
            }, { x: 0, y: 0 });
            center.x /= memberPositions.length;
            center.y /= memberPositions.length;
            var radius = memberPositions.reduce(function (maximum, position) {
                var dx = position.x - center.x;
                var dy = position.y - center.y;
                return Math.max(maximum, Math.sqrt(dx * dx + dy * dy));
            }, 0);
            return {
                community: community,
                center: screenPoint(center),
                radius: radius * transform.scale + 17,
                color: communityColor(community.id)
            };
        }).filter(Boolean).sort(function (left, right) {
            return right.radius - left.radius;
        });
        circles.forEach(function (circle) {
            context.beginPath();
            context.arc(
                circle.center.x,
                circle.center.y,
                Math.max(18, circle.radius),
                0,
                Math.PI * 2);
            context.fillStyle = withAlpha(circle.color, 0.13);
            context.fill();
            context.lineWidth = 1;
            context.strokeStyle = withAlpha(circle.color, 0.48);
            context.stroke();
            if (transform.scale >= 0.65) {
                context.font = "11px 'Microsoft YaHei', sans-serif";
                context.textBaseline = "middle";
                context.fillStyle = "#555A5E";
                context.fillText(
                    circle.community.id + " · " +
                        formatNumber(circle.community.nodeCount),
                    circle.center.x - circle.radius + 8,
                    circle.center.y - circle.radius + 12);
            }
        });
    }

    function drawNode(node) {
        var position = positions.get(node.id);
        if (!position) {
            return;
        }
        var point = screenPoint(position);
        var radius = node.id === selectedId ? 7 : 4.8;
        context.beginPath();
        context.arc(point.x, point.y, radius, 0, Math.PI * 2);
        context.fillStyle = colors.canvas;
        context.fill();
        context.beginPath();
        context.arc(point.x, point.y, radius - 1, 0, Math.PI * 2);
        context.fillStyle = node.id === selectedId
            ? colors.selected
            : node.nodeType === "external"
                ? colors.external
                : communityColor(node.communityId);
        context.fill();
        context.lineWidth = node.id === selectedId ? 2 : 1;
        context.strokeStyle = node.id === selectedId
            ? colors.canvas
            : node.nodeType === "external" && node.communityId
                ? communityColor(node.communityId)
                : colors.canvas;
        context.stroke();

        if (node.id === selectedId || (transform.scale >= 1.65 && node.id === hoveredId)) {
            context.font = "12px 'Microsoft YaHei', sans-serif";
            context.textBaseline = "middle";
            context.fillStyle = colors.text;
            context.fillText(nodeLabel(node).slice(0, 24), point.x + 10, point.y - 1);
        }
    }

    function draw() {
        if (pendingDrawFrame !== null) {
            window.cancelAnimationFrame(pendingDrawFrame);
            pendingDrawFrame = null;
        }
        if (!context || !canvasWidth || !canvasHeight) {
            return;
        }
        // #region debug-point D:draw-duration
        var debugDrawStartedAt = performance.now();
        var interaction = debugInteraction;
        if (interaction) {
            interaction.renderedFrameCount += 1;
            if (interaction.lastFrameAt !== null) {
                interaction.maxFrameIntervalMs = Math.max(
                    interaction.maxFrameIntervalMs,
                    debugDrawStartedAt - interaction.lastFrameAt);
            }
            interaction.maxInputToDrawMs = Math.max(
                interaction.maxInputToDrawMs,
                debugDrawStartedAt - interaction.lastInputAt);
            interaction.lastFrameAt = debugDrawStartedAt;
        }
        // #endregion
        context.clearRect(0, 0, canvasWidth, canvasHeight);
        context.fillStyle = colors.canvas;
        context.fillRect(0, 0, canvasWidth, canvasHeight);
        drawCommunities();
        drawEdges(false);
        if (selectedId) {
            drawEdges(true);
        }
        nodes.forEach(drawNode);
        // #region debug-point D:draw-duration
        debugDrawCount += 1;
        var debugDrawDuration = performance.now() - debugDrawStartedAt;
        if (interaction) {
            interaction.totalDrawDurationMs += debugDrawDuration;
            interaction.maxDrawDurationMs = Math.max(
                interaction.maxDrawDurationMs, debugDrawDuration);
        } else if (debugDrawCount <= 5 || debugDrawCount % 25 === 0 ||
                debugDrawDuration >= 16.7) {
            reportGraphDebug("D", "draw", "canvas draw completed", {
                drawCount: debugDrawCount,
                durationMs: Number(debugDrawDuration.toFixed(2)),
                nodeCount: nodes.length,
                edgeCount: edges.length
            });
        }
        // #endregion
    }

    function scheduleDraw() {
        if (debugInteraction) {
            debugInteraction.drawRequestCount += 1;
        }
        if (pendingDrawFrame !== null) {
            if (debugInteraction) {
                debugInteraction.coalescedDrawRequests += 1;
            }
            return;
        }
        pendingDrawFrame = window.requestAnimationFrame(function () {
            pendingDrawFrame = null;
            draw();
        });
    }

    function resizeCanvas() {
        var bounds = shell.getBoundingClientRect();
        var ratio = Math.min(window.devicePixelRatio || 1, 2);
        canvasWidth = Math.max(1, bounds.width);
        canvasHeight = Math.max(1, bounds.height);
        canvas.width = Math.round(canvasWidth * ratio);
        canvas.height = Math.round(canvasHeight * ratio);
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
        if (!positions.size) {
            transform.x = canvasWidth / 2;
            transform.y = canvasHeight / 2;
        } else if (!hasInteracted) {
            fitToCanvas();
            return;
        }
        draw();
    }

    function fitToCanvas() {
        if (!positions.size || canvasWidth < 1 || canvasHeight < 1) {
            return;
        }
        var bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
        positions.forEach(function (position) {
            bounds.minX = Math.min(bounds.minX, position.x);
            bounds.minY = Math.min(bounds.minY, position.y);
            bounds.maxX = Math.max(bounds.maxX, position.x);
            bounds.maxY = Math.max(bounds.maxY, position.y);
        });
        var graphWidth = Math.max(1, bounds.maxX - bounds.minX);
        var graphHeight = Math.max(1, bounds.maxY - bounds.minY);
        var scale = Math.min(
            (canvasWidth - 60) / graphWidth,
            (canvasHeight - 60) / graphHeight,
            1.8);
        transform.scale = Math.max(0.12, scale);
        transform.x = canvasWidth / 2 - (bounds.minX + graphWidth / 2) * transform.scale;
        transform.y = canvasHeight / 2 - (bounds.minY + graphHeight / 2) * transform.scale;
        draw();
    }

    function zoomBy(factor, point) {
        var anchor = point || { x: canvasWidth / 2, y: canvasHeight / 2 };
        var previousScale = transform.scale;
        transform.scale = Math.max(0.12, Math.min(4, previousScale * factor));
        var ratio = transform.scale / previousScale;
        transform.x = anchor.x - (anchor.x - transform.x) * ratio;
        transform.y = anchor.y - (anchor.y - transform.y) * ratio;
        scheduleDraw();
    }

    function fallbackLayout() {
        // #region debug-point A:fallback-layout
        var debugFallbackStartedAt = performance.now();
        // #endregion
        var nextPositions = new Map();
        var grouped = new Map();
        var unassigned = [];
        nodes.forEach(function (node) {
            if (!node.communityId) {
                unassigned.push(node);
                return;
            }
            if (!grouped.has(node.communityId)) {
                grouped.set(node.communityId, []);
            }
            grouped.get(node.communityId).push(node);
        });
        var communityIds = Array.from(grouped.keys()).sort();
        var orbit = Math.max(260, communityIds.length * 36);
        communityIds.forEach(function (communityId, groupIndex) {
            var angle = groupIndex / Math.max(1, communityIds.length) * Math.PI * 2;
            var centerX = Math.cos(angle) * orbit;
            var centerY = Math.sin(angle) * orbit;
            grouped.get(communityId).forEach(function (node, memberIndex) {
                var memberAngle = memberIndex * 2.399963229728653;
                var memberRadius = 18 * Math.sqrt(memberIndex + 1);
                nextPositions.set(node.id, {
                    x: centerX + Math.cos(memberAngle) * memberRadius,
                    y: centerY + Math.sin(memberAngle) * memberRadius
                });
            });
        });
        unassigned.forEach(function (node, index) {
            var angle = index * 2.399963229728653;
            var radius = orbit + 200 + 18 * Math.sqrt(index + 1);
            nextPositions.set(node.id, {
                x: Math.cos(angle) * radius,
                y: Math.sin(angle) * radius
            });
        });
        positions = nextPositions;
        loading.classList.add("hidden");
        if (!hasInteracted) {
            fitToCanvas();
        } else {
            draw();
        }
        // #region debug-point A:fallback-layout
        reportGraphDebug("A", "fallbackLayout", "fallback layout completed", {
            durationMs: Number(
                (performance.now() - debugFallbackStartedAt).toFixed(2)),
            nodeCount: nodes.length,
            edgeCount: edges.length
        });
        // #endregion
    }

    function layoutGraph() {
        var layoutSequence = graphLoadSequence;
        // #region debug-point B:worker-layout
        var debugWorkerStartedAt = performance.now();
        reportGraphDebug("B", "layoutGraph", "worker layout started", {
            nodeCount: nodes.length,
            edgeCount: edges.length
        });
        // #endregion
        if (layoutWorker) {
            layoutWorker.terminate();
        }
        try {
            layoutWorker = new Worker("js/social-network-graph-layout-worker.js?v=20261014-global-graph-1");
            layoutWorker.onmessage = function (event) {
                if (layoutSequence !== graphLoadSequence) {
                    return;
                }
                // #region debug-point B:worker-layout
                reportGraphDebug("B", "layoutGraph.onmessage",
                    "worker layout completed", {
                        durationMs: Number(
                            (performance.now() - debugWorkerStartedAt).toFixed(2)),
                        positionCount: (event.data.positions || []).length,
                        nodeCount: nodes.length,
                        edgeCount: edges.length
                    });
                // #endregion
                positions = new Map((event.data.positions || []).map(function (position) {
                    return [position.id, { x: position.x, y: position.y }];
                }));
                layoutWorker.terminate();
                layoutWorker = null;
                loading.classList.add("hidden");
                if (!hasInteracted) {
                    fitToCanvas();
                } else {
                    draw();
                }
                setStatus(graphTruncated
                    ? truncationReason || "当前全局网络按请求上限截取。"
                    : "已加载全量关系网络 · " + formatNumber(nodes.length) +
                        " 个节点 · " + formatNumber(edges.length) + " 条关系",
                graphTruncated ? "limited" : "ready");
                finishPendingBridgeGraphFocus(layoutSequence);
            };
            layoutWorker.onerror = function () {
                if (layoutSequence !== graphLoadSequence) {
                    return;
                }
                // #region debug-point B:worker-layout
                reportGraphDebug("B", "layoutGraph.onerror",
                    "worker layout failed", {
                        durationMs: Number(
                            (performance.now() - debugWorkerStartedAt).toFixed(2)),
                        nodeCount: nodes.length,
                        edgeCount: edges.length
                    });
                // #endregion
                if (layoutWorker) {
                    layoutWorker.terminate();
                    layoutWorker = null;
                }
                fallbackLayout();
                setStatus("布局计算已降级，当前仍可缩放、平移和搜索。", "limited");
                finishPendingBridgeGraphFocus(layoutSequence);
            };
            layoutWorker.postMessage({ nodes: nodes, edges: edges });
        } catch (error) {
            if (layoutSequence !== graphLoadSequence) {
                return;
            }
            // #region debug-point B:worker-layout
            reportGraphDebug("B", "layoutGraph.catch", "worker startup failed", {
                durationMs: Number(
                    (performance.now() - debugWorkerStartedAt).toFixed(2)),
                message: error && error.message
            });
            // #endregion
            fallbackLayout();
            setStatus("布局计算已降级，当前仍可缩放、平移和搜索。", "limited");
            finishPendingBridgeGraphFocus(layoutSequence);
        }
    }

    function renderSelectedNode(node) {
        selectedId = node ? node.id : null;
        var container = document.getElementById("social-network-graph-selected");
        var detailButton = document.getElementById("social-network-graph-user-detail");
        container.replaceChildren();
        if (!node) {
            container.textContent = "点击画布中的节点查看资料和网络位置。";
            detailButton.disabled = true;
            draw();
            return;
        }
        var name = document.createElement("strong");
        name.textContent = nodeLabel(node);
        var id = document.createElement("span");
        id.textContent = "账号 ID：" + node.id;
        var type = document.createElement("span");
        type.textContent = node.nodeType === "external" ? "外部节点 · 资料未采集" : "用户资料已采集";
        var metrics = document.createElement("span");
        metrics.textContent = "数据内被关注数 " + formatNumber(node.inDegree) +
            " · 数据内关注数 " + formatNumber(node.outDegree);
        container.append(name, id, type, metrics);
        detailButton.disabled = false;
        detailButton.onclick = function () {
            if (window.openSocialNetworkUserDetail) {
                window.openSocialNetworkUserDetail(node.id);
            }
        };
        draw();
    }

    function syncCommunityFilter(data, hasCommunityResult) {
        var select = document.getElementById(
            "social-network-graph-community-filter");
        communityCatalog = hasCommunityResult
            ? data.globalCommunities || []
            : [];
        if (data.communityFilterApplied === false ||
                data.communityFilterValid === false) {
            activeCommunityFilter = "all";
            activeCommunityId = "";
        } else {
            activeCommunityFilter = data.communityFilter || "all";
            activeCommunityId = data.communityFilterCommunityId || "";
        }
        select.replaceChildren();
        [
            { value: "all", label: "全部账号" },
            {
                value: "unassigned",
                label: "未归类账号（" +
                    formatNumber(data.globalUnassignedNodeCount || 0) + "）"
            }
        ].forEach(function (entry) {
            var option = document.createElement("option");
            option.value = entry.value;
            option.textContent = entry.label;
            select.appendChild(option);
        });
        communityCatalog.forEach(function (community) {
            var option = document.createElement("option");
            option.value = "community:" + community.id;
            option.textContent = community.id + "（" +
                formatNumber(community.nodeCount) + "）";
            select.appendChild(option);
        });
        select.value = activeCommunityFilter === "community"
            ? "community:" + activeCommunityId
            : activeCommunityFilter;
        select.disabled = !hasCommunityResult;
    }

    function positionCommunityMethodTooltip(trigger) {
        var tooltip = trigger && trigger.nextElementSibling;
        if (!tooltip) {
            return;
        }
        var margin = 12;
        var viewportWidth = document.documentElement.clientWidth ||
            window.innerWidth;
        var viewportHeight = document.documentElement.clientHeight ||
            window.innerHeight;
        tooltip.style.left = margin + "px";
        tooltip.style.top = margin + "px";
        var tooltipRect = tooltip.getBoundingClientRect();
        var anchorRect = trigger.getBoundingClientRect();
        var maxLeft = Math.max(
            margin,
            viewportWidth - tooltipRect.width - margin
        );
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

    function bindCommunityMethodTooltip() {
        var page = document.getElementById("page-social-network-graph");
        if (!page || page.getAttribute("data-community-method-bound") === "true") {
            return;
        }
        page.setAttribute("data-community-method-bound", "true");
        var findTrigger = function (target) {
            return target && target.closest
                ? target.closest("[data-network-scope-term]")
                : null;
        };
        var positionTrigger = function (event) {
            var trigger = findTrigger(event.target);
            if (trigger && page.contains(trigger)) {
                trigger.parentElement.classList.remove("is-dismissed");
                positionCommunityMethodTooltip(trigger);
            }
        };
        page.addEventListener("pointerover", positionTrigger);
        page.addEventListener("focusin", positionTrigger);
        page.addEventListener("click", function (event) {
            var trigger = findTrigger(event.target);
            if (!trigger || !page.contains(trigger)) {
                return;
            }
            trigger.parentElement.classList.remove("is-dismissed");
            var expanded = trigger.getAttribute("aria-expanded") === "true";
            trigger.setAttribute("aria-expanded", String(!expanded));
            trigger.parentElement.classList.toggle("is-open", !expanded);
            if (!expanded) {
                positionCommunityMethodTooltip(trigger);
            }
        });
        page.addEventListener("keydown", function (event) {
            if (event.key !== "Escape") {
                return;
            }
            var trigger = page.querySelector(
                '[data-network-scope-term][aria-expanded="true"]'
            );
            if (trigger) {
                trigger.setAttribute("aria-expanded", "false");
                trigger.parentElement.classList.remove("is-open");
                trigger.parentElement.classList.add("is-dismissed");
                trigger.focus();
                event.stopPropagation();
            }
        });
        var repositionVisibleTooltip = function () {
            var trigger = page.querySelector("[data-network-scope-term]");
            if (trigger && (trigger.matches(":hover") ||
                    trigger === document.activeElement ||
                    trigger.getAttribute("aria-expanded") === "true")) {
                positionCommunityMethodTooltip(trigger);
            }
        };
        window.addEventListener("resize", repositionVisibleTooltip);
        window.addEventListener("scroll", repositionVisibleTooltip, true);
    }

    function renderCommunityPanel(data) {
        var summary = document.getElementById("social-network-graph-community-summary");
        var list = document.getElementById("social-network-graph-community-list");
        var method = document.getElementById("social-network-graph-community-method");
        var communityToggle =
            document.getElementById("social-network-graph-community-toggle");
        var status = data.communityAnalysisStatus || "not_requested";
        var hasCommunityResult =
            status === "completed" && data.communityAssignmentsComplete === true;
        updateNodeLegendLabels(hasCommunityResult);
        updateCommunityToggleLabel(status, hasCommunityResult);
        syncCommunityFilter(data, hasCommunityResult);
        if (!hasCommunityResult) {
            communityToggle.disabled = true;
            list.replaceChildren();
            if (status === "running") {
                summary.textContent = "Louvain 正在运行，当前图暂不显示社群归属。";
                method.textContent = "分析完成后重新进入关系网络图即可读取结果。";
            } else if (status === "completed_unconverged") {
                summary.textContent = "Louvain 已结束但未收敛，当前图不采用这次划分。";
                method.textContent = "为避免把未稳定的成员归属当作结论，节点暂不按社群着色。";
            } else if (status === "failed") {
                summary.textContent = "社群结果读取或校验失败，当前图只显示关系。";
                method.textContent = "本次没有可与当前图数据确认一致的完整社群结果。";
            } else if (status === "completed") {
                summary.textContent = "社群结果不完整或与当前图数据不一致。";
                method.textContent = "当前不显示未通过完整性与数据身份校验的社群归属。";
            } else {
                summary.textContent = "社群分析尚未运行。";
                method.textContent =
                    "社群统计暂不可用；这不代表网络中没有社群。";
            }
            return;
        }
        communityToggle.disabled = false;
        var convergenceLabel = data.communityConverged === false
            ? "未收敛，结果仅供探索"
            : formatNumber(data.communityIterations || 0) + " 轮后稳定";
        var communitySummary = activeCommunityFilter === "community"
            ? "当前筛选：社群 " + activeCommunityId + " · " +
                formatNumber(data.communityFilterNodeCount || 0) + " 个账号 · " +
                formatNumber(data.communityFilterEdgeCount || 0) + " 条内部关系"
            : activeCommunityFilter === "unassigned"
                ? "当前筛选：未归类账号 · " +
                    formatNumber(data.communityFilterNodeCount || 0) + " 个账号"
                : "全部账号 · 完整范围 " +
                    formatNumber(data.globalCommunityCount || 0) + " 个社群 · " +
                    formatNumber(data.globalUnassignedNodeCount || 0) +
                    " 个账号未归类";
        if (graphTruncated) {
            communitySummary += " · 画布显示 " +
                formatNumber(nodes.length) + "/" +
                formatNumber(data.communityFilterNodeCount || nodes.length) + " 个账号、" +
                formatNumber(edges.length) + "/" +
                formatNumber(data.communityFilterEdgeCount || edges.length) + " 条关系";
        }
        summary.textContent = communitySummary + " · " + convergenceLabel;
        list.replaceChildren();
        communityCatalog.forEach(function (community) {
            var row = document.createElement("button");
            row.type = "button";
            row.className = "social-network-graph-community-item";
            row.setAttribute(
                "aria-label",
                "查看社群 " + community.id + " 的成员与关系来源证据");
            row.addEventListener("click", function () {
                openCommunityEvidence(community);
            });
            var marker = document.createElement("i");
            marker.style.setProperty(
                "--community-color",
                communityColor(community.id));
            var name = document.createElement("strong");
            name.textContent = community.id;
            var size = document.createElement("span");
            size.textContent = formatNumber(community.nodeCount) + " 个节点";
            var relations = document.createElement("small");
            relations.textContent = formatNumber(community.internalDirectedEdgeCount || 0) +
                " 条内部有向关注关系";
            row.append(marker, name, size, relations);
            list.appendChild(row);
        });
        method.textContent = (data.communityDirectionNote ||
            "社群由关注关系的无向投影识别；圈层不代表真实组织或群体身份。") +
            (data.communityConverged === false
                ? " 算法达到迭代上限，当前划分需要谨慎查看。"
                : " 本次划分已达到稳定状态。") +
            " 列表保留全网社群规模，画布只展示当前筛选范围。" +
            (activeCommunityFilter === "community"
                ? " 选择社群时只显示该社群内部关注关系。"
                : "");
    }

    function graphEvidenceSupported() {
        var datasetContext = window.socialNetworkWorkspace.getDatasetContext();
        var builtin = datasetContext.datasetId === "think-tank-social-network" &&
            (!datasetContext.datasetVersion ||
                datasetContext.datasetVersion === "v1") &&
            !datasetContext.sessionId;
        var upload = /^upload-/.test(String(datasetContext.datasetId || "")) &&
            Boolean(datasetContext.datasetVersion && datasetContext.sessionId);
        return (builtin || upload) &&
            Boolean(graphCommunityFingerprint) &&
            graphDatasetContextIdentity === datasetContextIdentity(datasetContext);
    }

    function datasetContextIdentity(datasetContext) {
        return JSON.stringify([
            datasetContext.datasetId,
            datasetContext.datasetVersion || "",
            datasetContext.sessionId || ""
        ]);
    }

    function setGraphEvidenceMessage(message, isError) {
        var count = document.getElementById("social-network-graph-evidence-count");
        var list = document.getElementById("social-network-graph-evidence-list");
        var page = document.getElementById("social-network-graph-evidence-page");
        var previous = document.getElementById("social-network-graph-evidence-prev");
        var next = document.getElementById("social-network-graph-evidence-next");
        count.textContent = message;
        list.replaceChildren();
        var empty = document.createElement("p");
        empty.className = "social-network-group-empty" +
            (isError ? " is-error" : "");
        empty.textContent = message;
        list.appendChild(empty);
        page.textContent = "—";
        previous.disabled = true;
        next.disabled = true;
    }

    function openCommunityEvidence(community) {
        evidenceCommunityId = community.id;
        evidenceMode = "members";
        evidencePair = null;
        evidenceOffset = 0;
        evidencePage = null;
        communityPairsLoaded = false;
        communityPairs = [];
        var dialog = document.getElementById(
            "social-network-graph-evidence-dialog");
        document.getElementById("social-network-graph-evidence-title")
            .textContent = "社群 " + community.id + " 的成员与关系证据";
        document.getElementById("social-network-graph-evidence-context")
            .textContent = formatNumber(community.nodeCount) +
                " 个成员 · " +
                formatNumber(community.internalDirectedEdgeCount || 0) +
                " 条社群内部有向关注关系。成员和关系来源按需分页读取；" +
                "箭头表示前者关注后者，重复来源不计入有效关系数。";
        document.querySelectorAll("[data-graph-evidence-tab]")
            .forEach(function (tab) {
                tab.setAttribute(
                    "aria-selected",
                    String(tab.dataset.graphEvidenceTab === "members"));
            });
        document.getElementById(
            "social-network-graph-evidence-pair-label").hidden = true;
        if (!dialog.open) {
            dialog.showModal();
        }
        if (!graphEvidenceSupported()) {
            setGraphEvidenceMessage(
                "当前没有与已加载关系图匹配的完整 Louvain 结果，请刷新关系图后重试。",
                true);
            return;
        }
        loadCommunityEvidencePage();
    }

    function selectCommunityEvidenceMode(mode) {
        evidenceMode = mode;
        evidenceOffset = 0;
        evidencePage = null;
        document.querySelectorAll("[data-graph-evidence-tab]")
            .forEach(function (tab) {
                tab.setAttribute(
                    "aria-selected",
                    String(tab.dataset.graphEvidenceTab === mode));
            });
        document.getElementById(
            "social-network-graph-evidence-pair-label").hidden =
                mode !== "between";
        if (!graphEvidenceSupported()) {
            setGraphEvidenceMessage(
                "当前没有与已加载关系图匹配的完整 Louvain 结果，请刷新关系图后重试。",
                true);
        } else if (mode === "between") {
            loadCommunityPairCatalog();
        } else {
            loadCommunityEvidencePage();
        }
    }

    function loadCommunityPairCatalog() {
        if (communityPairsLoaded &&
                communityPairsFingerprint === graphCommunityFingerprint) {
            renderCommunityPairOptions();
            return;
        }
        var sequence = ++evidenceRequestSequence;
        var expectedFingerprint = graphCommunityFingerprint;
        var requestContext = window.socialNetworkWorkspace.getDatasetContext();
        var expectedContextIdentity = graphDatasetContextIdentity;
        evidenceLoading = true;
        setGraphEvidenceMessage("正在读取社群间关系汇总…", false);
        window.socialNetworkWorkspace.requestDataset(
            window.socialNetworkWorkspace.datasetUrl(
                "network/louvain/insights", null, requestContext))
            .then(function (response) {
                if (sequence !== evidenceRequestSequence ||
                        expectedContextIdentity !== graphDatasetContextIdentity ||
                        expectedContextIdentity !==
                            datasetContextIdentity(
                                window.socialNetworkWorkspace.getDatasetContext()) ||
                        !document.getElementById(
                            "social-network-graph-evidence-dialog").open) {
                    return;
                }
                var data = response.data || {};
                if (data.dataFingerprint !== expectedFingerprint ||
                        expectedFingerprint !== graphCommunityFingerprint) {
                    setGraphEvidenceMessage(
                        "社群汇总与当前关系图的数据版本不一致，请重新加载关系图。",
                        true);
                    return;
                }
                if (!data.available) {
                    communityPairsLoaded = true;
                    communityPairsFingerprint = expectedFingerprint;
                    communityPairs = [];
                    setGraphEvidenceMessage(
                        "当前没有可用的完整、稳定社群间关系汇总。",
                        true);
                    return;
                }
                communityPairs = (data.communityPairs || []).filter(function (pair) {
                    return pair.first === evidenceCommunityId ||
                        pair.second === evidenceCommunityId;
                });
                communityPairsLoaded = true;
                communityPairsFingerprint = expectedFingerprint;
                renderCommunityPairOptions();
            })
            .catch(function (error) {
                if (sequence === evidenceRequestSequence &&
                        !error.staleDatasetContext &&
                        expectedContextIdentity === graphDatasetContextIdentity &&
                        expectedContextIdentity ===
                            datasetContextIdentity(
                                window.socialNetworkWorkspace.getDatasetContext())) {
                    setGraphEvidenceMessage(
                        "暂时无法读取社群间关系汇总，请稍后重试。",
                        true);
                }
            });
    }

    function renderCommunityPairOptions() {
        var select = document.getElementById(
            "social-network-graph-evidence-pair");
        select.replaceChildren();
        if (!communityPairs.length) {
            evidencePair = null;
            setGraphEvidenceMessage(
                "该社群当前没有连接其他已归类社群的有效关系。",
                false);
            return;
        }
        communityPairs.forEach(function (pair) {
            var other = pair.first === evidenceCommunityId
                ? pair.second
                : pair.first;
            var option = document.createElement("option");
            option.value = other;
            option.textContent = other + " · " +
                formatNumber(pair.tieCount) + " 对无向联系 / " +
                formatNumber(pair.directedRelationshipCount) +
                " 条有向关注";
            select.appendChild(option);
        });
        var selectedOther = select.value;
        evidencePair = communityPairs.find(function (pair) {
            return pair.first === evidenceCommunityId
                ? pair.second === selectedOther
                : pair.first === selectedOther;
        }) || communityPairs[0];
        select.value = evidencePair.first === evidenceCommunityId
            ? evidencePair.second
            : evidencePair.first;
        loadCommunityEvidencePage();
    }

    function renderCommunityEvidencePage(data) {
        var count = document.getElementById("social-network-graph-evidence-count");
        var list = document.getElementById("social-network-graph-evidence-list");
        var pageLabel = document.getElementById(
            "social-network-graph-evidence-page");
        var previous = document.getElementById(
            "social-network-graph-evidence-prev");
        var next = document.getElementById(
            "social-network-graph-evidence-next");
        var entries = evidenceMode === "members"
            ? data.members || []
            : data.ties || [];
        var offset = Number(data.offset) || 0;
        var total = Number(data.totalCount) || 0;
        var end = Math.min(offset + entries.length, total);
        var prefix;
        if (evidenceMode === "members") {
            prefix = "成员来源 · 共 " + formatNumber(total) + " 个成员";
        } else if (evidenceMode === "between") {
            prefix = "社群间关系 · " + formatNumber(total) +
                " 对无向联系 · " +
                formatNumber(evidencePair.directedRelationshipCount) +
                " 条有向关注";
            if (total !== evidencePair.tieCount) {
                prefix += " · 汇总与证据页计数不一致";
            }
        } else {
            var selectedCommunity = communityCatalog.find(function (community) {
                return community.id === evidenceCommunityId;
            });
            prefix = "社群内关系 · " + formatNumber(total) +
                " 对无向联系 · " +
                formatNumber(selectedCommunity
                    ? selectedCommunity.internalDirectedEdgeCount
                    : 0) + " 条有向关注";
        }
        if (data.analysisComplete === false) {
            prefix += " · 分析结果未完整";
        }
        if (data.converged === false) {
            prefix += " · Louvain 未收敛";
        }
        count.textContent = prefix +
            (total > 0
                ? " · 当前显示 " + formatNumber(offset + 1) + "–" +
                    formatNumber(end)
                : " · 当前没有记录");
        pageLabel.textContent = total
            ? formatNumber(offset + 1) + "–" + formatNumber(end) +
                " / " + formatNumber(total)
            : "0";
        previous.disabled = offset <= 0;
        next.disabled = entries.length === 0 || offset + entries.length >= total;
        list.replaceChildren();
        if (!entries.length) {
            var empty = document.createElement("p");
            empty.className = "social-network-group-empty";
            empty.textContent = "当前页没有可展示的证据。";
            list.appendChild(empty);
            return;
        }
        entries.forEach(function (entry) {
            list.appendChild(evidenceMode === "members"
                ? createCommunityMemberEvidence(entry)
                : createCommunityTieEvidence(entry));
        });
    }

    function createCommunityMemberEvidence(member) {
        var item = document.createElement("article");
        item.className = "social-network-group-evidence-item";
        var header = document.createElement("header");
        var identity = document.createElement("strong");
        identity.textContent = member.nodeId;
        var type = document.createElement("span");
        type.textContent = member.nodeType === "external"
            ? "外部账号 · 资料未采集"
            : "账号资料";
        header.append(identity, type);
        item.appendChild(header);
        var sources = member.profileSources || [];
        if (!sources.length) {
            var empty = document.createElement("p");
            empty.className = "social-network-group-source-empty";
            empty.textContent = "没有资料源行";
            item.appendChild(empty);
            return item;
        }
        var list = document.createElement("ul");
        sources.forEach(function (source) {
            var row = document.createElement("li");
            row.textContent = source.sourceFileName + " · 第 " +
                formatNumber(source.sourceRowNumber) + " 行";
            list.appendChild(row);
        });
        item.appendChild(list);
        return item;
    }

    function createCommunityTieEvidence(tie) {
        var item = document.createElement("article");
        item.className = "social-network-group-evidence-item";
        var header = document.createElement("header");
        var endpoints = document.createElement("strong");
        endpoints.textContent = tie.leftNodeId + " — " + tie.rightNodeId;
        var count = document.createElement("span");
        count.textContent = "1 对无向联系 · " +
            formatNumber((tie.supportingRelationships || []).length) +
            " 条有向关注";
        header.append(endpoints, count);
        item.appendChild(header);
        var list = document.createElement("ul");
        list.className = "social-network-group-source-directions";
        appendRelationshipSources(
            list, tie.supportingRelationships || [], false);
        appendRelationshipSources(
            list, tie.duplicateRelationshipRecords || [], true);
        item.appendChild(list);
        return item;
    }

    function appendRelationshipSources(list, sources, duplicate) {
        sources.forEach(function (source) {
            var row = document.createElement("li");
            row.textContent = source.sourceId + " → " + source.targetId +
                " · " + source.sourceFileName + " 第 " +
                formatNumber(source.sourceRowNumber) + " 行" +
                (source.sourceArrayIndex >= 0
                    ? " · 列表索引 " +
                        formatNumber(source.sourceArrayIndex)
                    : "") +
                (duplicate ? " · 重复来源（不计入有效关系数）" : "");
            list.appendChild(row);
        });
    }

    function loadCommunityEvidencePage() {
        if (!graphEvidenceSupported()) {
            setGraphEvidenceMessage(
                "当前没有与已加载关系图匹配的完整 Louvain 结果，请刷新关系图后重试。",
                true);
            return;
        }
        if (evidenceMode === "between" && !evidencePair) {
            setGraphEvidenceMessage(
                "该社群当前没有可读取的社群间关系。",
                false);
            return;
        }
        var sequence = ++evidenceRequestSequence;
        var expectedFingerprint = graphCommunityFingerprint;
        var requestContext = window.socialNetworkWorkspace.getDatasetContext();
        var expectedContextIdentity = graphDatasetContextIdentity;
        var resource;
        var query = { offset: evidenceOffset, pageSize: 20 };
        if (evidenceMode === "between") {
            resource = "network/louvain/communities/" +
                encodeURIComponent(evidencePair.first) + "/between/" +
                encodeURIComponent(evidencePair.second) + "/evidence";
        } else {
            resource = "network/louvain/communities/" +
                encodeURIComponent(evidenceCommunityId) + "/evidence";
            query.section = evidenceMode;
        }
        evidenceLoading = true;
        evidencePage = null;
        setGraphEvidenceMessage("正在读取来源证据…", false);
        window.socialNetworkWorkspace.requestDataset(
            window.socialNetworkWorkspace.datasetUrl(
                resource, query, requestContext))
            .then(function (response) {
                if (sequence !== evidenceRequestSequence ||
                        expectedContextIdentity !== graphDatasetContextIdentity ||
                        expectedContextIdentity !==
                            datasetContextIdentity(
                                window.socialNetworkWorkspace.getDatasetContext()) ||
                        !document.getElementById(
                            "social-network-graph-evidence-dialog").open) {
                    return;
                }
                evidenceLoading = false;
                var data = response.data || {};
                if (data.dataFingerprint !== expectedFingerprint ||
                        expectedFingerprint !== graphCommunityFingerprint) {
                    setGraphEvidenceMessage(
                        "来源证据与当前关系图的数据版本不一致，请重新加载关系图。",
                        true);
                    return;
                }
                if (!data.evidenceAvailable) {
                    var unavailable = data.unavailableReason ===
                        "community_not_found"
                        ? "当前结果中找不到这个社群。"
                        : "当前社群来源证据暂不可用，请刷新分析状态后重试。";
                    setGraphEvidenceMessage(unavailable, true);
                    return;
                }
                evidencePage = data;
                renderCommunityEvidencePage(data);
            })
            .catch(function (error) {
                if (sequence === evidenceRequestSequence &&
                        !error.staleDatasetContext &&
                        expectedContextIdentity === graphDatasetContextIdentity &&
                        expectedContextIdentity ===
                            datasetContextIdentity(
                                window.socialNetworkWorkspace.getDatasetContext())) {
                    evidenceLoading = false;
                    setGraphEvidenceMessage(
                        "暂时无法读取来源证据，请稍后重试。",
                        true);
                }
            });
    }

    function selectNode(node, openDetails) {
        if (!node) {
            return;
        }
        renderSelectedNode(node);
        if (openDetails && window.openSocialNetworkUserDetail) {
            window.openSocialNetworkUserDetail(node.id);
        }
    }

    function focusNode(node) {
        var position = positions.get(node.id);
        if (!position) {
            return;
        }
        selectedId = node.id;
        if (transform.scale < 0.8) {
            transform.scale = 0.8;
        }
        transform.x = canvasWidth / 2 - position.x * transform.scale;
        transform.y = canvasHeight / 2 - position.y * transform.scale;
        renderSelectedNode(node);
        canvas.focus();
    }

    function capturePendingBridgeGraphFocus() {
        var serialized = null;
        try {
            serialized = window.sessionStorage.getItem(
                "socialNetwork.pendingBridgeGraphFocus.v1");
            if (serialized) {
                window.sessionStorage.removeItem(
                    "socialNetwork.pendingBridgeGraphFocus.v1");
            }
        } catch (error) {
            pendingBridgeGraphFocus = {
                error: "浏览器未能读取定位请求，请返回候选列表重试。"
            };
            return;
        }
        if (!serialized) {
            return;
        }
        try {
            var request = JSON.parse(serialized);
            if (!request || request.accountId == null ||
                    request.datasetId !== "think-tank-social-network" ||
                    (request.datasetVersion &&
                        request.datasetVersion !== "v1")) {
                pendingBridgeGraphFocus = {
                    error: "定位请求的数据身份无效，请返回候选列表重新发起。"
                };
                return;
            }
            var current = window.socialNetworkWorkspace.getDatasetContext();
            if (current.sessionId ||
                    current.datasetId !== request.datasetId ||
                    (current.datasetVersion &&
                        current.datasetVersion !==
                            (request.datasetVersion || "v1"))) {
                pendingBridgeGraphFocus = {
                    error: "当前数据集与候选来源不同，已取消图内定位。"
                };
                return;
            }
            pendingBridgeGraphFocus = {
                datasetId: request.datasetId,
                datasetVersion: request.datasetVersion || "v1",
                accountId: String(request.accountId),
                communityId: request.communityId || ""
            };
        } catch (error) {
            pendingBridgeGraphFocus = {
                error: "定位请求内容无法识别，请返回候选列表重新发起。"
            };
        }
    }

    function finishPendingBridgeGraphFocus(loadSequence) {
        var request = pendingBridgeGraphFocus;
        if (!request || loadSequence !== graphLoadSequence) {
            return;
        }
        pendingBridgeGraphFocus = null;
        if (request.error) {
            setStatus(request.error, "limited");
            return;
        }
        var current = window.socialNetworkWorkspace.getDatasetContext();
        if (current.sessionId || current.datasetId !== request.datasetId ||
                (current.datasetVersion &&
                    current.datasetVersion !== request.datasetVersion) ||
                (graphDatasetVersion &&
                    graphDatasetVersion !== request.datasetVersion)) {
            setStatus(
                "定位请求对应的数据版本已变化，请返回候选列表刷新后重试。",
                "limited");
            return;
        }
        var node = nodeById.get(request.accountId);
        if (!node) {
            setStatus(
                "候选账号 " + request.accountId +
                " 未显示在当前画布中。画布可能受社群筛选或展示上限影响；" +
                "完整候选排行不受画布筛选影响，可调整筛选或在图中搜索账号 ID。",
                "limited");
            return;
        }
        if (!positions.has(node.id)) {
            setStatus(
                "账号 " + request.accountId +
                " 已载入，但当前画布尚未生成可定位的位置，请稍后重试。",
                "limited");
            return;
        }
        focusNode(node);
        setStatus(
            "已在当前画布定位到账号 " + request.accountId +
            (request.communityId
                ? "（所属社群 " + request.communityId + "）"
                : "") +
            (graphTruncated
                ? "。当前画布按展示上限截取，候选排行仍基于完整关系数据。"
                : "。候选排行基于完整关系数据。"),
            graphTruncated ? "limited" : "ready");
    }

    function cancelPendingBridgeGraphFocus(message) {
        if (!pendingBridgeGraphFocus) {
            return false;
        }
        pendingBridgeGraphFocus = null;
        setStatus(message, "error");
        return true;
    }

    function renderSearchResults(matches) {
        var container = document.getElementById("social-network-graph-search-results");
        container.replaceChildren();
        if (!matches.length) {
            return;
        }
        matches.slice(0, 8).forEach(function (node) {
            var button = document.createElement("button");
            button.type = "button";
            button.className = "social-network-graph-search-result";
            button.setAttribute("role", "option");
            var title = document.createElement("strong");
            title.textContent = nodeLabel(node);
            var subtitle = document.createElement("span");
            subtitle.textContent = node.id + (node.username ? " · @" + node.username : "");
            button.append(title, subtitle);
            button.addEventListener("click", function () {
                focusNode(node);
                container.replaceChildren();
            });
            container.appendChild(button);
        });
    }

    function searchNodes(query) {
        var normalized = query.trim().toLowerCase();
        if (!normalized) {
            renderSearchResults([]);
            setStatus("请输入用户 ID、用户名或显示名称。", "limited");
            return;
        }
        var matches = nodes.filter(function (node) {
            return [node.id, node.username, node.displayName]
                .filter(Boolean)
                .some(function (value) {
                    return String(value).toLowerCase().indexOf(normalized) >= 0;
                });
        }).sort(function (left, right) {
            if (left.id === normalized) {
                return -1;
            }
            if (right.id === normalized) {
                return 1;
            }
            return nodeLabel(left).localeCompare(nodeLabel(right), "zh-CN");
        });
        renderSearchResults(matches);
        if (!matches.length) {
            setStatus("当前数据集中没有匹配的用户。", "empty");
        } else if (matches.length === 1) {
            focusNode(matches[0]);
            renderSearchResults([]);
            setStatus("已定位到 " + nodeLabel(matches[0]) + "。", "ready");
        } else {
            setStatus("找到 " + formatNumber(matches.length) + " 个匹配用户，请选择要定位的节点。");
        }
    }

    function showTooltip(node, point) {
        if (!node) {
            tooltip.classList.add("hidden");
            return;
        }
        tooltip.textContent = nodeLabel(node) + " · " + node.id;
        tooltip.style.left = Math.min(point.x + 14, canvasWidth - 220) + "px";
        tooltip.style.top = Math.max(8, point.y - 12) + "px";
        tooltip.classList.remove("hidden");
    }

    function bindInteractions() {
        canvas.addEventListener("wheel", function (event) {
            event.preventDefault();
            hasInteracted = true;
            recordDebugInteractionInput("wheel");
            zoomBy(event.deltaY < 0 ? 1.12 : 0.89, canvasPoint(event));
        }, { passive: false });

        canvas.addEventListener("pointerdown", function (event) {
            var point = canvasPoint(event);
            var node = nodeAt(point);
            pointerAction = {
                type: node ? "node" : "pan",
                nodeId: node ? node.id : null,
                startX: point.x,
                startY: point.y,
                originalX: node ? positions.get(node.id).x : transform.x,
                originalY: node ? positions.get(node.id).y : transform.y,
                moved: false
            };
            canvas.setPointerCapture(event.pointerId);
            canvas.classList.add("is-dragging");
        });

        canvas.addEventListener("pointermove", function (event) {
            var point = canvasPoint(event);
            if (pointerAction) {
                var dx = point.x - pointerAction.startX;
                var dy = point.y - pointerAction.startY;
                if (Math.abs(dx) + Math.abs(dy) > 3) {
                    pointerAction.moved = true;
                    hasInteracted = true;
                }
                if (pointerAction.type === "pan" && pointerAction.moved) {
                    recordDebugInteractionInput("pan");
                }
                if (pointerAction.type === "node" && pointerAction.moved) {
                    positions.set(pointerAction.nodeId, {
                        x: pointerAction.originalX + dx / transform.scale,
                        y: pointerAction.originalY + dy / transform.scale
                    });
                } else if (pointerAction.type === "pan") {
                    transform.x = pointerAction.originalX + dx;
                    transform.y = pointerAction.originalY + dy;
                }
                scheduleDraw();
                return;
            }
            var node = nodeAt(point);
            var nextHoveredId = node ? node.id : null;
            if (nextHoveredId !== hoveredId) {
                hoveredId = nextHoveredId;
                if (transform.scale >= 1.65) {
                    scheduleDraw();
                }
            }
            showTooltip(node, point);
        });

        function endPointer(event) {
            if (!pointerAction) {
                return;
            }
            var action = pointerAction;
            pointerAction = null;
            if (action.type === "pan") {
                finishDebugInteraction(event.type);
            }
            canvas.classList.remove("is-dragging");
            if (action.type === "node") {
                var node = nodeById.get(action.nodeId);
                selectNode(node, !action.moved);
            }
            if (canvas.hasPointerCapture(event.pointerId)) {
                canvas.releasePointerCapture(event.pointerId);
            }
        }
        canvas.addEventListener("pointerup", endPointer);
        canvas.addEventListener("pointercancel", endPointer);
        canvas.addEventListener("pointerleave", function () {
            if (!pointerAction) {
                var hadHoveredNode = hoveredId !== null;
                hoveredId = null;
                tooltip.classList.add("hidden");
                if (hadHoveredNode && transform.scale >= 1.65) {
                    scheduleDraw();
                }
            }
        });

        document.getElementById("social-network-graph-search-form")
            .addEventListener("submit", function (event) {
                event.preventDefault();
                searchNodes(document.getElementById("social-network-graph-search").value);
            });
        document.getElementById("social-network-graph-zoom-in").addEventListener(
            "click", function () { hasInteracted = true; zoomBy(1.2); });
        document.getElementById("social-network-graph-zoom-out").addEventListener(
            "click", function () { hasInteracted = true; zoomBy(0.83); });
        document.getElementById("social-network-graph-fit").addEventListener(
            "click", function () { hasInteracted = true; fitToCanvas(); });
        document.getElementById("social-network-graph-reset").addEventListener(
            "click", function () {
                hasInteracted = false;
                loading.classList.remove("hidden");
                setStatus("正在重新计算画布布局…", "loading");
                layoutGraph();
            });
        document.getElementById("social-network-graph-community-toggle")
            .addEventListener("change", function (event) {
                communitiesVisible = event.target.checked;
                draw();
            });
        document.getElementById("social-network-graph-community-filter")
            .addEventListener("change", function (event) {
                var selectedFilter = event.target.value;
                if (selectedFilter.indexOf("community:") === 0) {
                    activeCommunityFilter = "community";
                    activeCommunityId = selectedFilter.slice("community:".length);
                } else {
                    activeCommunityFilter = selectedFilter;
                    activeCommunityId = "";
                }
                hasInteracted = false;
                selectedId = null;
                renderSelectedNode(null);
                loadGraph(true);
            });
        document.querySelectorAll("[data-graph-evidence-tab]")
            .forEach(function (tab) {
                tab.addEventListener("click", function () {
                    selectCommunityEvidenceMode(
                        tab.dataset.graphEvidenceTab);
                });
            });
        document.getElementById("social-network-graph-evidence-close")
            .addEventListener("click", function () {
                document.getElementById(
                    "social-network-graph-evidence-dialog").close();
            });
        document.getElementById("social-network-graph-evidence-dialog")
            .addEventListener("close", function () {
                evidenceRequestSequence += 1;
                evidenceLoading = false;
            });
        document.getElementById("social-network-graph-evidence-pair")
            .addEventListener("change", function (event) {
                var other = event.target.value;
                evidencePair = communityPairs.find(function (pair) {
                    return pair.first === evidenceCommunityId
                        ? pair.second === other
                        : pair.first === other;
                }) || null;
                evidenceOffset = 0;
                loadCommunityEvidencePage();
            });
        document.getElementById("social-network-graph-evidence-prev")
            .addEventListener("click", function () {
                if (evidencePage) {
                    evidenceOffset = Math.max(
                        0,
                        Number(evidencePage.offset || 0) -
                            Number(evidencePage.pageSize || 20));
                    loadCommunityEvidencePage();
                }
            });
        document.getElementById("social-network-graph-evidence-next")
            .addEventListener("click", function () {
                if (evidencePage) {
                    evidenceOffset = Number(evidencePage.offset || 0) +
                        Number(evidencePage.pageSize || 20);
                    loadCommunityEvidencePage();
                }
            });
    }

    async function loadGraph(force) {
        if (loadingGraph && !force) {
            return;
        }
        loadingGraph = true;
        var loadSequence = ++graphLoadSequence;
        loading.classList.remove("hidden");
        setStatus(activeCommunityFilter === "all"
            ? "正在加载关系网络…"
            : "正在加载筛选后的关系网络…", "loading");
        // #region debug-point C:graph-request
        var debugRequestStartedAt = performance.now();
        var requestContext = window.socialNetworkWorkspace.getDatasetContext();
        var requestContextIdentity = datasetContextIdentity(requestContext);
        reportGraphDebug("C", "loadGraph", "graph request started", {
            loadSequence: loadSequence
        });
        // #endregion
        try {
            var body = await window.socialNetworkWorkspace.requestDataset(
                window.socialNetworkWorkspace.datasetUrl("network/graph", {
                    maxNodes: 1000,
                    maxEdges: 15000,
                    includeExternalNodes: true,
                    communityFilter: activeCommunityFilter,
                    communityId: activeCommunityId
                }, requestContext));
            // #region debug-point C:graph-request
            reportGraphDebug("C", "loadGraph.response",
                "graph response received", {
                    durationMs: Number(
                        (performance.now() - debugRequestStartedAt).toFixed(2)),
                    nodeCount: body.data && (body.data.nodes || []).length,
                    edgeCount: body.data && (body.data.edges || []).length
                });
            var debugTransformStartedAt = performance.now();
            // #endregion
            if (loadSequence !== graphLoadSequence ||
                    requestContextIdentity !== datasetContextIdentity(
                        window.socialNetworkWorkspace.getDatasetContext())) {
                return;
            }
            graphDatasetContextIdentity = requestContextIdentity;
            var data = body.data || {};
            graphDatasetVersion = body.meta && body.meta.datasetVersion
                ? String(body.meta.datasetVersion)
                : null;
            var nextCommunityFingerprint =
                data.communityDataFingerprint || null;
            if (nextCommunityFingerprint !== graphCommunityFingerprint) {
                evidenceRequestSequence += 1;
                communityPairs = [];
                communityPairsLoaded = false;
                communityPairsFingerprint = null;
                var evidenceDialog = document.getElementById(
                    "social-network-graph-evidence-dialog");
                if (evidenceDialog.open) {
                    evidenceDialog.close();
                }
            }
            graphCommunityFingerprint = nextCommunityFingerprint;
            nodes = data.nodes || [];
            edges = data.edges || [];
            communities = data.displayedCommunities || data.communities || [];
            graphTruncated = Boolean(data.truncated);
            var returnedNodeCount = typeof data.returnedNodeCount === "number"
                ? data.returnedNodeCount
                : data.nodeCount || nodes.length;
            var returnedEdgeCount = typeof data.returnedEdgeCount === "number"
                ? data.returnedEdgeCount
                : data.edgeCount || edges.length;
            var totalNodeCount = typeof data.totalNodeCount === "number"
                ? data.totalNodeCount
                : returnedNodeCount;
            var totalEdgeCount = typeof data.totalEdgeCount === "number"
                ? data.totalEdgeCount
                : returnedEdgeCount;
            truncationReason = data.truncationReason || "";
            if (graphTruncated) {
                truncationReason = "当前展示 " + formatNumber(returnedNodeCount) + "/" +
                    formatNumber(totalNodeCount) + " 个节点、" +
                    formatNumber(returnedEdgeCount) + "/" +
                    formatNumber(totalEdgeCount) + " 条关系。" +
                    (truncationReason ? " " + truncationReason : "");
            }
            nodeById = new Map(nodes.map(function (node) { return [node.id, node]; }));
            communityMembers = new Map();
            nodes.forEach(function (node) {
                if (node.communityId) {
                    if (!communityMembers.has(node.communityId)) {
                        communityMembers.set(node.communityId, []);
                    }
                    communityMembers.get(node.communityId).push(node);
                }
            });
            document.getElementById("social-network-graph-node-count").textContent =
                formatNumber(returnedNodeCount) + " / " + formatNumber(totalNodeCount);
            document.getElementById("social-network-graph-edge-count").textContent =
                formatNumber(returnedEdgeCount) + " / " + formatNumber(totalEdgeCount);
            document.getElementById("social-network-graph-version").textContent =
                body.meta && body.meta.datasetVersion || "未提供";
            var datasetContext = window.socialNetworkWorkspace.getDatasetContext();
            document.getElementById("social-network-graph-dataset").textContent =
                datasetContext.sessionId
                    ? "上传数据集"
                    : "智库社交网络数据集";
            renderCommunityPanel(data);
            // #region debug-point C:graph-transform
            reportGraphDebug("C", "loadGraph.transform",
                "graph data transformed", {
                    durationMs: Number(
                        (performance.now() - debugTransformStartedAt).toFixed(2)),
                    nodeCount: nodes.length,
                    edgeCount: edges.length,
                    communityCount: communities.length
                });
            // #endregion
            if (!nodes.length) {
                communities = [];
                positions.clear();
                communityMembers.clear();
                if (layoutWorker) {
                    layoutWorker.terminate();
                    layoutWorker = null;
                }
                context.clearRect(0, 0, canvas.width, canvas.height);
                loading.textContent = "当前筛选范围没有可展示的账号。";
                setStatus("当前筛选范围没有可展示的账号。", "empty");
                loaded = true;
                finishPendingBridgeGraphFocus(loadSequence);
                return;
            }
            if (data.truncated) {
                setStatus(data.truncationReason || "关系网络已按上限截取。", "limited");
            } else {
                setStatus(
                    (activeCommunityFilter === "all"
                        ? "已读取完整关系范围 · "
                        : "已读取筛选后的完整范围 · ") +
                    formatNumber(returnedNodeCount) +
                    " 个节点 · " + formatNumber(edges.length) + " 条关系",
                    "ready");
            }
            fallbackLayout();
            layoutGraph();
            loaded = true;
        } catch (error) {
            if (error.staleDatasetContext ||
                    loadSequence !== graphLoadSequence ||
                    requestContextIdentity !== datasetContextIdentity(
                        window.socialNetworkWorkspace.getDatasetContext())) {
                return;
            }
            // #region debug-point C:graph-request
            reportGraphDebug("C", "loadGraph.catch", "graph request failed", {
                durationMs: Number(
                    (performance.now() - debugRequestStartedAt).toFixed(2)),
                message: error && error.message
            });
            // #endregion
            loading.textContent = "关系网络加载失败，请返回工作台后重试。";
            loading.classList.remove("hidden");
            if (!cancelPendingBridgeGraphFocus(
                    "关系网络加载失败，图内定位未完成；请返回候选列表重试。")) {
                setStatus("关系网络暂时不可用。", "error");
            }
            if (window.console && console.error) {
                console.error("[social-network-graph] load failed", error);
            }
        } finally {
            if (loadSequence === graphLoadSequence) {
                loadingGraph = false;
            }
        }
    }

    function clearGraphForDatasetContextChange() {
        var focusRequest = pendingBridgeGraphFocus;
        var currentContext = window.socialNetworkWorkspace.getDatasetContext();
        var preserveBridgeFocus = Boolean(focusRequest &&
            !focusRequest.error &&
            !currentContext.sessionId &&
            currentContext.datasetId === focusRequest.datasetId &&
            (!currentContext.datasetVersion ||
                currentContext.datasetVersion === focusRequest.datasetVersion));
        pendingBridgeGraphFocus = preserveBridgeFocus ? focusRequest : null;
        graphDatasetVersion = null;
        graphLoadSequence += 1;
        evidenceRequestSequence += 1;
        graphDatasetContextIdentity = "";
        graphCommunityFingerprint = null;
        communityPairsFingerprint = null;
        communityPairs = [];
        communityPairsLoaded = false;
        var evidenceDialog = document.getElementById(
            "social-network-graph-evidence-dialog");
        if (evidenceDialog.open) {
            evidenceDialog.close();
        }
        loadingGraph = false;
        loaded = false;
        nodes = [];
        edges = [];
        communities = [];
        communityCatalog = [];
        activeCommunityFilter = "all";
        activeCommunityId = "";
        nodeById.clear();
        communityColorById.clear();
        communityMembers.clear();
        positions.clear();
        selectedId = null;
        hoveredId = null;
        hasInteracted = false;
        pointerAction = null;
        transform = { scale: 1, x: 0, y: 0 };
        if (pendingDrawFrame !== null) {
            cancelAnimationFrame(pendingDrawFrame);
            pendingDrawFrame = null;
        }
        if (layoutWorker) {
            layoutWorker.terminate();
            layoutWorker = null;
        }
        context.clearRect(0, 0, canvas.width, canvas.height);
        document.getElementById("social-network-graph-search").value = "";
        document.getElementById("social-network-graph-search-results")
            .replaceChildren();
        document.getElementById("social-network-graph-selected").textContent =
            "切换数据集后，请重新选择关系节点。";
        var detailButton = document.getElementById(
            "social-network-graph-user-detail");
        detailButton.disabled = true;
        detailButton.onclick = null;
        tooltip.classList.add("hidden");
        loading.textContent = "数据集已切换，等待加载关系网络…";
        loading.classList.remove("hidden");
        document.getElementById("social-network-graph-node-count").textContent = "-";
        document.getElementById("social-network-graph-edge-count").textContent = "-";
        document.getElementById("social-network-graph-version").textContent = "加载中";
        document.getElementById("social-network-graph-dataset").textContent =
            window.socialNetworkWorkspace.getDatasetContext().sessionId
                ? "上传数据集"
                : "智库社交网络数据集";
        document.getElementById("social-network-graph-community-summary").textContent =
            "等待当前数据集的关系数据；社群分析尚未运行。";
        document.getElementById("social-network-graph-community-list").innerHTML = "";
        var communityFilter = document.getElementById(
            "social-network-graph-community-filter");
        communityFilter.value = "all";
        communityFilter.disabled = true;
        document.getElementById("social-network-graph-community-method").textContent =
            "当前数据集尚无已完成的 Louvain 社群结果；可先在团体与社群页运行，再返回关系图查看筛选。";
        document.getElementById("social-network-graph-community-toggle").disabled = true;
        updateNodeLegendLabels(false);
        updateCommunityToggleLabel("not_requested", false);
        setStatus(focusRequest && !preserveBridgeFocus
            ? "数据集已切换，原候选的图内定位已取消。"
            : preserveBridgeFocus
                ? "数据版本已补全，正在继续定位候选账号。"
                : "数据集已切换，等待加载关系网络。", "loading");
    }

    function initialize() {
        if (initialized) {
            resizeCanvas();
            if (!loaded && !loadingGraph) {
                loadGraph();
            }
            return;
        }
        initialized = true;
        bindCommunityMethodTooltip();
        resizeCanvas();
        bindInteractions();
        if (window.ResizeObserver) {
            new ResizeObserver(resizeCanvas).observe(shell);
        } else {
            window.addEventListener("resize", resizeCanvas);
        }
        if (!loaded) {
            loadGraph();
        }
    }

    window.addEventListener(
        "social-network-dataset-context-changed",
        function () {
            clearGraphForDatasetContextChange();
            if (document.getElementById("page-social-network-graph")
                    .classList.contains("active")) {
                loadGraph();
            }
        });

    window.onSocialNetworkGraphPageShown = function (name) {
        if (name !== "social-network-graph") {
            graphPageVisible = false;
            return;
        }
        capturePendingBridgeGraphFocus();
        if (graphPageVisible) {
            return;
        }
        graphPageVisible = true;
        if (!initialized) {
            initialize();
            return;
        }
        if (loadingGraph) {
            graphLoadSequence += 1;
            loadingGraph = false;
        }
        loaded = false;
        loadGraph();
    };
})();
