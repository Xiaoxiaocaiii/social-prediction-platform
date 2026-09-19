# 项目页面持续迭代交接文档

> 生成日期：2026-09-18  
> 项目：多模态社交媒体流行度预测与可视化分析平台  
> 交接用途：开启新对话，持续迭代首页及全部子页面

## 1. 交接结论

当前项目分为两条并行工作线：

1. **正式平台**：`src/main/resources/static/` 下的原生 HTML/CSS/JS 五页面，已经具备真实数据展示和接口联动能力。
2. **首页独立原型**：`docs/home-preview-ac.html`，用于先讨论和验证首页视觉方案。当前原型已落地“融合方案二：编辑部数据海报”。

后续默认规则：先在原型或小范围预览中确认视觉方向，再迁移到正式平台。除非用户明确要求，不得把原型样式直接覆盖正式页面。

## 2. 当前开发进度

### 已完成

- Java 17 + Spring Boot 3.2.5 + SQLite 本地运行架构。
- SMTPD 真实数据索引：282,481 条视频、15 个分类、Day1-Day30 热度序列。
- SQLite FTS5 trigram + BM25 检索，短词 LIKE 兜底。
- 基线预测：相似案例真实 30 天曲线逐日平均。
- 封面本地读取、按需提取和占位图兜底。
- 五个正式页面：
  - `#/` 趋势发现首页
  - `#/predict` 上传预测
  - `#/ranking` 热度排行榜
  - `#/insights` 数据洞察
  - `#/compare` 曲线对比
- 本地 ECharts 主题、轻量滚动渐入、Hash 路由、键盘焦点状态。
- 首页独立原型的导航、Hero、章节叙事和案例卡，统一按桌面固定布局维护。
- 首页原型当前视觉：纸张版面、底部栏目编号、竖排期刊信息、案例卡侧边标记，方向为“编辑部数据海报”。

### 当前工作区状态

当前已有用户未提交的正式页面修改：

- `src/main/resources/static/css/style.css`
- `src/main/resources/static/index.html`
- `src/main/resources/static/js/theme.js`

当前保留的首页原型和规范资产包括：

- `docs/home-preview-ac.html`
- `docs/home-nav-improvement.md`
- `docs/home-content-writing-plan.md`
- `docs/project-change-summary.md`

不要使用 `git reset --hard`、`git checkout --` 或其他破坏性命令清除这些改动。

## 3. 五个页面功能说明

### 首页 `#/`

- 展示平台定位、真实数据规模和研究入口。
- 搜索选题后进入预测页。
- 展示真实案例预览。
- 包含“数据基础 → 检索增强 → 时序预测 → 真实案例”的叙事结构。

### 上传预测 `#/predict`

- 输入文案，可选上传图片。
- 使用 BM25/FTS5 或 LIKE 检索相似历史视频。
- 以参考案例真实曲线平均生成 30 天预测。
- 展示预测曲线、峰值天、预测 Day30、参考案例数和案例列表。
- 当前图片只保存，不参与预测。

### 热度排行榜 `#/ranking`

- 浏览真实视频数据。
- 支持全站/分类范围和 Day30 总榜、Day1 新热榜、长红榜。
- 支持标题、标签、描述检索、分页和详情弹窗。

### 数据洞察 `#/insights`

- 分类平均热度对比。
- Day1 与 Day30 相关性散点。
- 整体或分类热度衰减曲线。
- 展示 KPI 和分析结论。

### 曲线对比 `#/compare`

- 选择 2~3 个视频。
- 叠加真实 30 天热度曲线。
- 对比峰值天、Day30 和衰减率。
- 表格展示迷你趋势线。

## 4. 页面架构

正式平台使用单页结构：

```text
src/main/resources/static/index.html
├─ 顶部导航
├─ #page-trend
├─ #page-predict
├─ #page-ranking
├─ #page-insights
├─ #page-compare
└─ footer
```

Hash 路由由 `js/router.js` 管理，页面切换和全局交互由 `js/app.js` 管理。

脚本职责：

| 文件 | 职责 |
| --- | --- |
| `api.js` | 统一封装 API 请求和响应错误 |
| `app.js` | 页面切换、Toast、弹窗、工具函数、图片兜底 |
| `router.js` | Hash 路由和前进后退 |
| `theme.js` | ECharts 浅色主题和颜色 Token |
| `effects.js` | IntersectionObserver 滚动渐入 |
| `trend.js` | 首页真实案例、搜索和首页交互 |
| `predict.js` | 上传预测和预测图表 |
| `ranking.js` | 排行榜筛选、分页、详情 |
| `insights.js` | 洞察图表、KPI 和结论 |
| `compare.js` | 视频选择、曲线叠加和对比表 |

首页原型是独立 HTML，不连接正式接口：

```text
docs/home-preview-ac.html
```

其 `#/predict`、`#/ranking`、`#/insights`、`#/compare` 仅表示目标路由。

## 5. 核心技术栈

