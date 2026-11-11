# 社交网络分析当前实现与功能约定

本文描述仓库当前提供的社交网络分析能力及其用户可见边界。详细字段、算法、接口与存储规则分别以本文关联的当前契约为准；本文不记录阶段任务、旧版状态或验收过程。

## 产品范围

平台包含两个相互隔离的工作台：

- **流行度预测**：基于 SMTPD 历史视频数据检索相似案例，并提供 30 天基线趋势。
- **社交网络分析**：分析用户资料、关注关系和推文样本，提供数据质量、网络结构、关系查询、节点排行、互动、主题、社群、团体及跨社群连接候选分析。

社交网络分析支持内置数据集 `think-tank-social-network/v1` 和用户上传数据。模块不调用预测接口，不向预测模块写入数据。

## 数据输入与会话

- 上传文件须由用户明确指定 `users`、`relationships` 或 `tweets` 角色；支持 CSV、JSONL、UTF-8 及 UTF-8 BOM。
- 单次最多 10 个文件，单文件不超过 128 MiB，单次请求不超过 160 MiB。上传任务先校验、规范化并生成质量预览；用户确认后才激活数据集。
- 同一角色可上传多个文件。输入字段、合并、去重、质量状态与来源行号遵守[字段契约](social-network-analysis-data-field-contract.md)和[规范化规则](social-network-analysis-normalization-quality-rules.md)。
- 上传数据只属于当前分析会话，不写入长期数据集或分析结果文件。会话闲置 2 小时或达到 24 小时最长存续时间后失效；关闭会话或服务重启后，旧会话不可继续使用。
- 上传数据可用于网络图、Louvain、Clique、社群/团体证据、跨社群连接候选、互动分析和相应导出。各模块必须使用同一数据集版本及会话上下文，不得混用内置数据与上传数据。

## 网络与社群

- 关注关系按 `source_user_id -> target_user_id` 表示。计算使用通过规范化校验的输入；自环、无效关系和重复关系按对应分析契约处理。
- 外部账号可以作为关系对象参与网络计算，但没有用户资料的账号不会成为跨社群连接候选。
- 全局关系图、Louvain 社群和跨社群连接候选使用完整规范化网络计算。画布最多返回 1,000 个节点和 15,000 条关系；筛选范围、完整计算范围与画布实际展示范围必须区分。
- Louvain 为账号划分不重叠的社群；Clique 表示可能重叠的紧密账号团体，两者用途不同。
- 跨社群连接候选须有账号资料且已归属社群；自环、同社群关系及连接未归类账号的关系不计入跨社群关系。候选按连接社群数、不同关系对象数、有向关系数降序，再按账号 ID 升序排列。
- 关系、社群和团体结果描述当前输入数据中的结构，不证明现实组织、关系亲密度、影响力、传播路径或因果关系。

## 分析、证据与导出

- 关系证据保留方向、来源文件、来源行号及适用的数组索引；证据接口与算法汇总接口的字段范围以各自契约为准。
- 图内定位等待画布布局完成；如果账号被筛选或展示上限排除，页面应说明其不在当前画布中。
- JSON、CSV 和可读报告由相关分析页面导出。Clique 导出当前团列表页，摘要最多展示 20 项候选；附加证据时按当前已加载的证据页导出。
- 导出读取当前数据集、版本、会话和分析结果身份。上下文失效、身份变化、尚无结果、零结果或超出导出上限时须返回明确状态，不生成误导性报告。
- 用户上传内容仅在会话期间可用；会话关闭、过期或服务重启后，关联的分析请求和导出必须明确返回会话失效。

## 资源与运行边界

- 禁止依据整机可用内存设置分析准入门槛；低于 2 GiB 不得成为拒绝分析或跳过测试的理由。
- Clique/Louvain Worker 的 Job Object 内存、CPU、进程数、运行时间和并发限制是独立的子进程运行约束，不代表整机可用内存门槛。
- 项目面向桌面 Microsoft Edge。真实数据目录是本机数据资产，不随源码仓库交付；`test-data/upload-demo/` 是合成上传样例，不代表真实数据或研究结论。

## 当前契约

- [数据字段契约](social-network-analysis-data-field-contract.md)：上传与内置数据字段、格式、角色及空值。
- [数据来源与授权边界](social-network-analysis-data-source-authorization.md)：来源说明、隐私、原文展示和导出边界。
- [规范化与质量规则](social-network-analysis-normalization-quality-rules.md)：用户、关系、推文处理及质量状态。
- [API 与路由契约](social-network-analysis-api-route-contract.md)：当前路由、请求上下文、响应及错误语义。
- [缓存与版本生命周期](social-network-analysis-cache-version-lifecycle.md)：内置结果和上传会话缓存身份及失效规则。
- [存储边界](social-network-analysis-storage-boundary.md)：数据、临时文件、会话和分析结果隔离。
- [网络指标与 PageRank 契约](social-network-analysis-network-metrics-pagerank-contract.md)：指标范围、公式、参数和解释。
- [文本处理规则](social-network-analysis-text-processing-rules.md)：互动文本、关键词、TF-IDF 与主题结果口径。
- [Louvain 参数与质量契约](social-network-louvain-quality-contract.md)：当前实现参数、结果状态与分区质量。
- [Clique/Louvain 输入与证据契约](social-network-clique-louvain-input-evidence-contract.md)：图投影、指纹、团体结果及来源证据。
- [Worker 资源约束](social-network-worker-resource-contract.md)：独立 Worker 的进程级限制和失败处理。
- [上传 CSV 模板](social-network-upload-template.md)：最小可用文件示例。

项目交付范围和本机操作见[README](../README.md)与[验收与演示说明](验收与演示说明.md)。
