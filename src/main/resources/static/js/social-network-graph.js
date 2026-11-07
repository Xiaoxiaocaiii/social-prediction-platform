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
    var nodes = [];
    var edges = [];
    var communities = [];
    var nodeById = new Map();
    var communityIndexById = new Map();
    var communityMembers = new Map();
    var positions = new Map();
    var selectedId = null;
    var hoveredId = null;
    var transform = { scale: 1, x: 0, y: 0 };
    var canvasWidth = 0;
    var canvasHeight = 0;
    var loaded = false;
    var initialized = false;
    var loadingGraph = false;
    var graphLoadSequence = 0;
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

    function communityColor(communityId) {
        var communityIndex = communityIndexById.get(communityId);
        return communityIndex === undefined
            ? colors.profile
            : colors.communityPalette[communityIndex % colors.communityPalette.length];
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
    }

    function drawCommunities() {
        if (!communitiesVisible) {
            return;
        }
        var circles = communities.map(function (community, index) {
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
                index: index,
                center: screenPoint(center),
                radius: radius * transform.scale + 17,
                color: colors.communityPalette[index % colors.communityPalette.length]
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
        context.clearRect(0, 0, canvasWidth, canvasHeight);
        context.fillStyle = colors.canvas;
        context.fillRect(0, 0, canvasWidth, canvasHeight);
        drawCommunities();
        drawEdges(false);
        if (selectedId) {
            drawEdges(true);
        }
        nodes.forEach(drawNode);
    }

    function scheduleDraw() {
        if (pendingDrawFrame !== null) {
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
    }

    function layoutGraph() {
        var layoutSequence = graphLoadSequence;
        if (layoutWorker) {
            layoutWorker.terminate();
        }
        try {
            layoutWorker = new Worker("js/social-network-graph-layout-worker.js?v=20261014-global-graph-1");
            layoutWorker.onmessage = function (event) {
                if (layoutSequence !== graphLoadSequence) {
                    return;
                }
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
            };
            layoutWorker.onerror = function () {
                if (layoutSequence !== graphLoadSequence) {
                    return;
                }
                if (layoutWorker) {
                    layoutWorker.terminate();
                    layoutWorker = null;
                }
                fallbackLayout();
                setStatus("布局计算已降级，当前仍可缩放、平移和搜索。", "limited");
            };
            layoutWorker.postMessage({ nodes: nodes, edges: edges });
        } catch (error) {
            if (layoutSequence !== graphLoadSequence) {
                return;
            }
            fallbackLayout();
            setStatus("布局计算已降级，当前仍可缩放、平移和搜索。", "limited");
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

    function renderCommunityPanel(data) {
        var summary = document.getElementById("social-network-graph-community-summary");
        var list = document.getElementById("social-network-graph-community-list");
        var method = document.getElementById("social-network-graph-community-method");
        var communityToggle =
            document.getElementById("social-network-graph-community-toggle");
        if (data.communityAnalysisStatus !== "completed") {
            communityToggle.disabled = true;
            summary.textContent = "社群分析尚未运行，当前接口不提供社群统计。";
            list.replaceChildren();
            method.textContent =
                "当前快照未执行 Louvain。社群数量、成员和未归类节点均不可用，不能解释为 0 个社群。";
            return;
        }
        communityToggle.disabled = false;
        var convergenceLabel = data.communityConverged === false
            ? "未收敛，结果仅供探索"
            : formatNumber(data.communityIterations || 0) + " 轮后稳定";
        var displayedCommunityCount = data.displayedCommunityCount;
        if (typeof displayedCommunityCount !== "number") {
            displayedCommunityCount = communities.length;
        }
        var displayedUnassignedNodeCount = data.displayedUnassignedNodeCount;
        if (typeof displayedUnassignedNodeCount !== "number") {
            displayedUnassignedNodeCount = data.unassignedNodeCount || 0;
        }
        var communitySummary = (graphTruncated ? "当前展示子图：" : "当前完整范围：") +
            formatNumber(displayedCommunityCount) + " 个社群 · " +
            formatNumber(displayedUnassignedNodeCount) + " 个孤立节点未归类";
        if (graphTruncated) {
            communitySummary += "；完整筛选范围：" +
                formatNumber(data.globalCommunityCount || 0) + " 个社群 · " +
                formatNumber(data.globalUnassignedNodeCount || 0) + " 个孤立节点未归类";
        }
        summary.textContent = communitySummary + " · " + convergenceLabel;
        list.replaceChildren();
        communities.forEach(function (community, index) {
            var row = document.createElement("div");
            row.className = "social-network-graph-community-item";
            var marker = document.createElement("i");
            marker.style.setProperty(
                "--community-color",
                colors.communityPalette[index % colors.communityPalette.length]);
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
                : " 本次划分已达到稳定状态。");
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
    }

    async function loadGraph() {
        if (loadingGraph) {
            return;
        }
        loadingGraph = true;
        var loadSequence = ++graphLoadSequence;
        loading.classList.remove("hidden");
        setStatus("正在加载全量关系网络…", "loading");
        try {
            var body = await window.socialNetworkWorkspace.requestDataset(
                window.socialNetworkWorkspace.datasetUrl("network/graph", {
                    maxNodes: 1000,
                    maxEdges: 15000,
                    includeExternalNodes: true
                }));
            if (loadSequence !== graphLoadSequence) {
                return;
            }
            var data = body.data || {};
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
            communityIndexById = new Map(communities.map(function (community, index) {
                return [community.id, index];
            }));
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
            if (!nodes.length) {
                loading.textContent = "当前数据集中没有可展示的关系节点。";
                setStatus("没有可展示的关系网络。", "empty");
                loaded = true;
                return;
            }
            if (data.truncated) {
                setStatus(data.truncationReason || "关系网络已按上限截取。", "limited");
            } else {
                setStatus(
                    "已读取完整筛选范围 · " + formatNumber(returnedNodeCount) +
                    " 个节点 · " + formatNumber(edges.length) + " 条关系",
                    "ready");
            }
            fallbackLayout();
            layoutGraph();
            loaded = true;
        } catch (error) {
            if (error.staleDatasetContext || loadSequence !== graphLoadSequence) {
                return;
            }
            loading.textContent = "关系网络加载失败，请返回工作台后重试。";
            loading.classList.remove("hidden");
            setStatus("关系网络暂时不可用。", "error");
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
        graphLoadSequence += 1;
        loadingGraph = false;
        loaded = false;
        nodes = [];
        edges = [];
        communities = [];
        nodeById.clear();
        communityIndexById.clear();
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
        document.getElementById("social-network-graph-community-method").textContent =
            "当前只提供关系数据，不会在切换数据集时自动运行 Louvain。";
        document.getElementById("social-network-graph-community-toggle").disabled = true;
        setStatus("数据集已切换，等待加载关系网络。", "loading");
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
        if (name === "social-network-graph") {
            initialize();
        }
    };
})();
