# 多模态社交媒体流行度预测与可视化分析平台 · 交接文档（给 Trae）

> 生成日期：2026-09-14 ｜ 分支：`main` ｜ 最新提交：`7bfebf6`
> 远程仓库：https://github.com/Xiaoxiaocaiii/social-prediction-platform.git
> 用途：把项目完整交接给接手方继续开发
> 说明：本文件是当前项目交接主文档，首页规范和改动总结位于 `docs/` 目录。

---

## 0. 阅读顺序建议

1. **第 1 节 项目定位与红线** —— 先明确什么绝对不能做
2. **第 2 节 技术栈与运行方式** —— 先让项目在你机器上跑起来
3. **第 9 节 铁律（禁改清单）** —— 动任何代码前必须先看
4. **第 10、11 节 踩坑记录与本机环境** —— 能省掉大量试错时间
5. **第 3~8 节** —— 仓库结构、数据层、后端、前端、接口契约
6. **第 12、13 节** —— 当前状态、待办、回归验收清单

---

## 1. 项目定位与红线

**项目名称**：多模态社交媒体流行度预测与可视化分析平台

**用途**：大学生创新训练项目（大创）答辩演示 + 软件著作权申报

**一句话**：把 SMTPD 数据集中的 282,481 条真实 YouTube 视频（封面图 + 文本 + 30 天热度序列）做成一个「数据可浏览、预测可演示、结论可追溯」的平台。

### 红线（用户多次强调，违反即返工）

1. **全部真实数据，禁止任何 mock / 假数据**。页面上出现的每一个数字都必须能从 `data/smtpd.db` 真实算出来。
2. **不做没有数据支撑的展示**。已明确否决过的：登录按钮、预测准确率、日均更新量、跨周涨幅等虚构指标。
3. **检索与预测是「基线实现」**：BM25 检索 + 参考案例真实曲线平均。模型（LSTM / 扩散模型）后续以独立 Python 服务接入，接口契约先留好。
4. **前端零 CDN 依赖**。ECharts、字体、图标全部本地化，因为答辩现场可能断网。
5. **接口契约向后兼容**：只允许新增字段，不允许修改已有字段的含义或结构。

---

## 2. 技术栈与运行方式

| 层次 | 选型 |
| --- | --- |
| 后端 | Java 17 + Spring Boot 3.2.5 + Maven + Spring JDBC |
| 数据库 | SQLite（xerial sqlite-jdbc 3.47.1.0），单文件索引 |
| 检索 | SQLite FTS5 `trigram` 分词（title / tags / description）+ BM25 排序，短词 LIKE 兜底 |
| 前端 | 原生 HTML/CSS/JS + ECharts 5.5.1（`src/main/resources/static/lib/echarts.min.js`，本地化） |
| 测试 | JUnit 5 + MockMvc，24 个用例（3+2+5+8+6） |
| 数据处理 | Python 脚本（CSV → SQLite、封面提取、跨磁盘分卷还原） |

> **重要**：这是**纯本地运行**的项目。不做 Docker、不做线上部署，**没有** npm / webpack / Vite 等前端构建链。前端就是静态文件，改完刷新浏览器即可（注意第 11 节的静态资源缓存坑）。

### 桌面 Edge 专用边界

- 项目仅面向桌面端 Microsoft Edge 浏览器开发与验收，覆盖最新 3 个正式版本及 Edge 企业版。
- 桌面验收分辨率为 `1920×1080`、`2560×1440`、`3840×2160`；页面采用固定桌面布局。
- 永久禁止接入移动端、手机、平板、触摸设备或任何响应式断点需求。
- CSS 禁止新增尺寸媒体查询；仅保留 `prefers-reduced-motion` 无障碍规则。
- 每次提交前必须运行 `pwsh -File scripts/check-desktop-only.ps1`，需求评审必须确认“不涉及移动端适配”。

### 启动

```bash
# 方式一：Windows 双击
start.bat

# 方式二：
mvn -s mvn-settings.xml spring-boot:run
```

