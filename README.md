# 多模态社交媒体流行度预测与社交网络分析平台 / Multimodal Social Media Popularity Prediction and Social Network Analysis Platform

本项目提供两个相互隔离的分析工作台：

1. **流行度预测**：基于 SMTPD 真实视频数据，检索历史案例并生成 30 天基线预测。
2. **社交网络分析**：基于真实用户、关注关系和推文样本，提供网络结构、互动统计、文本主题和结果导出。

页面、接口和图表均使用真实数据计算，不使用 mock 数据。社交网络分析不生成综合影响力分，不推断社会影响力、传播路径或因果关系。

当前交付范围与运行方式见[项目验收与演示说明](docs/验收与演示说明.md)。社交网络分析的当前功能、数据边界和实现约定见[社交网络分析当前实现](docs/social-network-analysis-current-implementation.md)。

## English Overview

This project provides two isolated workspaces:

1. **Popularity Prediction**: retrieves historical SMTPD video cases and produces a 30-day baseline forecast.
2. **Social Network Analysis**: analyzes real users, following relations, tweets, interactions, text topics, and exports.

The platform uses real data rather than mock data. Social-network metrics describe observed data and graph structure only; they do not infer influence, diffusion paths, or causality.

## 功能与页面 Features

### 流行度预测 Prediction

- 首页趋势发现：`#/`
- 上传预测：`#/predict`
- 热度排行榜：`#/ranking`
- 数据洞察：`#/insights`
- 曲线对比：`#/compare`
- 基于 BM25/FTS5 的历史案例检索和 30 天真实曲线平均基线

### Popularity Prediction

- Home and trend discovery: `#/`
- Prediction upload: `#/predict`
- Popularity ranking: `#/ranking`
- Data insights: `#/insights`
- Curve comparison: `#/compare`
- BM25/FTS5 case retrieval and a 30-day baseline averaged from real historical curves

### 社交网络分析 Social Network

- 九个任务页路由（共用任务工作台）：概览 `#/social-network`、当前数据 `#/social-network/data`、上传 `#/social-network/data/upload`、质量 `#/social-network/data/quality`、网络结构 `#/social-network/relations`、节点排行 `#/social-network/relations/ranking`、用户关系 `#/social-network/relations/user`、互动 `#/social-network/content`、主题 `#/social-network/content/topic`
- B01 数据集入口、质量摘要和当前会话数据上传：`#/social-network/data`、`#/social-network/data/quality`、`#/social-network/data/upload`
- B02 网络总览、结构指标、密度、孤立节点和 PageRank：`#/social-network/relations`
- B03 用户搜索、深度、Top-K、外部节点和局部关注网络：`#/social-network/relations/user`
- B04 入度、出度、PageRank、k-core 和介数中心性排行：`#/social-network/relations/ranking`
- B05 用户详情右侧抽屉：从排行及用户关系结果中的用户入口打开
- B06 点赞、评论、转发独立统计，以及推文排行、用户排行和按发布时间字段分组的时间分布：`#/social-network/content`
- B07 精确主题查询、相关推文/用户/关注关系联动：`#/social-network/content/topic`；关键词与 TF-IDF 接口已存在，但当前没有独立词表 UI
- 全局关系图、社群筛选和账号详情：`#/social-network/network`
- 团体与社群分析：复用 Clique 极大团分析和 Louvain 社群划分，提供成员、关系证据及结果导出
- 节点排行包含独立的跨社群连接候选视图，提供账号详情、关系证据、图内定位和 JSON/CSV/可读报告导出
- 当前会话上传支持 CSV/JSONL；上传数据可运行 Louvain 与 Clique，并用于关系图、社群/团体证据、跨社群候选及相应导出。上传结果按会话隔离，关闭、过期或服务重启后不可恢复
- 数据集入口集中导出；网络总览、重要节点、互动、主题和质量模块提供独立导出

### Social Network Analysis

