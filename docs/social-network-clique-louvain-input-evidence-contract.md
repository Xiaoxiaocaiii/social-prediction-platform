# Clique 与 Louvain 输入及证据契约

> 版本：1.0
> 文档用途：定义 Clique/Louvain 图输入、关系投影、指纹和来源证据的当前口径。
> 适用范围：内置 `think-tank-social-network/v1` 的注册输入；上传输入另按[字段契约](social-network-analysis-data-field-contract.md)和[会话契约](social-network-analysis-cache-version-lifecycle.md)处理。
> 结论边界：不用于推断现实群体、传播路径或因果关系。

## 1. 目的

本契约固定图输入、关系投影、内容指纹及结果证据的引用方式。接口和产品行为见[当前功能与实现约定](social-network-analysis-current-implementation.md)及[API 契约](social-network-analysis-api-route-contract.md)。

## 2. 输入数据与身份

图分析只读取以下两个注册文件：

| 逻辑角色 | 当前内置文件 | 用途 |
| --- | --- | --- |
| `users` | `all_csv/merged_dataset/all_user_info.csv` | 建立资料账号节点，包括没有任何关系边的有效账号 |
| `relationships` | `all_csv/merged_dataset/all_following_list.csv` | 从 `user_id` 和 `following_list` 解析有向关注关系 |

`all_tweets_raw.csv` 不属于 Clique/Louvain 图输入；推文内容和互动字段不影响图指纹或网络成员。原始 `source_folder`、用户名、显示名称和注册地均不作为节点主键，也不用于推断地区、机构类别或现实群体。

本节文件路径只适用于 `datasetId=think-tank-social-network`、`datasetVersion=v1`。上传数据使用会话内文件清单和独立指纹，不得套用内置文件路径；上传数据生命周期见[当前实现约定](social-network-analysis-current-implementation.md)。

账号 ID 始终作为字符串读取、比较、排序和输出，不转成整数或浮点数；保留前导零。分析总体由当前数据文件和版本定义，不由页面筛选、Top-K、图画布节点上限或分页定义。

## 3. 数据指纹

`datasetVersion=v1` 和 `normalizationVersion=normalization-v1` 是逻辑版本标识，不能单独证明文件字节未变化。Clique/Louvain 分析结果绑定下列指纹：

1. 对上述两个实际输入文件分别读取原始字节并计算 SHA-256；文件缺失或无法读取时拒绝生成完整结果。
2. 构造规范化清单，至少包含数据集 ID、数据集版本、规范化版本、输入契约版本、关系投影版本，以及每个文件的逻辑角色、注册逻辑路径、字节长度和文件 SHA-256。
3. 文件项按逻辑角色及逻辑路径稳定排序；清单以固定 UTF-8 规范序列化（固定键顺序，无路径分隔符变体，无绝对机器路径、修改时间或随机字段）。
4. 对规范化清单字节再计算 SHA-256，作为本次图输入 `dataFingerprint`。响应和分析记录同时保留两个文件各自的摘要及该总指纹。

版本常量为 `network-input-contract-v1.0` 与 `undirected-follow-projection-v1.0`。内置 v1 指纹对注册的用户/关系 CSV 原始字节计算 SHA-256，并对固定字段顺序的 UTF-8 JSON manifest 计算总指纹；不接受逻辑路径或规范化版本不匹配的 descriptor。算法版本、分辨率等作为结果参数独立记录，并与数据指纹共同标识分析结果。

## 4. 节点总体

### 4.1 资料账号节点

- `users` 文件中具有有效、非空 `user_id` 的账号按字符串 ID 去重，每个唯一 ID 形成一个 `PROFILE` 节点。
- 有效资料账号全部进入总体；没有有效关系边的账号仍保留，并标为孤立节点。
- 同一账号出现多条资料记录时，节点仍按一个 ID 计数。证据需列出相关资料文件行号及规范化质量状态；字段冲突不得静默选择某条记录作为身份依据。

### 4.2 外部节点

- 有效关系端点若未在资料 ID 集合中出现，则为外部节点。
- 只有出现在至少一条有效、非自环关系中的外部端点才进入图节点总体。
- 只出现在无效关系、自环或被排除重复关系中的 ID，不因该记录单独进入图节点总体。
- 外部节点仅表示当前关系文件中存在该 ID、但用户资料文件没有匹配记录；不表示其现实身份未知或属于任何特定组织。

### 4.3 节点证据

节点 ID 是稳定证据主键。资料节点引用用户文件逻辑路径和源行号；外部节点引用每条纳入关系的来源证据。节点输出还应注明 `PROFILE`/`EXTERNAL`、是否孤立以及数据指纹。名称字段只作为可选展示信息，缺失时保留 ID，不用名称合并账号。

## 5. 从有向关系到无向 tie

### 5.1 有向关系准入

关系文件中的 `user_id` 是 source；`following_list` 的每个有效元素是 target。`source → target` 表示 source 关注 target。

仅接纳满足以下条件的规范化关系：

- source 和 target 都是有效、非空的字符串 ID；
- 关注列表记录可解析；
- source 与 target 不相同；
- 同一有向 source-target 对只保留一个有效关系。

缺失端点、解析失败关系、自环和同向重复关系均不成为分析边。解析失败的整条关注列表记录按读取/质量规则排除并报告；不能将解析失败解释为空列表。反向关系 `target → source` 与 `source → target` 是不同的有向事实，在投影前分别保留。

