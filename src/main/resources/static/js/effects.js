(function () {
    "use strict";

    var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var finePointer = window.matchMedia("(hover: hover) and (pointer: fine)").matches;

    /* ---- 预加载动画 ---- */
    var preloader = document.getElementById("preloader");
    if (preloader) {
        var finishPreloader = function () {
            if (!preloader || preloader.classList.contains("preloader-done")) {
                return;
            }
            preloader.classList.add("preloader-done");
            setTimeout(function () {
                if (preloader.parentNode) {
                    preloader.parentNode.removeChild(preloader);
                }
            }, 520);
        };
        if (reduced) {
            finishPreloader();
        } else {
            window.addEventListener("load", function () {
                setTimeout(finishPreloader, 750);
            });
            setTimeout(finishPreloader, 3000);
        }
    }

    /* ---- 主题切换 ---- */
    var themeToggle = document.getElementById("theme-toggle");
    if (themeToggle) {
        themeToggle.addEventListener("click", function () {
            var root = document.documentElement;
            var current = root.getAttribute("data-theme") || "dark";
            var next = current === "dark" ? "light" : "dark";
            root.setAttribute("data-theme", next);
            try {
                localStorage.setItem("smtpd-theme", next);
            } catch (e) { /* ignore */ }
            window.dispatchEvent(new CustomEvent("themechange", { detail: { mode: next } }));
        });
    }

    /* ---- 滚动进度条 ---- */
    var progress = document.getElementById("scroll-progress");
    if (progress) {
        var updateProgress = function () {
            var doc = document.documentElement;
            var max = doc.scrollHeight - doc.clientHeight;
            progress.style.width = (max > 0 ? (doc.scrollTop / max) * 100 : 0) + "%";
        };
        window.addEventListener("scroll", updateProgress, { passive: true });
        window.addEventListener("resize", updateProgress);
        document.querySelectorAll(".nav-btn").forEach(function (btn) {
            btn.addEventListener("click", function () {
                setTimeout(updateProgress, 60);
            });
        });
        updateProgress();
    }

    /* ---- 顶栏滚动收缩 ---- */
    var topbar = document.querySelector(".topbar");
    if (topbar) {
        var updateTopbar = function () {
            var top = document.documentElement.scrollTop || document.body.scrollTop || 0;
            topbar.classList.toggle("topbar--scrolled", top > 24);
        };
        window.addEventListener("scroll", updateTopbar, { passive: true });
        updateTopbar();
    }

    /* ---- 滚动期间暂停重特效（极光/粒子），停止滚动 180ms 后恢复 ---- */
    var heavyTimer = null;
    var markScrolling = function () {
        document.body.classList.add("page-scrolling");
        clearTimeout(heavyTimer);
        heavyTimer = setTimeout(function () {
            document.body.classList.remove("page-scrolling");
        }, 180);
    };
    window.addEventListener("scroll", markScrolling, { passive: true });

    /* ---- 磁吸按钮 ---- */
    if (finePointer && !reduced) {
        document.querySelectorAll(".btn:not(:disabled)").forEach(function (el) {
            el.addEventListener("mousemove", function (e) {
                var r = el.getBoundingClientRect();
                var dx = (e.clientX - (r.left + r.width / 2)) * 0.22;
                var dy = (e.clientY - (r.top + r.height / 2)) * 0.3;
                el.style.transform = "translate(" + dx + "px, " + dy + "px)";
            });
            el.addEventListener("mouseleave", function () {
                el.style.transform = "";
            });
        });
    }

    /* ---- 视频卡 3D 倾斜 + 高光 ---- */
    if (finePointer && !reduced) {
        var attachTilt = function (el) {
            if (el.dataset.tiltApplied) {
                return;
            }
            el.dataset.tiltApplied = "1";
            el.addEventListener("mousemove", function (e) {
                var r = el.getBoundingClientRect();
                var px = (e.clientX - r.left) / r.width - 0.5;
                var py = (e.clientY - r.top) / r.height - 0.5;
                el.style.transform = "perspective(700px) rotateX(" + (-py * 5).toFixed(2) +
                    "deg) rotateY(" + (px * 7).toFixed(2) + "deg) translateY(-3px)";
                el.style.setProperty("--gx", ((px + 0.5) * 100).toFixed(1) + "%");
                el.style.setProperty("--gy", ((py + 0.5) * 100).toFixed(1) + "%");
            });
            el.addEventListener("mouseleave", function () {
                el.style.transform = "";
                el.style.removeProperty("--gx");
                el.style.removeProperty("--gy");
            });
        };
        document.querySelectorAll(".video-card").forEach(attachTilt);
        var tiltObserver = new MutationObserver(function () {
            document.querySelectorAll(".video-card").forEach(attachTilt);
        });
        tiltObserver.observe(document.body, { childList: true, subtree: true });
    }

    /* ---- 滚动揭示 ---- */
    var revealEls = document.querySelectorAll(".reveal");
    if ("IntersectionObserver" in window && !reduced) {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add("in");
                    io.unobserve(entry.target);
                }
            });
        }, { threshold: 0.12 });
        revealEls.forEach(function (el) {
            io.observe(el);
        });
    } else {
        revealEls.forEach(function (el) {
            el.classList.add("in");
        });
    }

    /* ---- 统计数字滚动 ---- */
    var counters = document.querySelectorAll("[data-count]");
    var counterRan = false;
    var runCounter = function (el) {
        var target = parseFloat(el.getAttribute("data-count"));
        if (isNaN(target)) {
            return;
        }
        var duration = 750;
        var start = null;
        var step = function (ts) {
            if (!start) {
                start = ts;
            }
            var p = Math.min((ts - start) / duration, 1);
            var eased = 1 - Math.pow(1 - p, 3);
            el.textContent = Math.round(target * eased).toLocaleString("zh-CN");
            if (p < 1) {
                requestAnimationFrame(step);
            }
        };
        requestAnimationFrame(step);
    };
    var tryCounters = function () {
        if (counterRan) {
            return;
        }
        var page = document.getElementById("page-ranking");
        if (page && page.classList.contains("active")) {
            counters.forEach(runCounter);
            counterRan = true;
        }
    };
    document.querySelectorAll(".nav-btn").forEach(function (btn) {
        btn.addEventListener("click", function () {
            setTimeout(tryCounters, 100);
        });
    });
    tryCounters();

    /* ---- Hero 微视差 ---- */
    var trendHero = document.querySelector("#page-trend .trend-hero");
    if (trendHero && !reduced) {
        var heroLayers = [
            { el: trendHero.querySelector(".page-overline"), speed: 0.06 },
            { el: trendHero.querySelector("h1"), speed: 0.04 },
            { el: trendHero.querySelector("p"), speed: 0.10 },
            { el: trendHero.querySelector(".trend-search-wrap"), speed: 0.14 },
            { el: trendHero.querySelector(".trend-chips"), speed: 0.17 }
        ];
        var updateHeroParallax = function () {
            if (!document.getElementById("page-trend").classList.contains("active")) {
                trendHero.style.opacity = "";
                heroLayers.forEach(function (layer) {
                    if (layer.el) {
                        layer.el.style.transform = "";
                    }
                });
                return;
            }
            var y = window.scrollY || document.documentElement.scrollTop || 0;
            var h = Math.max(window.innerHeight, 1);
            var p = Math.min(Math.max(y / h, 0), 1);
            trendHero.style.opacity = String(Math.max(1 - p * 0.9, 0));
            heroLayers.forEach(function (layer) {
                if (layer.el) {
                    layer.el.style.transform = "translateY(" + (y * layer.speed).toFixed(1) + "px)";
                }
            });
        };
        window.addEventListener("scroll", updateHeroParallax, { passive: true });
        window.addEventListener("resize", updateHeroParallax);
        updateHeroParallax();
    }

    /* ---- 背景粒子 ---- */
    var canvas = document.getElementById("bg-particles");
    if (canvas && !reduced) {
        var ctx = canvas.getContext("2d");
        var parts = [];
        var resize = function () {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        };
        resize();
        window.addEventListener("resize", resize);

        var PALETTE_DARK = ["rgba(34, 211, 238, ALPHA)", "rgba(167, 139, 250, ALPHA)"];
        var PALETTE_LIGHT = ["rgba(8, 145, 178, ALPHA)", "rgba(124, 58, 237, ALPHA)"];
        var lightMode = document.documentElement.getAttribute("data-theme") === "light";
        var particleCount = lightMode ? 26 : 55;
        for (var i = 0; i < particleCount; i++) {
            parts.push({
                x: Math.random() * canvas.width,
                y: Math.random() * canvas.height,
                r: Math.random() * 1.3 + 0.4,
                vx: (Math.random() - 0.5) * 0.16,
                vy: -(Math.random() * 0.22 + 0.06),
                base: Math.random() * 0.4 + 0.15,
                c: Math.random() > 0.55 ? 0 : 1,
                tw: Math.random() * Math.PI * 2
            });
        }

        var draw = function () {
            if (document.hidden || document.body.classList.contains("page-scrolling")) {
                requestAnimationFrame(draw);
                return;
            }
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            var isLight = document.documentElement.getAttribute("data-theme") === "light";
            var palette = isLight ? PALETTE_LIGHT : PALETTE_DARK;
            var alphaMul = isLight ? 0.4 : 1;
            for (var j = 0; j < parts.length; j++) {
                var p = parts[j];
                p.x += p.vx;
                p.y += p.vy;
                p.tw += 0.02;
                if (p.y < -4) {
                    p.y = canvas.height + 4;
                    p.x = Math.random() * canvas.width;
                }
                if (p.x < -4) { p.x = canvas.width + 4; }
                if (p.x > canvas.width + 4) { p.x = -4; }
                var alpha = p.base * (0.6 + 0.4 * Math.sin(p.tw)) * alphaMul;
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
                ctx.fillStyle = palette[p.c].replace("ALPHA", alpha.toFixed(3));
                ctx.fill();
            }
            requestAnimationFrame(draw);
        };
        draw();
    }
})();