- Nine task-page routes share the task workspace: overview `#/social-network`, current data `#/social-network/data`, upload `#/social-network/data/upload`, quality `#/social-network/data/quality`, network structure `#/social-network/relations`, node rankings `#/social-network/relations/ranking`, user relations `#/social-network/relations/user`, interactions `#/social-network/content`, and topics `#/social-network/content/topic`
- B01 dataset entry, quality summary, and temporary-session uploads: `#/social-network/data`, `#/social-network/data/quality`, and `#/social-network/data/upload`
- B02 network overview, structural metrics, density, isolates, and PageRank: `#/social-network/relations`
- B03 user search, depth and Top-K controls, external nodes, and local following networks: `#/social-network/relations/user`
- B04 indegree, outdegree, PageRank, k-core, and betweenness rankings: `#/social-network/relations/ranking`
- B05 user details in a shared side drawer, opened from ranking and user-relation results
- B06 separate like, reply, and repost statistics, plus tweet rankings, user rankings, and time distributions grouped by the available publish-time field: `#/social-network/content`
- B07 exact topic search with related tweets, users, and following relationships: `#/social-network/content/topic`; keyword and TF-IDF APIs exist, but there is no standalone vocabulary UI
- The global graph, community filters, and account details are available at `#/social-network/network`
- Clique and Louvain analysis provide group/community results, member and relationship evidence, and exports
- Node rankings include a separate cross-community connection-candidate view with account details, relationship evidence, graph location, and JSON/CSV/readable-report exports
- Current-session CSV/JSONL uploads support Louvain and Clique analysis, graph/community evidence, bridge candidates, and related exports. Upload results are isolated by session and expire when the session closes, expires, or the service restarts
- Dataset-level export remains available; network overview, important nodes, interactions, topics, and data quality also have module-level export actions

## 快速开始 Quick Start

### 环境要求 Requirements

- JDK 17
- Maven 3.9+
- Microsoft Edge 桌面端
- 推荐分辨率：`1920×1080`、`2560×1440`、`3840×2160`

### Environment Requirements

- JDK 17
- Maven 3.9 or later
- Desktop Microsoft Edge
- Recommended viewports: `1920×1080`, `2560×1440`, and `3840×2160`

### 启动 Run

Windows:

```bat
start.bat
```

或使用 Maven 启动 / Or start with Maven:

```bash
mvn -s mvn-settings.xml spring-boot:run
```

打开 <http://localhost:8080>。

Open <http://localhost:8080> after startup.

### 测试 Test

```powershell
mvn -s mvn-settings.xml test
Get-ChildItem scripts\*.test.js | ForEach-Object {
  node $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "Frontend test failed: $($_.Name)" }
}
git diff --check
```

Run the JavaScript contract tests in PowerShell as shown. A recorded test result is evidence only for the exact code and environment used for that run.

## 数据说明 Dataset

### 预测数据 Prediction Data

预测模块使用 SMTPD 视频数据，包括视频文本、封面和 30 天热度序列。原始数据与索引文件属于本地数据资产，不随代码仓库提交。

The prediction workspace uses SMTPD video data, including video text, covers, and 30-day popularity sequences. Raw data and indexes are local assets and are not included in the source repository.

### 社交网络内置数据 Built-in Social Dataset

当前内置数据集 ID 为 `think-tank-social-network`，版本为 `v1`：

The built-in social-network dataset is `think-tank-social-network`, version `v1`:

以下规模对应 2026-10-29 核验过的本机数据快照；克隆源码不包含原始社交网络数据，本地文件缺失或版本变化时不得直接套用这些计数。

The counts below describe the local snapshot verified on 2026-10-29. The source repository does not include the raw social-network dataset; do not assume these counts apply when local data is absent or has changed.

| 项目 | 数量 |
| --- | ---: |
| 用户数 | 626 |
| 关注关系数 | 12,963 |
| 推文数 | 237,840 |

| Item | Count |
| --- | ---: |
| Users | 626 |
| Following-relation records | 12,963 |
| Tweet records | 237,840 |

以上为数据集摘要中的规模口径：关注关系数对应候选关系记录，质量摘要还会单独报告规范化后的有效关注关系数；推文数对应原始推文记录数，不等于去重后纳入分析的推文数，也不代表账号完整历史推文数。

These are dataset-summary counts. The relationship count is the number of candidate records; the quality summary separately reports valid normalized relationships. The tweet count is the number of raw records, not the deduplicated analysis sample or an account's complete posting history.

内置适配文件 Built-in Adapter Files：

| 文件 | 角色 |
| --- | --- |
| `all_user_info.csv` | 用户资料 |
| `all_following_list.csv` | 聚合关注关系 |
| `all_tweets_raw.csv` | 推文与互动 |

