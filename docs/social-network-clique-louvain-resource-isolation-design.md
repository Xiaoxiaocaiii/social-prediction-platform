# Clique 与 Louvain 资源隔离设计

> **现行规则（2026-11-12）：不得根据整机可用物理内存拒绝分析或跳过测试，也不得将 2 GiB 设为启动/分析门槛。该规则已从源码中移除。Worker 自身的 Job Object 资源上限、超时和并发控制属于独立的进程运行限制，不读取整机剩余内存来决定是否准入。**

> 版本：1.0
> 状态：本文主体是按日期保留的资源隔离设计和历史验证记录。之后的 Clique/Louvain 工作流及上传会话链路已接入并通过阶段 E-10 用户验收；完整范围见[实施计划](../全局关系图与社群桥接分析实施计划.md)。历史服务端口、缓存和未完成状态不代表当前运行实例。
> 适用范围：Windows 桌面环境下 Clique/Louvain Worker 的设计约束和历史验证记录。
> 前置文档：[输入与证据契约](social-network-clique-louvain-input-evidence-contract.md)

## 1. 设计目标与限制

目标是在启动真实全网计算前，先把计算放入可终止、可计量的独立进程，并为时间、提交内存、CPU、并发和临时结果设置门禁。任何限制无法施加或核验时均拒绝启动。

本设计记录资源保护约束及其实现状态。操作系统硬限额可以约束 worker 进程树，但不保证电脑绝不卡顿、不保证算法在预算内完成，也不限制其他应用或 Spring 主进程消耗的资源；请以本文状态记录和任务清单的最新执行记录为准。

## 2. 建议的执行架构

### 2.1 一个轻量 worker 进程

- Spring 服务只负责参数校验、数据快照、启动监督、状态记录和结果验证；Clique/Louvain 不在 Spring 请求线程、普通工作区快照构建或现有 Web 服务 JVM 内执行。
- 由受信任的任务监督器启动一个轻量 Java worker，不启动完整 Spring Boot 应用。Windows 上应使用无控制台的 `javaw.exe` 或等效 detached 启动方式；CL03 实测 `java.exe` 会将 `conhost.exe` 纳入 Job 并占用活动进程数。不得用普通 `ProcessBuilder` 先运行算法、再尝试补加内存限制。
- worker 只接受明确的数据集 ID、版本、输入指纹、已允许的算法和固定参数；不接收任意类名、任意脚本或任意文件系统路径，不经 shell 拼接命令。
- 输入按 CL01 契约限定为用户资料和关注关系文件。启动前复制为本任务只读快照并计算摘要；worker 对该快照计算，避免计算中原始文件被修改造成指纹与结果不一致。临时目录使用随机任务 ID、当前用户可访问的 ACL，并位于仓库之外的系统临时目录。

### 2.2 先限额、后运行

推荐使用 Windows Job Object 管理 worker 进程及其所有后代：

1. 创建未公开、非跨用户共享的 Job Object，设置本任务所有限制。
2. 以 `CREATE_SUSPENDED` 创建 worker，使其尚未执行 Java 代码。
3. 将进程加入 Job Object；worker 不使用 `CREATE_BREAKAWAY_FROM_JOB`，Job 不启用允许 breakaway 的标志，以免子进程脱离进程树限额。只有配置成功、查询确认限制生效且进程归属正确后，才恢复主线程。
4. 任一设置、赋值、恢复、监测初始化失败时，终止尚未运行的进程，清理句柄和临时目录，并返回 `REJECTED_LIMIT_SETUP`；禁止降级成无限制子进程。
5. Job Object 设置 `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`。任务正常结束、取消、超时、主程序关闭或监督器异常时，监督器关闭 Job Object 并确认整个 job 中的进程已退出。

普通 `ProcessBuilder` 不提供“挂起创建后、运行前赋 Job 限制”的完整原子流程。因此，CL03 必须验证实际 Windows 启动实现；不能仅凭代码中出现 Job Object API 就判定限额有效。还须检查当前服务是否已属于外层 Job Object、系统版本的嵌套 Job 行为以及赋值失败路径。不能确认时拒绝启动。

## 3. 初始资源限制（历史方案）

以下仅记录本项目首轮历史方案。整机可用物理内存门槛已于 2026-11-08 废止，不能再作为启动、拒绝或跳过分析的依据。其他运行时限制按后续实际代码和验证状态判断。

