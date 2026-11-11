# 社交网络分析缓存与版本生命周期

> 文档状态：T07 已完成并通过验收
> 适用范围：社交网络分析模块 B01-B07 的结果缓存、版本追溯和会话生命周期
> 更新时间：2026-10-04
> 前置文档：
>
> - [API 与路由契约](social-network-analysis-api-route-contract.md)
> - [规范化与数据质量规则](social-network-analysis-normalization-quality-rules.md)
> - [网络指标与 PageRank 参数契约](social-network-analysis-network-metrics-pagerank-contract.md)
> - [文本处理规则](social-network-analysis-text-processing-rules.md)
>
> 说明：本文档冻结缓存和版本规则，不实现缓存组件、数据库、接口或页面。

## 1. 生命周期目标

缓存系统必须保证：

1. 同一数据集、同一版本、同一口径和同一范围可以复用结果；
2. 数据、规则或参数变化不会命中旧结果；
3. 内置数据与用户上传会话严格隔离；
4. 上传数据缓存不承担永久保存职责；
5. 缓存失败时可以直接重新计算；
6. 导出文件可以追溯生成时使用的全部版本；
7. 缓存命中不改变结果解释和质量提示；
8. 缓存键不包含用户 ID 数值化结果，不丢失字符串精度；
9. 预测模块缓存不与社交网络缓存共用；
10. 缓存错误不阻塞仍可用的降级能力。

## 2. 版本契约

### 2.1 数据集版本

```text
dataset_version: dataset-v1.0
```

数据集版本标识当前数据内容和文件组成。

必须升级的情况：

- 内置数据文件内容变化；
- 文件数量或文件类型变化；
- 关系、用户或推文样本变化；
- 数据来源范围变化；
- 数据集初始规模基线变化；
- 数据集身份或来源说明发生实质变化。

数据集版本不因页面样式变化升级。

### 2.2 规范化版本

```text
normalization_version: normalization-v1.0
```

必须升级的情况：

- 字段映射变化；
- 用户主键优先级变化；
- 关系方向变化；
- 去重规则变化；
- 自环或外部节点处理变化；
- 空值和零值规则变化；
- 未归属来源关联规则变化；
- 时间解析规则变化。

### 2.3 指标版本

```text
metric_version: network-metrics-v2.0
pagerank_version: pagerank-v1.0
```

必须升级的情况：

- 入度、出度或密度公式变化；
- 外部节点纳入范围变化；
- 自环处理变化；
- PageRank 阻尼系数变化；
- PageRank 收敛阈值或迭代上限变化；
- 是否使用边权重或时间衰减变化；
- 排名和并列规则变化。

### 2.4 文本版本

```text
text_processing_version: text-processing-v1.0
stopwords_version: stopwords-v1.0
tokenizer_version: tokenizer-v1.0
tfidf_version: tfidf-v1.0
```

必须升级的情况：

- URL、提及、Hashtag 或标点规则变化；
- 语言识别规则变化；
- 中文词典或英文分词规则变化；
- 停用词表变化；
- TF-IDF 公式变化；
- 主题匹配规则变化；
- 词元过滤规则变化。

### 2.5 解释版本

```text
explanation_version: explanation-v1.0
```

必须升级的情况：

- 指标定义文案变化；
- 结果边界文案变化；
- 缺失值/零值文案变化；
- PageRank、密度、关键词或主题解释边界变化；
- 质量告警文案影响结果理解；
- 业务模块读取的指标字典版本变化。

仅页面排版、颜色或间距变化不升级解释版本。

## 3. 当前首版版本集合

```text
dataset_version: dataset-v1.0
normalization_version: normalization-v1.0
metric_version: network-metrics-v2.0
pagerank_version: pagerank-v1.0
text_processing_version: text-processing-v1.0
stopwords_version: stopwords-v1.0
tokenizer_version: tokenizer-v1.0
tfidf_version: tfidf-v1.0
explanation_version: explanation-v1.0
```

版本集合必须在 API `meta`、缓存元信息、质量报告和导出文件中保持一致。

## 4. 缓存范围

### 4.1 可缓存结果

允许缓存：

