# 新会话项目接手提示词

请接手并继续开发当前项目：**多模态社交媒体流行度预测与可视化分析平台**。

这是一个用于大创答辩、软件著作权申报和真实数据演示的本地平台。当前准备进入较大规模的页面和功能调整，但在开始修改之前，必须先判断哪些内容值得保留、哪些内容应该重构、哪些内容可以删除。不要因为用户说“可能全部推翻”就直接删除现有页面、接口或数据逻辑。

---

## 一、项目基本信息

### 技术栈

- Java 17
- Spring Boot 3.2.5
- Maven
- Spring JDBC
- SQLite
- 原生 HTML / CSS / JavaScript
- 本地 ECharts 5.5.1
- JUnit 5 + MockMvc

### 数据基础

- 数据集：SMTPD
- 真实视频：`282,481` 条
- 内容分类：`15` 个
- 每条视频包含 Day1 到 Day30 热度序列
- 数据包括视频文本、封面路径、分类、热度指标和完整曲线
- 数据库和封面位于本地 `data/`，不在 Git 中

### 运行方式

```powershell
mvn -s mvn-settings.xml spring-boot:run
```

浏览器访问：

```text
http://localhost:8080
```

运行测试：

```powershell
mvn -s mvn-settings.xml test
```

前端资源修改后同步：

```powershell
mvn -s mvn-settings.xml process-resources
```

---

## 二、当前页面和业务结构

正式平台只有一个静态入口：

```text
src/main/resources/static/index.html
```

通过 Hash 路由切换五个页面：

| 路由 | 页面 | 主要脚本 |
| --- | --- | --- |
| `#/` | 趋势发现首页 | `js/trend.js` |
| `#/predict` | 上传预测 | `js/predict.js` |
| `#/ranking` | 热度排行榜 | `js/ranking.js` |
| `#/insights` | 数据洞察 | `js/insights.js` |
| `#/compare` | 曲线对比 | `js/compare.js` |

核心前端文件：

- `src/main/resources/static/index.html`：五个页面的 DOM、导航和脚本加载顺序
- `src/main/resources/static/css/style.css`：全站样式和首页视觉系统
- `src/main/resources/static/js/app.js`：页面切换、Toast、全局工具函数
- `src/main/resources/static/js/router.js`：Hash 路由
- `src/main/resources/static/js/api.js`：接口封装
- `src/main/resources/static/js/theme.js`：ECharts 主题
- `src/main/resources/static/js/trend.js`：首页搜索、真实案例预览、章节定位
- `src/main/resources/static/js/predict.js`：上传预测流程
- `src/main/resources/static/js/ranking.js`：排行榜和详情弹窗
- `src/main/resources/static/js/insights.js`：数据洞察图表
- `src/main/resources/static/js/compare.js`：曲线对比

---

## 三、当前首页现状

正式首页 `#/` 已经完成过一次较大迁移，目前包含：

- 学术研究风格的 Hero 首屏
- 品牌导航和四个正式业务入口
- 研究路径入口
- 真实案例预览卡
- 数据基础
- 检索增强
- 时序预测
- 真实案例与验证路径
- 蓝灰和米色错位纸张
- 条纹格子底色
- 真实视频封面和 Day30 趋势曲线

首页原型文件仍保留：

```text
docs/home-preview-ac.html
```

它是当前“编辑部数据海报”风格的原型参考，不等同于正式生产页面。正式页面已经迁移了其中的主要结构，但后续不应默认原型和正式页面继续保持 1:1 绑定。

当前设计和改动记录：

- `docs/home-nav-improvement.md`
- `docs/home-content-writing-plan.md`
- `docs/project-change-summary.md`
- `docs/project-page-iteration-handoff.md`
- `docs/trae-handoff.md`

---

## 四、必须保留的业务事实和技术契约

无论页面是否大改，以下内容不能在没有确认的情况下推翻：

1. **真实数据原则**
   - 禁止 mock 数据、假案例、虚构 KPI、虚构准确率和虚构预测结果。
   - 页面数字必须来自真实接口、数据库或已有固定业务口径。

2. **接口契约**
   - 不修改已有 API 路径、字段名、字段含义和返回结构。
   - 后端接口包括视频列表、视频详情、预测、洞察、曲线对比和健康检查。
   - 如确实需要新增接口，先说明用途、请求参数、响应结构和对现有页面的影响。

3. **预测逻辑**
   - 当前主路径是 BM25 / FTS5 检索相似案例。
   - 参考案例的真实 Day1-Day30 曲线逐日平均，形成 baseline。
   - 不要把 baseline 写成已训练完成的模型，也不要宣称预测准确率。

4. **图片边界**
   - 上传图片目前可以进入页面流程，但不参与当前预测计算。
   - 不要在没有模型和接口支持的情况下声称图片已经参与预测。

5. **数据字段**
   - 保留 Day1-Day30 曲线、峰值天、Day30 和衰减率的既有口径。
   - 不要修改分类名中的原始空格。

