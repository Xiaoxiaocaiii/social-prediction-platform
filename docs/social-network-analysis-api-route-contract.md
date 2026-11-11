# 社交网络分析 API 与路由契约

> 文档状态：T06 路由基线、E3-A 上传契约及 F3 同步导出契约已完成
> 适用范围：社交网络分析模块 B01-B07 的页面入口、接口边界和统一响应
> 更新时间：2026-10-17
> 前置文档：
>
> - [数据字段契约](social-network-analysis-data-field-contract.md)
> - [来源与授权元信息](social-network-analysis-data-source-authorization.md)
> - [规范化与数据质量规则](social-network-analysis-normalization-quality-rules.md)
> - [网络指标与 PageRank 参数契约](social-network-analysis-network-metrics-pagerank-contract.md)
> - [文本处理规则](social-network-analysis-text-processing-rules.md)
>
> 说明：本文保留 2026-10-17 的 T06 基础契约。2026-11-12 状态补充：全局图、Louvain/Clique、跨社群候选及上传会话分析已按[实施计划](../全局关系图与社群桥接分析实施计划.md)完成后续实现和验收。若下文仍有“暂停”“未接入”等早期状态文字，以本补充和当前源代码为准；具体实现不能只凭路由存在判定为已验收。

## 当前实现补充（2026-11-12）

- `GET /api/social-network/datasets/{datasetId}/network/graph` 已接入完整范围社群归属，并区分完整筛选范围与画布实际返回范围。
- `GET /api/social-network/datasets/{datasetId}/network/bridge-candidates` 返回分页跨社群连接候选；`GET /api/social-network/datasets/{datasetId}/network/bridge-candidates/{accountId}/evidence` 返回按社群和方向分页的来源关系证据。两者复用同一分析结果身份。
- `POST /api/social-network/datasets/{datasetId}/network/exports` 导出已完成的 Clique/Louvain 结果及用户选择的证据页，不启动新分析。
- 当前会话上传支持 Louvain/Clique、社群/团体证据、图筛选、跨社群候选及相应导出。上传分析需绑定数据集版本与会话；会话关闭、过期或服务重启后旧身份失效。
- 桥接候选是可复核的结构筛查结果，不属于 `/network/structure-roles` 的 k-core/介数排行，不代表现实影响力、传播中介或因果关系。

## 1. 路由边界

### 1.1 工作台入口

社交网络分析工作台入口固定为：

```text
#/social-network
```

该 Hash 只表示进入社交网络分析模块，不用于模块内 B01-B07 的滚动定位。

### 1.2 模块内导航

B01-B07 使用：

- JavaScript 平滑滚动；
- 当前模块状态；
- 侧边导航激活状态；
- 页面内筛选上下文。

禁止：

- 使用 `#b01`、`#b02` 等 section Hash；
- 通过 URL Hash 保存筛选条件；
- 通过刷新 URL 恢复复杂分析状态；
- 让社交网络模块导航改变流行度预测路由。

### 1.3 预测模块隔离

社交网络分析不得复用：

```text
/api/predict
```

预测模块继续使用自身页面、状态和接口契约。社交网络 API 必须全部位于：

```text
/api/social-network/
```

不得将社交网络字段追加到预测响应，也不得让预测接口返回用户、关系、推文或主题数据。

## 2. API 命名空间

统一前缀：

```text
/api/social-network/
```

接口命名使用资源名，不使用页面视觉名称。

固定资源分组：

```text
datasets
quality
capabilities
network
users
tweets
topics
exports
```

所有 ID 均按字符串传递：

```text
datasetId: string
userId: string
tweetId: string
requestId: string
```

禁止将用户 ID 或推文 ID 转成数值。

## 3. 通用请求约定

### 3.1 Headers

浏览器请求建议包含：

```text
Accept: application/json
Content-Type: application/json
X-Request-Id: <client-generated-id>
```

文件上传使用：

```text
Content-Type: multipart/form-data
```

服务器可以生成 `requestId`，但必须在响应中返回。

### 3.2 数据集上下文

除数据集列表接口外，所有分析请求必须明确：

```text
datasetId
datasetVersion
```

可以使用 Query 参数：

```text
?datasetId=...
&datasetVersion=...
```

也可以使用路径参数：

```text
/datasets/{datasetId}/...
```

同一个接口不得同时使用两套不同值。若路径和 Query 冲突，返回参数冲突错误。

对 `type=upload_session` 的数据集，所有质量、能力和分析请求还必须携带：

```text
sessionId
```

使用 Query 参数 `sessionId`。服务端必须验证 `datasetId`、`datasetVersion`、`sessionId` 属于同一个未过期会话。内置数据集不携带 `sessionId`。工作台当前数据集由当前标签页管理；激活上传数据集只是前端上下文切换，不修改服务端全局当前数据集指针。

### 3.3 分析范围

分析请求可携带：

```text
scope
from
to
userId
language
topic
includeExternalNodes
```

范围必须原样进入响应 `meta.scope`。

## 4. 统一响应包络

### 4.1 成功响应

```json
{
  "success": true,
  "code": "OK",
  "message": "请求成功",
  "requestId": "req-20261004-000001",
  "data": {},
  "warnings": [],
  "meta": {
    "datasetId": "intel-social-network",
    "datasetVersion": "dataset-v1.0",
    "normalizationVersion": "normalization-v1.0",
    "metricVersion": "network-metrics-v2.0",
    "textProcessingVersion": "text-processing-v1.0",
    "explanationVersion": "explanation-v1.0",
    "scope": {},
    "calculatedAt": "2026-10-04T12:00:00Z"
  }
}
```

