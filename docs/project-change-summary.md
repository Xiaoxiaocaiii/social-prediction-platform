# 项目改动总结

## 1. 文档概述

本文档汇总截至当前工作区状态已经实际完成的项目改动，覆盖正式生产首页、首页独立原型、桌面端开发边界、前端主题、交互逻辑、工程配置和项目文档。

项目后端、SQLite 数据、预测算法和 API 返回结构未被修改。本轮改动主要集中在原生 HTML/CSS/JavaScript 前端及配套规范文件，正式首页已经接入原型确认后的首页结构和真实接口数据。

## 2. 改动时间线

### 2026-09-18：桌面端边界与工程约束落地

- 删除正式首页中的 `viewport` 配置。
- 删除原有尺寸响应式媒体查询、移动端布局分支和触摸设备适配逻辑。
- 将页面基线收敛为桌面端 Microsoft Edge 固定布局。
- 新增桌面端源码扫描脚本、ESLint 限制、Prettier 配置、GitHub Actions 校验和需求模板勾选项。
- 更新 `README.md` 与 `docs/trae-handoff.md`，明确桌面 Edge 支持范围、验收分辨率和移动端需求禁入规则。

### 2026-09-18：首页独立原型完成内容与视觉迭代

- 在 `docs/home-preview-ac.html` 中完成 Hero、数据基础、检索增强、时序预测和真实案例四章节结构。
- 统一章节标题、正文、眉标、数据标签和注释的字体层级。
- 将第二章改为三步横向流程卡，第三章改为三列方法卡，第四章改为案例卡与验证卡并列。
- 完善 FTS5、BM25、LIKE 兜底、Day1-Day30、峰值天、Day30 和衰减率等专业内容的通俗说明。
- 完成桌面导航、研究路径、章节锚点和真实案例卡的原型交互。
- 形成“编辑部数据海报”视觉方向，加入蓝灰纸张、米色纸张、错位阴影、期刊标记和数据曲线元素。

### 2026-09-18：正式首页迁移

- 将独立原型的首页 Hero、研究路径、章节导航、四章节内容和案例证据卡迁移到正式 `#/` 页面。
- 保留正式平台已有的 Hash 路由和业务页面，不改变 `/predict`、`/ranking`、`/insights`、`/compare` 的业务逻辑。
- 首页搜索接入现有预测流程，提交后进入 `#/predict` 并填入关键词。
- 首页案例预览接入现有 `/api/videos` 和 `/api/videos/{id}` 接口。
- 更新 ECharts 主题与静态资源版本号，避免缓存旧页面资源。

### 2026-09-18 至当前：首屏比例和背景视觉调整

- 保留正式页面原有导航比例和布局。
- 将 Hero 设为导航下方的独立首屏区域，并使用动态导航高度参与章节定位。
- Hero 底色改为与页面其他区域一致的细密条纹格子，同时保留纸张、案例卡和阴影设计。
- 缩短 Hero 底部蓝色和米色纸张装饰高度，减少底部空白并露出条纹背景。
- 资源版本更新至 `home-grid-2`。

## 3. 各模块变更详情

### 3.1 正式首页结构与布局

**修改位置**

- `src/main/resources/static/index.html`
- `src/main/resources/static/css/style.css`

**改动内容**

- 正式首页 `#page-trend` 从旧的 Hero、三步说明和能力卡布局，改为 Hero 加四章节研究叙事布局。
- 新增 `#data`、`#retrieval`、`#temporal`、`#case` 四个章节。
- 新增章节导航、研究路径入口、数据统计模块、检索流程卡、时序方法卡、真实案例卡和正式页面验证入口。
- Hero 使用左右分栏：左侧为研究主题、搜索入口和数据规模，右侧为真实案例预览。
- 桌面主内容采用固定宽度基线和最小页面宽度，避免桌面 Edge 下因窗口过窄产生结构错位。

**实现功能或修复问题**

- 首页内容与已确认的独立原型结构一致。
- 四个章节可以通过章节导航和研究路径入口定位。
- 页面底部多余纸张被缩短，减少首屏下方空白。

