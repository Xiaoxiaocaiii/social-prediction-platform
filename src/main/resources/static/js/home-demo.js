(function () {
    "use strict";

    var title = document.getElementById("home-demo-title");
    var overline = document.querySelector(".home-demo-overline");
    var input = document.getElementById("home-demo-input");
    var state = document.getElementById("home-demo-state");
    var submit = document.getElementById("home-demo-submit");
    var pauseButton = document.getElementById("home-demo-pause");
    var demoWindow = document.querySelector(".home-demo-window");
    var chart = document.querySelector(".home-demo-chart");
    var kpis = document.querySelector(".home-demo-kpis");
    var predictView = document.getElementById("home-demo-predict");
    var socialView = document.getElementById("home-demo-social");
    var peak = document.getElementById("home-demo-peak");
    var day30 = document.getElementById("home-demo-day30");
    var samples = document.getElementById("home-demo-samples");

    if (!title || !overline || !input || !state || !submit || !pauseButton ||
        !demoWindow || !chart || !kpis || !predictView || !socialView) {
        return;
    }

    var topic = "城市夜跑装备分享";
    var reducedMotion = window.matchMedia &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var animationFrame = 0;
    var timelineId = 0;
    var paused = false;
    var pageVisible = true;

    function cancelTimeline() {
        timelineId += 1;
        window.cancelAnimationFrame(animationFrame);
    }

    function runTimeline(duration, render, complete) {
        var currentTimeline = timelineId;
        var elapsed = 0;
        var lastTime = null;

        function frame(now) {
            if (currentTimeline !== timelineId) {
                return;
            }
            if (lastTime !== null && !paused && pageVisible) {
                elapsed += Math.min(now - lastTime, 80);
            }
            lastTime = now;
            render(Math.min(elapsed / duration, 1), elapsed);
            if (elapsed < duration) {
                animationFrame = window.requestAnimationFrame(frame);
                return;
            }
            complete();
        }

        animationFrame = window.requestAnimationFrame(frame);
    }

    function setValues(values) {
        peak.textContent = values.peak;
        day30.textContent = values.day30;
        samples.textContent = values.samples;
    }

    function setPhase(phase) {
        demoWindow.classList.remove(
            "is-typing",
            "is-searching",
            "is-generating",
            "is-complete"
        );
        if (phase) {
            demoWindow.classList.add("is-" + phase);
        }
    }

    function showPredictionView() {
        title.textContent = "流行度预测";
        overline.textContent = "LIVE WORKSPACE PREVIEW";
        predictView.classList.remove("hidden");
        socialView.classList.add("hidden");
        demoWindow.classList.remove("is-social");
    }

    function showSocialView() {
        title.textContent = "社交网络分析";
        overline.textContent = "LIVE NETWORK PREVIEW";
        predictView.classList.add("hidden");
        socialView.classList.remove("hidden");
        demoWindow.classList.add("is-social");
        input.textContent = "World Economic Forum";
        state.textContent = "节点已选中";
        state.classList.remove("is-running");
        state.classList.add("is-complete");
        submit.textContent = "查看详情";
    }

    function showComplete() {
        setPhase("complete");
        state.textContent = "分析完成";
        state.classList.remove("is-running");
        state.classList.add("is-complete");
        submit.textContent = "重新推演";
        kpis.classList.remove("is-updating");
        setValues({
            peak: "12",
            day30: "8.42",
            samples: "36"
        });
    }

    function runPrediction() {
        cancelTimeline();
        showPredictionView();
        setPhase("typing");
        input.textContent = "";
        state.textContent = "准备分析";
        state.classList.remove("is-complete");
        state.classList.add("is-running");
        submit.textContent = "分析中";
        chart.classList.add("is-drawing");
        kpis.classList.add("is-updating");
        setValues({
            peak: "—",
            day30: "—",
            samples: "—"
        });

        if (reducedMotion) {
            input.textContent = topic;
            chart.classList.remove("is-drawing");
            showComplete();
            return;
        }

        runTimeline(3800, function (progress, elapsed) {
            var typingProgress = Math.min(elapsed / 1500, 1);
            var characters = Math.floor(topic.length * typingProgress);
            input.textContent = topic.slice(0, characters);
            if (elapsed >= 1050 && state.textContent !== "检索历史案例") {
                setPhase("searching");
                state.textContent = "检索历史案例";
            }
            if (elapsed >= 2150 && state.textContent !== "生成参考趋势") {
                setPhase("generating");
                state.textContent = "生成参考趋势";
                chart.classList.remove("is-drawing");
            }
            demoWindow.style.setProperty("--demo-progress", progress.toFixed(3));
        }, showComplete);
    }

    function runSocial() {
        cancelTimeline();
        showSocialView();
        setPhase("searching");
        state.textContent = "加载关系网络";
        state.classList.remove("is-complete");
        state.classList.add("is-running");
        submit.textContent = "加载中";
        runTimeline(2200, function (progress) {
            demoWindow.style.setProperty("--demo-progress", progress.toFixed(3));
            if (progress >= 0.7 && state.textContent !== "节点已选中") {
                setPhase("complete");
                state.textContent = "节点已选中";
                state.classList.remove("is-running");
                state.classList.add("is-complete");
                submit.textContent = "查看详情";
            }
        }, function () {});
    }

    submit.addEventListener("click", runPrediction);
    function setPaused(nextPaused) {
        paused = nextPaused;
        demoWindow.classList.toggle("is-paused", paused);
        pauseButton.setAttribute("aria-pressed", String(paused));
        pauseButton.textContent = paused ? "继续" : "暂停";
        pauseButton.setAttribute("aria-label", paused ? "继续首页演示" : "暂停首页演示");
    }

    pauseButton.addEventListener("click", function () {
        setPaused(!paused);
    });
    demoWindow.addEventListener("mouseenter", function () {
        setPaused(true);
    });
    demoWindow.addEventListener("mouseleave", function () {
        setPaused(false);
    });
    demoWindow.addEventListener("focusin", function () {
        setPaused(true);
    });
    demoWindow.addEventListener("focusout", function (event) {
        if (!demoWindow.contains(event.relatedTarget)) {
            setPaused(false);
        }
    });
    document.addEventListener("visibilitychange", function () {
        pageVisible = document.visibilityState === "visible";
        setPaused(!pageVisible);
    });

    runPrediction();

    if (!reducedMotion) {
        window.setInterval(function () {
            if (paused || !pageVisible) {
                return;
            }
            if (socialView.classList.contains("hidden")) {
                runSocial();
            } else {
                runPrediction();
            }
        }, 7200);
    }
})();
