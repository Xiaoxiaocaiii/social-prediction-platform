(function () {
    "use strict";
    if (typeof echarts === "undefined") {
        return;
    }

    var axisLine = { lineStyle: { color: "rgba(103, 232, 249, 0.28)" } };
    var axisTick = { lineStyle: { color: "rgba(103, 232, 249, 0.28)" } };
    var axisLabel = { color: "#94a3b8" };

    echarts.registerTheme("neon", {
        color: ["#22d3ee", "#a78bfa", "#fb7185", "#34d399", "#fbbf24", "#60a5fa"],
        backgroundColor: "transparent",
        textStyle: {
            color: "#94a3b8",
            fontFamily: '"PingFang SC", "Microsoft YaHei", "Segoe UI", system-ui, sans-serif'
        },
        title: {
            textStyle: { color: "#e2e8f0" }
        },
        legend: {
            textStyle: { color: "#94a3b8" },
            pageTextStyle: { color: "#94a3b8" }
        },
        categoryAxis: {
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { show: false }
        },
        valueAxis: {
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { lineStyle: { color: "rgba(103, 232, 249, 0.08)" } }
        },
        xAxis: {
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { show: false }
        },
        yAxis: {
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { lineStyle: { color: "rgba(103, 232, 249, 0.08)" } }
        },
        tooltip: {
            backgroundColor: "rgba(13, 21, 38, 0.94)",
            borderColor: "rgba(103, 232, 249, 0.35)",
            borderWidth: 1,
            padding: [8, 12],
            textStyle: { color: "#e2e8f0", fontSize: 12 },
            axisPointer: {
                lineStyle: { color: "rgba(103, 232, 249, 0.45)" },
                crossStyle: { color: "rgba(103, 232, 249, 0.45)" },
                shadowStyle: { color: "rgba(34, 211, 238, 0.08)" }
            }
        }
    });

    echarts.registerTheme("dawn", {
        color: ["#0891b2", "#7c3aed", "#e11d48", "#059669", "#d97706", "#2563eb"],
        backgroundColor: "transparent",
        textStyle: {
            color: "#475569",
            fontFamily: '"PingFang SC", "Microsoft YaHei", "Segoe UI", system-ui, sans-serif'
        },
        title: {
            textStyle: { color: "#0f172a" }
        },
        legend: {
            textStyle: { color: "#475569" },
            pageTextStyle: { color: "#475569" }
        },
        categoryAxis: {
            axisLine: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisTick: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisLabel: { color: "#475569" },
            splitLine: { show: false }
        },
        valueAxis: {
            axisLine: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisTick: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisLabel: { color: "#475569" },
            splitLine: { lineStyle: { color: "rgba(15, 23, 42, 0.08)" } }
        },
        xAxis: {
            axisLine: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisTick: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisLabel: { color: "#475569" },
            splitLine: { show: false }
        },
        yAxis: {
            axisLine: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisTick: { lineStyle: { color: "rgba(15, 23, 42, 0.18)" } },
            axisLabel: { color: "#475569" },
            splitLine: { lineStyle: { color: "rgba(15, 23, 42, 0.08)" } }
        },
        tooltip: {
            backgroundColor: "rgba(255, 255, 255, 0.96)",
            borderColor: "#e2e8f0",
            borderWidth: 1,
            padding: [8, 12],
            textStyle: { color: "#0f172a", fontSize: 12 },
            axisPointer: {
                lineStyle: { color: "rgba(15, 23, 42, 0.35)" },
                crossStyle: { color: "rgba(15, 23, 42, 0.35)" },
                shadowStyle: { color: "rgba(37, 99, 235, 0.08)" }
            }
        }
    });

    window.chartTheme = function () {
        return document.documentElement.getAttribute("data-theme") === "light" ? "dawn" : "neon";
    };

    window.chartColors = function () {
        var light = document.documentElement.getAttribute("data-theme") === "light";
        return {
            cyan: light ? "#0891b2" : "#22d3ee",
            cyanSoft: light ? "rgba(8, 145, 178, 0.24)" : "rgba(34, 211, 238, 0.28)",
            cyanSoft2: light ? "rgba(8, 145, 178, 0.03)" : "rgba(34, 211, 238, 0.03)",
            cyanGlow: light ? "rgba(8, 145, 178, 0.3)" : "rgba(34, 211, 238, 0.55)",
            cyanText: light ? "#0e7490" : "#67e8f9",
            violet: light ? "#7c3aed" : "#a78bfa",
            pink: light ? "#e11d48" : "#fb7185",
            gray: light ? "#94a3b8" : "#64748b"
        };
    };
})();
