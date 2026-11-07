(function (root, factory) {
    "use strict";

    var exportModel = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = exportModel;
    }
    if (root) {
        root.socialNetworkExportModel = exportModel;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    function copy(value) {
        return JSON.parse(JSON.stringify(value == null ? {} : value));
    }

    function freeze(value) {
        Object.keys(value).forEach(function (key) {
            if (value[key] && typeof value[key] === "object" &&
                    !Object.isFrozen(value[key])) {
                freeze(value[key]);
            }
        });
        return Object.freeze(value);
    }

    function createSnapshot(exportType, datasetContext, query) {
        if (!datasetContext || !datasetContext.datasetId) {
            throw new Error("导出快照缺少数据集身份");
        }
        var context = {
            datasetId: String(datasetContext.datasetId),
            datasetVersion: datasetContext.datasetVersion || null,
            sessionId: datasetContext.sessionId || null
        };
        var parameters = copy(query);
        return freeze({
            exportType: String(exportType),
            datasetContext: freeze(context),
            query: parameters
        });
    }

    function matchesDataset(snapshot, datasetContext) {
        if (!snapshot || !datasetContext) {
            return false;
        }
        var expected = snapshot.datasetContext;
        return expected.datasetId === datasetContext.datasetId &&
            expected.datasetVersion === (datasetContext.datasetVersion || null) &&
            expected.sessionId === (datasetContext.sessionId || null);
    }

    return {
        createSnapshot: createSnapshot,
        matchesDataset: matchesDataset
    };
});
