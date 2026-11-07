"use strict";

self.onmessage = function (event) {
    var nodes = event.data.nodes || [];
    var edges = event.data.edges || [];
    var groups = new Map();
    var unassigned = [];
    nodes.forEach(function (node) {
        if (!node.communityId) {
            unassigned.push(node);
            return;
        }
        if (!groups.has(node.communityId)) {
            groups.set(node.communityId, []);
        }
        groups.get(node.communityId).push(node);
    });
    var communityIds = Array.from(groups.keys()).sort();
    var communityOrbit = Math.max(260, communityIds.length * 36);
    var centerByCommunity = new Map();
    var positions = [];
    communityIds.forEach(function (communityId, communityIndex) {
        var angle = communityIndex / Math.max(1, communityIds.length) * Math.PI * 2;
        var center = {
            x: Math.cos(angle) * communityOrbit,
            y: Math.sin(angle) * communityOrbit
        };
        centerByCommunity.set(communityId, center);
        groups.get(communityId).forEach(function (node, memberIndex) {
            var memberAngle = memberIndex * 2.399963229728653;
            var memberRadius = 18 * Math.sqrt(memberIndex + 1);
            positions.push({
                id: node.id,
                communityId: communityId,
                x: center.x + Math.cos(memberAngle) * memberRadius,
                y: center.y + Math.sin(memberAngle) * memberRadius
            });
        });
    });
    unassigned.forEach(function (node, index) {
        var angle = index * 2.399963229728653;
        var radius = communityOrbit + 200 + 18 * Math.sqrt(index + 1);
        positions.push({
            id: node.id,
            communityId: null,
            x: Math.cos(angle) * radius,
            y: Math.sin(angle) * radius
        });
    });
    var indexById = new Map();
    positions.forEach(function (position, index) {
        indexById.set(position.id, index);
    });
    var edgeIndexes = edges.map(function (edge) {
        return [indexById.get(edge.source), indexById.get(edge.target)];
    }).filter(function (edge) {
        return edge[0] !== undefined && edge[1] !== undefined;
    });

    for (var iteration = 0; iteration < 80; iteration++) {
        var forces = positions.map(function () {
            return { x: 0, y: 0 };
        });
        for (var left = 0; left < positions.length; left++) {
            for (var right = left + 1; right < positions.length; right++) {
                var dx = positions[left].x - positions[right].x;
                var dy = positions[left].y - positions[right].y;
                var distanceSquared = dx * dx + dy * dy + 25;
                var strength = 260 / distanceSquared;
                var fx = dx * strength;
                var fy = dy * strength;
                forces[left].x += fx;
                forces[left].y += fy;
                forces[right].x -= fx;
                forces[right].y -= fy;
            }
        }
        edgeIndexes.forEach(function (edge) {
            var source = positions[edge[0]];
            var target = positions[edge[1]];
            var dx = target.x - source.x;
            var dy = target.y - source.y;
            var distance = Math.sqrt(dx * dx + dy * dy) || 1;
            var strength = (distance - 115) * 0.0009;
            var fx = dx / distance * strength;
            var fy = dy / distance * strength;
            forces[edge[0]].x += fx;
            forces[edge[0]].y += fy;
            forces[edge[1]].x -= fx;
            forces[edge[1]].y -= fy;
        });

        var cooling = 1 - iteration / 100;
        positions.forEach(function (position, index) {
            var force = forces[index];
            var communityCenter = centerByCommunity.get(position.communityId);
            if (communityCenter) {
                force.x += (communityCenter.x - position.x) * 0.0007;
                force.y += (communityCenter.y - position.y) * 0.0007;
            }
            force.x -= position.x * 0.0008;
            force.y -= position.y * 0.0008;
            var magnitude = Math.sqrt(force.x * force.x + force.y * force.y) || 1;
            var step = Math.min(magnitude, 7 * cooling);
            position.x += force.x / magnitude * step;
            position.y += force.y / magnitude * step;
        });
    }

    self.postMessage({ positions: positions });
};
