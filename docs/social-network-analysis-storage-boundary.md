# 社交网络分析数据存储边界

> 文档状态：存储边界历史契约；当前上传会话实现与验收见下方说明
> 适用范围：社交网络分析模块 F01-F07 的数据集、规范化结果、质量报告和会话边界
> 更新时间：2026-11-12
> 前置文档：
>
> - [当前实施与验收记录](../全局关系图与社群桥接分析实施计划.md)
> - [数据字段契约](social-network-analysis-data-field-contract.md)
> - [规范化与数据质量规则](social-network-analysis-normalization-quality-rules.md)
> - [API 与路由契约](social-network-analysis-api-route-contract.md)
> - [缓存与版本生命周期](social-network-analysis-cache-version-lifecycle.md)
>
> 说明：本文档只设计存储边界和逻辑结构，不创建 SQLite 表、不执行迁移、不实现 Repository 或接口。
> 当前状态：后续实现已按会话隔离约束支持上传数据分析缓存、过期清理及重启后失效。本文原有“待用户验收”状态不再适用；交付记录以 E-01 至 E-10 验收项为准。

## 1. 存储决策摘要

### 1.1 内置数据

内置“智库社交网络数据”使用独立的社交网络 SQLite 数据库文件：

```text
social_network.sqlite
```

该数据库与现有预测模块数据库物理隔离。

允许持久化：

- 数据集元信息；
- 文件清单和读取摘要；
- 规范化用户；
- 规范化关系；
- 去重后的分析推文；
- 质量报告；
- 规范化版本和结果追溯信息。

### 1.2 用户上传数据

用户上传数据只存在于当前分析会话：

- 原始文件进入临时会话空间；
- 规范化记录进入内存或会话级临时缓存；
- 不写入 `social_network.sqlite` 的永久业务表；
- 不写入预测模块数据库；
- 会话过期或主动结束后清理；
- 只保留用户主动下载的导出文件，不将其重新注册为平台数据集。

### 1.3 预测模块隔离

社交网络数据不得进入：

- 现有 SMTPD 视频表；
- 预测结果表；
- 预测缓存；
- 预测 Repository；
- `/api/predict` 业务链路；
- 预测页面状态管理。

## 2. 物理边界

### 2.1 独立数据库文件

社交网络数据库使用独立连接和独立文件：

```text
social_network.sqlite
```

禁止：

- 在现有预测 SQLite 文件中新增 `social_network_*` 表；
- 在预测连接上执行社交网络 SQL；
- 将预测业务表作为用户、关系或推文的外键父表；
- 共享预测 Repository；
- 复用预测业务缓存键。

### 2.2 独立代码边界

建议使用独立包：

```text
com.prediction.socialnetwork
```

建议逻辑分层：

```text
socialnetwork/
├── api
├── application
├── domain
├── repository
├── normalization
├── quality
├── metrics
├── text
└── cache
```

该目录结构是后续实施建议，本轮不创建代码。

### 2.3 独立数据目录

内置原始文件继续位于社交网络数据资产目录：

```text
all_csv/
csv/
```

规则：

- 原始目录只读；
- 不由运行时覆盖；
- 不把运行中间产物写回原始目录；
- 新版本数据使用独立版本目录或文件清单；
- 旧版本与新版本物理隔离；
- 数据版本由文件清单和版本元信息共同确定。

## 3. 数据层级

### 3.1 原始层

原始层由文件资产和临时上传文件组成：

- 保留原始字段；
- 保留原始文本；
- 保留源文件名；
- 保留源格式和编码；
- 保留源行号；
- 保留读取状态；
- 不覆盖原始值；
- 不把原始层直接暴露为业务计算接口。

内置原始文件：

```text
只读、版本化、可追溯
```

用户上传原始文件：

```text
当前会话临时、过期清理、不永久保存
```

### 3.2 规范化层

规范化层保存 T03 产生的标准结构：

- 用户标准记录；
- 唯一有效关系；
- 外部节点；
- 去重后的有效推文；
- 作者关联状态；
- 文本处理状态；
- 质量标记；
- 规范化版本。

规范化层可以持久化到内置数据的独立数据库。

### 3.3 分析层

首版分析层结果不要求永久落表：

- 网络指标通过缓存和版本元信息追溯；
- 互动统计通过计算结果和缓存追溯；
- 关键词、TF-IDF 和主题结果通过文本版本追溯；
- 导出结果通过导出元信息追溯；
- T15 再决定通用结果缓存的具体介质。

不得把分析结果反向写入原始表。

## 4. 逻辑数据集边界

### 4.1 数据集元信息

逻辑对象：

```text
social_network_dataset
```

设计字段：