### 4.2 必需字段

成功响应必须包含：

```text
success
code
message
requestId
data
warnings
meta
```

### 4.3 `warnings`

警告对象：

```json
{
  "code": "QUALITY_EXTERNAL_NODES",
  "message": "当前关系中包含外部节点",
  "severity": "warning",
  "count": 37,
  "affectedScope": "network"
}
```

警告不得包含：

- 本地绝对路径；
- Java 堆栈；
- 数据库连接信息；
- 文件系统临时目录；
- 未脱敏的服务器内部异常。

### 4.4 `meta`

分析结果至少附带：

```text
datasetId
datasetVersion
normalizationVersion
metricVersion（网络结果）
textProcessingVersion（文本结果）
explanationVersion
scope
calculatedAt
```

分页接口还必须附带：

```json
{
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 626,
    "hasNext": true
  }
}
```

## 5. 错误响应包络

```json
{
  "success": false,
  "code": "SN_DATASET_NOT_FOUND",
  "message": "未找到指定数据集",
  "requestId": "req-20261004-000001",
  "data": null,
  "warnings": [],
  "meta": {
    "datasetId": "unknown",
    "datasetVersion": null,
    "scope": {}
  }
}
```

错误响应禁止返回：

- `stackTrace`；
- `exceptionClass`；
- `filePath`；
- `sql`；
- `databaseUrl`；
- 原始服务器目录；
- 预测模块内部异常。

## 6. 错误编号

### 6.1 通用错误

| 错误码 | 含义 | HTTP 建议 |
| --- | --- | --- |
| `SN_BAD_REQUEST` | 请求参数格式错误 | 400 |
| `SN_MISSING_PARAMETER` | 缺少必填参数 | 400 |
| `SN_INVALID_ID` | ID 为空或格式非法 | 400 |
| `SN_SCOPE_CONFLICT` | 数据范围参数冲突 | 400 |
| `SN_UNSUPPORTED_FORMAT` | 文件格式不支持 | 400 |
| `SN_REQUEST_TIMEOUT` | 请求超时 | 408/504 |
| `SN_INTERNAL_ERROR` | 未分类服务错误 | 500 |
| `SN_NOT_IMPLEMENTED` | 能力尚未实现 | 501 |
| `SN_UPLOAD_MANIFEST_INVALID` | 上传 manifest 缺失、字段非法或包含重复 partId | 400 |
| `SN_UPLOAD_PART_MISMATCH` | manifest 与实际 multipart 文件部分不一致 | 400 |
| `SN_UPLOAD_FILE_BLOCKED` | 文件内容或必需字段存在阻断问题 | 422 |
| `SN_UPLOAD_TASK_NOT_FOUND` | 上传任务不存在 | 404 |
| `SN_UPLOAD_TASK_STATE_CONFLICT` | 当前上传任务状态不允许该操作 | 409 |
| `SN_UPLOAD_TASK_EXPIRED` | 上传任务已过期 | 410 |
| `SN_EXPORT_EMPTY_RESULT` | 当前筛选条件没有可导出结果 | 422 |
| `SN_EXPORT_TOO_LARGE` | 同步导出超过单次响应上限 | 413 |
| `SN_GROUP_EXPORT_NOT_READY` | Clique/Louvain 分析仍在运行，当前不能导出 | 409 |
| `SN_GROUP_EXPORT_UNAVAILABLE` | 组分析或证据在导出期间发生变化 | 503 |

### 6.2 数据集错误

| 错误码 | 含义 |
| --- | --- |
| `SN_DATASET_NOT_FOUND` | 数据集不存在 |
| `SN_DATASET_VERSION_NOT_FOUND` | 数据集版本不存在 |
| `SN_DATASET_UNAVAILABLE` | 数据集不可用 |
| `SN_DATASET_UPLOAD_FAILED` | 上传失败 |
| `SN_DATASET_SESSION_EXPIRED` | 上传分析会话已过期 |
| `SN_DATASET_SOURCE_UNVERIFIED` | 用户上传来源未核验 |
| `SN_DATASET_CONTEXT_MISMATCH` | datasetId、datasetVersion 与 sessionId 不属于同一会话 |

### 6.3 能力和质量错误

| 错误码 | 含义 |
| --- | --- |
| `SN_CAPABILITY_UNAVAILABLE` | 当前能力不可用 |
| `SN_CAPABILITY_LIMITED` | 当前能力降级可用 |
| `SN_QUALITY_BLOCKING` | 存在阻断性质量问题 |
| `SN_NO_VALID_RELATION` | 没有有效关系 |
| `SN_NO_VALID_TWEET` | 没有有效推文 |
| `SN_NO_VALID_TEXT` | 没有有效文本 |
| `SN_BOOKMARK_DISABLED` | 书签暂无有效数据 |

### 6.4 网络错误

| 错误码 | 含义 |
| --- | --- |
| `SN_NETWORK_CALCULATION_FAILED` | 网络计算失败 |
| `SN_PAGERANK_NOT_CONVERGED` | PageRank 未在限制内收敛 |
| `SN_NETWORK_SCOPE_TOO_LARGE` | 网络计算范围过大 |
| `SN_GRAPH_RENDER_LIMIT` | 网络图超过渲染上限 |
| `SN_USER_NOT_IN_NETWORK` | 用户不在当前网络范围 |

### 6.5 文本错误