浏览器打开 <http://localhost:8080>

> `mvn-settings.xml` 是**本机专用** Maven 配置（指向可写的本地仓库），已被 .gitignore 忽略。
> 标准 Maven 环境可直接用 `mvn spring-boot:run`，不需要 `-s`。

### 测试

```bash
mvn -s mvn-settings.xml test
```

### 健康检查

```bash
curl http://localhost:8080/api/health
# => {"code":0,"message":"ok","data":{"status":"ok","totalVideos":282481,"categories":15}}
```

---

## 3. 仓库结构

```text
social-prediction-platform/
├─ src/main/java/com/prediction/
│  ├─ PredictionApplication.java        # 启动类
│  ├─ api/                              # 6 个 Controller + 若干请求/响应 record
│  ├─ common/                           # ApiResponse 统一响应 + GlobalExceptionHandler
│  ├─ config/                           # AppProperties（配置）+ SqliteConfig（数据源）
│  ├─ data/                             # VideoRepository（所有 SQL）+ CoverService + 各 record
│  └─ service/                          # 预测服务（接口 + 基线实现 + 远程实现 + 选择器）
├─ src/main/resources/
│  ├─ application.yml                   # 端口 8080 + app.* 数据路径与预测模式
│  └─ static/                           # 前端（index.html + css/ + js/ + lib/ + img/）
├─ src/test/java/com/prediction/         # 5 个测试类，共 24 个用例
├─ scripts/
│  ├─ build_index.py                    # CSV → SQLite（建表 + 普通索引 + FTS5 trigram）
│  ├─ extract_covers.py                 # 从 img_yt.zip 批量提取封面
│  └─ extract_spanning_zip.py           # 跨磁盘分卷 zip 还原（见第 10 节坑 1）
├─ docs/
│  ├─ screenshots/                      # 5 张界面截图
│  ├─ home-preview-ac.html              # 当前首页原型
│  ├─ home-nav-improvement.md           # 首页导航规范
│  ├─ home-content-writing-plan.md      # 首页内容规范
│  ├─ project-change-summary.md         # 当前改动总结
│  └─ trae-handoff.md                   # 本文件
├─ data/                                # 数据资产（gitignore，见第 4 节）
├─ start.bat                            # 一键启动
├─ mvn-settings.xml                     # 本机 Maven 配置（gitignore）
└─ pom.xml
```

### 代码量分布（便于判断改动影响面）

| 文件 | 规模 | 职责 |
| --- | --- | --- |
| `data/VideoRepository.java` | ~11.8KB | **全部 SQL 集中在此**：分页/检索/详情/统计/相关案例 |
| `static/css/style.css` | ~31.9KB | 全站样式（最大文件，改样式优先看它） |
| `static/index.html` | ~19.3KB | 5 个页面 + 弹窗 + 页脚的完整 DOM |
| `static/js/insights.js` | ~10.2KB | 洞察页三图 + KPI + 结论卡 |
| `static/js/ranking.js` | ~10.5KB | 排行榜列表/筛选/分页/详情弹窗 |
| `static/js/predict.js` | ~9.2KB | 预测页上传 + 结果图 + 参考案例 |
| `static/js/compare.js` | ~8.9KB | 对比页选片 + 叠加曲线 + 指标表 |
| `static/js/app.js` | ~6.3KB | 页面切换、Toast、全局工具函数、弹窗、图片兜底 |
| `static/js/trend.js` | ~3.5KB | 首页搜索联动 + 今日爆款预览卡 |

---

## 4. 数据层

### 4.1 数据资产（全部在 `data/`，**不入版本库**）

| 文件 | 实际大小 | 说明 |
| --- | --- | --- |
| `data/basic_view_pn.csv` | 608 MB | SMTPD 原始数据，282,481 行，**45 列**（15 基础列 + Day 1~30） |
| `data/smtpd.db` | 2.03 GB | SQLite 索引（`videos` 表 + `videos_fts` 全文索引），由 `build_index.py` 生成 |
| `data/img_yt.zip` | 9.93 GB | 封面压缩包，内层路径为 `img_yt/{video_id}.jpg` |
| `data/covers/` | 19,901 张 / 1,444 个前缀子目录 | 已提取的本地封面，路径 `${video_id前2位}/${video_id}.jpg` |
| `data/uploads/` | 41 个文件 | 演示时上传的图片（仅存档，不参与计算） |

