# Codex 任务交接记录

> 生成日期：2026-08-18 ｜ 分支：main ｜ 用途：暂停当前任务时的交接备忘录（内部使用，未推送 GitHub）

## 1. 原始需求目标

**项目**：多模态社交媒体流行度预测与可视化分析平台（大创答辩 + 软著申报）

**数据**：SMTPD 数据集，282,481 条真实 YouTube 视频（封面图 + 文本 + 30 天热度序列）

**核心原则（用户多次强调，红线）**：
- 全部真实数据，禁止任何 mock/假数据
- 检索与预测为「基线实现」（BM25 + 参考案例真实曲线平均），模型可后接入
- 平台从旧 Java mock 项目完整重构为真实数据平台，并逐步产品化、美化

## 2. 已完成的全部功能与修改文件

### 页面（5 个，hash 路由）
- 趋势发现首页（默认落地页，全屏 Hero + 第二屏选题流）
- 上传预测、热度排行榜、数据洞察、曲线对比（4 个功能页，顶部导航）

### 后端修改
- `src/main/java/com/prediction/data/VideoSummary.java`：增加 `day1` 字段
- `src/main/java/com/prediction/data/VideoRepository.java`：普通列表与 FTS 查询增加 `day01` 列、summaryMapper 映射
- 其余后端（controller/service/config）未改动

### 前端修改（`src/main/resources/static/`）
- `index.html`：5 页面结构、导航改链接、主题切换按钮、预加载/滚动进度/粒子/极光 DOM、KPI/结论/序号/热点卡等结构
- `css/style.css`：全站样式（深色霓虹 A–E、浅色/深色双主题 F、趋势产品化 G、首页重构、滚动性能修复）
- `js/router.js`（新增）：hash 路由 `#/`、`#/predict`、`#/ranking`、`#/insights`、`#/compare`
- `js/theme.js`：注册 ECharts `neon`/`dawn` 双主题 + `chartTheme()`/`chartColors()` 工具
- `js/effects.js`：动效引擎（预加载、粒子、极光滚动暂停、滚动进度、磁吸、倾斜、揭示、数字滚动、顶栏收缩、主题切换）
- `js/predict.js`：主题感知、结果 KPI 行、全站平均对照虚线、入场动画
- `js/insights.js`：主题感知、洞察 KPI 行、结论标题、入场动画
- `js/compare.js`：主题感知、对比表 30 点迷你趋势线、入场动画
- `js/ranking.js`：主题感知、KPI 统计、排名序号、涨跌角标、入场动画
- `js/trend.js`（新增）：首页搜索联动、热点 Top5 渲染、示例词点击只填入、向下箭头滚动
- `js/api.js`、`lib/echarts.min.js`：未改动

### 文档与资产
- `README.md`：多次重写（含截图、双主题、路由说明）
- `docs/DEMO.md`、`docs/成果清单.md`：重写
- `docs/PLAN.md`：历史方案文档（未改）
- `docs/screenshots/`：10 张深浅主题截图
- `LICENSE`：新增 MIT
- `docs/项目重构报告.md`：**已删除**（本地 + GitHub 同步移除）

### 环境/数据（不入库）
- `data/`（原始 CSV / smtpd.db / img_yt.zip / covers）、`target/`、`mvn-settings.xml` 均在 .gitignore

## 3. Git 当前状态

- 分支：`main`；远程：`origin = https://github.com/Xiaoxiaocaiii/social-prediction-platform.git`
- 工作区：干净（无未提交改动）
- 最近提交：`8170b4a`（docs: 删除项目重构报告…），已推送
- 本交接文档 `codex-handoff.md` 创建后**未提交、未推送**（内部使用，如需保留请自行决定是否提交）
- 沙箱对 `.git` 只读，所有 git 写操作需提权执行

## 4. 遇到的问题与踩过的坑

