# 发给 Trae 的提示词（整段复制即可）

> 用法：把下面分割线之间的全部内容复制粘贴给 Trae（建议在项目根目录打开工程后再发）。
> 它会让 Trae 先读完交接文档和关键代码、把项目跑起来自测，再开始改代码。

---

你是接手「多模态社交媒体流行度预测与可视化分析平台」的开发者。这是一个**本地运行的 Java + SQLite + 原生前端**的真实数据平台（大创答辩 + 软著申报用），此前由另一位同学开发，现在由你继续。请严格按下面的顺序推进。

## 第一步：先读交接文档，不要急着改代码

按顺序读，`docs/trae-handoff.md` 必须**完整读完**：

1. `docs/trae-handoff.md` —— 交接文档（项目目标 / 架构 / 数据 / 接口 / 铁律 / 踩坑 / 当前进度 / 验收清单）。**这是主文档，必须完整读完**
2. `README.md` —— 对外说明

以下为**本地文档**，仓库里可能没有；能拿到就读，拿不到也不影响接手（关键结论已全部收进交接文档）：

- `docs/PLAN.md` —— 历史完整方案，接口契约与数据结构的源头
- `docs/DEMO.md` —— 演示脚本与备用文案
- `docs/成果清单.md` —— 软著 / 成果申报素材
- `codex-handoff.md` —— 上一轮开发者的备忘（**部分已过期，一切以 `docs/trae-handoff.md` 为准**）

## 第二步：读关键代码，建立全局认识

后端（Java 17 + Spring Boot 3.2.5 + Spring JDBC + SQLite）：

- `src/main/java/com/prediction/PredictionApplication.java`
- `src/main/java/com/prediction/api/`：`VideoController`、`PredictController`、`InsightsController`、`CompareController`、`CoverController`、`HealthController`
- `src/main/java/com/prediction/data/VideoRepository.java`：**所有 SQL 都集中在这个文件**
- `src/main/java/com/prediction/service/`：`PredictionService` 接口、`BaselinePredictionService`、`RemotePredictionService`、`PredictionServiceSelector`
- `src/main/java/com/prediction/config/`：`AppProperties`、`SqliteConfig`
- `src/main/resources/application.yml`

前端（原生 HTML/CSS/JS + 本地 ECharts，无任何构建链）：

- `src/main/resources/static/index.html`：5 个页面的 DOM 与脚本加载顺序
- `src/main/resources/static/js/app.js`：页面切换、Toast、全局工具函数、弹窗
- `src/main/resources/static/js/router.js`：hash 路由
- `src/main/resources/static/js/api.js`：接口封装
- `src/main/resources/static/js/theme.js`：仅浅色单主题 + 图表配色
- `src/main/resources/static/js/effects.js`：滚动渐入
- 五个页面模块：`trend.js`、`predict.js`、`ranking.js`、`insights.js`、`compare.js`

## 第三步：把项目跑起来并自测（改代码之前先做这一步）

```bash
# 启动（本项目必须从仓库根目录启动，数据路径基于 ${user.dir}）
mvn -s mvn-settings.xml spring-boot:run

# 健康检查，应返回 totalVideos=282481、categories=15
curl http://localhost:8080/api/health

# 跑测试，24 个用例应全绿
mvn -s mvn-settings.xml test
```

浏览器打开 <http://localhost:8080>，按交接文档第 13 节的「回归验收清单」把 5 个页面走一遍：
首页（滚到底两个区块必须可见）、上传预测（输入「三毛水果艺术」应出结果）、排行榜（搜索「火锅」约 167 条）、数据洞察（r = 0.9296）、曲线对比（点「示例：热门 vs 冷门」）。

> 数据文件在 `data/`（`smtpd.db` 2.03GB、`img_yt.zip` 9.93GB、`covers/` 1.99 万张），**不在 Git 仓库里**。
> 如果 `data/smtpd.db` 不存在，所有接口都会失败 —— 先跟我要数据，不要自己造数据。