| 维度 | 首轮限制 | 实施/解释 |
| --- | --- | --- |
| 墙钟时间 | 从调用进程创建 API 前启动单调时钟；worker 创建、JVM 启动、输入校验、计算、结果写出和进程退出总计不超过 30 秒 | 监督器使用单调时钟看门狗；超时调用 `TerminateJobObject`，不可只取消 Future 或中断 Java 线程 |
| Job 提交内存 | 256 MiB 总量 | 设置 Job Object 的 `JOB_OBJECT_LIMIT_JOB_MEMORY`；该值限制整个 job 可提交的虚拟内存，不是工作集/驻留物理内存保证 |
| 单进程提交内存 | 256 MiB | 可同时设置 `JOB_OBJECT_LIMIT_PROCESS_MEMORY` 作为 worker 单进程上限；Job 总上限仍是进程树总量上限 |
| CPU | Job CPU rate hard cap 为其可用父级配额的 25% | 设置 CPU rate control 的 `ENABLE | HARD_CAP` 与 2500/10000 rate；若没有受限父 Job，按整机总处理器时间的 25% 解释；若存在受限父 Job，则是父 Job 配额中的 25%。不等于零延迟或固定每核占比 |
| 活动进程数 | 最多 1 个 worker 进程 | Job 内不允许 worker 再派生进程；若实现确需子进程，先重新设计并单独批准，不绕开限制 |
| 算法并发 | Clique 与 Louvain 合计最多 1 个运行任务 | 进程级 `tryAcquire`；已占用时立即返回 `BUSY`，不排队、不抢占用户已有任务 |
| Clique 输出项 | 最多保留 100,000 个极大团 | 到上限即停止并标记不完整；具体停止检测与状态由 CL06 验收 |
| 结果文件字节 | 完整 Worker result artifact 最多 16 MiB | schema v2 为摘要预留 256 KiB，成员数组最多约 15.75 MiB。成员数组达到预算后只停止保存额外成员，不停止枚举或摘要计数；artifact 最终仍须整体不超过 16 MiB。用户可见 JSON/CSV/打印导出仍为 2 MiB |

Job Object 内存限额按提交内存而非物理驻留集计量。worker 可使用小于总 Job 上限的 JVM 堆上限，例如先以 `-Xmx96m` 作为调优起点；JVM 堆参数只是 worker 内部预算，不能替代 Job Object。Metaspace、线程栈、代码缓存、直接缓冲区、JVM 本身和子进程提交量都计入更高层的 Job 资源约束。具体 JVM 参数必须在 CL03 中以真实 worker 启动方式核验，不能用估算代替测量。

内存硬限达到时，新的提交可能失败；不能假定每次都会以相同错误退出。监督器须监控 Job 状态、worker 退出码和资源通知。发现内存限额或 worker 因分配失败退出，统一标记 `RESOURCE_LIMIT` 或更具体的资源失败状态，终止 job 并将结果标为不完整；不得把异常前输出当成完成结果。

## 4. 并发、取消与清理状态机

单任务生命周期建议采用以下状态：

```text
QUEUED (not used) -> STARTING -> RUNNING -> SUCCEEDED
                                  |-> TIME_LIMIT
                                  |-> RESOURCE_LIMIT
                                  |-> CANCELLED
                                  |-> FAILED
                                  |-> REJECTED_LIMIT_SETUP
```

不实际排队：请求到达时如果全局分析门闩已占用，直接返回 `BUSY`。获取门闩后，在 `try/finally` 中保证所有路径释放。门闩同时覆盖两种算法，不复用上传解析线程池；若未来变为多进程服务，进程内门闩不够，必须增加跨进程协调再开放并发。

