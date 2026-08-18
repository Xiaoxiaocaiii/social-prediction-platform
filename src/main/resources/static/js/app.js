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
        if (typeof window.onPageShown === "function") {
            window.onPageShown(name);
        }
    }
    window.showPage = showPage;

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

    window.coverHtml = function (coverPath) {
        return '<div class="video-cover-wrap">' +
            '<img class="video-cover" loading="lazy" src="' + coverSrc(coverPath) + '" alt="">' +
            '<span class="cover-overlay"></span>' +
            '<svg class="cover-trend-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
            'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
            '<polyline points="3 17 9 11 13 15 21 7"></polyline>' +
            '<polyline points="15 7 21 7 21 13"></polyline>' +
            '</svg>' +
            '</div>';
    };

    window.coverPathFor = function (videoId) {
        if (!videoId) {
            return "img/cover-placeholder.svg";
        }
        const prefix = videoId.length >= 2 ? videoId.slice(0, 2) : "xx";
        return "/covers/" + prefix + "/" + videoId + ".jpg";
    };

    window.escapeHtml = function (value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;"
            }[ch];
        });
    };

    window.days = Array.from({ length: 30 }, function (_, i) {
        return "第 " + (i + 1) + " 天";
    });

    window.miniTrend = function (values, color) {
        if (!values || values.length < 2) {
            return "";
        }
        color = color || "currentColor";
        var w = 76;
        var h = 24;
        var pad = 2;
        var min = values[0];
        var max = values[0];
        values.forEach(function (v) {
            if (v < min) { min = v; }
            if (v > max) { max = v; }
        });
        var range = (max - min) || 1;
        var pts = values.map(function (v, i) {
            var x = pad + (i / (values.length - 1)) * (w - pad * 2);
            var y = h - pad - ((v - min) / range) * (h - pad * 2);
            return x.toFixed(1) + "," + y.toFixed(1);
        });
        var area = "0," + h + " " + pts.join(" ") + " " + w + "," + h;
        return '<svg class="kpi-spark" viewBox="0 0 ' + w + " " + h +
            '" preserveAspectRatio="none" aria-hidden="true">' +
            '<polygon points="' + area + '" fill="' + color + '" opacity="0.12"/>' +
            '<polyline points="' + pts.join(" ") + '" fill="none" stroke="' + color +
            '" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/>' +
            "</svg>";
    };

    // ---- modal ----
    window.showModal = function (id) {
        document.getElementById(id).classList.remove("hidden");
    };

    window.hideModal = function (id) {
        document.getElementById(id).classList.add("hidden");
        if (typeof window.onModalClosed === "function") {
            window.onModalClosed(id);
        }
    };

    document.querySelectorAll("[data-close]").forEach(function (el) {
        el.addEventListener("click", function () {
            hideModal(el.dataset.close);
        });
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            document.querySelectorAll(".modal:not(.hidden)").forEach(function (m) {
                hideModal(m.id);
            });
        }
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
