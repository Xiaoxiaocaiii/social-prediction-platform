(function () {
    "use strict";

    var ROUTES = {
        "/": "trend",
        "/predict": "predict",
        "/ranking": "ranking",
        "/insights": "insights",
        "/compare": "compare"
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
    showRoute();
})();
