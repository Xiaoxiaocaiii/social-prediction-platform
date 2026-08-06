(function () {
    "use strict";

    const searchInput = document.getElementById("compare-q");
    const searchBtn = document.getElementById("compare-search");
    const pickerResults = document.getElementById("compare-picker-results");
    const selectedEl = document.getElementById("compare-selected");
    const compareBtn = document.getElementById("compare-run");
    const chartEl = document.getElementById("compare-chart");
    const tableBody = document.getElementById("compare-table-body");
    const resultEl = document.getElementById("compare-result");
    const presetA = document.getElementById("compare-preset-a");
    const presetB = document.getElementById("compare-preset-b");

    const COLORS = ["#2563eb", "#16a34a", "#d97706"];
    let selected = [];
    let chart = null;

    async function pickerSearch() {
        const q = searchInput.value.trim();
        if (!q) {
            showToast("请输入关键词搜索视频", "error");
            return;
        }
        try {
            const data = await API.get("/api/videos?q=" + encodeURIComponent(q) + "&pageSize=8");
            renderPicker(data.items);
        } catch (e) {
            showToast(e.message, "error");
        }
    }

    function renderPicker(items) {
        pickerResults.innerHTML = "";
        if (!items.length) {
            pickerResults.innerHTML = '<div class="empty">没有找到匹配的视频</div>';
            return;
        }
        items.forEach(function (item) {
            const el = document.createElement("div");
            el.className = "picker-item";
            el.innerHTML =
                '<img src="' + coverSrc(item.coverPath) + '" alt="">' +
                '<div class="picker-info">' +
                '<div class="picker-title">' + escapeHtml(item.title) + "</div>" +
                '<div class="picker-meta">' + escapeHtml(item.category) +
                " · 热度 " + formatNumber(item.day30) + "</div>" +
                "</div>";
            el.addEventListener("click", function () {
                addSelected(item);
            });
            pickerResults.appendChild(el);
        });
    }

    function addSelected(item) {
        if (selected.some(function (s) { return s.videoId === item.videoId; })) {
            showToast("该视频已在列表中", "error");
            return;
        }
        if (selected.length >= 3) {
            showToast("最多选择 3 个视频", "error");
            return;
        }
        selected.push({
            videoId: item.videoId,
            title: item.title,
            coverPath: item.coverPath,
            day30: item.day30
        });
        renderSelected();
    }

    function removeSelected(videoId) {
        selected = selected.filter(function (s) {
            return s.videoId !== videoId;
        });
        renderSelected();
    }

    function renderSelected() {
        selectedEl.innerHTML = "";
        selected.forEach(function (s) {
            const chip = document.createElement("div");
            chip.className = "chip";
            chip.innerHTML =
                '<img src="' + coverSrc(s.coverPath) + '" alt="">' +
                "<span>" + escapeHtml(s.title) + "</span>" +
                '<button class="chip-remove">×</button>';
            chip.querySelector(".chip-remove").addEventListener("click", function () {
                removeSelected(s.videoId);
            });
            selectedEl.appendChild(chip);
        });
        compareBtn.disabled = selected.length < 2;
    }

    async function compare() {
        if (selected.length < 2 || selected.length > 3) {
            showToast("请选择 2~3 个视频", "error");
            return;
        }
        compareBtn.disabled = true;
        compareBtn.textContent = "对比中…";
        try {
            const data = await API.post("/api/compare", {
                videoIds: selected.map(function (s) { return s.videoId; })
            });
            renderResult(data.items);
        } catch (e) {
            showToast(e.message, "error");
        } finally {
            compareBtn.disabled = false;
            compareBtn.textContent = "开始对比";
        }
    }

    function renderResult(items) {
        resultEl.classList.remove("hidden");
        drawChart(items);
        tableBody.innerHTML = items.map(function (video, i) {
            return "<tr>" +
                "<td><span class=\"legend-dot\" style=\"background:" + COLORS[i % 3] + "\"></span>" +
                escapeHtml(video.title) + "</td>" +
                "<td>" + escapeHtml(video.userId) + "</td>" +
                "<td>第 " + video.peakDay + " 天</td>" +
                "<td>" + (video.decayRate * 100).toFixed(1) + "%</td>" +
                "<td>" + formatNumber(video.day30) + "</td>" +
                "</tr>";
        }).join("");
        resultEl.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    function drawChart(items) {
        if (chart) {
            chart.dispose();
        }
        chart = echarts.init(chartEl);
        chart.setOption({
            tooltip: { trigger: "axis" },
            legend: { type: "scroll", top: 0 },
            grid: { left: 52, right: 20, top: 42, bottom: 34 },
            xAxis: {
                type: "category",
                data: window.days,
                axisLabel: { interval: 4 }
            },
            yAxis: { type: "value", name: "热度分" },
            series: items.map(function (video, i) {
                return {
                    name: video.title,
                    type: "line",
                    smooth: true,
                    data: video.curve,
                    symbolSize: 4,
                    lineStyle: { color: COLORS[i % 3], width: 2 },
                    itemStyle: { color: COLORS[i % 3] }
                };
            })
        });
    }

    async function loadPreset(ids) {
        try {
            const details = await Promise.all(ids.map(function (id) {
                return API.get("/api/videos/" + encodeURIComponent(id));
            }));
            selected = details.map(function (v) {
                return {
                    videoId: v.videoId,
                    title: v.title,
                    coverPath: coverPathFor(v.videoId),
                    day30: v.day30
                };
            });
            renderSelected();
            compare();
        } catch (e) {
            showToast(e.message, "error");
        }
    }

    searchBtn.addEventListener("click", pickerSearch);
    searchInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            pickerSearch();
        }
    });
    compareBtn.addEventListener("click", compare);
    presetA.addEventListener("click", function () {
        loadPreset(["a1fJqcNwCB4", "Z_xf02k7B7k"]);
    });
    presetB.addEventListener("click", function () {
        loadPreset(["krsBRQbOPQ4", "CSsD_UC5X_g"]);
    });
    window.addEventListener("resize", function () {
        if (chart) {
            chart.resize();
        }
    });

    renderSelected();
})();
