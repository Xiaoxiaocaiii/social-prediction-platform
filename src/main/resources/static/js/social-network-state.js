(function (root, factory) {
    "use strict";

    var createState = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = createState;
    }
    if (root) {
        root.createSocialNetworkState = createState;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    function copyObject(value) {
        return Object.assign({}, value || {});
    }

    function parameterKey(value) {
        if (Array.isArray(value)) {
            return "[" + value.map(parameterKey).join(",") + "]";
        }
        if (value && typeof value === "object") {
            return "{" + Object.keys(value).sort().map(function (key) {
                return JSON.stringify(key) + ":" + parameterKey(value[key]);
            }).join(",") + "}";
        }
        return JSON.stringify(value);
    }

    function createSocialNetworkState(initialDatasetContext) {
        var initialContext = copyObject(initialDatasetContext);
        var state = {
            datasetContext: initialContext,
            datasetRevision: 0,
            uploadDraft: {
                files: [],
                sourceDescription: ""
            },
            uploadTaskId: null,
            uploadTaskView: null,
            conditions: {
                userSearch: { query: "" },
                localNetwork: {
                    centerNodeId: "",
                    depth: "1",
                    topK: "30",
                    includeExternal: true
                },
                ranking: { metric: "indegree" },
                structuralRoles: { limit: 20 },
                interaction: { metric: "like_count", category: "tweets" },
                topic: { draft: "", submittedTerm: null, category: "tweets" },
                export: { type: "", format: "json" }
            },
            requestSequences: {}
        };
        var taskConditionRevisions = {};

        function getDatasetContext() {
            return copyObject(state.datasetContext);
        }

        function setDatasetContext(context) {
            var next = copyObject(context);
            var current = state.datasetContext;
            var changed = current.datasetId !== next.datasetId ||
                current.datasetVersion !== next.datasetVersion ||
                current.sessionId !== next.sessionId;
            if (changed) {
                Object.keys(current).forEach(function (key) {
                    delete current[key];
                });
                Object.assign(current, next);
                state.datasetRevision += 1;
                state.conditions.topic.submittedTerm = null;
                taskConditionRevisions.topic =
                    (taskConditionRevisions.topic || 0) + 1;
            }
            return changed;
        }

        function getCondition(task) {
            return copyObject(state.conditions[task]);
        }

        function setCondition(task, patch, invalidateRequests) {
            if (!state.conditions[task]) {
                throw new Error("未知的社交网络任务状态: " + task);
            }
            Object.assign(state.conditions[task], patch || {});
            if (invalidateRequests !== false) {
                taskConditionRevisions[task] =
                    (taskConditionRevisions[task] || 0) + 1;
            }
            return getCondition(task);
        }

        function beginRequest(task, parameters) {
            var sequence = (state.requestSequences[task] || 0) + 1;
            state.requestSequences[task] = sequence;
            return {
                task: task,
                sequence: sequence,
                datasetRevision: state.datasetRevision,
                conditionRevision: taskConditionRevisions[task] || 0,
                parameters: parameterKey(parameters)
            };
        }

        function isRequestCurrent(token, parameters) {
            return Boolean(token &&
                token.datasetRevision === state.datasetRevision &&
                token.sequence === state.requestSequences[token.task] &&
                token.conditionRevision ===
                    (taskConditionRevisions[token.task] || 0) &&
                (parameters === undefined ||
                    token.parameters === parameterKey(parameters)));
        }

        function isLatestRequest(token) {
            return Boolean(token &&
                token.sequence === state.requestSequences[token.task]);
        }

        function invalidateRequest(task) {
            state.requestSequences[task] =
                (state.requestSequences[task] || 0) + 1;
        }

        function replaceUploadFiles(files) {
            state.uploadDraft.files.splice(
                0,
                state.uploadDraft.files.length,
                ...(files || [])
            );
        }

        return {
            state: state,
            getDatasetContext: getDatasetContext,
            getDatasetRevision: function () {
                return state.datasetRevision;
            },
            setDatasetContext: setDatasetContext,
            getCondition: getCondition,
            setCondition: setCondition,
            beginRequest: beginRequest,
            isRequestCurrent: isRequestCurrent,
            isLatestRequest: isLatestRequest,
            invalidateRequest: invalidateRequest,
            replaceUploadFiles: replaceUploadFiles
        };
    }

    return createSocialNetworkState;
});