- **成功**：worker 正常退出、退出码为 0、输出长度在 CL06 的 16 MiB Worker artifact 上限内、指纹/参数匹配且结果结构校验通过后，才将临时结果原子提升为本进程可读结果。成员列表截断与枚举中断分别标记；只有完整遍历且校验通过时，摘要才作为全量统计。
- **超时**：监督器先对整个 Job Object 发终止，等待所有进程退出，再标记 `TIME_LIMIT`；不得保留“完整”状态。
- **用户取消**：终止整个 Job Object，等待退出并标记 `CANCELLED`；不假称 Java 线程中断等于进程树已停止。
- **内存/CPU 资源异常**：记录限额或峰值证据，终止 job，标记 `RESOURCE_LIMIT` 或 `FAILED`，结果完整性为 `INCOMPLETE`。
- **worker 崩溃或协议不合法**：终止 job，丢弃未验证输出，记录受限 stderr/退出码，标记 `FAILED`。
- **Spring/监督器进程被关闭**：由 `KILL_ON_JOB_CLOSE` 兜底终止 worker；下一次启动时清理仅属于本功能且可识别的过期临时目录，不扫描或删除其他应用/用户文件。
- **所有结束路径**：等待进程确认退出、关闭进程/线程/Job 句柄、删除本任务输入副本及未提交结果、释放并发门闩。清理失败必须记录并阻止假报“清理完成”。

worker 的 stdout/stderr 必须限制缓冲大小，避免异常日志撑满监督器内存。算法结果不得直接通过不设上限的 pipe 或集合堆入 Spring 主 JVM；由 worker 写到隔离临时结果，再按完整 Worker artifact 16 MiB 上限校验。artifact 携带有界成员列表、团规模分布、团总数和节点参与度；成员列表截断与枚举中断使用独立状态。用户可见导出仍遵守 2 MiB 上限。

## 5. 失败即关闭

以下任一情况发生时不启动真实算法：

- Job Object 不可用、限额设定失败或验证结果不明确；
- worker 无法在运行前挂起并成功加入 Job；
- 当前进程所处的外层 Job 限制导致嵌套赋值不可行；
- 本地进程门闩忙；
- 输入指纹与 CL01 规范不符或运行输入快照不可用；
- result artifact schema 不兼容、摘要校验失败或最终 Worker artifact 超过 16 MiB；
- 任务启动/取消/超时后无法确认 worker 进程树退出。

拒绝启动优先于自动放宽资源阈值、退回无保护的 `ProcessBuilder`、在 Web JVM 内计算或自动清理用户应用。此次/该次拒绝应返回可读原因，不改变已有服务的启动、停止状态。

## 6. CL03 必测项目

CL03 仅使用小型受控 worker 和合成图，不运行真实全网 Clique/Louvain：

1. 证明 worker 在 `CREATE_SUSPENDED` 后先进入 Job，且未设置 Job 限制时无法恢复执行。
2. 读取/查询 Job 限制与峰值统计，确认 Job 和进程提交内存上限、CPU hard cap、活动进程数限制均生效；刻意申请超过内存限额时确认任务成为非完整结果。
3. 使用可控挂起程序验证墙钟超时会终止 Job 中所有进程；CL03 使用短时测试 deadline 验证终止路径，生产接入仍须将完整 30 秒预算从进程创建前计时；使用派生测试子进程验证 job process-tree 清理行为。
4. 验证用户取消、worker 崩溃、Spring/监督器关闭、Job 创建/赋值失败后，所有进程和句柄被回收、临时目录被清理、门闩可再获取。
5. 验证并发第二任务立即返回 `BUSY`，没有请求队列；输出超过 CL06 字节限制时明确标记不完整。
6. 验证输入只读快照和摘要绑定；原始文件变化不致于让运行中结果错配到旧指纹。

任何关键测试不通过时，CL03 不通过；不进行 CL07/CL11真实数据试运行。

## 7. 当前状态及未决项

- 历史记录：CL03 曾实现独立的 Windows Job Object 运行器、整机可用内存预检和单任务门闩。2026-11-08 已移除整机可用内存预检；该门槛不得恢复。Worker Job 限制和单任务门闩是另一类运行时控制。
- 历史记录：`mvn '-Dtest=WindowsJobProcessTest,AnalysisRunGateTest' -DforkCount=0 test` 曾在 Windows 11 上通过 9 项受控合成测试。测试当时包含整机可用内存跳过条件；该条件现已废止，不能据此决定是否运行测试。
- 短时 deadline 证明超时终止路径，不代表完整 30 秒端到端 deadline 已接入；生产接入必须在创建进程前启动单调时钟并以 30 秒总预算等待/终止。没有与运行中的 Spring 服务并行验证，也没有运行真实网络数据。结果文件字节上限仍待 CL06 确定，真实数据计算仍禁止。
- 初测发现控制台 `java.exe` 会附带 `conhost.exe`，导致 Job 内活动进程数为 2；改用无控制台 `javaw.exe` 后，单 worker 上限与取消清理测试通过。未来调用必须使用无控制台启动方式，不能放宽 Job 的活动进程上限。
- 结果 artifact 最大字节数留给 CL06 在实现/真实数据前单独冻结；在 CL06 前不允许启动真实图计算。
- 旧记录中的可用内存数值仅为当时观察结果，不应在当前运行决策中使用。
- 本文不保证整台机器绝对不卡顿；Worker CPU/进程限制和单任务控制可能降低子进程对资源的占用，但其他进程、驱动、磁盘和系统调度仍可能造成延迟。

