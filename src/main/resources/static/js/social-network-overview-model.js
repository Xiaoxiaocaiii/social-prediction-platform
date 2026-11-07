(function (root, factory) {
    "use strict";

    var model = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkOverviewModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    function firstPresent() {
        for (var index = 0; index < arguments.length; index += 1) {
            if (arguments[index] !== null && arguments[index] !== undefined) {
                return arguments[index];
            }
        }
        return null;
    }

    function stateValue(status) {
        return status && typeof status === "object"
            ? status.state || status.status || null
            : status || null;
    }

    function responseData(response) {
        return response && response.data &&
            typeof response.data === "object"
            ? response.data
            : null;
    }

    function numberOrNull(value) {
        return typeof value === "number" && Number.isFinite(value)
            ? value
            : null;
    }

    function overviewFromResponses(networkResponse, interactionResponse,
            qualityResponse) {
        var network = responseData(networkResponse);
        var interaction = responseData(interactionResponse);
        var scale = interaction && interaction.scale || {};
        var metrics = interaction && interaction.metrics || {};
        var quality = responseData(qualityResponse);
        var qualityCounts = quality && quality.summary || {};
        var qualityState = stateValue(quality && quality.state);
        var issueCount = numberOrNull(qualityCounts.quality_issue_count);
        var impactCount = numberOrNull(
            qualityCounts.capability_impact_count
        );

        return {
            network: network ? {
                state: stateValue(network.state),
                nodeCount: numberOrNull(network.nodeCount),
                edgeCount: numberOrNull(network.edgeCount),
                isolatedNodeCount: numberOrNull(
                    network.isolatedNodeCount
                ),
                density: numberOrNull(network.density),
                componentCount: numberOrNull(
                    network.weaklyConnectedComponentCount
                ),
                largestComponentSize: numberOrNull(
                    network.largestWeaklyConnectedComponentNodeCount
                ),
                largestComponentRatio: numberOrNull(
                    network.largestWeaklyConnectedComponentRatio
                )
            } : null,
            interaction: interaction ? {
                state: stateValue(interaction.state),
                sampleCount: numberOrNull(scale.analysisSampleCount),
                like: metrics.like_count || null,
                reply: metrics.reply_count || null,
                repost: metrics.repost_count || null
            } : null,
            quality: quality ? {
                state: qualityState,
                issueCount: issueCount,
                capabilityImpactCount: impactCount
            } : null
        };
    }

    function fromResponse(body, context) {
        var data = body && body.data || {};
        var counts = data.counts || {};
        var quality = data.qualitySummary || {};
        var qualityCounts = quality.summary || {};
        var userQuality = quality.userQuality || {};
        var normalizedUsers = firstPresent(
            counts.normalized_user_count,
            userQuality.normalized_user_count
        );
        var status = firstPresent(
            data.status,
            body && body.meta && body.meta.qualityStatus,
            quality.datasetStatus,
            data.state
        );
        var state = stateValue(status);
        var issueCount = firstPresent(
            counts.quality_issue_count,
            qualityCounts.quality_issue_count
        );
        var impactCount = firstPresent(
            counts.capability_impact_count,
            qualityCounts.capability_impact_count
        );

        return {
            name: data.name || data.datasetId ||
                (context && context.datasetId) || "当前数据集",
            type: context && context.sessionId
                ? "临时上传数据"
                : data.type === "builtin"
                    ? "内置数据集"
                    : "当前分析数据集",
            version: data.version ||
                (body && body.meta && body.meta.datasetVersion) || null,
            status: status,
            userCount: normalizedUsers === null
                ? firstPresent(counts.user_record_count)
                : normalizedUsers,
            userCountBasis: normalizedUsers === null
                ? "用户记录；规范化用户数未提供"
                : "已规范化用户",
            relationshipCandidateCount:
                firstPresent(counts.relationship_candidate_count),
            tweetRecordCount: firstPresent(counts.tweet_record_count),
            qualityIssueCount: issueCount,
            capabilityImpactCount: impactCount,
            isTemporary: Boolean(context && context.sessionId),
            hasQualityNotice: state === "available_with_warnings" ||
                state === "limited" ||
                (issueCount !== null && Number(issueCount) > 0) ||
                (impactCount !== null && Number(impactCount) > 0)
        };
    }

    return {
        fromResponse: fromResponse,
        overviewFromResponses: overviewFromResponses
    };
});