| 错误码 | 含义 |
| --- | --- |
| `SN_TEXT_PROCESSING_FAILED` | 文本处理失败 |
| `SN_TEXT_QUERY_EMPTY` | 主题输入为空 |
| `SN_TEXT_QUERY_STOPWORD` | 主题输入全部为停用词 |
| `SN_TOPIC_NO_MATCH` | 当前样本没有匹配结果 |
| `SN_TOPIC_SAMPLE_LIMITED` | 主题有效样本不足 |

## 7. 数据集接口组

### 7.1 获取数据集列表

```text
GET /api/social-network/datasets
```

Query：

```text
status?
type?
page?
pageSize?
```

`data`：

```json
{
  "items": [
    {
      "datasetId": "intel-social-network",
      "name": "智库社交网络数据",
      "type": "builtin",
      "version": "dataset-v1.0",
      "status": "available_with_warnings",
      "userCount": 626,
      "relationshipCount": 12963,
      "tweetCount": 237840,
      "timeRange": {
        "from": "2016-05-03",
        "to": "2026-01-21"
      }
    }
  ]
}
```

### 7.2 获取数据集详情

```text
GET /api/social-network/datasets/{datasetId}
```

`data` 必须包含：

```text
datasetId
name
type
version
status
sourceDescription
timeRange
files
counts
qualitySummary
capabilitySummary
```

### 7.3 上传数据集

```text
POST /api/social-network/datasets/upload
```

请求使用 `multipart/form-data`。`manifest` 是 JSON 部分；其他部分的字段名必须与 manifest 中对应的 `partId` 完全一致。单次最多 10 个文件，同一角色允许多个文件，角色由用户显式填写。

```json
{
  "files": [
    { "partId": "file-1", "role": "users" },
    { "partId": "file-2", "role": "relationships" },
    { "partId": "file-3", "role": "tweets" }
  ],
  "sourceDescription": ""
}
```

`files` 必须包含 1-10 个唯一 `partId`；角色仅允许 `users`、`relationships`、`tweets`。实际文件部分必须与 manifest 一一对应，不允许额外或缺失文件。`sourceDescription` 可省略或为空，空值记录为“用户未提供”。上传前展示来源与责任提示即可继续，不要求强制勾选。客户端文件名仅作来源追溯，不用于推断数据角色或来源。

格式、编码、必需字段和行级质量要求遵循既有字段及规范化契约。单文件上限为 `128MiB`、单次请求上限为 `160MiB`；已在项目本机单并发运行模式通过边界请求和代表性推文样本验证。并发容量及峰值资源仍归 E5 评估。上传 POST 使用独立 multipart 解析边界，不能提高其他 Spring MVC 路由（包括 `/api/predict`）的全局 `10MB`/`12MB` 限制。

文件字节由 HTTP 请求同步接收；接收成功后异步读取、校验、规范化并生成质量预览。创建上传任务不创建可激活分析会话，也不切换当前数据集。

创建任务返回 HTTP `202` 和统一成功包络：

```json
{
  "success": true,
  "code": "SN_UPLOAD_ACCEPTED",
  "message": "上传已接收，正在检查数据",
  "requestId": "req-20261017-000001",
  "data": {
    "uploadTaskId": "upload-task-id",
    "status": "queued",
    "statusUrl": "/api/social-network/datasets/upload-tasks/upload-task-id"
  },
  "warnings": [],
  "meta": {
    "datasetId": null,
    "datasetVersion": null,
    "scope": {}
  }
}
```

### 7.4 上传任务状态与取消

查询任务：

```text
GET /api/social-network/datasets/upload-tasks/{uploadTaskId}
```

状态固定为：

```text
queued
processing
ready
failed
cancelling
cancelled
expired
```

`GET` 成功查询任务时返回 HTTP `200` 和统一成功包络。`queued`/`processing`/`cancelling` 返回当前 `status` 和安全的 `phase`；`failed`/`cancelled`/`expired` 返回终态及结构化原因。任务本身失败不代表状态查询 HTTP 请求失败。

`phase` 仅允许 `validating_files`、`reading_files`、`merging_records`、`normalizing`、`building_preview`、`registering_session`；不返回文件系统路径或实现内部状态。任务失败时 `data.error` 为：

```json
{
  "code": "SN_UPLOAD_FILE_BLOCKED",
  "message": "所选文件存在阻断性问题",
  "issues": [
    {
      "partId": "file-1",
      "fileName": "users.csv",
      "issueCode": "REQUIRED_FIELD_MISSING",
      "sourceRowNumbers": []
    }
  ]
}
```

`issues[]` 只返回结构化问题、用户提供的文件名和行号，不回显原始行、绝对路径或堆栈。任务进入 `failed`、`cancelled` 或 `expired` 终态后，任务详情保留 24 小时；`queued`、`processing`、`cancelling` 等活动任务不因该 TTL 清理。ready 会话先按会话 TTL 转为 `expired`，再开始任务详情保留期。详情清理后保留 24 小时轻量过期标记，期间查询或取消返回 `410 SN_UPLOAD_TASK_EXPIRED`；标记过期后，未知任务返回 `404 SN_UPLOAD_TASK_NOT_FOUND`。

`ready` 响应的 `data.preview` 必须包含：

```text
sessionId
datasetId
datasetVersion
type = upload_session
expiresAt
qualitySummary
capabilitySummary
files[]
```

