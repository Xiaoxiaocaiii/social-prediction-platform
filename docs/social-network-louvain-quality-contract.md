# Louvain 参数与质量契约

> 版本：1.0
> 状态：主分析默认参数固定为 CL09 契约；CL12 增加了受控敏感性参数入口。CL14 的独立结果 API 已暴露默认参数、完整成员映射、未分配节点和质量摘要，并以版本化指纹缓存。逐成员/逐 tie 的源行证据尚未作为结构化 Louvain API 字段输出，仍按后续证据/导出范围处理。
> 适用范围：内置 `think-tank-social-network/v1` 的完整规范化网络；本契约定义算法与质量口径。真实数据计算须遵守资源门禁，页面验收仍按后续任务执行。

## 1. 当前算法事实

源码位置：[`LouvainCommunityDetector`](../src/main/java/com/prediction/socialnetwork/network/LouvainCommunityDetector.java)。

| 项 | 当前固定口径 |
| --- | --- |
| 算法名/实现版本 | `Louvain` / `louvain-greedy-local-move-v1`。这是项目内的确定性贪心局部移动实现，不代表某个第三方库的版本。 |
| 图投影 | 主分析 `undirected_simple`：任一方向有效关注都生成一条无向 tie；反向关系和重复关系不增加权重；自环排除。CL12 另提供 `undirected_mutual` 敏感性投影，只有双向有效关系才生成 tie。 |
| 初始边权 | 无权，每条不同的无向 tie 权重为 1。多层聚合后的边权是原始 tie 数之和，仅供后续阶段计算。 |
| 分辨率 | 主分析固定 `1.0`。CL12 受控敏感性入口允许 `0.5` 到 `2.0`，不改变主分析默认值。 |
| 节点顺序 | 初始节点按字符串 ID 升序。主分析局部移动按度数降序、再按节点索引升序；CL12 可选度数升序对照。 |
| 候选与平局 | 候选社群按索引升序检查；只有候选分数超过当前分数 `1e-12` 才移动。完全相同或 epsilon 范围内的分数保持当前社群。 |
| 迭代上限 | 最多 100 次局部优化 pass，按所有聚合层合计；`iterations` 计 pass 数，不是移动次数或聚合层数。 |
| 随机性 | 当前路径不调用随机数，无随机种子参数。确定性只保证相同输入、版本和参数下的可重放，不保证对输入顺序、投影或参数扰动稳定。 |
| 社群 ID | 依成员最小字符串 ID 排序后生成 `C001`、`C002`……；ID 是本次结果的标签，不是跨版本稳定身份。 |

普通网络快照调用 `calculateWithoutCommunities`，社群状态为 `not_requested`；只有显式 `calculateWithCommunities` 路径才会调用检测器。相关合成测试覆盖普通快照不触发 Louvain。

## 2. 收敛、状态与孤立点

- 一个局部 pass 内没有任何节点移动时，该层局部优化收敛。
- 整体 `converged=true` 还要求最终层不再减少活动超节点数。若总 pass 达到 100 次仍未满足，返回 `converged=false`；当前 `status` 仍为 `COMPLETED`，两者语义不可合并。
- 没有任何合格无向 tie 时，不产生社群；所有节点都未分配，`iterations=0` 且 `converged=true`。
- 有边图中的零度节点不进入局部优化，保持未分配。孤立点必须计入总体节点覆盖校验，不能并入一个虚构社群。
- `communityByNodeId` 仅含已分配节点；`unassignedNodeCount` 是总节点数减已分配数。质量核验须满足 `assigned distinct IDs + unassigned IDs = all input node IDs`。
- `status=COMPLETED` 只表示检测器正常返回；只有同时满足 `converged=true`、覆盖校验通过且质量指标有效，才可称为收敛的完整分区。
- 检测器执行异常会向调用方传播，不会伪装成 `COMPLETED`。当前状态枚举没有 `FAILED` 和错误详情字段；未来隔离 Worker/API 必须将异常映射为明确失败结果，且不能用 `converged=false` 代替失败状态。

## 3. 模块度定义

使用主投影的无权简单无向图，分辨率 `γ=1.0`：

```text
Q = Σ_C [ l_C / m - (d_C / (2m))² ]
```

其中 `m` 是不同无向 tie 总数，`l_C` 是社群 C 内部 tie 数（每条无向 tie 只计一次），`d_C` 是 C 内节点度数之和。孤立节点的度为 0，不产生模块度贡献。若 `m=0`，模块度未定义，应输出 `null` 和原因，不输出 NaN。