> 仓库里**没有**这些数据文件。接手时要么拿到用户提供的数据，要么按 4.3 重建。
> 克隆仓库后如果 `data/smtpd.db` 缺失，平台所有接口都会报错 —— 这是最常见的「页面能开但接口全挂」原因。

### 4.2 SQLite 表结构

**`videos` 表（45 列）**

- 文本列（10）：`video_id`（主键）、`video_url`、`video_cover`、`user_url`、`user_image`、`user_id`、`category`、`title`、`tags`、`description`
- 数值列（5）：`user_subscribers`、`video_duration`、`title_length`、`tag_num`、`desc_length`
- 时序列（30）：`day01` ~ `day30`（热度分，公式 `log2(观看数 / 天数 + 1)`）

**索引**

- `idx_videos_category`（category）
- `idx_videos_day30`（day30，排序用）
- `videos_fts`：FTS5 虚拟表，`tokenize='trigram'`，索引 `title / tags / description`，`video_id` 为 `UNINDEXED` 列

**真实规模（已核对）**：`videos` 282,481 行，15 个分类，最大分类 `People  Blogs`（92,960 条）。

> 注意分类名里有**双空格**（`People  Blogs`、`News  Politics`、`Howto  Style`、`Film  Animation`、`Nonprofits  Activism`、`Travel  Events`、`Science  Technology`、`Autos  Vehicles`、`Pets  Animals`）。前端下拉选项与后端 `category = ?` 精确匹配都依赖这个原始写法，**不要手贱去规范化空格**。

### 4.3 重建索引（只在数据缺失时才需要）

```bash
python scripts/build_index.py            # 全量（约十来分钟，取决于机器）
python scripts/build_index.py --limit 20000   # 冒烟测试用
```

脚本行为：读 CSV → 建 `videos` 表与索引 → 批量插入（2 万条一提交）→ 建 FTS5 表并回填。
输出形如 `done: 282,481 rows, 15 categories, 0 skipped`。

> **坑**：`build_index.py` 会先 `DROP TABLE IF EXISTS`，**直接覆盖** `data/smtpd.db`。跑之前确认这不是你唯一的数据副本。

---

## 5. 后端实现

### 5.1 配置（`application.yml`）

```yaml
server:
  port: 8080
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
app:
  db-path: ${user.dir}/data/smtpd.db
  covers-dir: ${user.dir}/data/covers
  uploads-dir: ${user.dir}/data/uploads
  covers-zip: ${user.dir}/data/img_yt.zip
  prediction-mode: baseline          # baseline | remote
  remote-prediction-url: http://127.0.0.1:5001/predict
```

路径全部基于 `${user.dir}`（进程工作目录），所以**必须从项目根目录启动**，否则找不到数据。

### 5.2 统一响应与异常

- 所有接口返回 `{"code":0,"message":"ok","data":...}`；`code != 0` 表示失败（`ApiResponse`）。
- `GlobalExceptionHandler` 统一兜底：400 缺参 / 404 资源不存在 / 405 方法不支持 / 415 Content-Type 不支持 / 500 其他。

### 5.3 数据访问（`VideoRepository`）

所有 SQL 都在这一个类里，是后端唯一需要改 SQL 的地方。关键方法：

| 方法 | 用途 |
| --- | --- |
| `count()` / `countCategories()` | 健康检查用 |
| `findById(videoId)` | 详情：45 列 + 组装 `curve[30]`、`peakDay`、`decayRate` |
| `search(category, sort, q, page, pageSize)` | 分页浏览：分类筛选 + 排序 + 检索（≥3 字走 FTS，否则 LIKE） |
| `categoryStats()` | 洞察：分类数量与 Day1/Day30 均值 |
| `correlationAndScatter()` | 洞察：Day1-Day30 皮尔逊相关 + 3000 点确定性散点采样 |
| `decayCurves()` | 洞察/预测兜底：整体与按分类的 30 天均值曲线 |
| `searchRelevant(q, topK)` | 预测：BM25 取 Top-K 参考案例 |