## 必须遵守的红线（违反即返工）

1. **禁止任何 mock / 假数据**。页面上的每个数字都必须由 `data/smtpd.db` 真实算出。
2. **不做没有数据支撑的展示**。登录按钮、预测准确率、日均更新量、跨周涨幅等已被明确否决；算不出来就别加。
3. **禁止破坏接口契约与算法**。`/api/*` 的返回结构、基线预测算法（BM25 检索 + 参考案例真实曲线平均）保持稳定，后端只允许向后兼容地新增字段。
4. **禁止引入前端构建链**。没有 npm / webpack / Vite，答辩现场要能断网运行；确实需要库就把文件放进 `static/lib/`。
5. **预测页输入区布局不可改**（左上传区 + 右文案区的左右分栏）；只允许调 ECharts `option` 的样式层。
6. **图表数据与坐标映射不可改**（曲线数值、X/Y 轴映射、峰值标注位置与内容）。
7. **不要从外部 dispose ECharts 实例**，统一走 `window.dispatchEvent(new Event("themechange"))` 触发重绘。
8. **数据资产不入库**：`data/`、`target/`、`mvn-settings.xml` 不得提交；提交前必须 `git status` 自查。
9. **不要改分类名里的双空格**（如 `People  Blogs`），改了筛选和检索会直接失效。
10. **不要改字段名与 URL 路径**，也不要动 `VideoRepository` 里 FTS 检索的 SQL 形状（`WHERE video_id IN (SELECT ... FROM videos_fts WHERE videos_fts MATCH ?)`），直接的 FTS JOIN 会让查询卡死数十秒。

## 两个最容易踩的坑（务必记住）

- **改前端后必须同步静态资源**：Spring Boot 从 `target/classes` 提供静态文件，改完 `src/main/resources/static/` 要执行
  `mvn -s mvn-settings.xml process-resources`（或重启），并用 `Ctrl+F5` 强制刷新，否则你看到的还是旧文件。
- **改 JS/CSS 后递增缓存版本号**：`index.html` 里的资源都带 `?v=20260818`，改完记得把版本号往上加。

## 工作方式要求

- **先汇报再动手**：读完文档和代码后，先用一段话向我汇报你的理解（架构、数据流、当前进度、你打算做什么），确认无误后再改代码。
- **改视觉/交互前先做小样让我确认**，不要一次性大改版（历史上多次出现「改完又改回上一版」）。
- **每个改动都要真实自测**：跑测试 + 浏览器实际走查，用真实数据验证，而不是只看代码。
- **遇到不确定的地方先问我**，尤其是涉及数据口径、接口契约、视觉风格的问题，不要自己拍板。
- **提交前必查**：`git status` 确认没有 `data/`、`target/`、`mvn-settings.xml`、大体积 `.doc` 被误加。

## 当前状态与可选的后续方向

**已完成**：数据层（282,481 行 / 15 分类 / FTS5 trigram）、6 个后端接口、基线预测（BM25 + 参考案例曲线平均）、远程模型双实现可切换、5 个前端页面（hash 路由）、浅色单主题视觉、24 个测试全绿、README 与 5 张截图已上 GitHub。

**未完成 / 待定**（先跟我确认要不要做、按什么顺序做）：

1. 接入 LSTM 预测模型（Python 服务，契约已在交接文档 6.3 节，切 `app.prediction-mode=remote` 即可，前端零改动）
2. 让上传图片真正参与预测（依赖模型能力）
3. `POST /api/generate` 生成增强契约落地（扩散模型样例）
4. 软著申报材料整理（`docs/成果清单.md` 已有素材）
5. 顺手可修的小瑕疵：`README.md` 写 CSV 是「35 列」，实际是 **45 列**（15 基础列 + Day 1~30）

**已明确暂缓、不要自作主张加的功能**：搜索联想下拉、热门词滚动 ticker。

---

现在开始第一步：读完 `docs/trae-handoff.md` 与关键代码后，先向我汇报你的理解，再等我确认下一步做什么。
