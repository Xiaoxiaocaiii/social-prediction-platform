# Louvain 参数与质量契约

> 版本：1.0
> 文档用途：定义当前 Louvain 算法参数、分区质量、结果完整性和解释边界。
> 适用范围：内置数据及有效上传会话中的完整规范化关系网络。
> Worker 进程限制见[资源契约](social-network-worker-resource-contract.md)；整机可用内存不是分析准入条件。

## 1. 当前算法事实

源码位置：[`LouvainCommunityDetector`](../src/main/java/com/prediction/socialnetwork/network/LouvainCommunityDetector.java)。

| 项 | 当前固定口径 |
| --- | --- |
| 算法名/实现版本 | `Louvain` / `louvain-greedy-local-move-v1`。这是项目内的确定性贪心局部移动实现，不代表某个第三方库的版本。 |
| 图投影 | 主分析 `undirected_simple`：任一方向有效关注都生成一条无向 tie；反向关系和重复关系不增加权重；自环排除。显式敏感性计算可使用 `undirected_mutual`，仅双向有效关系生成 tie。 |
| 初始边权 | 无权，每条不同的无向 tie 权重为 1。更高聚合层的边权是其代表的原始 tie 数之和。 |
| 分辨率 | 主分析固定 `1.0`。显式敏感性计算允许 `0.5` 到 `2.0`，不改变主分析默认值。 |
| 节点顺序 | 初始节点按字符串 ID 升序。主分析局部移动按度数降序、再按节点索引升序；敏感性计算可选度数升序对照。 |
| 候选与平局 | 候选社群按索引升序检查；只有候选分数超过当前分数 `1e-12` 才移动。完全相同或 epsilon 范围内的分数保持当前社群。 |
| 迭代上限 | 最多 100 次局部优化 pass，按所有聚合层合计；`iterations` 计 pass 数，不是移动次数或聚合层数。 |
| 随机性 | 当前路径不调用随机数，无随机种子参数。确定性只保证相同输入、版本和参数下的可重放，不保证对输入顺序、投影或参数扰动稳定。 |
| 社群 ID | 依成员最小字符串 ID 排序后生成 `C001`、`C002`……；ID 是本次结果的标签，不是跨版本稳定身份。 |

普通网络快照调用 `calculateWithoutCommunities`，社群状态为 `not_requested`；只有显式 `calculateWithCommunities` 路径才会调用检测器。相关合成测试覆盖普通快照不触发 Louvain。

## 2. 收敛、状态与孤立点

- 一个局部 pass 内没有任何节点移动时，该层局部优化收敛。
- 整体 `converged=true` 还要求最终层不再减少活动超节点数。若总 pass 达到 100 次仍未满足，返回 `converged=false`，工作流状态为 `COMPLETED_UNCONVERGED`；Worker 已完成不代表算法已收敛。
- 没有任何合格无向 tie 时，不产生社群；所有节点都未分配，`iterations=0` 且 `converged=true`。
- 有边图中的零度节点不进入局部优化，保持未分配。孤立点必须计入总体节点覆盖校验，不能并入一个虚构社群。
- `communityByNodeId` 仅含已分配节点；`unassignedNodeCount` 是总节点数减已分配数。质量核验须满足 `assigned distinct IDs + unassigned IDs = all input node IDs`。
- `status=COMPLETED` 只表示检测器正常返回；只有同时满足 `converged=true`、覆盖校验通过且质量指标有效，才可称为收敛的完整分区。
- Worker 执行异常由分析工作流记录为失败状态；`workerCompleted`、`converged` 和 `complete` 分别表示 Worker 正常完成、算法收敛和完整分区校验通过，不可互相替代。

## 3. 模块度定义

使用主投影的无权简单无向图，分辨率 `γ=1.0`：

```text
Q = Σ_C [ l_C / m - (d_C / (2m))² ]
```

其中 `m` 是不同无向 tie 总数，`l_C` 是社群 C 内部 tie 数（每条无向 tie 只计一次），`d_C` 是 C 内节点度数之和。孤立节点的度为 0，不产生模块度贡献。若 `m=0`，模块度未定义，应输出 `null` 和原因，不输出 NaN。

当前质量摘要将不同分辨率下得到的分区统一按 `γ=1` 的 Q 公式评分，以便对分区作同口径比较；它不是检测器在该分辨率下的优化目标值。检测器的局部移动目标仍使用运行参数 `γ`。`CommunityDetectionResult` **不输出模块度**。双 K4 加一条桥边的人工复核例子为 `Q=11/26`，只用于说明公式，不代表内置数据结果。

## 4. 结果字段契约

当前分析结果携带以下身份、参数和质量信息：

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
| 来源证据 | 不内嵌在汇总对象中；通过成员、社群内关系和社群间关系证据接口分页读取，并保留逻辑文件、源行、列表索引及支持方向。 |
| `randomSeed` | 当前算法不使用随机数，因此无随机种子参数。 |

当前 Louvain 汇总包含算法与 Worker/质量/完整性版本、默认参数、完整成员映射、未分配节点 ID、社群汇总、输入规模、内部/跨社群 tie 数及模块度。Worker 执行状态与 `converged` 分开表示，未收敛状态不得标记为完整分区。逐成员和逐关系来源由独立证据接口返回；不得用汇总中的有向展示边数替代本契约的无向内部/跨社群 tie 数。

## 5. 解释边界

Louvain 输出是指定静态网络和参数下的算法分区，不等于现实组织、社会群体、地理区域或因果关系。确定性重跑相同结果不是稳定性证明；稳定性检查应另用获准的节点顺序、投影或分辨率扰动，并用 ARI/NMI 等分区相似度指标比较，而不是比较 `C001` 这类标签。

独立核验应重建投影并计算给定分区的覆盖、tie 计数和模块度；它不是第二个 Louvain 分区器。不同 Louvain 实现可能到达不同局部最优并给出不同分区，因此不能要求成员映射逐项相同；应比较各自的分区有效性、模块度、参数和必要的 ARI/NMI。

## English Summary

The production default is a deterministic, unweighted Louvain-style greedy local-moving algorithm on the `undirected_simple` projection. It uses resolution `1.0`, ID-sorted initial nodes, degree-descending local order with ID-index tie-breaks, a `1e-12` move epsilon, and at most 100 local passes across all aggregation levels. Explicit sensitivity parameters do not change the default. There is no random seed. Isolates remain unassigned. `COMPLETED` and `converged` are separate states.

The contract defines modularity for the simple undirected graph as `Q = Σ_C [l_C/m - (d_C/(2m))²]`; Q is undefined when there are no ties. The result API exposes the approved parameters, algorithm/quality/completeness versions, full member IDs, unassigned IDs, internal and cross-community undirected tie counts, and Q. Per-member and per-tie source rows are available through separate evidence endpoints. Independent validation checks a supplied partition; it is not a second Louvain implementation, and separate Louvain implementations need not return identical partitions. Deterministic output does not imply stability or identify real-world groups.