- 后端：Java 17、Spring Boot 3.2.5、Maven、Spring JDBC。
- 数据库：SQLite，xerial sqlite-jdbc 3.47.1.0。
- 检索：SQLite FTS5 `trigram`、BM25、LIKE 兜底。
- 前端：原生 HTML5、CSS3、JavaScript。
- 图表：本地 ECharts 5.5.1。
- 数据处理：Python 标准库脚本。
- 测试：JUnit 5、MockMvc，历史记录为 24 个用例。
- 浏览器支持范围：桌面端 Microsoft Edge 最新 3 个正式版本及 Edge 企业版。

### 浏览器与需求边界

- 验收分辨率为 `1920×1080`、`2560×1440`、`3840×2160`。
- 永久禁止新增移动端、手机、平板、触摸设备或响应式断点适配需求。
- 新需求必须在评审时确认“不涉及移动端适配”，并通过 `scripts/check-desktop-only.ps1` 校验。

## 6. 资源路径索引

### 核心文档

- `README.md`：项目概览、运行方式、接口和数据说明。
- `docs/trae-handoff.md`：后端、数据、接口、禁改清单和历史踩坑。
- `docs/home-nav-improvement.md`：首页导航和章节结构约束。
- `docs/home-preview-ac.html`：当前首页核心原型。

### 正式前端

- `src/main/resources/static/index.html`
- `src/main/resources/static/css/style.css`
- `src/main/resources/static/js/*.js`
- `src/main/resources/static/lib/echarts.min.js`
- `src/main/resources/static/img/cover-placeholder.svg`
- `src/main/resources/static/img/favicon.svg`

### 后端与数据

- `src/main/java/com/prediction/api/`
- `src/main/java/com/prediction/data/`
- `src/main/java/com/prediction/service/`
- `src/main/resources/application.yml`
- `data/smtpd.db`、`data/basic_view_pn.csv`、`data/covers/`、`data/uploads/`：本地数据资产，默认不入库。

## 7. 待优化需求

### 首页

- 继续校准“编辑部数据海报”背景的纸张层次、栏目编号和侧边标记。
- 保持标题、搜索区、统计区和案例卡的自然高度关系。
- 趋势曲线必须可见、细致、符合真实时序图，不使用粗圆波浪。
- 桌面端验证 `1920×1080`、`2560×1440`、`3840×2160`，不得出现布局错乱。

### 全部页面

- 在不破坏现有接口和图表数据映射的前提下，统一字体、间距和卡片层级，保持桌面固定布局。
- 优先改善信息层级、可读性和空状态/错误状态。
- 每个页面改动前先输出方案或局部预览，确认后再迁移。
- 不要把首页装饰背景未经确认直接复制到所有子页面。

### 后续产品能力

- 远程模型尚未接入，当前保持 baseline。
- 图片暂不参与预测。
- `POST /api/generate` 仅预留。
- 软著材料仍需整理。

## 8. 风险点与注意事项

1. **真实数据红线**：禁止 mock 数据、虚构指标和假案例。
2. **正式页面与原型隔离**：除非用户明确要求，不得修改正式页面。
3. **接口兼容**：只能向后兼容新增字段，不改变已有结构和含义。
4. **预测页布局禁改**：上传区与文案区左右分栏结构不能擅自重排。
5. **图表数据禁改**：不能为了视觉效果修改曲线数值、轴映射或峰值位置。
6. **分类双空格必须保留**：例如 `People  Blogs`，不能规范化。
7. **资源必须本地化**：不得引入 CDN、外部字体或重型构建链。
8. **静态缓存**：正式资源修改后要递增 `?v=` 版本号，并执行 `mvn -s mvn-settings.xml process-resources` 或重启服务。
9. **工作区已有修改**：新对话必须先查看 `git status` 和 `git diff`，不得覆盖用户未提交内容。
10. **数据文件不入库**：不要提交 `data/`、`target/`、`mvn-settings.xml`。
11. **无障碍**：正文对比度至少 4.5:1，大标题至少 3:1，装饰元素不能承担信息。
12. **性能**：避免 `background-attachment: fixed`、常驻高频动画、过重 blur 和大图片背景。

## 9. 建议的后续工作流

1. 准备：阅读本文件、`README.md`、`docs/trae-handoff.md`，检查 Git 状态。
2. 现状确认：启动服务，用 Edge 检查五个路由和首页原型。
3. 方案沟通：一次只选择一个页面或一个明确视觉模块。
4. 实现：只改目标文件，立即进行局部浏览器验证。
5. 验收：检查桌面 Edge、键盘焦点、控制台、布局稳定性和真实接口。
6. 收尾：更新交接文档和变更摘要，等待用户决定是否提交。

## 10. 缓存清理记录

- 已检查 `C:\Users\鹅\AppData\Local\Temp\trae`。
- 当前没有可识别的 `home-preview*` 临时预览文件，因此未执行删除。
- 已保留 `docs/home-preview-ac.html`、当前导航与内容规范，以及项目改动总结。