`files[]` 按文件返回 `partId`、用户提供的文件名、角色、格式、读取行数、有效/跳过/错误计数和质量状态。预览只返回摘要，不回显完整原始行或推文正文。质量详情通过第 8 节分页接口读取。只有质量预览可生成且存在可用数据时，才建立 `sessionId` 并进入 `ready`；全无可用数据或任一文件级阻断时进入 `failed`，不得产生可激活数据集。

取消任务：

```text
DELETE /api/social-network/datasets/upload-tasks/{uploadTaskId}
```

对 `queued` 或 `processing` 任务接受取消请求，返回 HTTP `202` 和当前任务状态 `cancelling`；重复取消 `cancelling` 返回 `202`，重复取消 `cancelled` 返回 `200`。客户端继续查询直至 `cancelled`。对 `ready`、`failed` 或 `expired` 任务取消返回 `409 SN_UPLOAD_TASK_STATE_CONFLICT`；`ready` 会话使用下面的会话关闭接口结束。取消或失败不得改变当前工作台数据集或内置数据。

不存在的任务返回 `404 SN_UPLOAD_TASK_NOT_FOUND`。任务和会话过期时，文件、预览及任务清理遵循会话生命周期及阶段 E5 安全清理要求。

结束上传分析会话：

```text
DELETE /api/social-network/datasets/{datasetId}/sessions/{sessionId}?datasetVersion=...
```

关闭成功返回 HTTP `200` 和 `data.status=closed`；重复关闭保持幂等。后续使用已关闭会话访问质量、能力或分析接口时返回 `410 SN_DATASET_SESSION_EXPIRED`。工作台切回内置数据时调用该接口；关闭失败不阻止当前标签页切回内置数据，未清理资源仍由 E5 生命周期清理兜底。

质量、能力和分析接口路径见第 8、9 节及后续模块章节。上传数据集请求均须带 `datasetVersion` 和 `sessionId`。工作台收到 `ready` 预览后，由用户确认是否在当前标签页激活；服务端不提供写入全局当前数据集指针的激活操作。首版上传数据只属于当前分析会话，不代表永久保存。

## 8. 质量接口组

### 8.1 获取质量摘要

```text
GET /api/social-network/datasets/{datasetId}/quality/summary
```

`data`：

```text
datasetStatus
summary
userQuality
relationshipQuality
tweetQuality
interactionQuality
textQuality
```

### 8.2 获取质量详情

```text
GET /api/social-network/datasets/{datasetId}/quality/details
```

Query：

```text
dimension?
severity?
page?
pageSize?
```

详情项：

```text
issueCode
dimension
severity
count
message
affectedCapability
sourceFile
sourceRowNumbers
```

路径和行号只允许作为结构化证据返回，禁止暴露本地绝对路径。

## 9. 能力接口组

### 9.1 获取能力矩阵

```text
GET /api/social-network/datasets/{datasetId}/capabilities
```

`data.items[]`：

```text
capabilityId
moduleId
status
reasonCode
reasonMessage
requiredInputs
availableInputs
fallback
```

能力状态固定为：

```text
available
limited
disabled
unavailable
checking
```

### 9.2 业务页面约束

B01-B07 页面必须读取能力接口结果，不得在页面自行判断：

- 文件是否存在；
- 数据是否足够；
- 书签是否有效；
- PageRank 是否可用；
- 文本样本是否足够。

## 10. 网络接口组

### 10.1 网络总览

```text
GET /api/social-network/datasets/{datasetId}/network/summary
```

Query：

```text
datasetVersion
includeExternalNodes?
scope?
```

`data`：

```text
nodeCount
edgeCount
profileUserCount
externalNodeCount
isolatedNodeCount
weaklyConnectedComponentCount
largestWeaklyConnectedComponentNodeCount
largestWeaklyConnectedComponentRatio
averageClusteringCoefficient
reciprocityRate
largestWeaklyConnectedComponentAveragePathLength
inDegreeSummary
outDegreeSummary
density
metricVersion
pagerankVersion
pageRankSummary
qualityFlags
```

结构指标均基于完整规范化网络范围，而非关系图接口的展示截断范围。不可用的比例/平均值以 `null` 返回；空网络的弱连通分量数为 `0`。`metricVersion` 当前为 `network-metrics-v2.0`，PageRank 口径版本为 `pagerank-v1.0`。

### 10.2 网络图

```text
GET /api/social-network/datasets/{datasetId}/network/graph
```

Query：

```text
includeExternalNodes=true|false（可选，默认 true）
maxNodes（可选，默认 100，服务端有效范围 1-1000）
maxEdges（可选，默认 500，服务端有效范围 1-15000）
```

`maxNodes` 和 `maxEdges` 超出有效范围时由服务端截断到边界值。全局图接口不接受 `centerUserId`、`depth`、`topK` 或 `scope`；局部网络由用户局部网络接口提供。

响应字段按“筛选后的完整网络范围”和“本次返回子图”分开计数：