当前质量摘要将不同分辨率下得到的分区统一按 `γ=1` 的 Q 公式评分，以便对分区作同口径比较；它不是检测器在该分辨率下的优化目标值。检测器的局部移动目标仍使用运行参数 `γ`。`CommunityDetectionResult` **不输出模块度**。合成测试中的独立模块度辅助程序验证了双 K4 加一条桥边的例子 `Q=11/26`；它不代替 CL10 的独立公式核验，也不是内置数据结果。

## 4. 结果字段契约

后续可重放结果至少应携带：

| 字段 | 要求 |
| --- | --- |
| `datasetId`, `datasetVersion`, `dataFingerprint` | 标识完整输入；指纹覆盖用户和关系文件及规范化/投影版本。 |
| `algorithm`, `algorithmVersion`, `resolution` | 固定为上述值；任何语义变化必须提升版本。 |
| `relationProjection`, `weighting`, `nodeOrder`, `tieBreakEpsilon`, `maxIterations` | 记录完整参数，不能只记录“Louvain”。 |
| `status`, `converged`, `iterations` | 分开表达执行是否正常、算法是否收敛及局部 pass 数；不收敛不得冒充成功分区。 |
| `communityId`, `memberNodeIds` | 输出每个社群的完整字符串 ID 成员；同一成员只能出现一次。成员列表排序仅用于稳定呈现。 |
| `unassignedNodeIds`, `unassignedNodeCount`, `inputNodeCount` | 明确孤立/未分配节点并验证总覆盖。仅有未分配计数不足以独立核验。 |
| `internalTieCount`, `crossCommunityTieCount` | 按简单无向 tie 计数，不与原始有向记录数混用。 |
| `modularity`, `modularityDefinition` | 输出上节定义的 Q 和公式版本；无边时为 `null` 并给出原因。 |
| `memberEvidence`, `tieEvidence` | 资料成员回指用户文件逻辑路径/源行；外部成员和每条 tie 回指关系文件逻辑路径、源行、列表索引及支持方向。 |
| `randomSeed` | 当前算法标记为 `not_applicable`；若以后引入随机步骤，必须记录实际种子及随机实现版本。 |

CL14 的 `LouvainAnalysisWorkflowView` 已输出算法与 Worker/质量/完整性契约版本、默认参数、完整成员映射、未分配节点 ID、社群汇总、输入规模、内部/跨社群 tie 数及模块度；Worker 执行状态与 `converged` 分开表示，未收敛状态不会标为 `complete=true`。逐成员资料源行和逐 tie 关系源行仍未作为结构化 Louvain API 字段输出，不能仅凭成员映射声称已提供来源行级证据。不得用 Controller 中的有向展示边数替代本契约的无向内部/跨社群 tie 数。

## 5. 解释边界

Louvain 输出是指定静态网络和参数下的算法分区，不等于现实组织、社会群体、地理区域或因果关系。确定性重跑相同结果不是稳定性证明；稳定性检查应另用获准的节点顺序、投影或分辨率扰动，并用 ARI/NMI 等分区相似度指标比较，而不是比较 `C001` 这类标签。

CL10 的独立核验器独立重建投影并计算给定分区的覆盖、tie 计数和模块度；它不是第二个 Louvain 分区器。不同 Louvain 实现可能到达不同局部最优并给出不同分区，因此不能要求成员映射逐项相同；应比较各自的分区有效性、模块度、参数和必要的 ARI/NMI。

## English Summary

The production default is a deterministic, unweighted Louvain-style greedy local-moving algorithm on the `undirected_simple` projection. It uses resolution `1.0`, ID-sorted initial nodes, degree-descending local order with ID-index tie-breaks, a `1e-12` move epsilon, and at most 100 local passes across all aggregation levels. CL12 adds bounded, explicit sensitivity parameters without changing the default: resolution `0.5`/`2.0`, degree-ascending order, and reciprocal-only projection. There is no random seed. Isolates remain unassigned. `COMPLETED` and `converged` are separate states.

The contract defines modularity for the simple undirected graph as `Q = Σ_C [l_C/m - (d_C/(2m))²]`; Q is undefined when there are no ties. The CL14 result API exposes the approved default parameters, algorithm/quality/completeness versions, full member IDs, unassigned IDs, internal and cross-community undirected tie counts, and Q. Per-member profile-file rows and per-tie relationship-file rows are not yet exposed as structured Louvain API evidence. The independent CL10 checker validates a supplied partition; it is not a second Louvain implementation, and separate Louvain implementations need not return identical partitions. Deterministic output does not imply stability or identify real-world groups.