```text
dataset_id: string
name: string
dataset_type: builtin | upload_session
dataset_version: string
status: healthy | available_with_warnings | partial | unavailable
source_description: text
time_range_from: date?
time_range_to: date?
normalization_version: string
metric_version: string?
text_processing_version: string?
explanation_version: string
created_at: datetime
updated_at: datetime
expires_at: datetime?
```

约束：

- `dataset_id` 唯一；
- `dataset_version` 必须可追溯；
- `dataset_type=builtin` 时不需要 `expires_at`；
- `dataset_type=upload_session` 时必须绑定 `session_id`；
- 用户上传数据不进入永久数据集注册。

### 4.2 文件清单

逻辑对象：

```text
social_network_dataset_file
```

设计字段：

```text
dataset_id: string
dataset_version: string
logical_file_name: string
file_role: users | relationships | tweets | unknown
source_format: csv | jsonl
source_encoding: utf8 | utf8_bom | unknown
source_digest: string
row_count: integer
read_ok_count: integer
read_warning_count: integer
read_error_count: integer
header_status: valid | missing | invalid
file_status: available | partial | unreadable
created_at: datetime
```

约束：

- 不保存预测业务文件；
- 不用本地绝对路径作为对外字段；
- `source_digest` 用于版本识别和缓存失效；
- 文件变化但版本未变化时禁止继续命中旧结果。

## 5. 用户数据边界

逻辑对象：

```text
social_network_user
```

设计字段：

```text
dataset_id: string
dataset_version: string
user_id: string
username: string?
display_name: string?
bio: text?
followers_count: integer?
following_count: integer?
tweet_count: integer?
created_at_source: string?
created_at_normalized: datetime?
location: string?
profile_status: collected | missing | conflict | external
normalization_status: normalized | warning | rejected
source_file_id: string?
source_row_number: integer?
quality_flags: json
normalization_version: string
```

约束：

- `(dataset_id, dataset_version, user_id)` 唯一；
- `user_id` 必须按字符串保存；
- 不把 username 作为主键；
- 外部节点可以单独进入节点索引，但不虚构资料；
- 资料冲突不静默覆盖；
- 原始字段和计算指标不混在用户表中。

## 6. 关系数据边界

逻辑对象：

```text
social_network_relationship
```

设计字段：

```text
dataset_id: string
dataset_version: string
relationship_id: string
source_user_id: string
target_user_id: string
source_node_status: user | external
target_node_status: user | external
relation_status: valid | self_loop | duplicate | invalid
analysis_included: boolean
source_file_id: string?
source_row_number: integer?
source_array_index: integer?
quality_flags: json
normalization_version: string
```

约束：

- 分析层唯一键为 `(dataset_id, dataset_version, source_user_id, target_user_id)`；
- 重复原始关系必须有质量记录；
- 自环原始保留但 `analysis_included=false`；
- 非法端点原始保留但 `analysis_included=false`；
- `source_user_id → target_user_id` 方向固定；
- 不以重复边放大网络指标。

## 7. 推文数据边界

逻辑对象：

```text
social_network_tweet
```

设计字段：

```text
dataset_id: string
dataset_version: string
tweet_id: string
author_user_id: string?
source_folder: string?
text_original: text?
published_at_source: string?
published_at_normalized: datetime?
like_count: integer?
reply_count: integer?
repost_count: integer?
bookmark_count: integer?
author_status: linked | unlinked | unassigned_source | missing
dedup_status: unique | duplicate | conflict
text_status: available | empty | failed
source_file_id: string?
source_row_number: integer?
quality_flags: json
normalization_version: string
```

约束：

- `tweet_id` 按字符串保存；
- `(dataset_id, dataset_version, tweet_id)` 为分析层唯一键；
- 原始重复记录不覆盖；
- 冲突重复记录保留质量证据；
- `text_original` 不被清洗文本替代；
- `Arab_API` 等未归属来源不生成虚构作者；
- 互动字段空值不转换为 0；
- 书签字段保留但全零时由能力层标记 `disabled`。

## 8. 质量报告边界

### 8.1 质量报告摘要

逻辑对象：

```text
social_network_quality_report
```

设计字段：

```text
dataset_id: string
dataset_version: string
report_version: string
dataset_status: string
generated_at: datetime
summary: json
user_quality: json
relationship_quality: json
tweet_quality: json
interaction_quality: json
text_quality: json
capability_impact: json
```

### 8.2 质量问题详情

逻辑对象：

```text
social_network_quality_issue
```

设计字段：

```text
dataset_id: string
dataset_version: string
issue_id: string
issue_code: string
dimension: user | relationship | tweet | interaction | text | file
severity: blocking | error | warning | info
count: integer
message: text
affected_capability: string?
source_file_id: string?
source_row_numbers: json?
created_at: datetime
```

约束：