内置数据的中文字段适配是项目内已确认的固定范围，不代表系统会自动识别任意字段名。

The built-in adapter files are `all_user_info.csv` (user profiles), `all_following_list.csv` (aggregated following relations), and `all_tweets_raw.csv` (tweets and interactions). Their Chinese column mappings are explicit project adapters; the system does not infer arbitrary field names.

## 当前会话上传格式 Current-Session Upload Formats

上传是当前可用的临时分析能力，不是永久数据集保存。用户为每个文件明确选择 `users`、`relationships` 或 `tweets` 角色；最多 10 个文件，同一角色可以选择多个文件。允许部分角色缺省，实际可用模块由服务端预览中的能力状态决定。

Uploads are available for temporary analysis sessions; they do not create permanently stored datasets. Assign each file an explicit `users`, `relationships`, or `tweets` role. A request accepts up to 10 files, including multiple files for the same role. Roles may be omitted; available analysis modules are determined by the server-side preview.

支持 UTF-8 CSV（必须有表头）或 JSONL（每行一个 JSON 对象），允许 UTF-8 BOM；不支持 Excel、XML、GBK/GB18030、UTF-16 或整文件 JSON 数组。单文件上限为 `128 MiB`，单次请求上限为 `160 MiB`。用户先查看服务端质量预览，再明确激活上传数据集；数据仅存在于当前浏览器标签页关联的分析会话中，空闲 2 小时或最长 24 小时后过期。

Supported inputs are UTF-8 CSV (with a header) and JSONL (one JSON object per line); UTF-8 BOM is accepted. Excel, XML, GBK/GB18030, UTF-16, and whole-file JSON arrays are not supported. The per-file limit is `128 MiB` and the per-request limit is `160 MiB`. The user reviews a server-generated quality preview and explicitly activates the dataset. Uploaded data belongs only to the current browser-tab session and expires after 2 hours of inactivity or 24 hours at most.

上传前会展示来源与责任提示，但不会验证数据来源或授权。来源说明可留空；空值记录为“用户未提供”。上传原文件、规范化快照和分析结果只在当前会话中使用，不作为永久数据集保存；内置数据分析结果的本机缓存属于独立存储边界。

Source and responsibility notices are displayed before upload, but the platform does not verify provenance or authorization. A source description is optional; an empty value is recorded as “not provided by the user.” Uploaded files, normalized snapshots, and analysis results are scoped to the current session and are not registered as permanent datasets. Built-in analysis artifacts use a separate local storage boundary.

以下是三种逻辑角色的字段示例：

The following examples show the standard fields for each logical role:

### `users.csv`

```csv
user_id,username,display_name,bio,followers_count,following_count
1002259603537702912,example_user,Example User,,1475,20
```

### `relationships.csv`

```csv
source_user_id,target_user_id
1002259603537702912,188736341
```

关系方向固定为：

The relationship direction is fixed:

```text
source_user_id → target_user_id
```

含义是 source 用户关注 target 用户。

This means that the source user follows the target user.

### `tweets.csv`

```csv
tweet_id,user_id,created_at,text,like_count,reply_count,repost_count,bookmark_count
1997991551738183929,1002259603537702912,2025-12-08,"Example text",3,0,0,0
```

JSONL 文件每行必须是一个 JSON 对象。CSV 必须有表头并使用 UTF-8；允许 UTF-8 BOM，不支持无表头 CSV、Excel、XML、GBK/GB18030 或整文件 JSON 数组。

JSONL requires one JSON object per line. CSV requires a header and UTF-8 encoding; UTF-8 BOM is accepted. Headerless CSV, Excel, XML, GBK/GB18030, and whole-file JSON arrays are not supported.

用户 ID 和推文 ID 始终按字符串处理，不能转换为整数或 JavaScript `Number`。

User and tweet IDs must remain strings; do not convert them to integers or JavaScript `Number` values.

完整字段规则见 [社交网络数据字段契约](docs/social-network-analysis-data-field-contract.md)。

See the [social-network data field contract](docs/social-network-analysis-data-field-contract.md) for the complete field rules.

## 指标口径 Metric Semantics

