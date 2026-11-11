# 社交网络分析 Worker 资源约束

本文定义 Clique 与 Louvain 隔离 Worker 的当前进程级限制、结果上限和失败处理。整机可用内存不是分析准入条件。

## 进程隔离

- Clique 与 Louvain 在独立 Java Worker 进程中运行，不在 Spring 请求线程中执行算法。
- Windows Worker 使用 Job Object 管理进程及其资源。进程须先以挂起状态创建、加入 Job 并验证限制，再恢复执行。
- Worker 任务共享单任务门闩，同时只允许一个分析任务运行；资源冲突时返回 `BUSY`，不排队。
- Job 配置或验证失败时不得启动无约束的替代进程。取消、超时、服务关闭或执行失败时终止 Worker，并清理任务输入和未提交结果。

## 固定上限

| 资源 | 当前上限 |
| --- | --- |
| Job 提交内存 | 256 MiB |
| Worker 单进程提交内存 | 256 MiB |
| CPU hard cap | 配额单位 2,500 / 10,000 |
| Job 活动进程数 | 1 |
| Clique 与 Louvain 并发 | 合计 1 个任务 |
| Worker 墙钟时间 | 最长 30 秒 |
| Clique 枚举数 | 最多 100,000 个 |
| Clique Worker 请求 | 最多 16 MiB |
| Clique Worker 结果文件 | 最多 16 MiB |
| 用户可见分析导出 | 最多 2 MiB |

这些值是独立 Worker 的进程/任务约束，不读取整机空闲内存，不代表整机可用内存最低要求。Worker 堆大小也不等于操作系统对进程树实施的 Job 限制。

## 结果完整性

- Worker 结果必须验证协议版本、输入身份、参数、文件大小和结构后才能发布。
- Clique 成员明细达到保留字节上限时，可以继续枚举并保留汇总；成员列表完整性须单独标明。
- 超时、取消、枚举上限或 Worker 失败必须使结果体现不完整或失败，不得将部分结果标记为完整。
- 用户可见导出超过 2 MiB 时整体拒绝，不生成截断文件或永久下载资源。
- Louvain 执行状态、算法收敛状态和结果完整性分别表达，不以其中一个状态代替其他状态。

## 禁止的准入规则

- 不得因整机可用内存低于 2 GiB 而拒绝分析、延后构建/测试或跳过测试。
- 不得将某次机器内存读数写成最低运行要求。
- Job Object 内存限制失败应报告为 Worker 资源配置或执行失败，不能伪装成整机内存准入结论。

## 实现依据

- `WindowsJobLimits`：Job 和进程内存、CPU 配额、活动进程上限。
- `WindowsCliqueEnumerationRunner`、`WindowsLouvainAnalysisRunner`：Worker 启动与时间限制。
- `CliqueEnumerationWorkerProtocol`：Clique Worker 请求、结果协议及 artifact 上限。
- `SocialNetworkGroupExportService`、`SocialNetworkController`：用户可见导出上限。
