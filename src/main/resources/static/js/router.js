(function () {
    "use strict";

    var ROUTES = {
        "/": "trend",
        "/predict": "predict",
        "/social-network": "social-network",
        "/social-network/network": "social-network-graph"
    };
    var GRAPH_ROUTE_PATH = "/social-network/network";
    var GRAPH_RETURN_SOURCES = {
        "/social-network/relations": true,
        "/social-network/relations/groups": true,
        "/social-network/relations/ranking": true
    };
    var previousPath = null;

    function currentPath() {
        var hash = location.hash || "#/";
        return hash.replace(/^#/, "") || "/";
    }

    function updateGraphReturnLink(sourcePath) {
        var backLink = document.getElementById("social-network-graph-back");
        if (!backLink) {
            return;
        }
        var returnPath = Object.prototype.hasOwnProperty.call(
            GRAPH_RETURN_SOURCES,
            sourcePath
        ) ? sourcePath : "/social-network";
        backLink.setAttribute("href", "#" + returnPath);
    }

    function showRoute() {
        var path = currentPath();
        var sourcePath = previousPath;
        previousPath = path;
        if (path === GRAPH_ROUTE_PATH) {
            updateGraphReturnLink(sourcePath);
        }
        var socialRoute = window.socialNetworkRouteTable
            ? window.socialNetworkRouteTable.resolve(path)
            : null;
        if (typeof window.showPage === "function") {
            if (socialRoute) {
                window.showPage(socialRoute.page, socialRoute.task);
                return;
            }
            if (path.indexOf("/social-network") === 0) {
                if (Object.prototype.hasOwnProperty.call(ROUTES, path)) {
                    window.showPage(ROUTES[path]);
                    return;
                }
                window.showPage("social-network-task", "unknown");
                return;
            }
            window.showPage(ROUTES[path] || ROUTES["/"]);
        }
    }

    window.addEventListener("hashchange", showRoute);
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", showRoute, { once: true });
    } else {
        showRoute();
    }
})();