### 5.2 无向、无权简单投影

对每条准入有向关系建立无序端点对：

```text
tie = (min_ordinal(sourceId, targetId), max_ordinal(sourceId, targetId))
```

以字符串序数顺序规范化端点，不能数值转换 ID。相同无序对只形成一条权重为 1 的 tie：

- 仅有 A→B：形成一条 A—B tie；
- 仅有 B→A：也形成一条 A—B tie；
- 同时有 A→B 与 B→A：仍只形成一条 A—B tie，方向证据保留两条；
- 不按关系重复记录数量、互相关注或账号属性增加权重。

该投影是用户已确认的“任一方向有关注即连边”口径。仅双向互相关注的投影不是本契约主结果，若以后获准作为敏感性分析，必须使用独立投影 ID、独立指纹/参数记录及独立结果，不覆盖主结果。

### 5.3 边证据单位

每条无向 tie 的主键为规范化端点 ID 对。其证据至少包括：

- 无向端点 `(leftId, rightId)` 和固定权重 `1`；
- 支持该 tie 的准入有向方向列表（`sourceId → targetId`）；
- 每个方向的原始逻辑文件、CSV 源行号、`following_list` 零基元素索引；
- 规范化关系状态及准入/排除原因；
- 若存在同向重复候选记录，保留其重复来源引用或可核验重复计数，但不得将其加入 tie 权重。

来源证据保留关系方向、逻辑文件、源行号、数组索引、端点、规范化状态和是否纳入分析。Clique/Louvain 的成员与关系证据通过分页接口按数据集、版本、会话和分析结果身份读取；分析汇总与证据明细是不同响应，不应假定汇总对象内嵌全部源行。

## 6. 可手工核验的示例

假设资料文件包含账号 `A`、`B`、`C`，关系文件记录：

| 来源证据 | 有向事实 |
| --- | --- |
| 第 10 行，列表索引 0 | A→B |
| 第 12 行，列表索引 1 | B→A |
| 第 14 行，列表索引 0 | A→C |

主投影应产生且仅产生两条无向 tie：

| 无向 tie | 支持方向 | 证据引用 |
| --- | --- | --- |
| A—B，权重 1 | A→B、B→A | 第 10 行/索引 0；第 12 行/索引 1 |
| A—C，权重 1 | A→C | 第 14 行/索引 0 |

B—C 不存在。若再出现一条 A→B 的重复原始记录，它不能增加 A—B 权重或 tie 数，但应能在数据质量/重复证据中被核查。该小例只验证投影定义，不代表当前真实数据结果。

## 7. 分析结果范围元数据

每个分析结果及其导出必须能读取或追溯以下信息：

- `datasetId`、`datasetVersion`、内置数据类型；
- `dataFingerprint`、各输入文件摘要、输入/规范化/投影版本；
- 实际节点、资料节点、外部节点、孤立节点、准入有向关系和无向 tie 数；
- 排除的无效关系、自环、同向重复关系数量及解析错误状态；
- 算法名和算法版本、全部算法参数、开始/结束时间、运行状态和资源终止原因；
- 节点及团/社群成员 ID；来源证据通过相应分页接口读取；
- 结果完整性状态及其原因。

页面筛选数、API 返回数和图形绘制数必须与完整计算范围分开说明，不得互相替代。

## 8. 解释边界

该图只描述当前数据文件记录的静态关注结构。无向 tie 不意味着双方互相关注，更不意味着互动、合作、传播或现实关系。孤立只表示在本次合格输入中没有纳入边。任何 Clique/Louvain 输出均不得据此推出账号地区、机构归属、现实群体身份、影响力或因果结论。

## 9. 指纹和证据实现

- `SocialNetworkDataFingerprintService` 为内置和上传图输入生成指纹；内置指纹限定注册的用户/关系文件，上传指纹绑定当前会话内上传文件及其角色、格式和输入契约。
- 结果身份校验数据集、版本、会话、输入指纹和分析参数；会话失效后不得复用上传数据的缓存结果或证据。
- `CliqueEvidenceAggregator` 支持分页或流式生成来源证据；成员不完整时不生成完整证据结果。来源明细由独立证据接口读取，不要求汇总响应携带全部源行。
- 进程和文件大小上限见[Worker 资源约束](social-network-worker-resource-contract.md)，路由见[API 与路由契约](social-network-analysis-api-route-contract.md)。

## English Summary

This contract defines Clique and Louvain inputs for the built-in `think-tank-social-network/v1` dataset only. The graph reads the registered user and relationship CSV files; tweet data is excluded. IDs remain strings. Valid profile IDs, including isolates, are retained. External IDs enter the node set only when referenced by a valid, non-self relationship.

Relationships are source-to-target follows. Invalid records, self-loops and duplicate directed pairs are excluded. Either direction creates one unweighted undirected tie; reciprocal directions collapse to one tie while both original directions remain evidence. Every tie must be traceable to the relationship file, source row and zero-based list index. Duplicate source records may be audited but do not increase tie weight.

`SocialNetworkDataFingerprintService` hashes registered graph inputs and a stable manifest. A result is reusable only when its data and analysis identity match. `CliqueEvidenceAggregator` preserves profile-row evidence, all valid directed sources for each tie, and duplicate source rows separately. Clique and Louvain run in isolated Worker processes; evidence is served through paginated endpoints. Machine-specific hashes and historical run counts are intentionally not part of this contract.