## 8. CL06 worker 接口收口记录（2026-11-01）

- 新增 Clique worker 的 JSON 请求/结果协议：请求文件上限 16 MiB，完整结果 artifact（包含状态和元数据）上限 2 MiB；读取结果前按文件实际长度拒绝超限内容。worker 由 `WindowsCliqueEnumerationRunner` 通过既有 Job Object 进程启动器调用，代码中包含单任务门闩、`javaw.exe`、取消/截止时间检查、受限结果读取和临时目录清理。
- `mvn -DskipTests clean test-compile` 首次发现测试 worker 的直接缓冲区分配参数存在 `long` 到 `int` 编译错误；修正后 `mvn -DskipTests test-compile` 成功，所有当前主/测试源码完成编译。
- 历史记录：协议测试 4 项、极大团枚举器测试 8 项通过。Windows Runner 测试最初因当时存在的、现已废止的整机可用物理内存门槛而跳过；之后在当时约 2.36 GiB 可用内存的环境中重跑，5 项受控测试通过。该内存读数只是历史快照，不是运行或测试要求；现行测试不得按整机空闲内存数值跳过。
- `MEMORY_LIMIT` 使用 Worker Job 峰值用量接近配置上限的启发式判断；本次受控 64 MiB Job 内存测试通过，尚未覆盖其他 Worker 内存限制情形。Runner 已区分普通 classpath 和 Spring Boot 可执行 JAR，并为后者构造 `PropertiesLauncher` 启动参数。历史打包 Worker 测试曾受已废止的整机可用内存跳过条件影响。
- 未运行真实数据算法、未接入页面或 Spring、未启动/停止服务或部署。此段为当时状态；整机空闲内存预检已于 2026-11-08 废止，不是当前真实计算的前置条件。

## 9. 官方 API 参考

- [Job Objects overview](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)：Job 对象对一组关联进程实施管理、资源限制和整体终止。
- [JOBOBJECT_EXTENDED_LIMIT_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information)：`ProcessMemoryLimit` 和 `JobMemoryLimit` 约束进程/Job 可提交虚拟内存，并提供峰值用量。
- [JOBOBJECT_CPU_RATE_CONTROL_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_cpu_rate_control_information)：CPU hard cap 的 `CpuRate` 单位为每 10,000 个处理器周期中的配额；文档还说明嵌套 Job 下配额相对父 Job 解释。
- [AssignProcessToJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-assignprocesstojobobject)：将进程关联到 Job；关联后的内存操作受该 Job 限制，赋值前发生的内存分配不受其检查。
- [SetInformationJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-setinformationjobobject)：设置 Job 限制。
- [CreateProcessW](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw)：进程创建 API；实现须使用挂起创建流程，并在恢复执行前完成 Job 归属和限额验证。
- [CreateJobObject](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createjobobjecta)：Job 句柄关闭语义；实现需核实并启用 `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` 以清理仍关联的进程。

以上为 CL02 设计依据，不代表本项目已通过目标 Windows 环境验证。

## English Summary

CL02 recommends running graph algorithms in a lightweight Java worker, outside Spring request and snapshot computation paths, under a Windows Job Object. Create the worker suspended, apply and verify limits, assign it to the Job, and only then resume it. If any setup or verification fails, terminate the suspended worker and reject the run; do not fall back to an unrestricted process.

Historically configured Worker controls include a 30-second wall-clock deadline, 256 MiB Job committed-memory limit (optionally the same per-process limit), a 25% CPU hard cap relative to the available parent-job quota (or total system processor cycles when there is no capped parent job), one active worker, one combined Clique/Louvain task, and a Clique output limit. **The former system-wide available-physical-memory preflight is retired and must not be used.** The full Worker artifact is limited to 16 MiB; schema v2 reserves 256 KiB for summary data and caps retained memberships separately. User-facing exports remain limited to 2 MiB. `-Xmx` is only an inner JVM heap guard, not an OS-level process-memory cap.

