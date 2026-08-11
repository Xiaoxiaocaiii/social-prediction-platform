(function () {
    "use strict";

    let loaded = false;
    let data = null;
    let charts = {};
    let decayKey = "__overall__";

    function round(v) {
        return Math.round(v * 100) / 100;
    }

    function initChart(id) {
        const el = document.getElementById(id);
        const old = echarts.getInstanceByDom(el);
        if (old) {
            old.dispose();
        }
        return echarts.init(el, window.chartTheme ? window.chartTheme() : "neon");
    }

    window.onPageShown = function (name) {
        if (name !== "insights") {
            return;
        }
        if (loaded) {
            Object.values(charts).forEach(function (c) {
                c.resize();
            });
            return;
        }
        load();
    };

    async function load() {
        try {
            data = await API.get("/api/insights");
            renderCategory();
            renderCorrelation();
            fillDecayOptions();
            renderDecay("__overall__");
            loaded = true;
        } catch (e) {
            showToast(e.message, "error");
        }
    }

    function renderCategory() {
        const cats = data.categoryStats;
        const C = window.chartColors ? window.chartColors() : {};
        charts.category = initChart("insight-category-chart");
        charts.category.setOption({
            animation: true,
            animationDuration: 800,
            animationEasing: "cubicOut",
            tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
            legend: { data: ["Day1 平均热度", "Day30 平均热度"], top: 0 },
            grid: { left: 64, right: 20, top: 44, bottom: 60 },
            xAxis: {
                type: "category",
                position: "bottom",
                data: cats.map(function (c) { return c.category; }),
                axisLabel: { rotate: 30, interval: "auto", fontSize: 11 },
                axisTick: { alignWithLabel: true }
            },
            yAxis: {
                type: "value",
                name: "热度分",
                nameLocation: "middle",
                nameGap: 42
            },
            series: [
                {
                    name: "Day1 平均热度",
                    type: "bar",
                    data: cats.map(function (c) { return round(c.avgDay1); }),
                    itemStyle: { color: C.violet || "#a78bfa" },
                    barMaxWidth: 16
                },
                {
                    name: "Day30 平均热度",
                    type: "bar",
                    data: cats.map(function (c) { return round(c.avgDay30); }),
                    itemStyle: { color: C.cyan || "#22d3ee" },
                    barMaxWidth: 16
                }
            ]
        });
    }

    function renderCorrelation() {
        const corr = data.correlation;
        const C = window.chartColors ? window.chartColors() : {};
        document.getElementById("insight-correlation-title").textContent =
            "早期热度与最终热度相关性（r = " + corr.pearson.toFixed(4) + "）";
        charts.correlation = initChart("insight-correlation-chart");
        charts.correlation.setOption({
            animation: true,
            animationDuration: 700,
            animationEasing: "cubicOut",
            tooltip: {
                formatter: function (p) {
                    return "Day1: " + p.value[0].toFixed(2) + "<br>Day30: " + p.value[1].toFixed(2);
                }
            },
            grid: { left: 70, right: 28, top: 20, bottom: 48 },
            xAxis: {
                type: "value",
                name: "Day1 热度",
                nameLocation: "end",
                nameGap: 20,
                min: 0
            },
            yAxis: {
                type: "value",
                name: "Day30 热度",
                nameLocation: "middle",
                nameGap: 44,
                min: 0
            },
            series: [{
                type: "scatter",
                data: corr.scatter,
                symbolSize: 3,
                itemStyle: {
                    color: C.cyan || "#22d3ee",
                    opacity: 0.7,
                    shadowBlur: 8,
                    shadowColor: C.cyanGlow || "rgba(34, 211, 238, 0.5)"
                }
            }]
        });
    }

    function fillDecayOptions() {
        const select = document.getElementById("insight-decay-category");
        const cats = Object.keys(data.decayCurves.byCategory);
        select.innerHTML =
            '<option value="__overall__">整体</option>' +
            cats.map(function (c) {
                return '<option value="' + escapeHtml(c) + '">' + escapeHtml(c) + "</option>";
            }).join("");
        select.addEventListener("change", function () {
            decayKey = select.value;
            renderDecay(select.value);
        });
    }

    function renderDecay(key) {
        decayKey = key;
        const curves = data.decayCurves;
        const C = window.chartColors ? window.chartColors() : {};
        const arr = key === "__overall__"
            ? curves.overall
            : curves.byCategory[key] || curves.overall;
        charts.decay = initChart("insight-decay-chart");
        charts.decay.setOption({
            animation: true,
            animationDuration: 900,
            animationEasing: "cubicOut",
            tooltip: { trigger: "axis" },
            grid: { left: 64, right: 20, top: 30, bottom: 44 },
            xAxis: {
                type: "category",
                data: window.days,
                axisLabel: { interval: 4 }
            },
            yAxis: {
                type: "value",
                name: "热度分",
                nameLocation: "middle",
                nameGap: 42,
                min: 0
            },
            series: [{
                type: "line",
                smooth: true,
                data: arr.map(function (v) { return round(v); }),
                symbolSize: 4,
                lineStyle: { color: C.cyan || "#22d3ee", shadowColor: C.cyanGlow || "rgba(34, 211, 238, 0.5)", shadowBlur: 10 },
                itemStyle: { color: C.cyan || "#22d3ee" },
                areaStyle: { opacity: 0.1 }
            }]
        });
    }

    window.addEventListener("resize", function () {
        Object.values(charts).forEach(function (c) {
            c.resize();
        });
    });

    window.addEventListener("themechange", function () {
        if (!loaded || !data) {
            return;
        }
        renderCategory();
        renderCorrelation();
        renderDecay(decayKey);
    });
})();
