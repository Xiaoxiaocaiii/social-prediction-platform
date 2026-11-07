(function () {
    "use strict";

    var ROUTES = {
        "/": "trend",
        "/predict": "predict",
        "/social-network": "social-network",
        "/social-network/network": "social-network-graph"
    };

    function currentPath() {
        var hash = location.hash || "#/";
        return hash.replace(/^#/, "") || "/";
    }

    function showRoute() {
        var path = currentPath();
        var socialRoute = window.socialNetworkRouteTable
            ? window.socialNetworkRouteTable.resolve(path)
            : null;
        if (typeof window.showPage === "function") {
            if (socialRoute) {
                window.showPage(socialRoute.page, socialRoute.task);
                return;
            }
            if (path.indexOf("/social-network") === 0) {
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
