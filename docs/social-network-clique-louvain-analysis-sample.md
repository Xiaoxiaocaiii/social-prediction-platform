# Clique 与 Louvain 问题—证据分析样例

> 数据范围：内置 `think-tank-social-network/v1` 的用户资料与关注关系文件；不包含推文。原始 CSV 位于本机 `all_csv/merged_dataset/`，不随源码交付。
> 口径：完整节点总体；任一方向有效关注构成一条无权简单无向 tie。
> 样例性质：基于已记录的全网结果，并附两个可回查原始 CSV 的局部证据。它不是参考报告的复现，也不代表现实组织或社会群体。产品集成和验收状态见[实施计划](../全局关系图与社群桥接分析实施计划.md)。

## 研究问题

1. 当前关注网络中，是否存在由至少三个账号构成、且任意成员之间均有无向 tie 的极大团？这些团是否大量重叠？
2. 在主投影及固定 Louvain 参数下，网络如何形成算法社群？处理顺序、分辨率和互相关注投影变化后，成员分区是否相同？

问题只针对静态数据中的网络结构，不试图识别账号的现实组织归属，也不推断互动、传播或因果关系。

## 数据与方法

图输入为本机 `all_csv/merged_dataset/all_user_info.csv` 与 `all_csv/merged_dataset/all_following_list.csv`。标准读取和规范化后，输入范围为 **626 个节点、12,963 条有效有向关系、10,422 条主投影无向 tie**。每个 tie 只计一次；双向关注不增加权重。两份原始图输入已完成 SHA-256 指纹计算：关系文件摘要为 `0560b8aa0f4b8de88d4304662df64797c93aed0a9bd92279de7acb45c3073703`，用户文件摘要为 `05adac5ea7190ee4eb93bbd646eea8717aa792b9bc9228e80caaf9d12a4baba4`，稳定 manifest 的总 `dataFingerprint` 为 `8e4ef44ab6416ea31fee230355b1db1407a969d823b12e5edbdc353a51858855`。

Clique 口径为规模至少 3 的极大团；重叠团分别计数。Louvain 主分析使用 `resolution=1.0`、度数降序处理和 `undirected_simple` 投影。85 个零度节点不分配到社群；社群标签如 `C001` 仅是本次结果标签。

## Clique 证据

内置数据的完整生产枚举报告 **50,250 个**规模至少为 3 的极大团，最大团规模为 24；成员列表已完整保留。规模为 3 的团有 181 个，因此规模至少为 4 的团有 50,069 个。独立校验器对生产结果逐团检查成员两两连边和极大性，并重算分布与参与度；本轮 BitSet Worker 对同一内置数据得到完全相同的成员摘要和 SHA-256。两实现均属于 Bron–Kerbosch 算法家族，因此这是针对当前数据的独立代码路径交叉证据，不是对该算法家族所有潜在缺陷的数学证明。

下面是从原始行构造并单独核验的一个三节点例子。账号 ID 均为字符串；资料行号按 CSV 源记录编号，关注列表索引从 0 开始。

| 节点 ID | `all_user_info.csv` 资料行 |
| --- | ---: |
| `1003664684455792640` | 4 |
| `110770765` | 23 |
| `2817096996` | 329 |

| 无向 tie | 原始有向证据 | `all_following_list.csv` 行 / 列表索引 |
| --- | --- | --- |
| `1003664684455792640` — `110770765` | `110770765 → 1003664684455792640` | 23 / 5 |
| `1003664684455792640` — `2817096996` | `2817096996 → 1003664684455792640` | 329 / 33 |
| `110770765` — `2817096996` | `110770765 → 2817096996` | 23 / 6 |

三条 tie 均可由关注表中的方向证据重建。逐一检查完整的 626 节点投影，没有其他节点同时连接这三个成员，因此该三节点集合在此投影中是极大团。CL08 还对全部 50,250 个团完成了第二 Worker 结果比对及流式来源证据核验；本表用于展示如何人工审阅单团证据。

## Louvain 证据

基准结果为 **6 个社群、541 个已分配节点、85 个未分配孤立节点**；10,422 条 tie 中有 5,021 条社群内 tie、5,401 条跨社群 tie。模块度为 `Q=0.2828216800436288`，算法在 12 轮后收敛。质量摘要由父进程按输入关系独立重建投影后复核。

一个成员与关系证据切片如下：基准分区将 `101848693` 和 `18213483` 都分配到 `C001`；两者资料分别位于用户表第 8 行和第 190 行。关系 `101848693 → 18213483` 位于关注表第 8 行、`following_list` 零基索引 4，故该无向 tie 计入 `C001` 的内部边。成员映射来自本次生产 Worker 基准结果，原始资料/关系行号则按 ID 回查输入 CSV；这是一条可审阅的证据样例，不表示当前 API 已提供全体成员与来源行聚合。

CL12 在同一完整节点总体上比较分区，未分配节点各自按一个单例参与相似度计算：

| 对照 | 社区数；已分配/未分配 | ARI / NMI（相对基准） |
| --- | --- | --- |
| 基准：γ=1，度数降序，任一方向投影 | 6；541/85 | 1.000000 / 1.000000 |
| γ=1，度数升序，任一方向投影 | 7；541/85 | 0.839262 / 0.899694 |
| γ=0.5，度数降序，任一方向投影 | 2；541/85 | 0.099971 / 0.623159 |
| γ=2.0，度数降序，任一方向投影 | 26；541/85 | 0.447705 / 0.742663 |
| γ=1，度数降序，仅互相关注投影 | 9；401/225 | 0.317537 / 0.633599 |

