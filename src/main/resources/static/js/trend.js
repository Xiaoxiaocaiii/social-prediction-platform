(function () {
    "use strict";

    const searchInput = document.getElementById("trend-search");
    const searchBtn = document.getElementById("trend-search-btn");
    const hotGrid = document.getElementById("trend-hot-grid");
    const scrollDown = document.getElementById("trend-scroll-down");

    if (scrollDown) {
        scrollDown.addEventListener("click", function () {
            const hot = document.querySelector(".trend-hot");
            if (hot) {
                hot.scrollIntoView({ behavior: "smooth", block: "start" });
            }
        });
    }

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

    document.querySelectorAll(".trend-chip").forEach(function (chip) {
        chip.addEventListener("click", function () {
            const word = chip.textContent.trim();
            searchInput.value = word;
            searchInput.focus();
        });
    });

    function buildHot(item, rank) {
        const delta = item.day1 > 0 ? ((item.day30 - item.day1) / item.day1) * 100 : 0;
        const deltaClass = delta >= 0 ? "delta-up" : "delta-down";
        const deltaText = (delta >= 0 ? "↑" : "↓") + Math.abs(delta).toFixed(1) + "%";
        const el = document.createElement("div");
        el.className = "video-card";
        el.innerHTML =
            '<span class="rank-num">' + String(rank).padStart(2, "0") + "</span>" +
            '<img class="video-cover" loading="lazy" src="' + coverSrc(item.coverPath) + '" alt="">' +
            '<div class="video-card-body">' +
            '<div class="video-title">' + escapeHtml(item.title) + "</div>" +
            '<div class="trend-card-note">Day30 较 Day1 ' +
            (delta >= 0 ? "上涨 " : "下降 ") + Math.abs(delta).toFixed(1) + "%</div>" +
            '<div class="video-meta">' +
            "<span>" + escapeHtml(item.category) + "</span>" +
            '<span class="meta-right">' +
            '<span class="heat-badge">热度 ' + formatNumber(item.day30) + "</span>" +
            '<span class="delta-badge ' + deltaClass + '">' + deltaText + "</span>" +
            "</span>" +
            "</div>" +
            "</div>";
        el.addEventListener("click", function () {
            if (typeof window.openVideoDetail === "function") {
                window.openVideoDetail(item.videoId);
            }
        });
        return el;
    }

    API.get("/api/videos?sort=day30_desc&page=1&pageSize=5")
        .then(function (data) {
            if (!data.items || !data.items.length) {
                hotGrid.innerHTML = '<div class="empty">暂无数据</div>';
                return;
            }
            data.items.forEach(function (item, i) {
                hotGrid.appendChild(buildHot(item, i + 1));
            });
        })
        .catch(function (e) {
            hotGrid.innerHTML = '<div class="empty">加载失败：' + escapeHtml(e.message) + "</div>";
        });
})();
