(function () {
    "use strict";

    const zone = document.getElementById("upload-zone");
    const fileInput = document.getElementById("predict-file");
    const hint = document.getElementById("upload-hint");
    const preview = document.getElementById("upload-preview");
    const previewImg = document.getElementById("upload-preview-img");
    const clearBtn = document.getElementById("upload-clear");
    const textEl = document.getElementById("predict-text");
    const topkEl = document.getElementById("predict-topk");
    const submitBtn = document.getElementById("predict-submit");
    const resultEl = document.getElementById("predict-result");
    const noteEl = document.getElementById("predict-note");
    const chartEl = document.getElementById("predict-chart");
    const refsEl = document.getElementById("predict-refs");
    const refsWrap = document.getElementById("predict-refs-wrap");

    let selectedFile = null;
    let chart = null;
    let lastPrediction = null;
    let lastOverall = null;
    let overallPromise = null;

    function getOverall() {
        if (!overallPromise) {
            overallPromise = API.get("/api/insights")
                .then(function (d) {
                    return (d.decayCurves && d.decayCurves.overall) || null;
                })
                .catch(function () {
                    return null;
                });
        }
        return overallPromise;
    }

    function setFile(file) {
        selectedFile = file;
        if (file) {
            if (previewImg.src) {
                URL.revokeObjectURL(previewImg.src);
            }
            previewImg.src = URL.createObjectURL(file);
            hint.classList.add("hidden");
            preview.classList.remove("hidden");
        } else {
            preview.classList.add("hidden");
            hint.classList.remove("hidden");
            previewImg.src = "";
        }
    }

    zone.addEventListener("click", function () {
        fileInput.click();
    });

    fileInput.addEventListener("change", function () {
        setFile(fileInput.files[0] || null);
    });

    clearBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        fileInput.value = "";
        setFile(null);
    });

    ["dragover", "dragenter"].forEach(function (ev) {
        zone.addEventListener(ev, function (e) {
            e.preventDefault();
            zone.classList.add("dragover");
        });
    });
    ["dragleave", "drop"].forEach(function (ev) {
        zone.addEventListener(ev, function (e) {
            e.preventDefault();
            zone.classList.remove("dragover");
        });
    });
    zone.addEventListener("drop", function (e) {
        const file = e.dataTransfer.files && e.dataTransfer.files[0];
        if (file) {
            setFile(file);
        }
    });

    async function submit() {
        const text = textEl.value.trim();
        if (!text) {
            showToast("请先输入文案", "error");
            textEl.focus();
            return;
        }
        submitBtn.disabled = true;
        submitBtn.textContent = "预测中…";
        resultEl.classList.add("hidden");
        try {
            const formData = new FormData();
            formData.append("text", text);
            formData.append("topK", topkEl.value);
            if (selectedFile) {
                formData.append("file", selectedFile);
            }
            const data = await API.upload("/api/predict", formData);
            await render(data);
        } catch (e) {
            showToast(e.message, "error");
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "开始预测";
        }
    }

    async function render(data) {
        noteEl.textContent = data.note || "";
        refsEl.innerHTML = "";
        if (!data.references || !data.references.length) {
            refsEl.innerHTML = '<div class="empty">未找到参考案例，已使用全库平均曲线兜底</div>';
        } else {
            data.references.forEach(function (item) {
                refsEl.appendChild(buildRef(item));
            });
        }
        resultEl.classList.remove("hidden");
        refsWrap.classList.remove("hidden");
        let peakIdx = 0;
        for (let i = 1; i < data.prediction.length; i++) {
            if (data.prediction[i] > data.prediction[peakIdx]) {
                peakIdx = i;
            }
        }
        document.getElementById("kpi-peak").textContent = peakIdx + 1;
        document.getElementById("kpi-day30").textContent = formatNumber(data.prediction[29]);
        document.getElementById("kpi-refs").textContent =
            (data.references ? data.references.length : 0);
        const overall = await getOverall();
        drawChart(data.prediction, overall);
        resultEl.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    function drawChart(prediction, overall) {
        lastPrediction = prediction;
        lastOverall = overall || null;
        if (chart) {
            chart.dispose();
        }
        const C = window.chartColors ? window.chartColors() : {};
        chart = echarts.init(chartEl, window.chartTheme ? window.chartTheme() : "neon");

        let peakIdx = 0;
        for (let i = 1; i < prediction.length; i++) {
            if (prediction[i] > prediction[peakIdx]) {
                peakIdx = i;
            }
        }
        chart.setOption({
            animation: true,
            animationDuration: 900,
            animationEasing: "cubicOut",
            tooltip: { trigger: "axis" },
            legend: { data: ["基线预测", "全站平均"], top: 0, textStyle: { fontSize: 11 } },
            grid: { left: 52, right: 24, top: 44, bottom: 50 },
            xAxis: {
                type: "category",
                data: window.days,
                axisLabel: { rotate: 30, interval: "auto" }
            },
            yAxis: { type: "value", name: "热度分", scale: true },
            series: [{
                name: "基线预测",
                type: "line",
                smooth: true,
                data: prediction,
                symbol: "none",
                lineStyle: { width: 3, color: C.cyan || "#22d3ee", shadowColor: C.cyanGlow || "rgba(34, 211, 238, 0.55)", shadowBlur: 12 },
                itemStyle: { color: C.cyan || "#22d3ee" },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: C.cyanSoft || "rgba(34, 211, 238, 0.28)" },
                        { offset: 1, color: C.cyanSoft2 || "rgba(34, 211, 238, 0.03)" }
                    ])
                },
                markPoint: {
                    data: [{
                        name: "峰值 第" + (peakIdx + 1) + " 天",
                        coord: [window.days[peakIdx], prediction[peakIdx]],
                        symbol: "circle",
                        symbolSize: 8,
                        itemStyle: { color: C.cyan || "#22d3ee" },
                        label: {
                            position: "top",
                            fontSize: 11,
                            color: C.cyanText || "#67e8f9",
                            fontWeight: 600
                        }
                    }]
                }
            }].concat(overall ? [{
                name: "全站平均",
                type: "line",
                smooth: true,
                data: overall,
                symbol: "none",
                lineStyle: { width: 2, color: C.gray || "#64748b", type: "dashed" },
                itemStyle: { color: C.gray || "#64748b" },
                z: 1
            }] : [])
        });
    }

    function buildRef(item) {
        const el = document.createElement("div");
        el.className = "video-card";
        el.innerHTML =
            '<img class="video-cover" loading="lazy" src="' + coverSrc(item.coverPath) + '" alt="">' +
            '<div class="video-card-body">' +
            '<div class="video-title">' + escapeHtml(item.title) + "</div>" +
            '<div class="video-meta">' +
            "<span>" + escapeHtml(item.userId) + "</span>" +
            '<span class="heat-badge">热度 ' + formatNumber(item.day30) + "</span>" +
            "</div>" +
            "</div>";
        el.addEventListener("click", function () {
            if (typeof window.openVideoDetail === "function") {
                window.openVideoDetail(item.videoId);
            }
        });
        return el;
    }

    window.addEventListener("resize", function () {
        if (chart) {
            chart.resize();
        }
    });

    submitBtn.addEventListener("click", submit);
    window.addEventListener("themechange", function () {
        if (lastPrediction) {
            drawChart(lastPrediction, lastOverall);
        }
    });
    textEl.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
            submit();
        }
    });
})();
