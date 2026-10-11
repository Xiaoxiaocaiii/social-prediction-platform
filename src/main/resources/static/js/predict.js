(function () {
    "use strict";

    const zone = document.getElementById("upload-zone");
    const fileInput = document.getElementById("predict-file");
    const hint = document.getElementById("upload-hint");
    const preview = document.getElementById("upload-preview");
    const previewImg = document.getElementById("upload-preview-img");
    const clearBtn = document.getElementById("upload-clear");
    const textEl = document.getElementById("predict-text");
    const topkInput = document.getElementById("predict-topk");
    const submitBtn = document.getElementById("predict-submit");
    const resultEl = document.getElementById("predict-result");
    const noteEl = document.getElementById("predict-note");
    const chartEl = document.getElementById("predict-chart");
    const refsEl = document.getElementById("predict-refs");
    const refsWrap = document.getElementById("predict-refs-wrap");
    const refsMoreBtn = document.getElementById("predict-refs-more");
    const methodEl = document.getElementById("predict-method");
    const subjectEl = document.getElementById("predict-subject");
    const summaryEl = document.getElementById("predict-subject-summary");
    const summaryTextEl = document.getElementById("predict-summary-text");
    const editBtn = document.getElementById("predict-edit");
    const resultsShell = document.getElementById("predict-results-shell");
    const PREDICTION_STATES = Object.freeze({
        IDLE: "idle",
        LOADING: "loading",
        SUCCESS: "success",
        EMPTY: "empty",
        ERROR: "error"
    });

    editBtn.addEventListener("click", function () {
        showInputLayout();
        textEl.focus();
        window.scrollTo({
            top: Math.max(0, subjectEl.getBoundingClientRect().top + window.scrollY - 96),
            behavior: "smooth"
        });
    });

    function showResultsLayout(text) {
        summaryTextEl.textContent = text;
        subjectEl.classList.add("hidden");
        summaryEl.classList.remove("hidden");
        resultsShell.classList.remove("hidden");
    }

    function showInputLayout() {
        subjectEl.classList.remove("hidden");
        summaryEl.classList.add("hidden");
        resultsShell.classList.add("hidden");
    }
    let predictionState = PREDICTION_STATES.IDLE;

    function setPredictionState(state) {
        if (Object.values(PREDICTION_STATES).includes(state)) {
            predictionState = state;
        }
    }

    let selectedFile = null;
    let chart = null;
    let lastPrediction = null;
    let lastOverall = null;
    let overallPromise = null;
    const REFERENCE_BATCH_SIZE = 9;
    let referenceItems = [];
    let referencesShown = 0;

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
        if (file) {
            const type = (file.type || "").toLowerCase();
            const name = (file.name || "").toLowerCase();
            const validType = ["image/jpeg", "image/jpg", "image/png", "image/webp"].includes(type);
            const validExtension = /\.(jpe?g|png|webp)$/.test(name);
            if (!validType && !(type === "" && validExtension)) {
                fileInput.value = "";
                showToast("图片格式不支持，请选择 JPG、PNG 或 WebP 图片", "error");
                return;
            }
        }
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
        if (submitBtn.disabled) {
            return;
        }
        const text = textEl.value.trim();
        if (!text) {
            showToast("请先输入文案", "error");
            textEl.focus();
            return;
        }
        submitBtn.disabled = true;
        submitBtn.textContent = "推演中...";
        showResultsLayout(text);
        clearPredictionView();
        setPredictionState(PREDICTION_STATES.LOADING);
        resultEl.classList.add("predict-loading");
        resultEl.classList.remove("hidden");
        resultEl.setAttribute("aria-busy", "true");
        methodEl.classList.add("predict-loading");
        methodEl.setAttribute("aria-busy", "true");
        refsWrap.classList.add("predict-loading");
        refsWrap.setAttribute("aria-busy", "true");
        renderReferencesLoading();
        try {
            const formData = new FormData();
            formData.append("text", text);
            formData.append("topK", topkInput ? topkInput.value : "30");
            if (selectedFile) {
                formData.append("file", selectedFile);
            }
            const data = await API.upload("/api/predict", formData);
            await render(data);
        } catch (e) {
            clearLoadingState();
            clearPredictionView();
            showInputLayout();
            setPredictionState(PREDICTION_STATES.ERROR);
            showToast(e.message, "error");
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "开始推演";
        }
    }

    function clearPredictionView() {
        if (chart) {
            chart.dispose();
            chart = null;
        }
        lastPrediction = null;
        lastOverall = null;
        noteEl.textContent = "";
        document.getElementById("kpi-peak").textContent = "—";
        document.getElementById("kpi-day30").textContent = "—";
        document.getElementById("kpi-refs").textContent = "—";
        document.getElementById("predict-kpi-trend").innerHTML = "";
        [
            "predict-method-source",
            "predict-method-fields",
            "predict-method-retrieval",
            "predict-method-matched-count",
            "predict-method-used-count",
            "predict-method-aggregation",
            "predict-method-image",
            "predict-method-boundary"
        ].forEach(function (id) {
            document.getElementById(id).textContent = "";
        });
        [
            "predict-status-normal",
            "predict-status-sparse",
            "predict-status-fallback"
        ].forEach(function (id) {
            document.getElementById(id).classList.add("hidden");
        });
        refsEl.innerHTML = "";
        referenceItems = [];
        referencesShown = 0;
        refsMoreBtn.classList.add("hidden");
        refsWrap.classList.add("hidden");
        resultEl.classList.add("hidden");
        methodEl.classList.add("hidden");
        setPredictionState(PREDICTION_STATES.IDLE);
    }

    function clearLoadingState() {
        resultEl.classList.remove("predict-loading");
        resultEl.removeAttribute("aria-busy");
        methodEl.classList.remove("predict-loading");
        methodEl.removeAttribute("aria-busy");
        refsWrap.classList.remove("predict-loading");
        refsWrap.removeAttribute("aria-busy");
    }

    function renderMethod(data, actualReferences) {
        methodEl.classList.remove("hidden");
        document.getElementById("predict-method-source").textContent = "SMTPD 真实视频数据";
        document.getElementById("predict-method-fields").textContent = "标题、标签、描述";
        document.getElementById("predict-method-retrieval").textContent =
            data.retrievalMethod || "FTS5 检索并按 BM25 排序，短文本或异常查询使用 LIKE 兜底";
        document.getElementById("predict-method-matched-count").textContent =
            data.matchedCount == null ? "接口未提供" : data.matchedCount;
        document.getElementById("predict-method-used-count").textContent =
            data.usedSampleCount == null ? actualReferences.length : data.usedSampleCount;
        document.getElementById("predict-method-aggregation").textContent =
            data.aggregationMethod || "历史案例曲线按天平均计算参考趋势";
        document.getElementById("predict-method-image").textContent =
            data.imageUsed === true ? "已参与热度计算" : "当前未参与热度计算";
        const isFallback = data.fallback === true || actualReferences.length === 0;
        document.getElementById("predict-method-boundary").textContent = isFallback
            ? "本次未使用具体相似案例，当前展示全站平均热度参考曲线，仅用于历史样本参考"
            : "当前结果仅用于历史样本参考，不代表未来热度";
        [
            "predict-status-normal",
            "predict-status-sparse",
            "predict-status-fallback"
        ].forEach(function (id) {
            document.getElementById(id).classList.add("hidden");
        });
        const statusId = isFallback
            ? "predict-status-fallback"
            : actualReferences.length < 5
                ? "predict-status-sparse"
                : "predict-status-normal";
        document.getElementById(statusId).classList.remove("hidden");
    }

    function renderEmptyReferences() {
        refsEl.innerHTML = '<div class="empty">未找到可展示的相似历史案例，当前趋势使用全站平均热度参考曲线。</div>';
    }

    function updateReferencesMoreButton() {
        const hasMore = referencesShown < referenceItems.length;
        refsMoreBtn.classList.toggle("hidden", !hasMore);
        refsMoreBtn.disabled = !hasMore;
    }

    function renderReferenceBatch() {
        const nextItems = referenceItems.slice(
            referencesShown,
            referencesShown + REFERENCE_BATCH_SIZE
        );
        nextItems.forEach(function (item) {
            refsEl.appendChild(buildRef(item));
        });
        referencesShown += nextItems.length;
        updateReferencesMoreButton();
    }

    function renderReferences(data, actualReferences) {
        refsEl.innerHTML = "";
        referenceItems = actualReferences.slice();
        referencesShown = 0;
        refsMoreBtn.classList.add("hidden");
        if (data.fallback === true || actualReferences.length === 0) {
            renderEmptyReferences();
        } else {
            renderReferenceBatch();
        }
        refsWrap.classList.remove("hidden");
    }

    async function render(data) {
        clearLoadingState();
        noteEl.textContent = data.note || "";
        const actualReferences = data.references || [];
        setPredictionState(
            data.fallback === true || actualReferences.length === 0
                ? PREDICTION_STATES.EMPTY
                : PREDICTION_STATES.SUCCESS
        );
        renderMethod(data, actualReferences);
        renderReferences(data, actualReferences);
        resultEl.classList.remove("hidden");
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
        const C = window.chartColors ? window.chartColors() : { cyan: "#22d3ee" };
        document.getElementById("predict-kpi-trend").innerHTML =
            window.miniTrend(data.prediction, C.cyan);
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
        const mainColor = "#2F6FED";
        const averageColor = "#94A3B8";
        const peakColor = "#E8894A";
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
            legend: { data: ["当前热度参考趋势", "全站平均"], top: 0, textStyle: { fontSize: 11, color: "#64748B" } },
            grid: { left: 52, right: 24, top: 44, bottom: 50 },
            xAxis: {
                type: "category",
                data: window.days,
                axisLabel: { rotate: 30, interval: "auto" }
            },
            yAxis: { type: "value", name: "热度分", scale: true },
            series: [{
                name: "当前热度参考趋势",
                type: "line",
                smooth: true,
                data: prediction,
                symbol: "none",
                lineStyle: { width: 3, color: mainColor, type: "solid" },
                itemStyle: { color: mainColor },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: "rgba(47, 111, 237, 0.18)" },
                        { offset: 1, color: "rgba(47, 111, 237, 0.02)" }
                    ])
                },
                markPoint: {
                    data: [{
                        name: "峰值 第" + (peakIdx + 1) + " 天",
                        coord: [window.days[peakIdx], prediction[peakIdx]],
                        symbol: "circle",
                        symbolSize: 8,
                        itemStyle: { color: peakColor },
                        label: {
                            position: "top",
                            fontSize: 11,
                            color: peakColor,
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
                lineStyle: { width: 2, color: averageColor, type: "dashed" },
                itemStyle: { color: averageColor },
                z: 1
            }] : [])
        });
    }

    function renderReferencesLoading() {
        refsWrap.classList.remove("hidden");
        refsMoreBtn.classList.add("hidden");
        refsMoreBtn.disabled = true;
        const skeletonCard =
            '<div class="skeleton-card">' +
            '<div class="skeleton skeleton-cover"></div>' +
            '<div class="skeleton-card-body"><div class="skeleton skeleton-line"></div><div class="skeleton skeleton-line"></div></div>' +
            "</div>";
        refsEl.innerHTML = skeletonCard.repeat(3);
    }

    function buildRef(item) {
        const el = document.createElement("div");
        el.className = "video-card";
        const category = item.category
            ? '<span>' + escapeHtml(item.category) + "</span>"
            : "";
        const peakDay = item.peakDay == null
            ? ""
            : '<span>峰值天 ' + escapeHtml(String(item.peakDay)) + "</span>";
        el.innerHTML =
            coverHtml(item.coverPath) +
            '<div class="video-card-body">' +
            '<div class="video-title">' + escapeHtml(item.title) + "</div>" +
            '<div class="video-meta">' +
            "<span>" + escapeHtml(item.userId) + "</span>" +
            category +
            "</div>" +
            '<div class="video-meta">' +
            '<span class="heat-badge">Day30 ' + formatNumber(item.day30) + "</span>" +
            peakDay +
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

    refsMoreBtn.addEventListener("click", renderReferenceBatch);
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