1. **Spring Boot 静态资源来自 `target/classes`**：改 `src/main/resources/static` 后必须 `mvn -s mvn-settings.xml process-resources`（或重启）才生效。
2. **控制台中文乱码**：Windows 控制台 GBK 显示 UTF-8 文件会乱码，文件本身正常；`apply_patch` 匹配中文需逐字精确（注意空格/全角符号）。
3. **浏览器缓存/环境**：页面「整页点不动、只能输入文字」曾出现，根因是 JS 未执行时预加载遮罩卡在全屏（或在内嵌预览里查看）；务必用真实浏览器 + `Ctrl+F5` 验证。
4. **滚动卡顿**：曾由 `background-attachment: fixed`、顶栏 `backdrop-filter`、极光 blur+透明度动画、粒子常驻 rAF 导致；已修复（去 fixed/backdrop-filter、blur 降为 55px、透明度静态、滚动期间暂停重特效）。
5. **示例词交互**：曾「点击即触发预测」，用户要求改为只填入搜索框，已修复。
6. **Playwright 验证**：工具链在 `C:\Users\鹅\.codex\visualizations\2026\08\05\019fcfc5-98ce-79e1-8d55-e55f283543f8\pw-smoke`（playwright-core + 系统 Edge）；headless 无 GPU，滚动性能数据仅参考；无限动画元素（如向下箭头）会导致点击被判「不稳定」，需 `force: true`。
7. **uv / browser-act 安装失败**：Miniconda Python SSL 证书问题 → skill 安装改用 `--method git`；uv 安装 browser-act-cli 卡死占锁，最终放弃并清理（uv 缓存 10.2GB 已删、技能目录已删）。
8. **ECharts 实例在模块闭包内**：外部无法直接 dispose；主题切换通过 `window` 派发 `themechange` 事件、各模块自行重绘，验证用 `echarts.getInstanceByDom(el)`。
9. **PowerShell 细节**：`$pid` 是保留变量；递归删除前需先清只读属性并校验目标路径在预期目录内。

## 5. 禁止修改的模块（铁律）

1. **上传预测页布局**：左侧上传表单区与右侧图表容器的左右分栏结构、位置、宽度、样式不可改；只允许调整图表 ECharts option 的样式层。
2. **图表数据与坐标映射**：预测/洞察/对比/详情曲线的原始数值、X/Y 轴映射、峰值标注位置与内容不可改。
3. **接口契约与算法**：`/api/*` 返回结构与基线预测算法（BM25 检索 + 真实曲线平均）不可改；后端只允许向后兼容扩展（如新增字段）。
4. **数据资产**：`data/`、`target/`、`mvn-settings.xml` 不得入库。
5. **不做假数据**：登录按钮、虚构指标（预测准确率/日均更新量）、跨周涨幅等已明确否决，新增展示必须真实数据可算。
6. **图表实例管理**：不要从外部直接 dispose 模块闭包持有的 ECharts 实例，统一走 `themechange` 事件。

## 6. 下一步要做的顺序（待用户逐批审批）

**已确认方向、未开始：视觉美化（纯视觉效果，4 批）**

1. 批 1：封面 hover 视觉层（放大+渐变遮罩+趋势图标）+ 卡片热度相对条（Day30/榜单最大值）+ KPI 卡迷你趋势线
2. 批 2：洞察结论 callout 高亮卡 + 区块超大序号（01/02/03 编辑感）+ 极淡噪点纹理
3. 批 3：主按钮扫光（shine）+ 骨架屏 shimmer（替换「加载中…」）+ Hero 微视差
4. 批 4：双主题进一步分化（浅色纸感/深色屏感）+ 全站回归 + 截图与文档更新 + 推送 GitHub

每批完成后必须：双主题截图验证 + `git` 提交 + 向用户汇报，用户批准后才进入下一批。

**暂缓/未批准（功能向，当前用户只要视觉）**：搜索联想下拉、热门词滚动 ticker。

**日常工作流备忘**：
- 启动：`start.bat`（后台）或 `mvn -s mvn-settings.xml spring-boot:run`，访问 http://localhost:8080
- 前端改动后：`mvn -s mvn-settings.xml process-resources -q` 同步
- 提交需提权；推送前确认无大文件入库（`git status` + `git check-ignore data/`）
