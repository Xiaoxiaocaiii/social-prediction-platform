(function () {
    "use strict";

    // ---- navigation ----
    const pages = document.querySelectorAll(".page");
    const workbenchMenu = document.getElementById("workbench-menu");
    const workbenchToggle = document.getElementById("workbench-toggle");
    const workbenchCurrent = document.getElementById("workbench-current");
    const workbenchLinks = workbenchMenu
        ? Array.from(workbenchMenu.querySelectorAll(".workbench-link"))
        : [];
    let workbenchHoverTimer = null;
    let workbenchPinned = false;
    const workbenchLabels = {
        trend: "首页",
        predict: "流行度预测",
        "social-network": "社交网络分析"
    };

    function closeWorkbenchMenu() {
        if (!workbenchMenu || !workbenchToggle) {
            return;
        }
        workbenchPinned = false;
        workbenchMenu.classList.remove("is-open");
        workbenchMenu.classList.remove("is-hovering");
        workbenchToggle.setAttribute("aria-expanded", "false");
    }

    function updateBrand(name) {
        const title = document.getElementById("brand-title");
        const subtitle = document.getElementById("brand-sub");
        const socialNetwork = name === "social-network";
        if (title) {
            title.textContent = "社交媒体分析平台";
        }
        if (subtitle) {
            subtitle.textContent = socialNetwork
                ? "REAL DATA · SOCIAL NETWORK STUDY"
                : name === "trend"
                    ? "REAL DATA · RESEARCH WORKSPACE"
                    : "REAL DATA · POPULARITY STUDY";
        }
        document.title = socialNetwork
            ? "社交媒体分析平台 · 社交网络分析"
            : name === "trend"
                ? "社交媒体分析平台"
                : "社交媒体分析平台 · 流行度预测";
    }

    function showPage(name) {
        updateBrand(name);
        const navShell = document.getElementById("nav-shell");
        if (navShell) {
            navShell.classList.toggle("hidden", name === "trend");
        }
        workbenchLinks.forEach(function (link) {
            link.classList.toggle("is-active", link.dataset.route === name);
        });
        if (workbenchCurrent) {
            workbenchCurrent.textContent = workbenchLabels[name] || "工作台";
        }
        document.body.classList.toggle("home-route", name === "trend");
        pages.forEach(function (page) {
            page.classList.toggle("active", page.id === "page-" + name);
        });
        window.scrollTo({ top: 0 });
        if (typeof window.onPageShown === "function") {
            window.onPageShown(name);
        }
        if (typeof window.onSocialNetworkPageShown === "function") {
            window.onSocialNetworkPageShown(name);
        }
    }
    window.showPage = showPage;

    const homeSceneCards = Array.from(document.querySelectorAll(".home-scene-card"));
    function toggleHomeScene(card) {
        const expanded = card.classList.toggle("is-expanded");
        card.setAttribute("aria-expanded", String(expanded));
        if (expanded) {
            homeSceneCards.forEach(function (otherCard) {
                if (otherCard !== card) {
                    otherCard.classList.remove("is-expanded");
                    otherCard.setAttribute("aria-expanded", "false");
                }
            });
        }
    }
    homeSceneCards.forEach(function (card) {
        card.addEventListener("click", function (event) {
            if (event.target.closest("a")) {
                return;
            }
            toggleHomeScene(card);
        });
        card.addEventListener("keydown", function (event) {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                toggleHomeScene(card);
            }
        });
    });

    if (workbenchMenu && workbenchToggle) {
        workbenchMenu.addEventListener("mouseenter", function () {
            clearTimeout(workbenchHoverTimer);
            workbenchMenu.classList.add("is-hovering");
        });
        workbenchMenu.addEventListener("mouseleave", function () {
            if (workbenchPinned) {
                return;
            }
            workbenchHoverTimer = setTimeout(function () {
                workbenchMenu.classList.remove("is-hovering");
            }, 140);
        });
        workbenchToggle.addEventListener("click", function () {
            workbenchPinned = !workbenchPinned;
            workbenchMenu.classList.toggle("is-open", workbenchPinned);
            workbenchMenu.classList.remove("is-hovering");
            workbenchToggle.setAttribute("aria-expanded", String(workbenchPinned));
        });
        workbenchLinks.forEach(function (link) {
            link.addEventListener("click", function () {
                clearTimeout(workbenchHoverTimer);
                closeWorkbenchMenu();
            });
        });
        document.addEventListener("click", function (event) {
            if (!workbenchMenu.contains(event.target)) {
                closeWorkbenchMenu();
            }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeWorkbenchMenu();
            }
        });
    }

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

    window.skeletonCards = function (count) {
        var html = "";
        for (var i = 0; i < count; i++) {
            html += '<div class="skeleton-card">' +
                '<div class="skeleton skeleton-cover"></div>' +
                '<div class="skeleton-card-body">' +
                '<div class="skeleton skeleton-line" style="width:88%"></div>' +
                '<div class="skeleton skeleton-line" style="width:58%"></div>' +
                '<div class="skeleton skeleton-line" style="width:38%"></div>' +
                "</div>" +
                "</div>";
        }
        return html;
    };

    window.skeletonDetail = function () {
        return '<div class="skeleton-detail">' +
            '<div class="skeleton skeleton-detail-cover"></div>' +
            '<div class="skeleton skeleton-line" style="width:70%"></div>' +
            '<div class="skeleton skeleton-line" style="width:44%"></div>' +
            '<div class="skeleton-detail-metrics">' +
            '<div class="skeleton skeleton-metric"></div>' +
            '<div class="skeleton skeleton-metric"></div>' +
            '<div class="skeleton skeleton-metric"></div>' +
            "</div>" +
            '<div class="skeleton skeleton-chart"></div>' +
            '<div class="skeleton skeleton-line" style="width:90%"></div>' +
            '<div class="skeleton skeleton-line" style="width:74%"></div>' +
            "</div>";
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