CL03 added a Java/JNA Job Object launcher, a now-retired system memory preflight, a single-task gate and synthetic Windows tests. Nine controlled tests passed on Windows 11, covering Job memory/CPU limits, a shortened timeout, cancellation, active-process rejection, process-tree cleanup, Job-handle cleanup and concurrency refusal. The worker must use `javaw.exe` or an equivalent detached launch because console `java.exe` adds `conhost.exe` to the Job. A later change connected an explicit POST/GET Spring workflow; it reuses results in the current application process and does not start analysis from ordinary graph requests. Browser integration was incomplete at the time of this historical note; later product and upload flows are recorded through E-10. Analysis must not be blocked by the retired whole-machine free-memory check; Worker Job Object and result-protocol failures remain meaningful.

CL06 follow-up on 2026-11-01 added standalone Clique worker protocol code (16 MiB request cap, 2 MiB whole-result-artifact cap) and a Job-supervised runner. Four protocol tests and eight enumerator tests passed. The initial Runner test batches were skipped by a then-existing whole-machine memory safety margin; that skip rule is retired. The tests covered worker success/artifact read, cancellation, a short deadline, unexpected exit, the tested Job memory-limit mapping and launch from the executable archive. This validates only controlled synthetic scenarios; the peak-memory heuristic is not verified for every Worker memory-limit failure. A subsequent Spring change connected the worker to an explicit API workflow. CL07 later ran once on real data under the old result contract but stopped at the member-array byte limit; schema v2 and its current Runner validation remain pending as described in the dated amendment below. No service lifecycle was changed.

### 2026-11-01 Bounded-summary amendment

The first CL07 worker run stopped when the retained clique-member JSON array reached its byte budget. To preserve the agreed 2 MiB whole-artifact ceiling without treating that membership-storage budget as an enumeration limit, the worker result contract now uses schema v2. It reserves 256 KiB for aggregate counters and bounds retained clique memberships to about 1.75 MiB; after that membership budget is reached, enumeration continues while accumulating clique count, size distribution, and participation counts for every input node, including zero-participation nodes. `COMPLETED` describes enumeration status, while `membershipListComplete` independently describes whether every enumerated membership was retained. Any timeout, clique-count limit, cancellation, or resource failure still makes the summary partial. Nineteen focused enumerator, protocol, workflow, and controller tests passed, including an in-process WorkerMain protocol-v2 round trip. Historical Runner-test skips in this paragraph were caused by the now-retired whole-machine available-memory conditions.

### 2026-11-01 有界汇总补充说明

首次 CL07 Worker 运行因团成员 JSON 数组达到字节预算而停止。为保留已约定的完整 artifact 2 MiB 上限，同时避免把成员明细存储预算当作枚举预算，Worker 结果协议升级为 schema v2：为汇总计数预留 256 KiB，成员列表最多约 1.75 MiB；成员预算耗尽后继续枚举，并累计团总数、规模分布和所有输入节点（含零参与节点）的参与度。`COMPLETED` 表示枚举状态，`membershipListComplete` 单独表示成员列表是否完整。超时、团数限制、取消或资源失败仍使汇总不完整。枚举器、协议、工作流和 Controller 专项合成测试 19 项通过，含同进程 WorkerMain schema v2 往返。历史 Runner 测试曾因当时的整机可用内存跳过条件而跳过；该条件现已废止，不能作为当前测试规则。

### CL07 schema v2 后续受控运行（2026-11-01）

- Before the real run, a targeted `WindowsCliqueEnumerationRunnerTest` passed 1/1. It launched a synthetic triangle through the Windows Job Object `javaw.exe` worker and verified the schema-v2 summary fields and artifact round trip.
- Historical note: one real run used the built-in snapshot (626 nodes, 12,963 directed edges) after the then-existing 3.63 GiB physical-memory preflight passed. That preflight was not an algorithm requirement and has since been removed. Snapshot construction took 7.470 seconds; the production Runner completed in 2.801 seconds. Host JShell heap was capped at 768 MiB.
- Result: `COMPLETED`, full summary for 50,250 maximal cliques of size at least 3, largest clique size 24; all-node participation and clique-size distribution were validated by the protocol. Membership retention was bounded at 1,834,868 encoded bytes of 1,835,008 permitted, retaining 13,669 clique lists (`membershipListComplete=false`). The complete artifact passed the 2 MiB protocol size/structure checks.
- Configured Worker limits remained 30 seconds, 256 MiB Job memory, 25% CPU, and 100,000 cliques. Peak Worker Job memory was not exposed and was not measured. Post-run available physical memory was 3.60 GiB. The transient result was not published into the older 8098 application process, which still returned 404 for the new endpoint; no service lifecycle action was taken.

