(function () {
    "use strict";

    const grid = document.getElementById("rank-grid");
    const totalEl = document.getElementById("rank-total");
    const pageInfoEl = document.getElementById("rank-page-info");
    const prevBtn = document.getElementById("rank-prev");
    const nextBtn = document.getElementById("rank-next");
    const categoryEl = document.getElementById("rank-category");
    const sortEl = document.getElementById("rank-sort");
    const qEl = document.getElementById("rank-q");
    const searchBtn = document.getElementById("rank-search");

    const pageSize = 24;
    let page = 1;
    let total = 0;
    let loading = false;
    let detailChart = null;
    let currentVideoId = null;

    API.get("/api/health")
        .then(function (h) {
            document.getElementById("stat-videos").textContent = formatNumber(h.totalVideos);
            document.getElementById("stat-categories").textContent = h.categories;
        })
        .catch(function () {});

    API.get("/api/insights")
        .then(function (d) {
            const overall = d.decayCurves && d.decayCurves.overall;
            const C = window.chartColors ? window.chartColors() : { cyan: "#22d3ee" };
            if (overall && overall.length >= 30) {
                document.getElementById("stat-avgday30").textContent = formatNumber(overall[29]);
                let peak = 0;
                for (let i = 1; i < overall.length; i++) {
                    if (overall[i] > overall[peak]) {
                        peak = i;
                    }
                }
                document.getElementById("stat-avgpeak").textContent = peak + 1;
                addStatSpark("stat-avgday30", overall, C.cyan);
                addStatSpark("stat-avgpeak", overall, C.cyan);
            }
            if (d.categoryStats && d.categoryStats.length) {
                addStatSpark("stat-categories", d.categoryStats.map(function (c) {
                    return c.count;
                }), C.cyan);
            }
        })
        .catch(function () {});

    function addStatSpark(id, values, color) {
        const el = document.getElementById(id);
        const stat = el && el.closest(".stat");
        if (!stat || stat.querySelector(".stat-spark")) {
            return;
        }
        const spark = document.createElement("div");
        spark.className = "stat-spark";
        spark.innerHTML = window.miniTrend(values, color);
        stat.appendChild(spark);
    }

    function buildQuery() {
        const params = new URLSearchParams();
        params.set("page", page);
        params.set("pageSize", pageSize);
        if (categoryEl.value) {
            params.set("category", categoryEl.value);
        }
        if (sortEl.value) {
            params.set("sort", sortEl.value);
        }
        const q = qEl.value.trim();
        if (q) {
            params.set("q", q);
        }
        return params.toString();
    }

    async function load() {
        if (loading) {
            return;
        }
        loading = true;
        grid.innerHTML = '<div class="loading">加载中…</div>';
        try {
            const data = await API.get("/api/videos?" + buildQuery());
            total = data.total;
            render(data.items);
        } catch (e) {
            grid.innerHTML = "";
            totalEl.textContent = "";
            showToast(e.message, "error");
        } finally {
            loading = false;
        }
    }

    function render(items) {
        totalEl.textContent = "共 " + formatNumber(total) + " 条";
        if (!items.length) {
            grid.innerHTML = '<div class="empty">没有找到匹配的视频</div>';
        } else {
            grid.innerHTML = "";
            const maxDay30 = items.reduce(function (m, it) {
                return Math.max(m, it.day30 || 0);
            }, 0);
            items.forEach(function (item, i) {
                grid.appendChild(buildCard(item, (page - 1) * pageSize + i + 1, maxDay30));
            });
        }
        const pages = Math.max(1, Math.ceil(total / pageSize));
        pageInfoEl.textContent = "第 " + page + " / " + pages + " 页";
        prevBtn.disabled = page <= 1;
        nextBtn.disabled = page >= pages;
    }

    function buildCard(item, rank, maxDay30) {
        const el = document.createElement("div");
        el.className = "video-card";
        const delta = item.day1 > 0 ? ((item.day30 - item.day1) / item.day1) * 100 : 0;
        const deltaClass = delta >= 0 ? "delta-up" : "delta-down";
        const deltaText = (delta >= 0 ? "↑" : "↓") + Math.abs(delta).toFixed(1) + "%";
        const pct = maxDay30 > 0 ? ((item.day30 / maxDay30) * 100).toFixed(1) : 0;
        el.innerHTML =
            '<span class="rank-num">' + String(rank).padStart(2, "0") + "</span>" +
            coverHtml(item.coverPath) +
            '<div class="video-card-body">' +
            '<div class="video-title">' + escapeHtml(item.title) + "</div>" +
            '<div class="video-meta">' +
            "<span>" + escapeHtml(item.category) + "</span>" +
            '<span class="meta-right">' +
            '<span class="heat-badge">热度 ' + formatNumber(item.day30) + "</span>" +
            '<span class="delta-badge ' + deltaClass + '">' + deltaText + "</span>" +
            "</span>" +
            "</div>" +
            '<div class="heat-relative">' +
            '<span class="heat-relative-track">' +
            '<span class="heat-relative-fill" style="width:' + pct + '%"></span>' +
            "</span>" +
            "</div>" +
            "</div>";
        el.addEventListener("click", function () {
            openDetail(item.videoId);
        });
        return el;
    }

    async function openDetail(videoId) {
        currentVideoId = videoId;
        showModal("detail-modal");
        document.getElementById("detail-body").innerHTML = '<div class="loading">加载中…</div>';
        try {
            const video = await API.get("/api/videos/" + encodeURIComponent(videoId));
            renderDetail(video);
        } catch (e) {
            document.getElementById("detail-body").innerHTML =
                '<div class="empty">' + escapeHtml(e.message) + "</div>";
        }
    }

    function renderDetail(video) {
        const tags = (video.tags || "")
            .split(",")
            .map(function (t) { return t.trim(); })
            .filter(Boolean)
            .slice(0, 24);
        const body = document.getElementById("detail-body");
        body.innerHTML =
            '<img class="detail-cover" src="' + coverSrc(coverPathFor(video.videoId)) + '" alt="">' +
            '<div class="detail-title">' + escapeHtml(video.title) + "</div>" +
            '<div class="detail-author">' +
            (video.userImage
                ? '<img src="' + escapeHtml(video.userImage) + '" alt="">'
                : "") +
            "<span>" + escapeHtml(video.userId) + " · " + escapeHtml(video.category) + "</span>" +
            "</div>" +
            '<div class="tag-list">' +
            tags.map(function (t) { return '<span class="tag">' + escapeHtml(t) + "</span>"; }).join("") +
            "</div>" +
            '<div class="metric-row">' +
            '<div class="metric"><div class="metric-value">' + formatNumber(video.day30) + "</div>" +
            '<div class="metric-label">Day30 热度</div></div>' +
            '<div class="metric"><div class="metric-value">第 ' + video.peakDay + " 天</div>" +
            '<div class="metric-label">热度峰值</div></div>' +
            '<div class="metric"><div class="metric-value">' +
            (video.decayRate * 100).toFixed(1) + "%</div>" +
            '<div class="metric-label">衰减率</div></div>' +
            "</div>" +
            '<div id="detail-chart" class="detail-chart"></div>' +
            '<div class="detail-desc">' + escapeHtml(video.description) + "</div>" +
            '<a class="detail-link" href="' + escapeHtml(video.videoUrl) +
            '" target="_blank" rel="noopener">查看原视频 ↗</a>';
        drawCurve(video.curve);
    }

    function drawCurve(curve) {
        if (detailChart) {
            detailChart.dispose();
        }
        const C = window.chartColors ? window.chartColors() : {};
        detailChart = echarts.init(document.getElementById("detail-chart"), window.chartTheme ? window.chartTheme() : "neon");
        detailChart.setOption({
            animation: true,
            animationDuration: 800,
            animationEasing: "cubicOut",
            tooltip: { trigger: "axis" },
            grid: { left: 52, right: 20, top: 24, bottom: 34 },
            xAxis: {
                type: "category",
                data: window.days,
                axisLabel: { interval: 4 }
            },
            yAxis: { type: "value", name: "热度分" },
            series: [{
                type: "line",
                smooth: true,
                data: curve,
                symbolSize: 5,
                lineStyle: { color: C.cyan || "#22d3ee", shadowColor: C.cyanGlow || "rgba(34, 211, 238, 0.5)", shadowBlur: 10 },
                itemStyle: { color: C.cyan || "#22d3ee" },
                areaStyle: { opacity: 0.1 }
            }]
        });
    }

    window.addEventListener("resize", function () {
        if (detailChart) {
            detailChart.resize();
        }
    });

    window.onModalClosed = function (id) {
        if (id === "detail-modal" && detailChart) {
            detailChart.dispose();
            detailChart = null;
        }
    };

    window.addEventListener("themechange", function () {
        if (currentVideoId && !document.getElementById("detail-modal").classList.contains("hidden")) {
            openDetail(currentVideoId);
        }
    });

    window.openVideoDetail = openDetail;

    // ---- events ----
    searchBtn.addEventListener("click", function () {
        page = 1;
        load();
    });
    qEl.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            page = 1;
            load();
        }
    });
    categoryEl.addEventListener("change", function () {
        page = 1;
        load();
    });
    sortEl.addEventListener("change", function () {
        page = 1;
        load();
    });
    prevBtn.addEventListener("click", function () {
        if (page > 1) {
            page--;
            load();
        }
    });
    nextBtn.addEventListener("click", function () {
        page++;
        load();
    });

    load();
})();