6. **ECharts**
   - 使用已有本地 `echarts.min.js`。
   - 不引入 CDN、外部字体或重型构建链。
   - 不从外部直接 dispose 已有 ECharts 实例。

---

## 五、桌面端开发边界

本项目只支持桌面端 Microsoft Edge：

- 覆盖 Edge 最新 3 个正式版本和 Edge 企业版
- 验收分辨率：`1920×1080`、`2560×1440`、`3840×2160`
- 不做手机、平板、触摸设备和移动端适配
- 不新增尺寸媒体查询
- 不新增 viewport 配置
- 不新增触摸事件和设备尺寸分支
- 仅允许保留 `prefers-reduced-motion` 无障碍规则

每次修改后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-desktop-only.ps1
git diff --check
```

---

## 六、开始大改前的强制流程

不要直接重写代码。必须按以下顺序执行：

### 第一步：确认工作树

先运行：

```powershell
git status --short
git diff --stat
```

当前工作树可能包含未提交的前端和文档修改。禁止使用：

- `git reset --hard`
- `git checkout --`
- `git restore`
- `git clean -f`
- 任何会覆盖或删除用户现有修改的命令

### 第二步：读取核心文件

至少完整读取：

1. `README.md`
2. `docs/trae-handoff.md`
3. `docs/project-change-summary.md`
4. `docs/project-page-iteration-handoff.md`
5. `src/main/resources/static/index.html`
6. `src/main/resources/static/css/style.css`
7. `src/main/resources/static/js/app.js`
8. `src/main/resources/static/js/router.js`
9. `src/main/resources/static/js/api.js`
10. 当前要改页面对应的 JS 模块

### 第三步：建立保留 / 重构 / 删除清单

把现有内容分成三类：

#### A. 必须保留

- 后端 API
- SQLite 和真实数据结构
- 五个 Hash 路由
- 预测、排行榜、洞察、曲线对比的核心业务行为
- ECharts 数据映射和现有测试
- 桌面 Edge 校验规则

#### B. 可以重构

- 页面 DOM 结构
- CSS Token、字体、间距、颜色和卡片样式
- 首页 Hero 和章节布局
- 导航视觉
- 信息层级和交互反馈
- 页面之间的视觉统一

#### C. 只有确认后才能删除

- 正式页面
- 已接入真实数据的组件
- 接口调用
- 路由入口
- 现有测试
- 项目交接和变更文档

### 第四步：先给出三种方案

新会话需要先向用户汇报：

1. **保守优化**：保留现有业务和页面结构，只重做视觉与交互层。
2. **结构重构**：保留 API、数据和路由，重新组织页面 DOM、信息架构和 CSS。
3. **重新设计**：重新设计页面，但先建立业务契约清单和迁移映射，再逐页替换。

每种方案必须说明：

- 保留什么
- 重写什么
- 删除什么
- 影响哪些文件
- 是否影响 API、数据和路由
- 如何回归验证

在用户明确选择前，不要开始大规模删除或重写。

---

## 七、视觉迭代原则

当前项目倾向于沉稳、清晰、具有研究感的桌面界面，但用户可以重新选择方向。不要把旧的“编辑部数据海报”风格当成永久不可改变的要求。

无论采用何种新风格：

- 真实数据和业务状态优先于装饰
- 标题、正文、数据和操作必须有明确层级
- 背景装饰不能遮挡文字、输入框、图表和焦点
- 趋势曲线应体现真实离散采样、峰值和衰减关系
- 不使用粗大、圆润、缺乏数据含义的装饰曲线
- 保证足够留白，不把卡片挤到页面底部
- 保持键盘可访问和焦点可见

---

## 八、验收要求

修改完成后至少验证：

1. `#/` 首页加载和真实案例接口
2. 首页搜索进入 `#/predict`
3. 四个业务导航可跳转
4. 研究路径和章节锚点可定位
5. `#/predict` 的输入、上传、预测和结果展示
6. `#/ranking` 的筛选、搜索、分页和详情弹窗
7. `#/insights` 的图表和 KPI
8. `#/compare` 的示例按钮、曲线和指标表
9. 浏览器控制台无错误
10. 桌面 Edge 目标分辨率无横向溢出和内容裁剪
11. Maven 测试全部通过
12. 桌面端策略扫描通过

推荐命令：

```powershell
mvn -s mvn-settings.xml process-resources
mvn -s mvn-settings.xml test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-desktop-only.ps1
git diff --check
```

---

## 九、给新会话的第一条回复要求

开始工作时，不要直接说“我会全部重做”或“我会保留现在的设计”。先输出：

1. 对当前项目架构和数据流的理解
2. 当前正式页面与首页原型的关系
3. 已完成内容、可重构内容和不可随意删除内容
4. 保守优化、结构重构、重新设计三种方案的差异
5. 你建议优先选择的方案及原因
6. 等待用户确认后再执行大改

除非用户明确要求，否则不要提交 Git，不要推送远程，也不要修改后端和数据库。
