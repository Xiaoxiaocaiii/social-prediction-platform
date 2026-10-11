# 多模态社交媒体流行度预测与社交网络分析平台

本项目提供两个相互隔离的分析工作台：

1. **流行度预测**：基于 SMTPD 真实视频数据，检索历史案例并生成 30 天基线预测。
2. **社交网络分析**：基于真实用户、关注关系和推文样本，提供网络结构、互动统计、文本主题和结果导出。

页面、接口和图表均使用真实数据计算，不使用 mock 数据。社交网络分析不生成综合影响力分，不推断社会影响力、传播路径或因果关系。

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

### 社交网络分析 Social Network

- 工作台入口：`#/social-network`
- B01 数据集入口与质量摘要
- B02 网络总览、密度、孤立节点和 PageRank
- B03 用户搜索、深度、Top-K、外部节点和局部关注网络
- B04 入度、出度、PageRank 与原始账户字段排行
- B05 用户详情右侧抽屉
- B06 点赞、评论、转发独立统计、推文排行、用户排行和时间序列
- B07 关键词、TF-IDF、精确文本搜索和主题关系联动
- JSON、CSV、打印摘要导出

## 快速开始 Quick Start

### 环境要求 Requirements

- JDK 17
- Maven 3.9+
- Microsoft Edge 桌面端
- 推荐分辨率：`1920×1080`、`2560×1440`、`3840×2160`

### 启动 Run

Windows:

```bat
start.bat
```

或：

```bash
mvn -s mvn-settings.xml spring-boot:run
```

打开 <http://localhost:8080>。

### 测试 Test

```bash
mvn -s mvn-settings.xml test
node --check src/main/resources/static/js/social-network.js
git diff --check
```

## 数据说明 Dataset

### 预测数据 Prediction Data

预测模块使用 SMTPD 视频数据，包括视频文本、封面和 30 天热度序列。原始数据与索引文件属于本地数据资产，不随代码仓库提交。

### 社交网络内置数据 Built-in Social Dataset

当前内置数据集 ID 为 `think-tank-social-network`，版本为 `v1`：

| 项目 | 数量 |
| --- | ---: |
| 用户 | 626 |
| 关系候选 | 12,963 |
| 推文样本 | 237,840 |

内置适配文件：

| 文件 | 角色 |
| --- | --- |
| `all_user_info.csv` | 用户资料 |
| `all_following_list.csv` | 聚合关注关系 |
| `all_tweets_raw.csv` | 推文与互动 |

内置数据的中文字段适配是项目内已确认的固定范围，不代表系统会自动识别任意字段名。

## 上传模板 Upload Template

社交网络上传首选三个独立的 UTF-8 CSV 或 JSONL 文件：

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

```text
source_user_id → target_user_id
```

含义是 source 用户关注 target 用户。

### `tweets.csv`

```csv
tweet_id,user_id,created_at,text,like_count,reply_count,repost_count,bookmark_count
1997991551738183929,1002259603537702912,2025-12-08,"Example text",3,0,0,0
```

JSONL 文件每行必须是一个 JSON 对象。CSV 必须有表头并使用 UTF-8；允许 UTF-8 BOM，不支持无表头 CSV、Excel、XML、GBK/GB18030 或整文件 JSON 数组。

用户 ID 和推文 ID 始终按字符串处理，不能转换为整数或 JavaScript `Number`。用户上传数据只属于当前分析会话，不作为永久数据集保存。

完整字段规则见 [社交网络数据字段契约](docs/social-network-analysis-data-field-contract.md)。

## 指标口径 Metric Semantics

| 指标 | 含义 |
| --- | --- |
| 入度 | 指向该节点的有效关系数量 |
| 出度 | 该节点发出的有效关系数量 |
| 密度 | 当前规范化有向网络的结构密度 |
| PageRank | 网络结构位置指标，不等于社会影响力 |
| 点赞/评论/转发 | 三类互动分别统计，不合成综合分 |
| 关键词 | 精确词元匹配后的频率与文档频率 |
| TF-IDF | 文本区分度，不表示影响力 |
| 主题网络 | 匹配用户之间的真实关注关系，不表示传播路径 |

缺失值表示未采集或无法关联；明确采集到的 `0` 保留为有效零值。重复推文在原始层保留，分析层按规则去重并报告冲突。

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
| `GET /.../{datasetId}/rankings` | 指标排行 |
| `GET /.../{datasetId}/users/search` | 用户搜索 |
| `GET /.../{datasetId}/users/{userId}` | 用户详情 |
| `GET /.../{datasetId}/interactions/*` | 互动分析 |
| `GET /.../{datasetId}/text/*` | 文本与主题分析 |
| `POST /.../{datasetId}/exports` | JSON/CSV/打印摘要导出 |

响应统一包含 `success`、`code`、`message`、`requestId`、`data`、`warnings` 和 `meta`。错误响应不暴露本地路径、堆栈或内部配置。

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
- 当前真实数据工作集约 2.2GB，继续扩大数据规模时需要分区索引、按需导出或异步大结果处理。
- 项目仅支持桌面 Microsoft Edge，不扩展移动端、平板端或触摸设备适配。

## 验收步骤 Acceptance

1. 启动应用并打开 `http://localhost:8080/#/social-network`。
2. 确认数据集显示 626 用户、12,963 关系和 237,840 推文。
3. 检查网络总览、PageRank、用户搜索、局部网络和用户详情抽屉。
4. 切换点赞、评论、转发，检查互动排行和时间序列。
5. 输入真实关键词，检查主题推文、相关用户和真实关系边。
6. 分别生成 JSON、CSV 和打印摘要导出。
7. 检查加载、空、部分可用和失败提示。
8. 在三个目标桌面分辨率检查表格、长文本、图表和关系方向。
9. 切换 `#/predict`，确认预测页面无回归。

性能实测见 [T34 性能验收报告](docs/social-network-analysis-performance-acceptance.md)。

## 项目结构 Project Structure

```text
src/main/java/com/prediction/           # Spring Boot 后端
src/main/resources/static/              # HTML/CSS/JavaScript 前端
src/main/java/com/prediction/socialnetwork/ # 社交网络领域、分析和导出
scripts/                                # 预测数据处理脚本
docs/                                   # 契约、规划、验收和使用文档
data/                                   # 本地预测数据资产，不提交到 Git
```

## 数据来源与声明 Data Notice

预测数据来源为 SMTPD：<https://github.com/zhuwei321/SMTPD>。

社交网络数据集由项目开发者整理并提供，具体采集来源、时间范围、处理方式和授权边界以项目数据说明为准。使用者应自行确认数据集授权与隐私合规要求。