派生字段计算方式（`videoRowMapper`）：

- `peakDay`：曲线上最大值的第几天（并列取靠前的）
- `decayRate`：`(day01 - day30) / day01`，`day01 <= 0` 时取 0
- `coverPath`：`/covers/{video_id前2位}/{video_id}.jpg`

### 5.4 封面服务（`CoverService`）

`GET /covers/{prefix}/{filename}` 的三级策略：

1. 本地已有 → 直接返回（`data/covers/{prefix}/{filename}`）
2. 本地没有 → 从 `data/img_yt.zip` 里按 `img_yt/{videoId}.jpg` **按需提取**并落盘，再返回
3. 还是拿不到 → 返回内置占位图 `static/img/cover-placeholder.svg`

> `prefix` 和 `filename` 都有正则白名单校验（防路径穿越），改这里务必保留校验。

### 5.5 洞察结果缓存

`InsightsController` 用 `volatile` 字段缓存 `InsightsPayload`，首次全量统计约 3~5 秒，之后毫秒级返回；
`GET /api/insights?refresh=true` 强制重算。**并发安全靠 `synchronized compute`**，不要改成无锁直接赋值丢失缓存语义。

---

## 6. 预测机制

### 6.1 双实现，配置切换

```java
PredictionService（接口）
├─ BaselinePredictionService   # 默认，app.prediction-mode=baseline
└─ RemotePredictionService     # app.prediction-mode=remote
PredictionServiceSelector      # 按配置返回当前实现
```

前端与 `/api/predict` 契约**完全不变**，切换模型只改配置。

### 6.2 基线算法（当前线上行为）

1. 文案长度 ≥ 3 → `videos_fts MATCH` 按 BM25 取 Top-K（`LIMIT` 上限 20）
2. 查询语法异常 → 捕获 `DataAccessException` 降级 LIKE
3. 文案过短（< 3 字）→ LIKE 模糊匹配（title/tags/description），按 `day30` 降序
4. 预测曲线 = Top-K 参考案例真实 30 天曲线的**逐天平均**
5. Top-K 全部查不到详情（命中为空）→ 回退**全站平均衰减曲线** `decayCurves().overall()`
6. 图片当前**仅保存到 `data/uploads/`，不参与检索与预测**（页面上已如实标注）

返回体带 `mode` 与 `note` 字段，`note` 固定为「基线预测：参考案例真实曲线平均，模型接入后替换」。

### 6.3 模型接入（未完成，但契约已定）

Python 服务契约：

```text
POST http://127.0.0.1:5001/predict
入参: {"text": "...", "imagePath": "...", "topK": 5}
出参: {"prediction": [30 个数值], "references": [{"videoId","title","coverPath","userId","day30","score"}]}
```

接入步骤：模型服务起来 → 改 `app.prediction-mode: remote` → 重启。Java 端与前端**零改动**。

---

## 7. 前端实现

### 7.1 页面与路由

5 个页面用 **hash 路由**，同在一个 `index.html` 里，靠 `.page.active` 切换：

| 路由 | 页面 | `section id` | JS 模块 |
| --- | --- | --- | --- |
| `#/` | 趋势发现（首页，默认） | `page-trend` | `trend.js` |
| `#/predict` | 上传预测 | `page-predict` | `predict.js` |
| `#/ranking` | 热度排行榜 | `page-ranking` | `ranking.js` |
| `#/insights` | 数据洞察 | `page-insights` | `insights.js` |
| `#/compare` | 曲线对比 | `page-compare` | `compare.js` |

