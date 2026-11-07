(function (root, factory) {
    "use strict";

    var model = factory(
        root && root.socialNetworkOverviewModel
            ? root.socialNetworkOverviewModel
            : require("./social-network-overview-model.js")
    );
    if (typeof module === "object" && module.exports) {
        module.exports = model;
    }
    if (root) {
        root.socialNetworkDatasetModel = model;
    }
})(typeof window !== "undefined" ? window : globalThis, function (overviewModel) {
    "use strict";

    function presentText(value) {
        if (value === null || value === undefined) {
            return null;
        }
        var text = String(value).trim();
        return !text || /^(null|undefined)$/i.test(text) ? null : text;
    }

    function validDate(value) {
        var date = presentText(value);
        if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
            return null;
        }
        var parsed = new Date(date + "T00:00:00Z");
        return !Number.isNaN(parsed.getTime()) &&
            parsed.toISOString().slice(0, 10) === date
            ? date
            : null;
    }

    function fromResponse(body, context) {
        var response = body || {};
        var data = response.data || {};
        var counts = data.counts || {};
        var range = data.timeRange || {};
        var files = Array.isArray(data.files) ? data.files : null;
        var summary = overviewModel.fromResponse(response, context);

        return {
            datasetId: presentText(data.datasetId) ||
                presentText(context && context.datasetId),
            sessionId: presentText(context && context.sessionId),
            name: presentText(data.name) || summary.name,
            type: summary.type,
            version: summary.version,
            status: summary.status,
            sourceDescription: presentText(data.sourceDescription),
            timeRangeFrom: validDate(range.from),
            timeRangeTo: validDate(range.to),
            fileCount: counts.file_count === null || counts.file_count === undefined
                ? null
                : counts.file_count,
            files: files && files.map(function (file) {
                return {
                    logicalFileName: presentText(file && file.logicalFileName),
                    role: presentText(file && file.role),
                    format: presentText(file && file.format),
                    encoding: presentText(file && file.encoding)
                };
            }),
            userCount: summary.userCount,
            userCountBasis: summary.userCountBasis,
            relationshipCandidateCount: summary.relationshipCandidateCount,
            tweetRecordCount: summary.tweetRecordCount,
            isTemporary: summary.isTemporary,
            hasQualityNotice: summary.hasQualityNotice
        };
    }

    return {
        fromResponse: fromResponse,
        presentText: presentText,
        validDate: validDate
    };
});