| 字段 | 口径 |
| --- | --- |
| `graphScope` | `full_normalized_network`（包含外部节点）或 `profile_nodes_only`（排除外部节点及其关联边） |
| `scope` | 对应范围的用户可读说明；范围在应用节点/边上限之前确定 |
| `totalNodeCount` / `totalEdgeCount` | `graphScope` 范围内、应用 `maxNodes`/`maxEdges` 前的完整节点数和有向关系数 |
| `returnedNodeCount` / `returnedEdgeCount` | `nodes[]` / `edges[]` 本次实际返回的数量 |
| `nodes[]` / `edges[]` | 画布本次收到的子图；返回边的两端都必须存在于返回节点中 |
| `truncated` | 节点数超过 `maxNodes`，或返回节点之间的关系数超过 `maxEdges` 时为 `true` |
| `truncatedBy[]` | 导致截断的上限，可包含 `maxNodes`、`maxEdges` |
| `truncationReason` | 说明本次返回量和相应候选量；未截断时为 `null` |
| `limits` | 服务端实际采用的节点和关系上限 |
| `globalCommunities[]` | 完整筛选范围内按既有 Louvain 分区汇总的社群 |
| `globalCommunityCount` / `globalUnassignedNodeCount` | 完整筛选范围内的社群数和未归类节点数 |
| `displayedCommunities[]` | 仅按返回节点和返回边汇总的画布社群 |
| `displayedCommunityCount` / `displayedUnassignedNodeCount` | 本次画布子图的社群数和未归类节点数 |
| `communityAssignmentScope` | 固定为 `full_normalized_network`，说明社群归属由完整规范化关系网络计算；关闭外部节点时只过滤统计成员和关系，不重新运行 Louvain |

社群摘要项包含：

```text
id
nodeCount
profileNodeCount
externalNodeCount
internalDirectedEdgeCount
```

节点字段：

```text
id
nodeType
inDegree
outDegree
pageRank
isolated
username?（资料用户）
displayName?（资料用户）
communityId?（已归类节点）
```

边字段：

```text
source
target
relationType
```

为兼容现有客户端，`nodeCount`、`edgeCount`、`communities`、`communityCount` 和 `unassignedNodeCount` 暂保留为旧字段，分别等于 `returnedNodeCount`、`returnedEdgeCount`、`displayedCommunities`、`displayedCommunityCount` 和 `displayedUnassignedNodeCount`。新客户端应使用口径明确的新字段。

例：在默认上限下，完整筛选范围可能为 626 个节点、12,963 条关系，而本次只返回 100 个节点及其范围内最多 500 条关系。此时 `totalNodeCount`/`totalEdgeCount` 仍描述完整筛选范围，`returnedNodeCount`/`returnedEdgeCount` 与画布社群字段只描述本次子图；不得用返回数组长度替代完整范围统计。

### 10.3 重要节点排行

```text
GET /api/social-network/datasets/{datasetId}/network/rankings
```

Query：

```text
metric=in_degree|out_degree|pagerank
page?
pageSize?
includeExternalNodes?
scope?
```

结果项：

```text
rank
nodeId
displayName?
value
sampleSize
nodeType
profileStatus
metricVersion
```

### 10.3.1 B04 结构角色排行

```text
GET /api/social-network/datasets/{datasetId}/network/structure-roles
```

Query：

```text
limit=1..100 (default 20)
```

`data` 返回完整规范化网络范围的 `nodeCount`、无向简单投影后的 `undirectedTieCount`、`algorithmVersion` 和 `scope`，并分别提供 `kCore`、`betweenness` 两个排行块及 `crossCommunityEvidence` 状态块。分页或 `limit` 只限制返回行，不改变计算范围；两类排行均包含资料用户和符合 C1 范围的外部节点，节点项用 `nodeType` / `status` 标识。

排行块字段：

```text
label
state
items[]
reason
```

节点项字段：

```text
rank
nodeId
displayName?
username?
nodeType
status
value
rawValue?          # 仅介数排行提供
reason
```

`kCore.items[].value` 是 core number；`betweenness.items[].value` 是 D1 冻结公式计算的归一化介数，`rawValue` 是按无序节点对计数的原始介数。两个指标分别降序排序，不合成为综合分。`n < 3` 时介数块返回 `state=unavailable`、空 `items` 和原因说明；`n >= 3` 且没有可用关系时，介数值按数学定义保留为 0 并正常返回排行。

`crossCommunityEvidence` 是该结构角色接口的兼容状态块，不承载桥接候选排行。跨社群候选由独立的 `/network/bridge-candidates` 接口提供，不能将其与 k-core 或介数排行合并。

### 10.3.2 全网事实摘要

```text
GET /api/social-network/datasets/{datasetId}/network/evidence/summary
```

仅支持内置数据集，不接受排行 `limit` 或其他统计参数。上传数据集请求返回 HTTP 400、`SN_EVIDENCE_SUMMARY_BUILTIN_ONLY`；上传会话仍须提供与数据集 ID、版本匹配的上下文。

`data` 返回 `analysisSnapshot`、总状态、`facts[]`、`networkInterpretations[]` 和 `cannotConclude[]`。事实项分别保留状态/原因、可复核文字、数值字段及来源引用；节点级证据使用完整网络快照，不依赖排行或图展示的截断。摘要不合成综合分、不生成因果结论，也不推断地区或机构类别。

摘要缓存键包含数据集 ID/类型/版本、规范化与网络/PageRank 算法版本、结构角色算法版本、完整规范化网络范围及摘要算法版本。当前仅缓存内置数据；上传临时会话不进入本摘要链路，因此不会在会话关闭后保留上传摘要。

### 10.4 用户局部网络

```text
GET /api/social-network/datasets/{datasetId}/users/{userId}/network
```

Query：

```text
depth=1|2
topK?
direction=in|out|both
includeExternalNodes?
```

用户 ID必须按字符串路径编码。

## 11. 用户接口组

### 11.1 用户搜索

```text
GET /api/social-network/datasets/{datasetId}/users
```

Query：

```text
q?
userId?
profileStatus?
page?
pageSize?
sortBy?
sortOrder?
```