| 指标 | 含义 |
| --- | --- |
| 数据内被关注数（入度） | 当前规范化关系数据中指向该节点的唯一有效关注关系数量 |
| 数据内关注数（出度） | 当前规范化关系数据中该节点指向其他节点的唯一有效关注关系数量 |
| 密度 | 当前规范化有向网络的结构密度 |
| PageRank | 网络结构位置指标，不等于社会影响力 |
| 点赞/评论/转发 | 三类互动分别统计，不合成综合分 |
| 关键词 | 精确词元匹配后的频率与文档频率 |
| TF-IDF | 文本区分度，不表示影响力 |
| 主题网络 | 匹配用户之间的真实关注关系，不表示传播路径 |

缺失值表示未采集或无法关联；明确采集到的 `0` 保留为有效零值。重复推文在原始层保留，分析层按规则去重并报告冲突。

| Metric | Meaning |
| --- | --- |
| In-degree | Number of unique valid normalized relations directed to a node within the dataset |
| Out-degree | Number of unique valid normalized relations originating from a node within the dataset |
| Density | Structural density of the normalized directed network |
| PageRank | A network-position metric, not a measure of social influence |
| Likes / replies / reposts | Reported separately; never combined into a composite score |
| Keywords | Frequency and document frequency after exact token matching |
| TF-IDF | Text distinctiveness, not influence |
| Topic network | Real following relations among matched users; not a diffusion path |

Missing values mean “not collected” or “not linkable”; an observed `0` remains a valid zero. Duplicate tweets remain in the raw layer; the analysis layer deduplicates according to its rules and reports conflicts.

## API 概览 API

所有社交网络接口前缀为：

```text
/api/social-network/datasets/{datasetId}
```

主要接口：

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 健康检查 |
| `GET /.../{datasetId}` | 数据集摘要、质量和能力 |
| `GET /.../{datasetId}/network/summary` | 网络总览 |
| `GET /.../{datasetId}/network/local` | 局部网络 |
| `GET /.../{datasetId}/network/graph` | 全局关系图数据、社群归属及完整范围/画布展示范围摘要 |
| `GET /.../{datasetId}/network/bridge-candidates` | 跨社群连接候选分页 |
| `GET /.../{datasetId}/network/bridge-candidates/{accountId}/evidence` | 候选账号按社群和方向分页的关注关系证据 |
| `POST /.../{datasetId}/network/exports` | 已完成的 Clique/Louvain 结果及当前页证据导出 |
| `GET /.../{datasetId}/rankings` | 指标排行 |
| `GET /.../{datasetId}/users/search` | 用户搜索 |
| `GET /.../{datasetId}/users/{userId}` | 用户详情 |
| `GET /.../{datasetId}/interactions/*` | 互动分析 |
| `GET /.../{datasetId}/text/*` | 文本与主题分析 |
| `POST /.../{datasetId}/exports` | JSON/CSV/打印摘要导出 |
| `POST /api/social-network/datasets/upload` | 接收上传文件并创建异步预览任务 |
| `GET /api/social-network/datasets/upload-tasks/{uploadTaskId}` | 查询上传任务和质量预览状态 |
| `DELETE /api/social-network/datasets/upload-tasks/{uploadTaskId}` | 取消尚未完成的上传任务 |
| `DELETE /api/social-network/datasets/{datasetId}/sessions/{sessionId}` | 结束临时上传分析会话 |

| Endpoint | Description |
| --- | --- |
| `GET /api/health` | Health check |
| `GET /.../{datasetId}` | Dataset summary, quality, and capabilities |
| `GET /.../{datasetId}/network/summary` | Network overview |
| `GET /.../{datasetId}/network/local` | Local following network |
| `GET /.../{datasetId}/network/graph` | Global graph data, community assignments, and full/displayed-scope summaries |
| `GET /.../{datasetId}/network/bridge-candidates` | Paginated cross-community connection candidates |
| `GET /.../{datasetId}/network/bridge-candidates/{accountId}/evidence` | Paginated relationship evidence by community and direction |
| `POST /.../{datasetId}/network/exports` | Export completed Clique/Louvain results and selected evidence pages |
| `GET /.../{datasetId}/rankings` | Metric rankings |
| `GET /.../{datasetId}/users/search` | User search |
| `GET /.../{datasetId}/users/{userId}` | User details |
| `GET /.../{datasetId}/interactions/*` | Interaction analysis |
| `GET /.../{datasetId}/text/*` | Text and topic analysis |
| `POST /.../{datasetId}/exports` | Synchronous JSON, CSV, or print-summary export |
| `POST /api/social-network/datasets/upload` | Accept upload files and create an asynchronous preview task |
| `GET /api/social-network/datasets/upload-tasks/{uploadTaskId}` | Read upload-task and preview status |
| `DELETE /api/social-network/datasets/upload-tasks/{uploadTaskId}` | Cancel a pending upload task |
| `DELETE /api/social-network/datasets/{datasetId}/sessions/{sessionId}` | Close a temporary analysis session |