`router.js` 监听 `hashchange` → 调 `app.js` 暴露的 `window.showPage(name)`；
`showPage` 负责切 `.active`、滚回顶部，并调用各页模块注册的 `window.onPageShown(name)` 回调。

**新增页面**：在 `ROUTES`（router.js）注册 → 加 `section` → 加 JS 文件并在 `index.html` 底部按序引入（模块顺序不能乱：`theme → effects → api → app → router → 各页模块`）。

### 7.2 脚本加载顺序（`index.html` 底部）

```text
lib/echarts.min.js → js/theme.js → js/effects.js → js/api.js → js/app.js
→ js/router.js → js/predict.js → js/ranking.js → js/insights.js → js/compare.js → js/trend.js
```

所有资源带 `?v=20260818` 版本号（**防缓存**用）。改完 JS/CSS 后建议递增这个版本号，否则浏览器可能仍用旧文件。

### 7.3 各模块职责

- **`api.js`**：`API.get/post/upload` 封装，自动解析 `{code,message,data}`，`code != 0` 或 HTTP 非 2xx 时抛 `Error`（消息可直接给 Toast）。
- **`app.js`**：导航与页面切换、`showToast`、全局工具函数（`formatNumber` / `escapeHtml` / `coverSrc` / `coverHtml` / `coverPathFor` / `days` / `miniTrend` / `skeletonCards` / `skeletonDetail`）、弹窗 `showModal/hideModal`、全局图片 error 兜底（捕获阶段监听，动态插入的 `<img>` 也生效）。
- **`theme.js`**：**仅注册浅色单主题**，并提供 `window.chartTheme()`（恒返回 `"light"`）与 `window.chartColors()`（橙主 `#ff7a3c` + 蓝辅 `#4a5fd0` + 绿 `#17a37c` 等）。**深色主题与切换按钮已彻底移除**。
- **`effects.js`**：只做一件事 —— 用 `IntersectionObserver` 给 `.reveal` 元素加 `.in` 类实现滚动渐入；命中 `prefers-reduced-motion` 时直接全部显示。
- **`predict.js` / `ranking.js` / `insights.js` / `compare.js` / `trend.js`**：各自负责一页的数据获取、渲染、ECharts 图表。

### 7.4 ECharts 约定

- 主题统一走 `echarts.registerTheme("light", ...)`（在 `theme.js`），各图表 `echarts.init(el, window.chartTheme())`。
- **实例都保存在模块闭包里，外部拿不到引用**。需要重绘时不要从外部 `dispose`，而是派发事件：

```js
window.dispatchEvent(new Event("themechange"));   // 各模块自行监听并 setOption 重绘
```

- 调试时可用 `echarts.getInstanceByDom(el)` 取实例。
- 图表容器是**固定高度**的 div（`#predict-chart` / `#insight-*` / `#compare-chart`），`resize` 由各模块自己监听。

### 7.5 首页结构（`page-trend`）

```text
.hero-split
├─ .hero-copy          大标题 + 搜索框 + 示例词 chips + 三个数字（282,481 / 15 / 30）
└─ #hero-preview.reveal  「LIVE · 今日爆款」预览卡（取 Day30 第 1 名的详情渲染）
.section-block.how-section.reveal        「三步看懂流行度」3 张卡
.section-block.capability-section.reveal 「用真实数据做判断」3 张能力卡
```

首页搜索框交互（`trend.js`）：**示例词点击只填入输入框，不触发预测**（这是用户明确要求的行为，别改回去）；点「开始预测」或按回车才跳 `#/predict` 并自动提交。

---

## 8. 接口契约

统一响应：`{"code":0,"message":"ok","data":...}`

