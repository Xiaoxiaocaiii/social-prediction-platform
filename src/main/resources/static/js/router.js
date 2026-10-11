(function () {
    "use strict";

    var ROUTES = {
        "/": "trend",
        "/predict": "predict",
        "/social-network": "social-network"
    };

    function currentPath() {
        var hash = location.hash || "#/";
        var path = hash.replace(/^#/, "") || "/";
        return ROUTES[path] ? path : "/";
    }

    function showRoute() {
        var page = ROUTES[currentPath()];
        if (typeof window.showPage === "function") {
            window.showPage(page);
        }
    }

    window.addEventListener("hashchange", showRoute);
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", showRoute, { once: true });
    } else {
        showRoute();
    }
    window.addEventListener("load", showRoute, { once: true });
})();