响应统一包含 `success`、`code`、`message`、`requestId`、`data`、`warnings` 和 `meta`。错误响应不暴露本地路径、堆栈或内部配置。

The main API prefix is `/api/social-network/datasets/{datasetId}`. Responses use the shared `success`, `code`, `message`, `requestId`, `data`, `warnings`, and `meta` envelope. Errors do not expose local paths, stack traces, or internal configuration.

全局关系图接口默认最多返回 100 个节点和 500 条关系，可通过 `maxNodes`、`maxEdges` 及 `includeExternalNodes` 调整展示范围；单次请求上限为 1,000 个节点和 15,000 条关系，并通过 `truncated` 标识截断。社群划分和跨社群候选基于完整规范化网络及同一份 Louvain 结果；响应分别提供完整筛选范围统计和本次画布展示统计。展示被截断不代表范围外账号或关系不存在。

The global-graph API defaults to at most 100 nodes and 500 relations. `maxNodes`, `maxEdges`, and `includeExternalNodes` can adjust the displayed range, up to 1,000 nodes and 15,000 relations per request. Responses mark truncation with `truncated` and separate full filtered-scope statistics from the displayed canvas. Community assignments and bridge candidates use the complete normalized network and the same Louvain result. Truncation does not mean omitted accounts or relations do not exist.

数据集入口保留集中式导出；网络总览、重要节点、互动、主题和数据质量模块也可导出当前结果。导出 API 同步返回内容，前端使用浏览器 Blob 生成文件，不创建服务端临时下载文件。单次导出上限为 `2 MiB`，超限整体拒绝；主题导出必须带当前查询词。

Dataset-level export remains available. Network overview, important nodes, interactions, topics, and data-quality modules can also export their current results. The API returns export content synchronously; the browser creates a local Blob download, and the server does not create a temporary download file. Each export is limited to `2 MiB` and is rejected as a whole when oversized. Topic exports require the current query term.

## 状态与限制 States and Limitations

统一状态包括：

```text
idle / loading / available / empty / limited / failed / disabled / unavailable
```

- `limited` 表示网络截断、样本不足或作者关联覆盖有限。
- `empty` 表示没有可展示的结果，不生成虚假图表或空表头。
- 互动接口逐模块容错，单个指标失败不会关闭整个互动模块。
- 外部节点可以参与网络计算，但默认不进入用户资料排行。
- 当前导出是同步响应承载，不生成永久文件或下载链接。
- 内置快照缓存复用同一数据集版本；版本变化必须重新计算。
- Clique/Louvain 使用受限的独立 Worker；禁止按整机可用内存设置分析准入门槛，具体约束见[Worker 资源契约](docs/social-network-worker-resource-contract.md)。
- 项目仅支持桌面 Microsoft Edge，不扩展移动端、平板端或触摸设备适配。

- `limited` means the graph, sample, or author-linkage coverage is capped.
- `empty` means there is no result to display; the UI does not invent chart values or empty table headers.
- Interaction sections degrade independently; one failed metric does not disable the entire module.
- External nodes may participate in network calculations but are excluded from user-profile rankings by default.
- Exports are synchronous responses; the server does not keep permanent files or download links.
- Snapshot caches are scoped to a dataset version; a version change requires recalculation.
- Clique/Louvain run in bounded Worker processes. Whole-machine available memory is not an analysis gate; see the [Worker resource contract](docs/social-network-worker-resource-contract.md).
- Desktop Microsoft Edge is the only supported browser target; mobile, tablet, and touch layouts are out of scope.

## 验收与演示检查 Acceptance and Demo Checks

以下是目标机器的演示前检查步骤，用于验证当前运行环境，不代表检查已自动完成。