- 数据集元信息；
- S01 质量报告；
- S02 能力矩阵；
- 网络总览；
- 网络图局部结果；
- 入度、出度和 PageRank 排行；
- 用户详情；
- 推文列表和互动摘要；
- 主题关键词和 TF-IDF 结果；
- 导出任务的临时生成结果。

### 4.2 不缓存为永久业务数据

禁止把以下内容作为永久数据集保存：

- 用户上传原始文件；
- 用户上传原始推文原文；
- 用户上传关系文件；
- 当前分析会话的临时规范化结果；
- 未经用户主动下载的导出文件；
- 错误堆栈和内部路径。

### 4.3 缓存不是数据源

缓存只保存可重新生成的分析结果或临时中间结果：

- 缓存命中必须仍返回版本和口径元信息；
- 缓存失效后可以重新读取原始数据；
- 缓存损坏不应导致数据集不可用；
- 缓存不改变原始层保留规则；
- 缓存不替代 S01/S02 权威输出。

## 5. 缓存键

### 5.1 基础组成

每个缓存键必须包含：

```text
cache_namespace
dataset_id
dataset_type
dataset_version
session_id（仅上传数据）
normalization_version
metric_version（适用时）
pagerank_version（适用时）
text_processing_version（适用时）
stopwords_version（适用时）
tokenizer_version（适用时）
tfidf_version（适用时）
explanation_version
result_type
canonical_scope
canonical_parameters
```

### 5.2 命名空间

固定使用：

```text
social-network
```

示例结果类型：

```text
dataset-metadata
quality-report
capability-matrix
network-summary
network-graph
network-ranking
user-detail
tweet-list
interaction-summary
topic-analysis
export
```

预测模块必须使用独立命名空间，不得共享社交网络键。

### 5.3 规范化范围

`canonical_scope` 必须：

- 使用稳定字段顺序；
- 删除未参与计算的空可选字段；
- 保留字符串 ID原值；
- 统一日期和时间格式；
- 统一枚举大小写；
- 明确 `includeExternalNodes`；
- 明确时间、语言、用户、主题和分页范围；
- 不包含随机数、请求时间或浏览器临时字段。

### 5.4 键生成

逻辑表示：

```text
cache_material = namespace + "|" + canonical_json(context)
cache_key = sha256(cache_material)
```

缓存键只用于定位，不作为用户可见业务 ID。

### 5.5 示例

```json
{
  "cacheNamespace": "social-network",
  "datasetId": "intel-social-network",
  "datasetType": "builtin",
  "datasetVersion": "dataset-v1.0",
  "normalizationVersion": "normalization-v1.0",
  "metricVersion": "network-metrics-v2.0",
  "pagerankVersion": "pagerank-v1.0",
  "explanationVersion": "explanation-v1.0",
  "resultType": "network-summary",
  "scope": {
    "includeExternalNodes": true,
    "from": null,
    "to": null
  },
  "parameters": {}
}
```

## 6. 结果元信息

每个缓存结果必须保存：

```text
cacheKey
resultType
createdAt
lastAccessedAt
expiresAt
datasetId
datasetType
datasetVersion
sessionId?
normalizationVersion
metricVersion?
pagerankVersion?
textProcessingVersion?
explanationVersion
scope
sampleCounts
qualityStatus
capabilityStatus
calculatedAt
```

缓存命中响应必须把这些信息映射回 API `meta`。

## 7. 内置数据生命周期

### 7.1 内置数据标识

```text
datasetType: builtin
datasetId: intel-social-network
datasetVersion: dataset-v1.0
```

### 7.2 生命周期

- 应用启动时可以加载元信息；
- 首次请求时可以按结果类型计算；
- 同一版本和同一口径可以复用；
- 内置版本可在应用生命周期内持续使用；
- 新版本发布时，不覆盖旧版本结果；
- 新版本完成校验后成为默认版本；
- 旧版本可进入过期清理流程；
- 原始 `all_csv/` 和 `csv/` 不被缓存写入覆盖。

### 7.3 内置缓存失效

以下情况必须失效：