### 3.2 首页导航与章节跳转

**修改位置**

- `src/main/resources/static/index.html`
- `src/main/resources/static/js/trend.js`
- `src/main/resources/static/js/app.js`

**改动内容**

- 保留正式页面导航栏的品牌、四个业务入口及尺寸比例。
- 研究路径放置在 Hero 左下区域，箭头位于文字左侧。
- 章节链接和研究路径使用原生事件监听及平滑滚动。
- 章节定位偏移量动态读取 `.topbar` 的实际高度并增加安全间距。
- 点击正式验证入口时，继续使用 Hash 路由进入排行榜、数据洞察和曲线对比页面。

**实现功能或修复问题**

- 解决原型导航与正式页面导航风格不一致的问题。
- 解决固定导航遮挡章节标题的问题。
- 保持跨页面 Hash 路由可用。

### 3.3 真实案例预览与 API 联动

**修改位置**

- `src/main/resources/static/js/trend.js`
- `src/main/resources/static/index.html`

**改动内容**

- 首页加载 `/api/videos?sort=day30_desc&page=1&pageSize=1` 获取 Day30 榜首候选。
- 再调用 `/api/videos/{videoId}` 获取详情和完整 30 天曲线。
- 案例卡展示标题、分类、Day30、Peak Day、Decay Rate、封面和趋势曲线。
- 保留无数据、详情加载失败和接口失败时的占位反馈。

**实现功能或修复问题**

- 首页案例由静态演示卡改为真实接口数据。
- 预测入口继续沿用正式预测页已有的文案提交流程。
- 未新增 mock 数据，也未改变后端接口结构。

### 3.4 首页四章节内容

**修改位置**

- `src/main/resources/static/index.html`
- `docs/home-content-writing-plan.md`

**改动内容**

- 数据基础章节说明文本、封面和 Day1-Day30 序列的作用。
- 检索增强章节说明 FTS5、BM25 和 LIKE 兜底的真实处理链路。
- 时序预测章节说明逐日平均、峰值天、Day30 和衰减率。
- 真实案例章节说明从排行榜、数据洞察和曲线对比回到证据源的验证路径。
- 各章节使用 `BASIS`、`ROLE`、`OUTPUT` 标签区分依据、作用和输出。

**实现功能或修复问题**

- 将专业术语转换为可读的研究叙事。
- 明确当前基线预测不是训练完成的模型，也不承诺未来走势。
- 避免把相关性、平均曲线或案例指标表述成未经验证的准确率和保证。

### 3.5 纸张海报与条纹背景视觉

**修改位置**

- `src/main/resources/static/css/style.css`

**改动内容**

- 新增蓝灰和米色错位纸张、阴影、期刊式眉标、细数据曲线和案例证据卡样式。
- Hero 底层使用径向渐变与 `32px × 32px` 细密网格。
- 纸张装饰高度调整为蓝色 `500px`、米色 `475px`。
- 保留案例卡圆角、白色内容面板和三项指标布局。

**实现功能或修复问题**

- 条纹格子与正式页面整体背景统一。
- 纸张仍承担视觉主体，不因更换底色而丢失原有设计。
- 裁掉 Hero 底部过长纸张，改善留白比例。

### 3.6 全站颜色与图表主题

**修改位置**

- `src/main/resources/static/css/style.css`
- `src/main/resources/static/js/theme.js`

**改动内容**

- 建立学术研究风格颜色 Token：墨蓝、砖橙、浅灰、米白和绿色。
- 统一页面背景、边框、文本、阴影、圆角和字体变量。
- 更新 ECharts 轴线、分割线、标签、标题、图例、提示框和系列颜色。
- 将图表字体统一为本地中文字体和数据字体组合。

**实现功能或修复问题**

- 正式页面与首页视觉主题一致。
- 图表在浅色背景上的对比度和层级更加统一。
- 未引入外部字体、CDN 或新构建链。

### 3.7 桌面端工程约束与自动校验

