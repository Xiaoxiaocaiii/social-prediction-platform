(function () {
    "use strict";
    if (typeof echarts === "undefined") {
        return;
    }

    var axisLine = { lineStyle: { color: "rgba(58, 90, 140, 0.22)" } };
    var axisTick = { lineStyle: { color: "rgba(58, 90, 140, 0.16)" } };
    var axisLabel = { color: "#667386" };

    echarts.registerTheme("light", {
        color: ["#3a5a8c", "#c2703d", "#5f8b7c", "#a88a58", "#7d7896", "#6f91ae"],
        backgroundColor: "transparent",
        textStyle: {
            color: "#536071",
            fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif'
        },
        title: {
            textStyle: { color: "#1e2532", fontFamily: '"Noto Serif SC", "Songti SC", serif' }
        },
        legend: {
            textStyle: { color: "#536071" },
            pageTextStyle: { color: "#536071" }
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
            splitLine: { lineStyle: { color: "rgba(58, 90, 140, 0.09)" } }
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
            splitLine: { lineStyle: { color: "rgba(58, 90, 140, 0.09)" } }
        },
        tooltip: {
            backgroundColor: "rgba(255, 255, 255, 0.97)",
            borderColor: "#e6eaf0",
            borderWidth: 1,
            padding: [8, 12],
            textStyle: { color: "#1e2532", fontSize: 12 },
            axisPointer: {
                lineStyle: { color: "rgba(58, 90, 140, 0.45)" },
                crossStyle: { color: "rgba(58, 90, 140, 0.45)" },
                shadowStyle: { color: "rgba(58, 90, 140, 0.06)" }
            }
        }
    });

    window.chartTheme = function () {
        return "light";
    };

    window.chartColors = function () {
        return {
            cyan: "#3a5a8c",
            cyanSoft: "rgba(58, 90, 140, 0.18)",
            cyanSoft2: "rgba(58, 90, 140, 0.03)",
            cyanGlow: "rgba(58, 90, 140, 0.22)",
            cyanText: "#2f4b77",
            violet: "#c2703d",
            pink: "#5f8b7c",
            gray: "#8994a3"
        };
    };
})();