- 数据集版本变化；
- 规范化版本变化；
- 指标或 PageRank 版本变化；
- 文本或停用词版本变化；
- 解释版本变化；
- 缓存结果结构变化；
- 质量报告发现阻断性问题；
- 缓存校验失败。

## 8. 用户上传会话生命周期

### 8.1 会话标识

上传数据必须绑定：

```text
sessionId
datasetId
datasetType: upload_session
datasetVersion
```

同一个文件内容在不同会话中也不得默认共享缓存。

### 8.2 生命周期

首版建议固定：

```text
idle TTL: 2 小时
hard TTL: 24 小时
```

含义：

- 2 小时无请求后，会话缓存可清理；
- 创建后 24 小时达到硬上限，必须失效；
- 用户主动结束会话时立即失效；
- 浏览器关闭不保证服务端立即清理，但不延长硬上限；
- 会话失效后不得恢复上传原始文件；
- 重新上传必须创建新 `sessionId`。

### 8.3 上传缓存隔离

- 只能由同一 `sessionId` 访问；
- 不进入内置数据集默认缓存；
- 不与其他上传会话共享；
- 不生成永久 dataset 记录；
- 导出文件不延长上传会话硬 TTL；
- 用户主动下载的文件由用户自行保存，平台不将其作为数据集归档。

## 9. 缓存命中和重新计算

### 9.1 命中条件

只有以下全部一致才可命中：

- `cache_namespace`；
- `dataset_id`；
- `dataset_type`；
- `dataset_version`；
- `session_id`（上传数据）；
- 所有适用处理版本；
- `result_type`；
- 规范化后的范围；
- 规范化后的计算参数。

### 9.2 版本优先

即使结果数值看起来相同，只要版本不同，也不得复用旧结果。

### 9.3 缓存未命中

缓存未命中不是业务错误：

1. 读取当前数据集；
2. 使用当前版本规则计算；
3. 生成结果和质量元信息；
4. 写入新的缓存；
5. 返回结果并标记 `cacheStatus=miss`。

### 9.4 缓存命中

缓存命中响应可以标记：

```text
cacheStatus: hit
```

但不得隐藏：

- 数据集版本；
- 统计范围；
- 样本量；
- 质量警告；
- 能力状态；
- 计算时间。

## 10. 缓存失败回退

### 10.1 读取失败

缓存读取失败时：

- 记录内部诊断信息；
- 不向用户暴露路径和堆栈；
- 直接重新计算；
- 成功计算后尝试写入新缓存；
- 即使写缓存失败，也可以返回计算结果；
- 响应增加 warning：

```text
缓存读取失败，已重新计算当前结果。
```

### 10.2 写入失败

缓存写入失败时：

- 不将业务请求标记为失败；
- 返回当前计算结果；
- warning 标记缓存不可用；
- 下次请求允许重新计算；
- 不回写原始文件；
- 不创建永久上传数据。

### 10.3 计算失败

计算失败时：

- 不写入成功结果；
- 返回结构化业务错误；
- 保留可用的其他指标；
- 不使用上一个版本结果冒充当前版本；
- 不把旧缓存降级成当前版本结果；
- 允许用户重试。

### 10.4 错误缓存

首版不缓存可见业务错误作为长期结果：

- 不缓存未收敛 PageRank；
- 不缓存文本处理失败；
- 不缓存数据集不可用；
- 不缓存版本冲突；
- 可记录短期内部诊断，但不能作为正常命中结果返回。

## 11. 失效事件

缓存必须在以下事件发生时失效：

| 事件 | 失效范围 |
| --- | --- |
| 数据集版本升级 | 该数据集全部分析结果 |
| 规范化规则升级 | 依赖规范化的全部结果 |
| 网络指标升级 | 网络、排行和用户网络结果 |
| 文本规则升级 | 关键词、TF-IDF 和主题结果 |
| 解释版本升级 | 需要新解释文案的结果 |
| 数据集文件变化但版本未变 | 阻止命中，要求先修订版本 |
| 上传会话过期 | 该 session 全部缓存 |
| 用户主动清理会话 | 该 session 全部缓存 |
| 缓存校验失败 | 当前缓存条目 |
| 结果结构变更 | 对应 resultType 全部条目 |

