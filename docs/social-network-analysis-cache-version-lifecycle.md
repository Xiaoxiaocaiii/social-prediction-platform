# 社交网络分析缓存与版本生命周期

本文记录当前工作区快照、Clique/Louvain 结果缓存及上传会话的实际生命周期。

## 工作区快照

- 内置数据集快照在服务进程内按数据集 ID 和版本复用；图分析输入另按数据指纹校验。
- 上传数据的规范化工作区快照只保存在当前服务进程内，并以会话 ID 隔离。
- 上传会话关闭或过期时，数据集注册、工作区快照和该会话的 Clique/Louvain 内存缓存一并移除。
- 服务重启后，上传会话、上传快照和内存缓存不可恢复。内置 Clique/Louvain 文件缓存可在重启后读取，但必须通过数据指纹、缓存键及完整性校验。

## Clique/Louvain 结果缓存

- `SocialNetworkAnalysisResultCache` 只缓存 Clique 和 Louvain 分析结果，不是所有网络、互动和文本 API 共用的通用缓存。
- 每种分析类型及每个会话槽位只保留一个最新结果；上传会话以 `sessionId` 隔离。
- 缓存键包含结果类型及当前分析上下文。分析工作流另校验数据集 ID、版本、输入指纹和算法参数，不匹配时不得复用旧结果。
- 上传会话的结果只在内存中；关闭或过期后清除，不写入持久化结果文件。
- 内置数据的完整 Clique 结果及完整、收敛的 Louvain 结果可持久化。文件位置与校验规则见[存储边界](social-network-analysis-storage-boundary.md)。

## 上传会话生命周期

| 规则 | 当前值 |
| --- | --- |
| 闲置时长 | 2 小时 |
| 最长会话时长 | 24 小时 |
| 过期检查间隔 | 默认 60 秒 |
| 终态上传任务保留 | 24 小时 |
| 任务过期标记保留 | 24 小时 |
| 孤立上传临时目录保留 | 24 小时后清理 |

每次有效访问会刷新闲置过期时间，但不延长 24 小时最长时限。会话关闭或过期后，旧请求返回 `410 SN_DATASET_SESSION_EXPIRED`；数据集、版本和会话不匹配返回上下文错误。上传会话不会因为任务记录或本机内置分析 artifact 的保留而延长。

## 版本与结果身份

- 数据集版本标识当前数据集版本；内置数据为 `v1`，上传数据的版本与唯一会话关联。
- 规范化、关系投影、算法和质量规则的版本参与相关分析结果身份；语义变化时应同步检查对应版本常量、缓存键和验证测试。
- Clique/Louvain 结果身份必须匹配数据集、版本、会话（若为上传数据）、输入指纹及分析参数。
- PageRank、文本处理和导出元信息中的版本号由相应实现生成；它们不意味着这些 API 全部共用同一种缓存。

当前字段和版本常量见[网络指标与 PageRank 契约](social-network-analysis-network-metrics-pagerank-contract.md)、[文本处理规则](social-network-analysis-text-processing-rules.md)、[Louvain 质量契约](social-network-louvain-quality-contract.md)及[Clique/Louvain 输入证据契约](social-network-clique-louvain-input-evidence-contract.md)。
