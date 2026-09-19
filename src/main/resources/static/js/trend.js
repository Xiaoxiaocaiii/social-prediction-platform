(function () {
    "use strict";

    const searchInput = document.getElementById("trend-search");
    const searchBtn = document.getElementById("trend-search-btn");
    const previewEl = document.getElementById("hero-preview");

    function goPredict(keyword) {
        location.hash = "#/predict";
        setTimeout(function () {
            const text = document.getElementById("predict-text");
            if (text) {
                text.value = keyword;
            }
            const submit = document.getElementById("predict-submit");
            if (submit) {
                submit.click();
            }
        }, 80);
    }

    function runSearch() {
        const q = searchInput.value.trim();
        if (!q) {
            showToast("请输入关键词", "error");
            searchInput.focus();
            return;
        }
        goPredict(q);
    }

    searchBtn.addEventListener("click", runSearch);
    searchInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            runSearch();
        }
    });

    function scrollToChapter(target) {
        if (!target) {
            return;
        }
        var header = document.querySelector(".topbar");
        var offset = (header ? header.getBoundingClientRect().height : 74) + 22;
        var top = target.getBoundingClientRect().top + window.scrollY - offset;
        window.scrollTo({ top: Math.max(0, top), behavior: "smooth" });
    }

    document.querySelectorAll(".chapter-link, .start-btn").forEach(function (link) {
        link.addEventListener("click", function (event) {
            var target = document.querySelector(link.getAttribute("href"));
            if (!target) {
                return;
            }
            event.preventDefault();
            history.pushState(null, "", "#/");
            scrollToChapter(target);
            document.querySelectorAll(".chapter-link").forEach(function (chapterLink) {
                chapterLink.classList.toggle("active", chapterLink.getAttribute("href") === "#" + target.id);
            });
        });
    });

    document.querySelectorAll(".route[href]").forEach(function (link) {
        link.addEventListener("click", function () {
            location.hash = link.getAttribute("href").slice(1);
        });
    });

    function renderPreview(video) {
        const color = window.chartColors ? window.chartColors().cyan : "#3a5a8c";
        previewEl.innerHTML =
            '<article class="evidence">' +
            '<div class="evidence-head"><span>真实案例预览</span><span class="evidence-source">DAY30 RANKING</span></div>' +
            '<div class="cover"><img src="' + coverSrc(coverPathFor(video.videoId)) + '" alt=""><span class="cover-caption">A TRACEABLE VIDEO SAMPLE</span></div>' +
            '<div class="evidence-body">' +
            '<div class="evidence-title">' + escapeHtml(video.title) + "</div>" +
            '<div class="evidence-meta">' + escapeHtml(video.category) +
            " · 热度 " + formatNumber(video.day30) + "</div>" +
            '<div class="curve">' + window.miniTrend(video.curve, color) + "</div>" +
            '<div class="evidence-metrics">' +
            '<span>Peak Day<b>' + String(video.peakDay).padStart(2, "0") + "</b></span>" +
            '<span>Day30<b>' + formatNumber(video.day30) + "</b></span>" +
            '<span>Decay Rate<b>' + (video.decayRate * 100).toFixed(1) + "%</b></span>" +
            "</div>" +
            "</div>" +
            "</article>";
    }

    API.get("/api/videos?sort=day30_desc&page=1&pageSize=1")
        .then(function (data) {
            if (!data.items || !data.items.length) {
                previewEl.innerHTML =
                    '<div class="preview-card"><div class="preview-empty">今日暂无爆款</div></div>';
                return;
            }
            return API.get("/api/videos/" + encodeURIComponent(data.items[0].videoId))
                .then(renderPreview)
                .catch(function () {
                    previewEl.innerHTML =
                        '<div class="preview-card"><div class="preview-empty">预览加载失败</div></div>';
                });
        })
        .catch(function (e) {
            previewEl.innerHTML =
                '<div class="preview-card"><div class="preview-empty">加载失败：' +
                escapeHtml(e.message) + "</div></div>";
        });
})();