| 接口 | 方法 | 参数 | 说明 |
| --- | --- | --- | --- |
| `/api/health` | GET | — | `{status, totalVideos, categories}` |
| `/api/videos` | GET | `page`(1) `pageSize`(20, 上限 100) `category` `sort` `q` | 分页列表 |
| `/api/videos/{id}` | GET | — | 详情 + `curve[30]` + `peakDay` + `decayRate`；不存在返回 404 |
| `/api/insights` | GET | `refresh`(false) | `{categoryStats, correlation, decayCurves}` |
| `/api/compare` | POST | body `{"videoIds":[...]}`（**必须 2~3 个**） | 各视频曲线 + 指标 |
| `/api/predict` | POST | multipart：`text`(必填) `topK`(5) `file`(可选图片) | 基线预测 + 参考案例 |
| `/covers/{prefix}/{filename}` | GET | — | 封面；缺失按需提取 / 占位图 |

`sort` 取值：`day30_desc`（默认）、`day1_desc`、`longrun_desc`、`title`。

- `day30_desc` → `ORDER BY day30 DESC, day01 DESC`
- `day1_desc` → `ORDER BY day01 DESC, day30 DESC`
- `longrun_desc`（长红榜）→ `ORDER BY (day01-day30)/day01 ASC, day30 DESC`（衰减最小者在前）
- `title` → `ORDER BY title COLLATE NOCASE ASC`

`post /api/predict` 的错误分支：`text` 为空 → 400；`file` 的 Content-Type 非 `image/*` → 400。

---

## 9. 铁律（禁改清单）

改代码前先过一遍这一节。以下都是用户明确定过的约束，**不是建议**。

1. **预测页输入区布局不可改**
   结构是 `.predict-input-row` 内的「左上传区 `#upload-zone` + 右文案区 `.predict-fields`」左右分栏，位置、宽度、样式保持现状。
   只允许调整图表 ECharts `option` 的样式层（颜色、坐标轴标签、tooltip 等）。

2. **图表数据与坐标映射不可改**
   预测 / 洞察 / 对比 / 详情曲线的原始数值、X–Y 轴映射、峰值标注的位置与内容都不许动。
   即：不要为了「看起来更好」去改数据、平滑曲线、裁剪 Y 轴范围。

3. **接口契约与算法不可改**
   `/api/*` 的返回结构、以及基线预测算法（BM25 检索 + 真实曲线平均）保持稳定。
   后端只允许**向后兼容扩展**（例如新增字段），不允许破坏性修改。

4. **数据资产不入库**
   `data/`、`target/`、`mvn-settings.xml` 一律不得提交。改完先 `git status` 自查。

5. **不做假数据**
   登录按钮、预测准确率、日均更新量、跨周涨幅等已被明确否决。
   任何新增展示必须是**真实数据可算**的；实在算不出来就不要加。

6. **不要从外部 dispose ECharts 实例**
   模块闭包持有实例，统一通过 `window.dispatchEvent(new Event("themechange"))` 触发重绘。

7. **不要引入新的重型依赖 / 构建链**
   没有 npm、没有打包器，答辩环境要能断网跑。确实需要库就把文件放进 `static/lib/`。

8. **不要改分类名里的双空格**
   见 4.2 的提示，改了会导致筛选与检索直接失效。

---

## 10. 踩坑记录（血泪史，能省你几小时）

1. **跨磁盘分卷 zip 无法用标准库读**
   原始封面是 7-Zip 跨磁盘分卷（spanning），Python / .NET 的标准 zip 库都读不了，
   最终由 `scripts/extract_spanning_zip.py` 按「分卷基准 + 相对偏移」手动解析还原出内层 `img_yt.zip`。

2. **FTS5 直接 JOIN `videos` 会触发灾难性查询计划**
   写成 `JOIN videos_fts ON ...` 单查询会卡死数十秒。
   必须用**子查询形式**：`WHERE video_id IN (SELECT video_id FROM videos_fts WHERE videos_fts MATCH ?)`
   或小派生表 JOIN（`searchRelevant` 就是后者的写法）。**改检索 SQL 时务必保持这个形状。**

3. **FTS 查询必须转义**
   `escapeFtsQuery()` 把用户输入包成 `"..."` 并去掉引号，否则用户输入 `-`、`"`、`AND` 等会让 FTS5 语法报错。
   即便这样仍要 try/catch 降级 LIKE。