在本次确定性实现中，度数升序产生的分区较接近基准但并不相同；改变分辨率或关系投影会产生更明显的成员差异。所有变体均收敛，但收敛不等于分区稳定或现实有效。互相关注变体在自己的投影图上计算 `Q(γ=1)`，该值不能与主投影模块度直接比较。当前实现没有随机步骤，随机种子实验不适用。

## 结论边界

- 极大团允许重叠，50,250 不是互斥群体数；Louvain 社群则是本次算法生成的分区，两者计数不可直接比较。
- 生产枚举与 BitSet 第二实现对当前真实输入的全量结果一致，且独立逐团核验与来源回查均通过；两实现同属一个算法家族，不构成普遍的数学完备性证明。
- 本样例展示一个团和一个 Louvain 内部 tie 的行级证据。父进程已流式聚合并核对全部 50,250 个团的来源；数据指纹也已生成。本文件本身不是产品接口说明；Clique/Louvain 结果、缓存、会话和页面的后续集成状态见实施计划。
- 关系表只描述当前整理的数据快照。缺少关注记录不能证明现实中不存在关系；图算法结果不识别真实组织、地理身份、影响力或因果。
- 参考报告的节点、边、团数、社群数及南北结论不是本数据集的基准；数据同源性尚未确认。

## 核验记录

- Louvain 基准使用生产 Windows Job Object Runner 重跑以恢复临时成员映射；运行前可用物理内存为 2,191,499,264 字节。Worker 状态为 `COMPLETED`，耗时 1,185 ms，Job 峰值内存 171,098,112 字节。结果与 CL11 已记录的节点数、边数、社群数、分配数、tie 数、模块度和迭代数一致。
- 三节点极大团使用完整内置 CSV 构造无向简单邻接集，核对节点数为 626、tie 数为 10,422；检查三条成员边及全体节点是否存在共同扩展点。该步骤只核验一个样例，没有再次运行全量 Clique 枚举。
- `SocialNetworkDataFingerprintServiceTest` 对内置注册图输入实际运行，输出两份原始文件 SHA-256 与 manifest 指纹；指纹生成不读取 tweets。
- CL08 真实双 Worker 测试通过：生产与 BitSet 实现都返回 50,250 个团及完整成员，成员 SHA-256 和摘要相同；全量证据核对 612,962 个成员引用、3,743,649 条 tie、5,412,658 条有效方向来源，流式回查耗时 2,266 ms。
- 未启动或停止服务，未部署、暂存或提交。本样例只更新文档，不接入 API、缓存或页面。

## English Summary

This sample asks whether the built-in `think-tank-social-network/v1` snapshot contains overlapping maximal cliques of size at least three, and how the deterministic Louvain partition changes under the approved parameter and projection variants. The graph uses 626 nodes, 12,963 valid directed relationships and 10,422 unweighted undirected ties. Tweet data is excluded. The users and relationships CSVs have been fingerprinted; the stable manifest SHA-256 is `8e4ef44ab6416ea31fee230355b1db1407a969d823b12e5edbdc353a51858855`.

The production Clique run reported 50,250 maximal cliques of size at least three, with largest size 24 and complete retained membership lists. An independent verifier checked every supplied clique for pairwise adjacency and maximality, and recalculated the summaries. A real-data BitSet Worker returned the same complete membership digest and summaries. Both implementations use the Bron-Kerbosch algorithm family, so this is independent code-path evidence for this input, not a general mathematical proof against shared algorithm-family defects. As a concrete source-row example, IDs `1003664684455792640`, `110770765` and `2817096996` form a three-node maximal clique. Their profile rows are 4, 23 and 329. The supporting directions are `110770765 → 1003664684455792640` (relationship row 23, list index 5), `2817096996 → 1003664684455792640` (row 329, index 33), and `110770765 → 2817096996` (row 23, index 6). No fourth node is adjacent to all three in the complete 626-node projection. This verifies one example, not the full enumeration.

The Louvain baseline produced six communities, 541 assigned nodes, 85 unassigned isolates, 5,021 internal ties, 5,401 cross-community ties, `Q=0.2828216800436288`, and convergence after 12 iterations. For a source-traceable membership slice, IDs `101848693` and `18213483` are both in `C001`; their profile rows are 8 and 190, and the relationship `101848693 → 18213483` is in relationship row 8, list index 4. The full-universe ARI/NMI values versus baseline were 0.839262/0.899694 for degree-ascending order, 0.099971/0.623159 for resolution 0.5, 0.447705/0.742663 for resolution 2.0, and 0.317537/0.633599 for reciprocal-only projection. Each unassigned node was treated as its own singleton for these comparisons.

These results describe this static dataset and these algorithm settings only. Maximal cliques overlap; Louvain communities partition assigned nodes. Convergence does not establish stability or real-world validity. Parent-side streaming aggregation audited all 50,250 cliques, including 612,962 member references, 3,743,649 clique ties and 5,412,658 valid directed-source references. This sample is not a product API specification; later API, cache, session, and page integration is recorded in the implementation plan. The reference report's counts and geographic conclusions are not benchmarks for this dataset.
