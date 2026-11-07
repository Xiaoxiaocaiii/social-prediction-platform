(function (root, factory) {
    "use strict";

    var routeTable = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = routeTable;
    }
    if (root) {
        root.socialNetworkRouteTable = routeTable;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    var taskByPath = {
        "/social-network": "overview",
        "/social-network/data": "data",
        "/social-network/data/upload": "upload",
        "/social-network/data/quality": "quality",
        "/social-network/relations": "relations",
        "/social-network/relations/ranking": "ranking",
        "/social-network/relations/user": "user-relations",
        "/social-network/relations/groups": "groups",
        "/social-network/content": "interaction",
        "/social-network/content/topic": "topic"
    };

    var taskDetails = {
        overview: {
            title: "分析概览",
            returnHref: "#/",
            returnLabel: "返回平台首页"
        },
        data: {
            title: "数据与质量",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        upload: {
            title: "上传数据",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        quality: {
            title: "数据与质量",
            returnHref: "#/social-network/data",
            returnLabel: "返回数据与质量"
        },
        relations: {
            title: "网络结构",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        ranking: {
            title: "节点排行",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        "user-relations": {
            title: "用户关系",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        groups: {
            title: "团体与社群",
            returnHref: "#/social-network/relations",
            returnLabel: "返回关系与节点"
        },
        interaction: {
            title: "互动分析",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        },
        topic: {
            title: "主题分析",
            returnHref: "#/social-network",
            returnLabel: "返回分析概览"
        }
    };

    function resolve(path) {
        var task = taskByPath[path];
        if (!task) {
            return null;
        }
        return Object.assign({
            page: "social-network-task",
            task: task
        }, taskDetails[task]);
    }

    function getTask(task) {
        if (!Object.prototype.hasOwnProperty.call(taskDetails, task)) {
            return null;
        }
        return Object.assign({ task: task }, taskDetails[task]);
    }

    return {
        resolve: resolve,
        getTask: getTask,
        paths: Object.keys(taskByPath)
    };
});