4. **≥3 字才走 FTS**
   `trigram` 分词器最短匹配 3 个字符。短词（如「火锅」「美食」）走 FTS 会没结果，所以要 LIKE 兜底。
   注意中文的「字数」就是字符数，2 个汉字=2 字符 → 走 LIKE。

5. **Spring Boot 静态资源来自 `target/classes`**
   改 `src/main/resources/static/` 后，**必须**执行 `mvn -s mvn-settings.xml process-resources`（或重启应用）才会生效，
   否则浏览器看到的还是旧文件，会误以为改动无效。

6. **浏览器缓存会导致「明明改了却没变」**
   资源都带 `?v=20260818`，改完记得递增版本号；调试时用 `Ctrl+F5` 强制刷新。

7. **「整页点不动、只能输入文字」**
   曾因预加载遮罩卡在全屏（JS 未执行时遮罩不消失）导致。现在预加载已移除；若再出现类似现象，
   先确认 JS 是否报错、是否在**嵌入式预览**里查看（要在真实浏览器里验证）。

8. **滚动卡顿的根因**
   曾由 `background-attachment: fixed`、顶栏 `backdrop-filter`、极光 blur + 透明度动画、粒子常驻 rAF 造成。
   现在：去掉了 fixed / backdrop-filter，blur 降到 55px，透明度静态，滚动期间暂停重特效。**加动效前先想清楚性能代价。**

9. **洞察页直接打开 / 刷新会空白**
   `#/insights` 直开时，`insights.js` 的 `onPageShown` 注册晚于 router 首次调用 `showRoute`，导致不加载数据。
   修法是注册后**自己再触发一次**。新增页面模块请照抄这个模式。

10. **截图会把滚动渐入的区块拍成空白**（2026-09-14 新发现）
    首页 `home.png` 曾用整页截图生成，但截图过程没有真正滚动，`.reveal` 元素停留在 `opacity:0`，
    于是「三步看懂流行度」「用真实数据做判断」两个区块在图上**完全消失**，中间一大片空白。
    **正确做法**：用 Playwright 先逐屏滚到底（触发 IntersectionObserver），再回到顶部截 `fullPage`。
    页面截图生成脚本参考本仓库之外的 `pw-smoke` 工具链（playwright-core + 系统 Edge），见第 11 节。

11. **控制台中文乱码**
    Windows 控制台是 GBK，看 UTF-8 文件会乱码，**文件本身没问题**。需要时 `chcp 65001`。

12. **PowerShell 细节**
    `$pid` 是保留变量不能赋值；递归删除前先清只读属性并校验目标路径在预期目录内。

---

## 11. 本机环境特殊说明

- **Maven 本地仓库**：本机全局 `settings.xml` 强制指向不可写目录，`-Dmaven.repo.local` 无效，
  所以项目内提供了 `mvn-settings.xml`（已 gitignore），必须用 `mvn -s mvn-settings.xml ...`。
  如果接手方环境正常，可以去掉 `-s`。
- **Java**：JDK 17。`mvn -version` / `java -version` 先确认。
- **Python**：本机为 Miniconda（`E:\Miniconda\python.exe`）。脚本只需要标准库，无第三方依赖。
- **浏览器自动化验证**：本机曾用 `playwright-core` + 系统 Edge（headless）做回归与截图，
  工具链目录 `C:\Users\鹅\.codex\visualizations\2026\08\05\...\pw-smoke`（不在本仓库内）。
  headless 无 GPU，**性能数据仅供参考**；无限动画元素（如滚动箭头）点击会被判「不稳定」，需 `force: true`。
- **沙箱**：`.git` 可能是只读的，git 写操作（commit / push）需要提权。

---

## 12. 当前状态与待办

### 已完成

- 数据层：SQLite 索引（282,481 行 / 15 分类 / FTS5 trigram）、封面按需提取、跨磁盘分卷还原
- 后端：6 个接口全部返回真实数据，统一响应与全局异常处理完备
- 预测：基线实现（BM25 + 参考案例曲线平均）+ 远程模型双实现可切换
- 前端：5 个页面（hash 路由、独立 URL、刷新保持、前进后退、分享）
- 视觉：**浅色单主题**（白底 + 橙主蓝辅 + 圆润字体），轻量动效（滚动渐入 + 卡片错峰入场）
- 文档：README、交接文档、首页规范、改动总结和 5 张界面截图
- 测试：24 个用例全部通过