数据文件发生变化但 `dataset_version` 没有变化时，不允许继续使用旧缓存；应先生成新的数据集版本。

## 12. 导出生命周期

### 12.1 导出元信息

导出文件必须携带：

```text
exportId
datasetId
datasetType
datasetVersion
sessionId?
normalizationVersion
metricVersion?
pagerankVersion?
textProcessingVersion?
explanationVersion
scope
sampleCounts
createdAt
exportedAt
sourceDescription
boundaryNotes
```

### 12.2 导出临时文件

建议固定：

```text
export idle TTL: 24 小时
export hard TTL: 48 小时
```

导出文件过期后：

- 下载链接失效；
- 不影响数据集和分析缓存；
- 用户可以在当前会话重新生成；
- 不把导出文件作为永久数据集；
- 不通过导出文件恢复已过期上传会话。

### 12.3 原文导出

包含推文原文时必须附：

```text
原文来自当前采集推文样本，不代表账号完整历史推文。
```

并带来源、版本、范围和授权边界说明。

## 13. 缓存与质量状态

缓存命中不应掩盖当前质量状态：

- S01 质量报告版本必须进入缓存键；
- `qualityStatus` 必须进入结果元信息；
- `capabilityStatus` 必须进入结果元信息；
- 质量状态变化时，即使指标数值不变，也应重新生成或失效；
- `limited` 结果可以缓存；
- `disabled` 结果可以缓存状态说明，但不能缓存虚构数据；
- `unavailable` 不作为正常分析结果缓存。

## 14. 并发和一致性

同一缓存键同时计算时：

- 可以使用单飞/锁机制避免重复计算；
- 锁等待超时后允许直接重新计算；
- 不返回其他版本的结果；
- 写入必须使用完整结果替换；
- 禁止写入半成品作为可命中缓存；
- 结果元信息和数据必须原子一致。

## 15. 版本变更示例

### 15.1 PageRank 参数变化

旧：

```text
pagerank-v1.0
damping_factor=0.85
```

新：

```text
pagerank-v1.1
damping_factor=0.90
```

结果不得复用旧 PageRank 缓存。

### 15.2 停用词变化

旧：

```text
stopwords-v1.0
```

新：

```text
stopwords-v1.1
```

关键词、TF-IDF 和主题结果必须重新计算，网络结构结果无需因此失效。

### 15.3 数据集文件变化

即使新增推文数量很少，也必须：

1. 生成新的 `dataset_version`；
2. 重新生成受影响的质量报告；
3. 重新计算依赖该数据集的结果；
4. 不继续命中旧版本缓存。

## 16. T07 验收清单

- [x] `dataset_version`；
- [x] `normalization_version`；
- [x] `metric_version`；
- [x] `pagerank_version`；
- [x] `text_processing_version`；
- [x] `stopwords_version`；
- [x] `tokenizer_version`；
- [x] `tfidf_version`；
- [x] `explanation_version`；
- [x] 缓存命名空间；
- [x] 缓存键组成；
- [x] 规范化范围和参数；
- [x] 内置数据生命周期；
- [x] 上传会话生命周期；
- [x] 上传缓存隔离；
- [x] 缓存失效事件；
- [x] 缓存命中和未命中；
- [x] 缓存读取/写入失败回退；
- [x] 错误结果不作为正常缓存；
- [x] 导出文件生命周期；
- [x] 导出版本追溯；
- [x] 质量状态与缓存一致性；
- [x] 并发写入边界；
- [x] 未实现缓存；
- [x] 未创建数据库；
- [x] 未执行 T08。

## 17. T07 后续依赖

T07 完成后具备依赖条件的任务包括：

```text
T15 F07 结果缓存与版本标识
T17 P1 数据基础闭环阶段门
T29 导出与结果追溯
T30 缓存命中和失效测试
```

按当前工作流，下一次只允许执行：

```text
T08 实施前契约评审
```

在用户验收 T07 前：

- 不执行 T08；
- 不执行 T15；
- 不执行 T17；
- 不执行 T29；
- 不执行 T30；
- 不实现缓存；
- 不创建数据库；
- 不提交 Git。