### 11.2 用户详情

```text
GET /api/social-network/datasets/{datasetId}/users/{userId}
```

`data` 分区：

```text
identity
profile
rawFields
networkMetrics
tweetSummary
interactionSummary
topicSummary
profileStatus
```

必须区分：

- 原始账户字段；
- 计算网络指标；
- 当前采集推文样本；
- 当前筛选互动结果；
- 分析性说明。

### 11.3 用户推文摘要

```text
GET /api/social-network/datasets/{datasetId}/users/{userId}/tweets
```

Query：

```text
from?
to?
language?
sortBy?
page?
pageSize?
```

## 12. 推文接口组

### 12.1 推文列表

```text
GET /api/social-network/datasets/{datasetId}/tweets
```

Query：

```text
q?
userId?
sourceStatus?
language?
from?
to?
minLikes?
minReplies?
minReposts?
sortBy?
sortOrder?
page?
pageSize?
```

### 12.2 推文详情

```text
GET /api/social-network/datasets/{datasetId}/tweets/{tweetId}
```

`data`：

```text
tweetId
author
textOriginal
publishedAt
interactions
matchedTerms?
sourceStatus
dedupStatus
```

推文原文必须带：

```text
当前采集推文样本，不代表账号完整历史推文。
```

### 12.3 互动摘要

```text
GET /api/social-network/datasets/{datasetId}/tweets/interactions/summary
```

Query：

```text
userId?
from?
to?
language?
topic?
```

点赞、评论、转发必须独立返回：

```text
total
average
median
validSampleCount
missingCount
zeroCount
```

## 13. 主题接口组

### 13.1 主题分析

```text
POST /api/social-network/datasets/{datasetId}/topics/analyze
```

请求：

```json
{
  "query": "人工智能",
  "language": "auto",
  "from": null,
  "to": null,
  "includeUnassignedSource": false,
  "page": 1,
  "pageSize": 20
}
```

响应 `data`：

```text
query
normalizedQuery
matchedTerms
matchMethod
matchedTweetCount
matchedUserCount
keywordSummary
tfidfSummary
relatedTweets
relatedUsers
networkLinkage
```

### 13.2 主题结果约束

- 关键词和 TF-IDF 规则由 T05 决定；
- 结果必须返回 `tweetId`；
- 未归属来源推文可以进入相关推文；
- 未归属来源推文不生成相关用户；
- 主题共现不生成关系边；
- 网络联动只读取真实关注关系；
- 页面不得把结果称为传播路径、意见领袖或社会影响力。

## 14. 导出接口组

### 14.1 同步生成导出结果

```text
POST /api/social-network/datasets/{datasetId}/exports
```

请求：

```json
{
  "exportType": "network|users|tweets|interactions|topic|quality",
  "format": "csv|json|print",
  "query": {},
  "scope": {}
}
```

支持类型为 `network`、`users`、`tweets`、`interactions`、`topic`、`quality`。`quality` 是既有集中式导出类型；当前提供模块内入口的类型为 `network`、`users`、`interactions`、`topic`、`quality`。全局关系图已交付，但此集中式导出不导出画布节点/边；Clique/Louvain 结果及跨社群候选分别使用其专用导出能力。`tweets` 保留为集中式旧类型，输出全部去重且纳入分析的推文样本。

可识别的查询字段：

| 类型 | `query` 字段 | 范围 |
| --- | --- | --- |
| `network` | 无 | B02 完整规范化网络摘要及互动规模，不输出关系图节点/边 |
| `users` | `metric=indegree|outdegree|pagerank` | 当前指标 Top 20；同时包含 B04 k-core 与介数排行、全体指标分布、跨指标对照及 Top-20 并集节点证据。排行列表遵循各自返回上限，分布和对照保留完整范围及状态 |
| `interactions` | `metric=like_count|reply_count|repost_count` | 当前指标摘要、推文/用户 Top 10、按页面时间序列展示范围截取的最近 30 项 |
| `topic` | 必填 `term` | 当前精确词元查询；最多返回 30 条匹配推文、30 个用户及 30 条真实关注关系边，推文包含页面展示的原文 |
| `quality` | 无 | 当前数据集质量摘要、覆盖信息及结构化 warnings |
| `tweets` | 无 | 全部去重且纳入分析的原始推文字段；超过限制时整体拒绝 |

未列出的查询字段不改变导出范围。数据集版本及上传会话上下文按通用数据集上下文约定从请求 Query 校验。

模块导出只在 `metadata.query` 回显实际生效的条件：重要节点和互动指标使用实际选择值（省略时分别采用接口默认值），主题使用当前精确词元；网络、质量及旧版全量推文导出的无效筛选字段不会伪装成已应用条件。网络结果同时包含完整网络摘要、EA22 的完整 k-core 层级及节点证据、全体指标分布和 EA23 可追溯事实摘要；事实摘要仅支持内置数据集时，其他数据集会明确返回 `unavailable` 状态及原因。重要节点结果包含与当前快照一致的指标分布、跨指标对照和有限 Top-20 并集节点证据，不导出关系图 Canvas 的节点/边。

响应使用统一成功包络，HTTP `201`。`data` 字段：

```text
exportId
exportType
status = completed
statusLabel
expiresAt = null
downloadAvailable = false
format
contentType
contentLength
metadata
resultState = available|limited
result
```

