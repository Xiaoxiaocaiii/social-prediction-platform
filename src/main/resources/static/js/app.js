(function () {
    "use strict";

    // ---- navigation ----
    const navButtons = document.querySelectorAll(".nav-btn");
    const pages = document.querySelectorAll(".page");

    function showPage(name) {
        navButtons.forEach(function (btn) {
            btn.classList.toggle("active", btn.dataset.page === name);
        });
        pages.forEach(function (page) {
            page.classList.toggle("active", page.id === "page-" + name);
        });
        window.scrollTo({ top: 0 });
    }

    navButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            showPage(btn.dataset.page);
        });
    });

    // ---- toast ----
    const toast = document.getElementById("toast");
    let toastTimer = null;

    window.showToast = function (message, type) {
        toast.textContent = message;
        toast.className = "toast show " + (type || "error");
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            toast.className = "toast";
        }, 3200);
    };

    // ---- helpers ----
    window.formatNumber = function (value) {
        if (value === null || value === undefined || isNaN(value)) {
            return "-";
        }
        return Number(value).toLocaleString("zh-CN", { maximumFractionDigits: 2 });
    };

    window.coverSrc = function (coverPath) {
        return coverPath || "img/cover-placeholder.svg";
    };

    window.days = Array.from({ length: 30 }, function (_, i) {
        return "第 " + (i + 1) + " 天";
    });

    // ---- image fallback (capture-phase so it works for dynamically added imgs) ----
    document.addEventListener("error", function (e) {
        const img = e.target;
        if (!img || img.tagName !== "IMG") {
            return;
        }
        if (img.dataset.fallbackApplied === "1") {
            return;
        }
        if (img.src && img.src.indexOf("cover-placeholder.svg") !== -1) {
            return;
        }
        img.dataset.fallbackApplied = "1";
        img.src = "img/cover-placeholder.svg";
    }, true);
})();