### 已知未完成 / 待定

1. **模型未接入**：`app.prediction-mode` 仍是 `baseline`，LSTM 预测服务与扩散模型（`flax/stable-diffusion-2-1`）样例都还没到位。
2. **图片不参与预测**：需要模型支持后启用（当前页面已如实说明）。
3. **`POST /api/generate` 只预留未实现**（生成增强契约，暂不交互化）。
5. **暂缓/未批准的功能**（用户当前只要视觉，别自作主张加）：搜索联想下拉、热门词滚动 ticker。

### 文档层面已知的小瑕疵（顺手可修）

- `README.md` 的数据说明里写 `basic_view_pn.csv` 是「35 列」，**实际是 45 列**（15 基础列 + Day 1~30），建议改正。
- `README.md` 写 `img_yt.zip` 9.25GB / `smtpd.db` 1.89GB，实际分别为 9.93GB / 2.03GB（数据增长导致，非必须修）。
- ~~`docs/附件3：大学生创新训练项目申报书-陈梓华.doc`（6.3MB）未被忽略也未入库~~ → 已于 2026-09-14 由用户删除，此项已解决。

---

## 13. 回归验收清单

每次改动后建议按这个顺序走一遍（约 5 分钟）：

1. `git status` 确认没有 `data/`、`target/`、`mvn-settings.xml`、大 `.doc` 被误加
2. `mvn -s mvn-settings.xml test` → 24 个用例应全绿
3. 启动后 `curl http://localhost:8080/api/health` → `totalVideos=282481`、`categories=15`
4. 前端改动过的话先 `mvn -s mvn-settings.xml process-resources`，再 `Ctrl+F5`
5. 浏览器逐页走查：
   - `#/` 首页：Hero + 真实案例卡 + 四章节研究路径
   - `#/predict`：输入「三毛水果艺术」→ 出参考案例 + 曲线 + KPI（峰值天 / 预测 Day30 / 参考案例数）
   - `#/ranking`：切「榜单范围 + 榜单口径」→ 搜索「火锅」（约 167 条）→ 点卡片开详情弹窗 → Esc 关闭
   - `#/insights`：直开/刷新都能加载（r = 0.9296），三张图正常
   - `#/compare`：点「示例：热门 vs 冷门」→ 叠加曲线 + 指标表（含 30 点迷你趋势线）
6. 断网检查：拔网线刷新，页面样式与图表仍应正常（唯一外部链接是详情页「查看原视频」）

**备用演示文案**（确定有结果）：`三毛水果艺术`（FTS）、`火锅`（LIKE，约 167 条）、`cooking`（FTS 英文）、`美食`（LIKE）

---

## 14. 交接注意事项

### Git

- 分支 `main`，远程 `origin`；工作区应保持干净，提交信息用中文描述性短句（沿用现有风格）。
- **提交前必查**：`git status` + `git check-ignore data/ target/ mvn-settings.xml`
- `.gitignore` 当前忽略：`/data/`、`/target/`、`/logs/`、`*.log`、`.m2/`、`.idea/`、`*.iml`、`.vscode/`、`.claude/`、`mvn-settings.xml`
- 当前文档资产包括：`README.md`、`docs/trae-handoff.md`、首页规范、改动总结、界面截图与源码

### 文档策略

当前只保留与运行、维护、首页规范和改动追踪直接相关的文档；历史方案、重复提示词和旧版原型已清理。

### 沟通约定

- 拿不准的视觉/交互改动**先做小样给用户确认**，不要一次性大改版（历史上多次「改回上一版」）。
- 用户的验收方式是**真实浏览器 + 真实数据**，所以要主动跑起来自测，而不是只改代码。
