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
})();
