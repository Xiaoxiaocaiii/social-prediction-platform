# 社交网络分析当前 API 路由

本文列出控制器中实际注册的社交网络 API 路由。请求参数、数据范围和响应字段以对应接口实现为准。

## 通用约定

- API 前缀为 `/api/social-network`。
- 成功和错误响应使用 `success`、`code`、`message`、`requestId`、`data`、`warnings`、`meta` 包络。
- 内置数据集 ID 为 `think-tank-social-network`，版本为 `v1`。用户 ID、推文 ID 均按字符串处理。
- 上传会话请求必须使用该路由声明支持的 `datasetVersion`、`sessionId` 参数；会话关闭、过期或服务重启后，旧会话数据不可恢复。
- 通用分析接口与 Clique/Louvain、证据、跨社群候选接口的参数并不完全相同。不要把某个接口的参数或错误码推定为所有接口的统一契约。

## 数据集与上传

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `GET` | `/datasets` | 获取当前工作区数据集摘要 |
| `GET` | `/datasets/{datasetId}` | 获取数据集、质量和能力摘要 |
| `GET` | `/datasets/{datasetId}/capabilities` | 获取机器可读能力状态 |
| `GET` | `/datasets/{datasetId}/quality/summary` | 获取质量摘要 |
| `POST` | `/datasets/upload` | 接收 multipart 文件及 manifest，创建异步预览任务 |
| `GET` | `/datasets/upload-tasks/{uploadTaskId}` | 查询上传任务和预览状态 |
| `DELETE` | `/datasets/upload-tasks/{uploadTaskId}` | 取消可取消的上传任务 |
| `DELETE` | `/datasets/{datasetId}/sessions/{sessionId}` | 关闭上传分析会话 |

上传由独立 Servlet 处理，不受 Spring MVC 全局 multipart 大小配置替代。当前上限为最多 10 个文件、单文件 128 MiB、单请求 160 MiB。格式、角色、字段和质量要求见[数据字段契约](social-network-analysis-data-field-contract.md)及[规范化规则](social-network-analysis-normalization-quality-rules.md)。

## 网络分析

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `GET` | `/datasets/{datasetId}/network/summary` | 网络摘要和结构指标 |
| `GET` | `/datasets/{datasetId}/network/graph` | 有节点/关系展示上限的关系图 |
| `GET` | `/datasets/{datasetId}/network/local` | 指定中心账号的局部关系图 |
| `GET` | `/datasets/{datasetId}/network/structure-roles` | k-core、介数和跨社群证据状态 |
| `GET` | `/datasets/{datasetId}/network/evidence/nodes` | 节点来源证据 |
| `GET` | `/datasets/{datasetId}/network/evidence/summary` | 内置数据全网事实摘要 |
| `GET` | `/datasets/{datasetId}/rankings` | 入度、出度或 PageRank 排行 |

普通用户资料、关系、互动、文本路由：

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `GET` | `/datasets/{datasetId}/users/search` | 搜索用户 |
| `GET` | `/datasets/{datasetId}/users/{userId}` | 获取用户详情 |
| `GET` | `/datasets/{datasetId}/interactions/summary` | 互动指标摘要 |
| `GET` | `/datasets/{datasetId}/interactions/tweets` | 按互动指标获取推文排行 |
| `GET` | `/datasets/{datasetId}/interactions/users` | 按互动指标获取用户排行 |
| `GET` | `/datasets/{datasetId}/interactions/timeseries` | 按日期汇总互动指标 |
| `GET` | `/datasets/{datasetId}/text/summary` | 文本样本摘要 |
| `GET` | `/datasets/{datasetId}/text/keywords` | 获取关键词 |
| `GET` | `/datasets/{datasetId}/text/tfidf` | 获取 TF-IDF 结果 |
| `GET` | `/datasets/{datasetId}/text/search` | 精确词元搜索 |
| `GET` | `/datasets/{datasetId}/text/topic` | 查询主题相关结果 |

## Clique、Louvain 与关系证据

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `POST` | `/datasets/{datasetId}/network/cliques` | 请求 Clique 分析；可用 `force` 请求重算 |
| `GET` | `/datasets/{datasetId}/network/cliques` | 查询 Clique 状态和当前结果页 |
| `GET` | `/datasets/{datasetId}/network/cliques/insights` | 获取团体分析摘要 |
| `GET` | `/datasets/{datasetId}/network/cliques/insights/shared` | 查询两个账号共同出现的团体 |
| `GET` | `/datasets/{datasetId}/network/cliques/{cliqueIndex}/evidence` | 查询团体成员或关系来源证据 |
| `POST` | `/datasets/{datasetId}/network/louvain` | 请求 Louvain 分析；可用 `force` 请求重算 |
| `GET` | `/datasets/{datasetId}/network/louvain` | 查询 Louvain 状态和当前结果 |
| `GET` | `/datasets/{datasetId}/network/louvain/insights` | 获取社群分析摘要 |
| `GET` | `/datasets/{datasetId}/network/louvain/communities/{communityId}/evidence` | 查询社群成员或关系来源证据 |
| `GET` | `/datasets/{datasetId}/network/louvain/communities/{first}/between/{second}/evidence` | 查询两个社群之间的关系证据 |
| `POST` | `/datasets/{datasetId}/network/exports` | 导出当前 Clique 或 Louvain 结果 |

这些分析、洞察和证据路由支持 `datasetVersion`、`sessionId` 上下文。分页参数以各控制器默认值及验证规则为准。

## 跨社群连接候选

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `GET` | `/datasets/{datasetId}/network/bridge-candidates` | 获取候选账号分页 |
| `GET` | `/datasets/{datasetId}/network/bridge-candidates/{accountId}/evidence` | 获取候选账号的来源关系证据 |

支持 `datasetVersion`、`sessionId`、`offset` 和 `pageSize`；证据路由还支持社群、方向和预期分析结果身份参数。候选资格和排序规则见[当前实现约定](social-network-analysis-current-implementation.md)。

## 集中式导出

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `POST` | `/datasets/{datasetId}/exports` | 同步生成网络、用户、推文、互动、主题或质量导出 |

集中式导出与 Clique/Louvain 的 `/network/exports` 是不同接口，二者的请求结构和导出范围不可混用。导出上限和报告语义见[当前实现约定](social-network-analysis-current-implementation.md)。

## 当前上下文失效语义

上传数据上下文与数据集 ID、版本及会话 ID 绑定。失配通常返回 `400` 上下文错误；会话关闭、过期或服务重启后返回 `410 SN_DATASET_SESSION_EXPIRED`。各控制器针对参数错误、结果未就绪和资源不可用使用不同错误码；以该路由实际响应为准。