该接口同步生成结果，不创建服务端任务、临时文件或后续下载资源；不提供导出任务查询/下载路由。`exportId` 仅用于本次响应追溯。前端需要文件时，使用本次响应在浏览器内生成 Blob 下载；`downloadAvailable=false` 表示服务端没有可复用的文件 URL，不妨碍本地下载。

JSON 文件的根对象为：

```json
{
  "exportType": "network",
  "metadata": {},
  "data": {}
}
```

CSV 使用 UTF-8 BOM 和 CRLF 行结束符，包含元数据记录及按 `recordType` 区分的结果行；嵌套字段使用点分列名，列表字段以 `|` 连接。文本单元格若去除前导空白后以 `=`, `+`, `-`, `@` 开头，会在原文本前加单引号以降低电子表格公式注入风险；数值类型保持数值格式。打印摘要包含相同元数据及结果字段，列表最多展开 20 项并注明省略数量。

`metadata` 必须包含：

```text
datasetId
datasetVersion
normalizationVersion
metricVersion
textProcessingVersion
explanationVersion
algorithmVersions
scope
sampleCounts
sourceDescription
exportedAt
boundaryNotes
```

`sampleCounts.resultCount` 与 `returnedCount` 表示实际纳入本次导出的记录数，不表示数据集总样本量。网络摘要和质量报告按一个摘要结果计数；用户排行和互动/主题结果按实际返回的各类列表项合计。`userCount`、`tweetCount`、`edgeCount` 是当前规范化快照的全量计数；模块自身的完整范围、返回数量和 `truncated` 字段以 `result` 内各结果块为准。达到页面列表上限时保留完整总数与本次返回数，并将 `resultState` 标记为 `limited`。

`algorithmVersions` 回显本次结果涉及的规范化、网络指标、PageRank、结构角色、文本处理和解释版本；网络内置数据结果另含事实摘要算法版本。单次最终导出内容上限为 2 MiB（2,097,152 字节），按 UTF-8 字节数计算。超过上限整体返回 HTTP `413` 和 `SN_EXPORT_TOO_LARGE`，不返回部分文件；集中式 `tweets` 还会在生成前检查 5,000 条上限和估算字节数。任何完整证据列表若使最终内容超限，均整体拒绝，不静默截断或扩大限额。无匹配主题或其他无可导出结果返回 HTTP `422`、`SN_EXPORT_EMPTY_RESULT`；无效格式、类型、指标或主题参数返回 HTTP `400`。主题查询词必填；无匹配不生成只有元数据的空文件。结构角色不可用时可保留空排行和状态说明，只要该模块仍有其他可导出结果。

### 14.2 页面入口边界

- 数据集入口保留集中式导出面板。
- 网络总览、重要节点、推文与互动、主题分析、数据质量模块提供各自的导出入口，并读取当前指标/查询词。
- 全局关系图已验收；通用 `network` 总览导出仍不包含画布节点/边。Clique/Louvain 和跨社群候选通过各自业务入口导出对应分析结果，不等同于导出当前完整画布。

### 14.3 Clique/Louvain 当前结果导出

```text
POST /api/social-network/datasets/{datasetId}/network/exports
```

该路由与第 14.1 节集中式导出接口分开，仅读取已保存/缓存的 Clique 或 Louvain 当前结果，不启动分析、不调用 Worker。请求：

内置数据可直接使用该路径。上传数据必须在 URL 查询参数中同时提供 `datasetVersion` 和 `sessionId`；导出身份、当前分析状态及可选成员/关系证据都必须属于同一上传会话。缺少或不匹配上下文返回 HTTP `400`、`SN_DATASET_CONTEXT_INVALID`，会话已关闭、过期或服务重启后失效返回 HTTP `410`、`SN_DATASET_SESSION_EXPIRED`。导出内容只在响应中返回，不创建长期文件。

```json
{
  "analysisType": "clique|louvain",
  "format": "json|csv|print",
  "offset": 0,
  "pageSize": 20,
  "evidence": {
    "type": "clique|louvain|between",
    "id": "目标 ID",
    "secondId": null,
    "section": "members|ties",
    "offset": 0,
    "pageSize": 20
  }
}
```

`evidence` 可省略；提供时只读取当前选中目标和当前成员/关系来源证据页，页大小为 1–100。Clique 导出当前团列表页并保留总体团数及完整性；Louvain 导出当前完整分区及收敛/Worker 状态。证据页单独记录可用状态、目标、offset、返回数、总数和是否还有后续页，不会自动拼接其他页。分析参数、数据集版本、输入文件指纹、缓存键、算法/投影契约版本和计算时间随导出保留。

响应为 HTTP `201` 的统一成功包络。`data.result` 是浏览器可直接保存的文件文本：JSON 格式返回完整 JSON 文件字符串，CSV 返回 UTF-8 BOM/CRLF CSV 字符串，`print` 返回 UTF-8 文本；`data.contentLength` 是该字符串 UTF-8 编码的准确字节数。`data.metadata` 同时提供结构化追溯信息，`downloadAvailable=false` 表示服务端未创建文件或下载 URL。

`print` 是面向阅读的中文分析报告，不再逐行输出 `metadata.*`、`analysis.*` 等机器字段路径。Clique 报告说明极大团口径、整体规模分布和本次导出的分页范围，并按团列出当前页账号 ID；Louvain 报告说明社群规模、孤立账号、内部/跨社群关系、模块度和各社群摘要。附加证据时按成员或关系来源分节展示；不附加时会明确注明。账号目前以数据 ID 展示。完整映射及机器可读字段仍由 JSON/CSV 提供。