- 质量报告不直接修改原始或规范化数据；
- 质量问题必须可回溯逻辑文件名和行号；
- 不保存服务器绝对路径；
- 报告版本变化时结果缓存必须按 T07 规则处理；
- S02 读取质量报告，不由页面自行推断能力。

## 9. 来源追溯边界

每条规范化用户、关系和推文至少保留：

```text
dataset_id
dataset_version
source_file_id
source_row_number
normalization_version
```

关系聚合列表额外保留：

```text
source_array_index
```

推文文本分析额外回溯：

```text
tweet_id
text_processing_version
tokenizer_version
tfidf_version
```

网络指标额外回溯：

```text
metric_version
pagerank_version
scope
calculated_at
```

## 10. 用户上传会话边界

### 10.1 会话对象

逻辑对象：

```text
social_network_upload_session
```

首版只定义会话模型，不建立永久表。

字段：

```text
session_id: string
dataset_id: string
dataset_type: upload_session
created_at: datetime
last_accessed_at: datetime
idle_expires_at: datetime
hard_expires_at: datetime
status: active | expired | closed | failed
file_manifest: json
quality_summary: json?
```

### 10.2 临时空间

上传文件可以在系统临时目录创建会话级临时空间：

```text
temporary upload/session/{session_id}/
```

约束：

- 不属于项目长期数据目录；
- 不写入 `all_csv/` 或 `csv/`；
- 不写入预测数据库；
- 会话结束、过期或失败后清理；
- 清理失败只记录内部诊断，不改变数据集授权边界；
- 不把临时路径返回给前端。

### 10.3 生命周期

采用 T07 固定规则：

```text
idle TTL: 2 小时
hard TTL: 24 小时
```

会话过期后：

- 上传原始文件不可恢复；
- 规范化临时结果不可恢复；
- 缓存条目失效；
- 导出链接不恢复会话；
- 重新上传创建新会话。

## 11. 结果和缓存边界

T09 不创建永久分析结果表。

分析结果按 T07 规则进入结果缓存：

- 缓存键带数据集和全部适用版本；
- 缓存值带质量和能力状态；
- 缓存失效后重新计算；
- 缓存失败不改变原始数据；
- 缓存不作为数据源；
- 网络、互动和文本结果不写入用户/关系/推文基础表。

后续 T15 再确定缓存介质和清理实现。

## 12. 与预测数据库的隔离验收

必须满足：

- 社交网络使用独立 SQLite 文件；
- 预测数据库不新增社交网络表；
- 社交网络 Repository 不依赖预测 Repository；
- 不共享预测实体；
- 不共享预测缓存键；
- 不共享预测业务事务；
- 预测模块升级不修改社交网络表；
- 社交网络上传失败不影响预测页面；
- 社交网络清理任务不删除预测数据。

## 13. 删除和清理规则

### 13.1 内置数据

内置数据不由普通用户请求删除。

版本更新时：

- 新版本建立新的版本边界；
- 旧版本可标记过期；
- 清理前必须确认无活动分析任务；
- 清理不影响原始归档文件；
- 导出文件按自己的 TTL 清理。

### 13.2 上传数据

以下事件触发清理：

- 用户主动结束会话；
- idle TTL 到期；
- hard TTL 到期；
- 上传解析失败；
- 应用明确执行会话清理任务。

清理对象：

- 临时原始文件；
- 会话级规范化记录；
- 会话级质量报告；
- 会话级缓存；
- 未完成导出临时文件。

## 14. T09 验收清单

- [x] 独立社交网络 SQLite 文件边界；
- [x] 不触碰 SMTPD 预测表；
- [x] 内置数据和上传会话区分；
- [x] 数据集元信息边界；
- [x] 文件清单边界；
- [x] 用户数据边界；
- [x] 关系数据边界；
- [x] 推文数据边界；
- [x] 质量报告摘要和详情边界；
- [x] 原始文件和规范化结果追溯；
- [x] 文件名和行号追溯；
- [x] `following_list` 数组下标追溯；
- [x] 版本字段追溯；
- [x] 上传临时会话；
- [x] 上传数据不永久保存；
- [x] 上传缓存 TTL；
- [x] 内置数据版本隔离；
- [x] 分析结果不反写基础表；
- [x] 删除和清理规则；
- [x] 未创建 SQLite 表；
- [x] 未执行 T10 或 T11。

## 15. T09 后续依赖

T09 完成后具备依赖条件的任务包括：

```text
T10 F01 数据集注册与来源管理
T11 F02 文件读取与格式适配
T12 F03 数据规范化与关联
```

按当前工作流，下一次只允许执行：

```text
T10 F01 数据集注册与来源管理
```

在用户验收 T09 前：

- 不执行 T10；
- 不执行 T11；
- 不执行 T12；
- 不创建 SQLite 表；
- 不实现 Repository；
- 不实现文件读取器；
- 不实现接口；
- 不提交 Git。
