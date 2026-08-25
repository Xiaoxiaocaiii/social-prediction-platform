(function () {
    "use strict";
    if (typeof echarts === "undefined") {
        return;
    }

    var axisLine = { lineStyle: { color: "rgba(15, 23, 42, 0.16)" } };
    var axisTick = { lineStyle: { color: "rgba(15, 23, 42, 0.16)" } };
    var axisLabel = { color: "#475569" };

    echarts.registerTheme("light", {
        color: ["#ff7a3c", "#4a5fd0", "#17a37c", "#f59e0b", "#8b5cf6", "#0ea5e9"],
        backgroundColor: "transparent",
        textStyle: {
            color: "#334155",
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
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { show: false }
        },
        valueAxis: {
            axisLine: axisLine,
            axisTick: axisTick,
            axisLabel: axisLabel,
            splitLine: { lineStyle: { color: "rgba(15, 23, 42, 0.08)" } }
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
            splitLine: { lineStyle: { color: "rgba(15, 23, 42, 0.08)" } }
        },
        tooltip: {
            backgroundColor: "rgba(255, 255, 255, 0.97)",
            borderColor: "#e2e8f0",
            borderWidth: 1,
            padding: [8, 12],
            textStyle: { color: "#0f172a", fontSize: 12 },
            axisPointer: {
                lineStyle: { color: "rgba(37, 99, 235, 0.45)" },
                crossStyle: { color: "rgba(37, 99, 235, 0.45)" },
                shadowStyle: { color: "rgba(37, 99, 235, 0.06)" }
            }
        }
    });

    window.chartTheme = function () {
        return "light";
    };

    window.chartColors = function () {
        return {
            cyan: "#ff7a3c",
            cyanSoft: "rgba(255, 122, 60, 0.18)",
            cyanSoft2: "rgba(255, 122, 60, 0.03)",
            cyanGlow: "rgba(255, 122, 60, 0.22)",
            cyanText: "#f06523",
            violet: "#4a5fd0",
            pink: "#17a37c",
            gray: "#94a3b8"
        };
    };
})();