1. 启动应用并打开 `http://localhost:8080/#/social-network`。
2. 确认数据集显示用户数 626、关注关系数 12,963 和推文数 237,840，并区分候选关系、规范化有效关系及原始/去重推文口径。
3. 检查网络总览、PageRank、用户搜索、局部网络和用户详情抽屉。
4. 检查当前会话上传：选择文件角色、查看服务端质量预览、显式激活，再切回内置数据并关闭会话。
5. 切换点赞、评论、转发，检查互动排行和时间序列。
6. 输入真实关键词，检查主题推文、相关用户和真实关系边。
7. 分别使用集中式入口及五个模块入口生成 JSON、CSV 和打印摘要；检查查询条件、截断信息和超限提示。
8. 检查加载、空、部分可用和失败提示。
9. 在三个目标桌面分辨率检查表格、长文本、导出对话框、图表和关系方向。
10. 打开全局关系图，检查社群筛选、画布截断说明、账号详情及图内定位。
11. 运行 Clique/Louvain，检查成员与关系证据；在节点排行核对跨社群候选及导出内容。
12. 上传三份演示 CSV，检查质量预览、激活、Louvain/Clique、跨社群候选、证据和导出；关闭或重启后确认旧会话失效。
13. 切换 `#/predict`，确认预测页面无回归。

### English Checklist

These checks must be executed on the target machine; their presence here is not evidence that the current runtime passed. See the [acceptance and demo guide](docs/验收与演示说明.md) for current run instructions.

1. Start the application and open `http://localhost:8080/#/social-network`.
2. Confirm the dataset summary shows 626 users, 12,963 relation records, and 237,840 raw tweet records; distinguish candidate relations from valid normalized relations and raw tweets from deduplicated samples.
3. Check the network overview, PageRank, user search, local network, and user-details drawer.
4. Test a current-session upload: assign file roles, inspect the server-generated quality preview, explicitly activate the dataset, then switch back to the built-in dataset and close the session.
5. Switch between likes, replies, and reposts; check rankings and the time series.
6. Search for a real topic term and check matched tweets, related users, and real following relations.
7. Generate JSON, CSV, and print-summary exports from both the dataset entry and all five module actions; verify query metadata, truncation information, and oversized-result handling.
8. Check loading, empty, partial, and failed states.
9. At all three target desktop viewports, inspect tables, long text, export dialogs, charts, and relation direction.
10. Open the global graph and check community filters, truncation notices, account details, and graph navigation.
11. Run Clique/Louvain, inspect member and relationship evidence, and verify bridge candidates and exports from node rankings.
12. Upload the three demo CSV files and check preview, activation, analysis, bridge evidence, and exports; confirm the old session expires after closure or restart.
13. Open `#/predict` and confirm the prediction workspace has not regressed.

社交网络分析当前功能与实现边界见[当前实现说明](docs/social-network-analysis-current-implementation.md)；本机运行及演示步骤见[项目验收与演示说明](docs/验收与演示说明.md)。

See the [current social-network implementation](docs/social-network-analysis-current-implementation.md) for supported behavior and boundaries. See the [acceptance and demo guide](docs/验收与演示说明.md) for local setup and verification steps.

## 项目结构 Project Structure

```text
src/main/java/com/prediction/           # Spring Boot 后端
src/main/resources/static/              # HTML/CSS/JavaScript 前端
src/main/java/com/prediction/socialnetwork/ # 社交网络领域、分析和导出
scripts/                                # 预测数据处理脚本
docs/                                   # 当前实现契约、演示和使用文档
data/                                   # 本地预测数据资产，不提交到 Git
```

`src/main/java/com/prediction/` contains the Spring Boot backend; `src/main/resources/static/` contains the HTML, CSS, and JavaScript frontend; `scripts/` contains prediction-data utilities; `docs/` contains current contracts and operating guides. Local prediction assets under `data/` are not committed.

## 数据来源与声明 Data Notice

预测数据来源为 SMTPD：<https://github.com/zhuwei321/SMTPD>。

社交网络数据集由项目开发者整理并提供；当前仓库未附带完整的采集来源与授权证明材料，因此不能据此声称来源或授权已核验。使用者应自行确认数据集的使用权限与隐私合规要求。

The prediction dataset is sourced from SMTPD: <https://github.com/zhuwei321/SMTPD>.

The social-network dataset is curated and provided by the project developer. This repository does not include complete collection-provenance or authorization evidence, so it does not claim those details have been verified. Users are responsible for confirming applicable permissions and privacy requirements.