### CL07 schema v2 后续受控运行（2026-11-01）

- 历史记录：真实计算前定向 `WindowsCliqueEnumerationRunnerTest` 通过 1/1：通过 Windows Job Object 隔离的 `javaw.exe` Worker 执行合成三角形，并校验 schema v2 摘要字段及 artifact 往返。
- 历史记录：随后对内置快照执行一次真实运行（626 节点、12,963 条有向边）；当时的 3.63 GiB 整机可用内存预检只是旧门槛，不是这次算法运行的必要条件，现已彻底废止。快照构建 7.470 秒，生产 Runner 运行 2.801 秒；宿主 JShell 堆上限为 768 MiB。
- 结果为 `COMPLETED`：规模至少 3 的极大团完整枚举 **50,250** 个，最大团规模 **24**；协议校验了全体节点参与度和团规模分布。成员保留用量 1,834,868 字节，预算 1,835,008 字节，保留 13,669 组成员明细（`membershipListComplete=false`）。完整 artifact 通过 2 MiB 大小和结构校验。
- Worker 配置限制仍为 30 秒、Job 内存 256 MiB、CPU 25%、最多 100,000 个团。Worker 峰值 Job 内存未由接口暴露，也未测量；运行后可用物理内存 3.60 GiB。临时结果未发布到仍返回新端点 404 的 8098 旧应用；未执行服务生命周期操作。

### CL07 Worker 成员预算扩容补充（2026-11-01）

- The user authorized a larger internal Worker artifact. The artifact ceiling is now 16 MiB, with a 15.75 MiB clique-array budget after the 256 KiB summary reserve. User-facing JSON/CSV/print exports remain at 2 MiB.
- The first 16 MiB-cap run still retained only 15,315 memberships because the production Runner passed the old 2 MiB algorithm default. The Runner default was corrected to pass `MAX_CLIQUE_ARRAY_BYTES`; a production-entry Job Object triangle test plus seven protocol tests passed (8/8, no skips).
- A subsequent real run completed and retained all 50,250 maximal cliques: `membershipListComplete=true`, 7,095,786 encoded member-array bytes, 612,962 total node-clique incidences. The complete artifact passed schema/size validation. Limits remain 30 seconds, 256 MiB Job memory, 25% CPU, and 100,000 cliques. Peak Job memory was not measured.
- The final focused regression across enumerator, worker protocol, Spring workflow/controller, and the production Runner test passed 21/21 with no skips.
- The result remains transient and was not published to the old 8098 process; no service restart/deployment occurred.

### CL07 Worker 成员预算扩容补充（2026-11-01）

- 用户授权扩大 Worker 内部 artifact。当前 artifact 上限为 16 MiB；扣除 256 KiB 摘要预留后，团成员数组预算为 15.75 MiB。用户可见 JSON/CSV/打印摘要导出仍为 2 MiB。
- 首次 16 MiB cap 运行仍只保留 15,315 个成员列表，原因是生产 Runner 传给算法的默认预算仍为 2 MiB。修复 Runner 默认值，使其传入 `MAX_CLIQUE_ARRAY_BYTES` 后，生产入口 Job Object 三角形测试与 7 项协议测试共 8 项通过，无跳过。
- 后续真实运行完整枚举并保留全部 50,250 个极大团成员列表：`membershipListComplete=true`，成员数组编码 7,095,786 字节，节点-团参与关系总数 612,962。完整 artifact 通过 schema 和大小校验。运行限制仍是 30 秒、Job 内存 256 MiB、CPU 25%、最多 100,000 个团；Worker 峰值 Job 内存未测量。
- 枚举器、Worker 协议、Spring 工作流/Controller 和生产 Runner 定向回归合计 **21 项通过**，0 失败、0 跳过。
- 结果仍是临时计算进程内数据，未发布到 8098 旧服务；没有重启或部署服务。
