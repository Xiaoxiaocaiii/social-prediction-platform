# 社交网络分析当前存储边界

本文描述当前源码实际使用的数据位置和持久化范围。

## 输入数据

- 内置数据从数据集注册表指定的 CSV 文件读取，当前路径位于 `all_csv/merged_dataset/`。
- 预测模块的 SQLite 数据库由 `app.db-path` 配置，默认是项目目录下的 `data/smtpd.db`。社交网络分析不在该数据库中建业务表。
- 社交网络分析不使用名为 `social_network.sqlite` 的内置业务数据库。
- 上传文件需显式选择 `users`、`relationships` 或 `tweets` 角色；上传数据不会注册为跨服务重启可恢复的永久数据集。

## 上传数据的临时存储

- HTTP multipart 暂存目录默认位于 `${java.io.tmpdir}/social-network-analysis-http-uploads/`。
- 上传任务和当前会话文件暂存目录默认位于 `${java.io.tmpdir}/social-network-analysis-uploads/`。
- 规范化后的上传工作区快照、数据集注册和 Clique/Louvain 结果缓存保存在服务进程内存中。
- 会话空闲 2 小时或达到最长 24 小时后失效；主动关闭会话时，服务移除内存快照、分析缓存和会话暂存目录。
- 服务重启会丢失上传会话及其内存快照。孤立的受管临时目录由后台清理，默认每 60 秒检查，超过 24 小时的孤立目录可删除。
- 任务终态记录保留 24 小时；任务记录清理后，过期标记再保留 24 小时。该任务记录 TTL 不延长上传会话的有效期。

上传文件上限见[API 路由契约](social-network-analysis-api-route-contract.md)。清理失败会记录诊断并排队重试，不改变会话失效语义。

## 内置分析结果文件

Clique/Louvain 工作流可将符合完整性条件的内置数据结果持久化到独立 JSON artifact。默认目录为：

```text
%LOCALAPPDATA%\social-prediction-platform\analysis-results\
```

若环境变量 `LOCALAPPDATA` 不可用，则默认使用用户目录下的 `.social-prediction-platform/analysis-results/`。可用 `app.analysis-results-directory` 更改目录，可用 `app.analysis-results-enabled` 关闭持久化；当前默认启用。

当前 artifact 包括：

- `clique-v1.json`：完整内置 Clique 结果；
- `louvain-v1.json`：完整且收敛的内置 Louvain 结果；
- `group-evidence-v1.json`：内置图输入与来源证据数据；
- `louvain-community-pairs-v2.json`：内置社群对关系数据。

读取 artifact 时会校验 schema、数据集/版本、输入指纹、缓存键及结果完整性。不符合条件、无法读取或超过 32 MiB 的社群证据 artifact 会被忽略，并由工作流按当前能力重新计算或报告不可用。上传会话的分析结果不写入这些持久化 artifact。

用户通过浏览器下载的文件由客户端生成；服务端同步导出接口不创建长期下载文件或永久导出记录。

## 隔离边界

- 上传数据、会话快照和上传分析缓存不进入预测数据库或内置分析结果文件。
- 预测 SQLite 数据、社交网络源 CSV、上传临时文件和内置分析 artifact 是不同的数据边界。
- 数据来源、字段映射和用户可见解释分别见[来源与授权边界](social-network-analysis-data-source-authorization.md)、[数据字段契约](social-network-analysis-data-field-contract.md)及[当前实现约定](social-network-analysis-current-implementation.md)。