最终文件超过 2 MiB 时整体返回 HTTP `413`、`SN_EXPORT_TOO_LARGE`，不返回部分文件；尚无结果返回 HTTP `422`、`SN_EXPORT_EMPTY_RESULT`；分析仍处于 `RUNNING`/`BUSY` 时返回 HTTP `409`、`SN_GROUP_EXPORT_NOT_READY`；参数非法返回 HTTP `400`。证据组装期间分析身份变化时返回 HTTP `503`、`SN_GROUP_EXPORT_UNAVAILABLE`。CSV 复用集中式导出的文本公式防护，数值不转义。

## 15. 分页、排序和筛选

### 15.1 分页

默认：

```text
page = 1
pageSize = 20
```

最大页面大小：

```text
pageSize <= 100
```

网络图不使用普通分页，使用 `maxNodes` 和 `maxEdges`。

### 15.2 排序

排序参数必须使用白名单：

```text
sortBy
sortOrder=asc|desc
```

非法排序字段返回：

```text
SN_BAD_REQUEST
```

### 15.3 ID 和文本查询

- ID 精确匹配；
- 文本查询按 T05 规范化规则处理；
- 不对用户 ID做数值转换；
- 不将 username 当作 userId；
- 不将显示名称当作唯一主键；
- 查询条件必须回显在 `meta.scope`。

## 16. HTTP 状态和业务状态

HTTP 状态表示请求层结果，业务 `code` 表示社交网络领域结果。

| HTTP | 使用场景 |
| --- | --- |
| 200 | 查询或会话关闭成功，包括带 warnings 的成功、上传任务状态查询成功 |
| 201 | 同步导出结果生成成功 |
| 202 | 上传任务接收成功、上传任务取消请求已接受 |
| 400 | 参数错误 |
| 404 | 数据集、用户、推文、导出或上传任务不存在 |
| 409 | 版本、范围或任务状态冲突 |
| 413 | 上传文件或导出结果超过限制 |
| 422 | 字段可读但无法进入对应分析 |
| 410 | 上传任务记录已清理，或上传分析会话已过期/关闭 |
| 500 | 未分类服务错误 |
| 501 | 当前能力未实现 |
| 503 | 数据集或分析服务暂不可用 |

部分可用数据不应使用 500：

- 使用 200；
- `success=true`；
- 返回 `warnings`；
- `meta` 中保留质量和能力状态；
- `data` 只返回当前可用结果。

## 17. 请求上下文和结果回溯

每次分析请求必须可回溯：

```text
requestId
datasetId
datasetVersion
normalizationVersion
metricVersion
textProcessingVersion
scope
calculatedAt
```

结果中的原始证据：

- 关系结果回溯 source/target 和关系来源；
- 用户结果回溯 userId 和资料状态；
- 推文结果回溯 tweetId、sourceFile、sourceRowNumber；
- 主题结果回溯 tweetId 和 matchedTerms；
- 质量问题回溯逻辑文件名和原始行号。

## 18. 前端与接口边界

前端负责：

- 发起请求；
- 保存当前数据集上下文；
- 展示 `data`；
- 展示 `warnings`；
- 根据 `capabilityStatus` 控制入口；
- 展示错误 `message`；
- 保留筛选上下文；
- 处理 loading、empty、error 和 limited。

前端不负责：

- 自行计算 PageRank；
- 自行判断数据集是否健康；
- 自行把空值变成 0；
- 自行推断外部节点；
- 自行生成主题扩展词；
- 自行改变指标口径；
- 自行合并点赞、评论、转发；
- 调用 `/api/predict` 获取社交数据。

后端负责：

- 读取和规范化数据；
- 统一读取 S01/S02 结果；
- 执行网络、互动和文本分析；
- 附带版本和统计口径；
- 保护内部错误信息；
- 返回结构化警告和错误。

## 19. T06 验收清单

- [x] 工作台入口 `#/social-network`；
- [x] 模块内导航不使用 section Hash；
- [x] `/api/social-network/` 命名空间；
- [x] 预测接口隔离；
- [x] 数据集接口组；
- [x] 质量接口组；
- [x] 能力接口组；
- [x] 网络接口组；
- [x] 用户接口组；
- [x] 推文接口组；
- [x] 主题接口组；
- [x] 导出接口组；
- [x] 统一成功响应；
- [x] 统一错误响应；
- [x] `requestId`；
- [x] 数据集、版本、范围和口径元信息；
- [x] 分页、筛选和排序；
- [x] 错误编号；
- [x] 用户可见错误边界；
- [x] 不暴露本地路径和堆栈；
- [x] 未实现 Controller、Service 或前端请求；
- [x] 未执行 T07、T10、T18、T19、T20、T21、T29。

## 20. T06 后续依赖

> 本节保留 T06 阶段的历史依赖记录；其“下一次只允许执行 T07”不表示当前工作区的下一任务。

T06 完成后具备依赖条件的任务包括：

```text
T07 缓存和版本生命周期
T10 F01 数据集注册与来源管理
T18 B01 数据集入口
T19 B02 网络总览
T20 B03 用户关注网络
T21 B04 重要节点分析
T29 导出与结果追溯
```

按当前工作流，下一次只允许执行：

```text
T07 缓存和版本生命周期
```

在用户验收 T06 前：

- 不执行 T07；
- 不执行 T10；
- 不执行 T18；
- 不执行 T19；
- 不执行 T20；
- 不执行 T21；
- 不执行 T29；
- 不实现 API；
- 不提交 Git。