**修改位置**

- `README.md`
- `docs/trae-handoff.md`
- `.eslintrc.json`
- `.prettierrc.json`
- `.prettierignore`
- `scripts/check-desktop-only.ps1`
- `.github/workflows/desktop-only-check.yml`
- `.github/ISSUE_TEMPLATE/feature_request.md`

**改动内容**

- 明确仅支持桌面 Microsoft Edge 最新三个正式版本及企业版。
- 固定验收分辨率为 `1920×1080`、`2560×1440`、`3840×2160`。
- 扫描尺寸媒体查询、viewport、触摸事件、设备尺寸分支和移动端关键词。
- ESLint 禁止移动端相关字符串和非无障碍 `matchMedia`。
- GitHub Actions 自动执行桌面策略检查和 `git diff --check`。
- 需求模板增加“不涉及移动端适配”的必填确认项。

**实现功能或修复问题**

- 清除已有移动端适配入口。
- 建立提交前和 CI 阶段的桌面端边界校验。
- 将开发规范、需求入口和自动检查统一到同一约束。

### 3.8 项目交接与原型文档

**新增或更新位置**

- `docs/project-page-iteration-handoff.md`
- `docs/home-nav-improvement.md`
- `docs/home-content-writing-plan.md`
- `docs/home-preview.html`
- `docs/home-preview-v3.html`
- `docs/home-preview-ac.html`

**改动内容**

- 记录正式五页面、数据规模、接口约定和前端模块边界。
- 记录首页原型视觉方向、导航规范、章节写作规范和桌面验收边界。
- 保存首页原型的多个视觉迭代版本。

**实现功能或修复问题**

- 为后续维护提供统一的页面、数据和设计基线。
- 明确原型与正式首页的边界，并记录已迁移到正式首页的内容。

## 4. 改动影响范围评估

### 4.1 直接影响范围

- 正式首页 `#/` 的 HTML 结构、CSS 视觉系统和首页交互逻辑。
- 全站图表主题颜色、字体和背景层级。
- 前端静态资源缓存版本。
- 项目开发规范、CI 校验和需求模板。
- 首页原型及其配套说明文档。

### 4.2 保持不变的范围

- Java Spring Boot 后端代码。
- SQLite 数据库和 SMTPD 原始数据。
- 预测算法、检索接口和 API 返回结构。
- 上传预测、热度排行榜、数据洞察、曲线对比页面的既有业务模块。
- 本地 ECharts 文件和前端构建方式。

### 4.3 已完成验证

- `mvn -s mvn-settings.xml process-resources` 执行成功。
- `mvn -s mvn-settings.xml test` 已完成，24 个测试用例通过。
- `git diff --check` 已执行。
- `scripts/check-desktop-only.ps1` 已执行并通过，扫描 15 个源文件。
- 浏览器已验证首页结构、真实案例接口展示、研究路径章节定位和正式页面 Hash 路由。

## 5. 文件变更索引

| 分类 | 文件 |
| --- | --- |
| 正式首页 | `src/main/resources/static/index.html` |
| 全站样式 | `src/main/resources/static/css/style.css` |
| 首页交互 | `src/main/resources/static/js/trend.js` |
| 页面状态 | `src/main/resources/static/js/app.js` |
| 图表主题 | `src/main/resources/static/js/theme.js` |
| 桌面规范 | `README.md`、`docs/trae-handoff.md` |
| 自动校验 | `scripts/check-desktop-only.ps1`、`.github/workflows/desktop-only-check.yml` |
| 开发配置 | `.eslintrc.json`、`.prettierrc.json`、`.prettierignore` |
| 需求模板 | `.github/ISSUE_TEMPLATE/feature_request.md` |
| 首页原型与规范 | `docs/home-preview-ac.html`、`docs/home-nav-improvement.md`、`docs/home-content-writing-plan.md` |
| 交接文档 | `docs/project-page-iteration-handoff.md`、`docs/home-prototype-handoff-prompt.md`、`docs/new-conversation-handoff-prompt.md` |
