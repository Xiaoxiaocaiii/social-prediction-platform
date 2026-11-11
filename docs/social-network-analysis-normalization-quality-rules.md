# 社交网络分析规范化与数据质量规则

> 文档状态：T03 已完成并通过验收
> 适用范围：F03 数据规范化与关联、S01 数据质量与覆盖报告的规则冻结
> 更新时间：2026-10-04
> 前置文档：
>
> - [数据字段契约](social-network-analysis-data-field-contract.md)
> - [数据来源与授权元信息](social-network-analysis-data-source-authorization.md)
> - [当前实施与验收记录](../全局关系图与社群桥接分析实施计划.md)
>
> 说明：本文档冻结规范化、关联、异常识别和质量报告口径，不实现读取器、数据库、接口或页面。

## 1. 规则目标

T03 负责把 F02 输出的原始记录转换为可追溯的规范化记录，并为 S01 提供质量事实。

本规则必须同时满足：

1. 原始记录不被静默删除；
2. 分析层只消费通过规范化规则的记录；
3. 每项异常都有状态、数量、范围和证据；
4. 用户 ID、推文 ID 全部按字符串处理；
5. 外部节点参与结构计算但不虚构资料；
6. 未归属来源推文与已关联作者分离；
7. 重复记录在原始层保留、分析层按规则处理；
8. 空值、明确零值和非法值严格区分；
9. 质量报告解释能力降级原因，但不自动修复原始数据；
10. 固定真实输入时，规范化数量可以复现。

## 2. 三层数据模型

### 2.1 原始层

原始层保留 F02 读取到的记录，不修改字段值。

每条原始记录至少关联：

```text
dataset_id
source_file
source_format
source_encoding
source_row_number
raw_record
read_status
```

原始层保留：

- 合法记录；
- 重复记录；
- 自环记录；
- 外部节点关系；
- 未归属来源推文；
- 字段缺失记录；
- 时间解析失败记录；
- 互动异常记录；
- 内容冲突记录。

### 2.2 规范化层

规范化层将字段映射为 T01 标准字段，并附加处理状态：

```text
normalized_record
normalization_status
association_status
dedup_status
quality_flags
source_file
source_row_number
```

规范化层不等于“全部可分析”。记录可以被规范化，但因质量问题只进入部分分析。

### 2.3 分析层

分析层只消费：

- 通过必填字段检查的记录；
- 关系方向明确的有效边；
- 按 `tweet_id` 去重后的有效推文；
- 已明确关联或标记为外部的用户节点；
- 可解析的时间字段；
- 有效互动字段；
- 有效文本样本。

业务模块不得直接读取原始层或绕过规范化层。

## 3. 统一记录状态

### 3.1 读取状态

```text
read_ok
read_warning
read_error
read_skipped
```

### 3.2 规范化状态

```text
normalized
normalized_with_warnings
rejected_required_field
rejected_invalid_format
```

### 3.3 关联状态

```text
linked
external_node
unassigned_source
unlinked_author
not_applicable
```

### 3.4 去重状态

```text
unique
duplicate_preserved_raw
duplicate_excluded_analysis
conflict_duplicate
not_applicable
```

### 3.5 质量严重级别

```text
blocking
error
warning
info
```

含义：

| 级别 | 含义 | 示例 |
| --- | --- | --- |
| blocking | 文件或数据类型无法进入对应分析 | 缺少标准必填字段、文件无法解析 |
| error | 记录无法参与对应计算 | user_id 为空、关系端点为空 |
| warning | 记录可保留但影响部分能力 | 作者无法关联、时间无法解析 |
| info | 不影响核心计算的提示 | 可选 bio 为空、书签全零 |

## 4. 用户规范化规则

### 4.1 用户主键

用户主键优先级：

```text
明确账号 ID（user_id / 内置账号ID）
→ 关注列表中的 user_id
→ 推文中的 source_folder
```

最终规范化主键：

```text
user_id: string
```

禁止：

- 使用用户名作为主键；
- 使用显示名称作为主键；
- 将 ID 转换为 Java 数值或 JavaScript Number；
- 使用浮点值保存 ID；
- 用简介或推文推断缺失 ID。

### 4.2 用户字段处理

| 字段 | 空值 | 非法值 | 规范化处理 |
| --- | --- | --- | --- |
| `user_id` | error | error | 字符串化，保留前导零 |
| `username` | warning/info | warning | 保留原值，不作主键 |
| `display_name` | info | warning | 保留原值 |
| `bio` | info | warning | 保留原文 |
| `followers_count` | warning | error | 非负整数或 missing |
| `following_count` | warning | error | 非负整数或 missing |
| `tweet_count` | warning | error | 原始账户字段，不替代采集样本 |
| `created_at` | warning | warning | 原值保留，解析失败标记 |
| `location` | info | warning | 原值保留 |

### 4.3 用户重复

同一文件出现重复 `user_id` 时：

- 原始记录全部保留；
- 标记 `duplicate_user_id`；
- 不静默覆盖；
- 生成字段冲突报告；
- 如果字段值完全一致，可选择一条作为规范化主记录；
- 如果字段值冲突，不自动选择“最新”或“最完整”记录；
- 冲突字段进入质量报告；
- 业务层默认使用明确的规范化主记录，并标注冲突状态。

### 4.4 用户资料缺失

用户记录缺失时：

- 关系中的节点仍可成为外部节点；
- 不创建虚构用户资料；
- 不填充用户名、显示名称、简介和账户字段；
- 允许参与网络结构计算；
- 默认不进入完整用户资料排行；
- 质量报告统计资料未采集数量。

## 5. 关系规范化规则

### 5.1 标准关系

```text
source_user_id → target_user_id
```

语义：

```text
source_user_id 关注 target_user_id
```

### 5.2 标准边表处理

每一行生成一条候选关系：

```text
source_user_id
target_user_id
```

检查：

- 两端是否为空；
- 两端是否为字符串；
- 是否存在非法对象/数组；
- 是否为自环；
- 是否为重复边；
- target 是否存在用户资料；
- source 是否存在用户资料。

### 5.3 内置聚合关注列表处理

当前内置关系文件：

```text
source_folder,following_list,username,user_id
```

处理顺序：

1. 以 `user_id` 或明确账号 ID确定 source；
2. 解析 `following_list` Python 列表字符串；
3. 列表每个元素转换为 target 字符串；
4. 生成一条有向边；
5. 保留聚合原行、数组下标和生成边；
6. 空列表生成 0 条边；
7. 非法列表整行进入读取/规范化质量报告；
8. 不用 username 代替 target ID。

### 5.4 关系端点缺失

| 情况 | 状态 | 分析处理 |
| --- | --- | --- |
| source 为空 | error | 不进入分析边，原始保留 |
| target 为空 | error | 不进入分析边，原始保留 |
| source 非法类型 | error | 不进入分析边 |
| target 非法类型 | error | 不进入分析边 |
| source 不在 users | external/unlinked | 保留边，标记外部 source |
| target 不在 users | external_node | 保留边，标记外部 target |

### 5.5 自环

首版处理：

- 原始层保留；
- 规范化层标记 `self_loop`；
- 分析层默认不纳入网络密度、入度和出度；
- 是否纳入 PageRank 需由 T04 参数契约进一步固定；
- 质量报告显示自环数量；
- 不静默删除；
- 不把自环解释为有效关注互动。

### 5.6 重复关系

重复定义：

```text
source_user_id + target_user_id
```

处理：

- 原始层全部保留；
- 分析层默认保留一条唯一有向边；
- 其余标记 `duplicate_excluded_analysis`；
- 统计重复关系数量；
- 不按重复次数放大入度、出度或密度；
- 原始行号全部可追溯。

## 6. 外部节点规则

### 6.1 定义

关系中出现、但用户资料中不存在的账号 ID称为：

```text
外部节点
```

### 6.2 保留规则

- 外部节点参与关系网络结构；
- 外部节点参与边和节点数量统计；
- 外部节点可以参与入度、出度等结构计算；
- 外部节点 ID 按字符串保留；
- 外部节点不自动创建资料；
- 外部节点默认不进入用户资料排行；
- 外部节点在 B03/B07 使用弱化样式；
- 质量报告显示外部节点数量和占比。

### 6.3 禁止补齐

不得根据以下内容补齐外部节点资料：

- 用户名片段；
- 关注者名称；
- 推文文本；
- 目录名称；
- 关系邻居；
- 互动字段。

## 7. 推文规范化规则

### 7.1 作者关联

标准上传字段：

```text
tweets.user_id
```

内置字段：

```text
source_folder
```

关联顺序：

1. source_folder 与明确用户账号 ID匹配；
2. source_folder 与用户目录/关注列表 user_id匹配；
3. 推文作者进入已关联用户；
4. 无法关联且来源为 `Arab_API` 等时标记 `unassigned_source`；
5. 无法关联但看似账号 ID时标记 `unlinked_author`；
6. 不虚构作者资料。

### 7.2 tweet_id

- 必须是字符串；
- 空值为 error；
- 原始层保留所有记录；
- 分析层按 `tweet_id` 去重；
- 内容完全一致的重复记录标记普通重复；
- 同 ID 内容或字段冲突标记 `conflict_duplicate`；
- 重复数量和冲突数量进入质量报告；
- 不以文件行号替代 tweet_id。

### 7.3 重复推文处理

| 情况 | 原始层 | 规范化层 | 分析层 |
| --- | --- | --- | --- |
| tweet_id 唯一 | 保留 | unique | 纳入 |
| 内容完全重复 | 全部保留 | duplicate_preserved_raw | 只纳入一条 |
| 同 ID 内容不同 | 全部保留 | conflict_duplicate | 默认不纳入冲突组，待质量提示 |
| tweet_id 为空 | 保留错误记录 | rejected_required_field | 不纳入 |

不得：

- 按时间随机选择冲突记录；
- 按互动量选择冲突记录；
- 静默覆盖早期记录；
- 将重复数计入有效推文样本。

### 7.4 推文文本

- 原文保留；
- 空文本保留并标记；
- 空文本不进入有效文本样本；
- F06 生成独立分析文本；
- URL、提及、标签和语言处理不在 T03 删除；
- 文本无法识别不删除推文；
- 主题结果必须能回溯 tweet_id。

## 8. 互动字段规则

字段：

```text
like_count
reply_count
repost_count
bookmark_count
```

### 8.1 值分类

| 输入 | 状态 | 分析行为 |
| --- | --- | --- |
| 空值 | missing | 不作为 0 统计 |
| 明确 `0` | valid_zero | 计入有效零值 |
| 非负整数 | valid | 正常统计 |
| 负数 | invalid | 不纳入对应指标 |
| 小数 | invalid | 不纳入对应指标 |
| 带单位文本 | invalid | 不纳入对应指标 |

### 8.2 分指标规则

- 点赞、评论、转发分别统计；
- 总量、平均值和中位数分别计算；
- 每项显示有效样本量；
- 空值不进入该指标分母；
- 明确 0 进入该指标分母；
- 不创建综合互动分；
- 不将互动指标解释为社会影响力。

### 8.3 书签规则

- 字段保留；
- 原始值保留；
- 当前内置数据全 0 时质量状态为 `disabled`；
- 不开放默认书签排行；
- 页面显示“暂无有效书签数据”；
- 后续出现有效差异时，必须重新评估能力矩阵。

## 9. 时间字段规则

### 9.1 解析

- 保留原始时间字符串；
- 生成规范化时间；
- 支持 T01 约定的日期/日期时间格式；
- 解析失败标记 `time_parse_failed`；
- 不用默认日期填充；
- 不根据 source_folder、文件名或目录日期推断。

### 9.2 分析影响

| 状态 | 推文列表 | 时间分布 | 质量报告 |
| --- | --- | --- | --- |
| 可解析 | 纳入 | 纳入 | 计入有效时间 |
| 空值 | 可保留 | 排除 | missing |
| 无法解析 | 可保留 | 排除 | warning |
| 时间范围异常 | 可保留 | 视规则处理 | warning/error |

### 9.3 时间范围

当前内置样本范围：

```text
2016-05-03 至 2026-01-21
```

该范围是当前采集样本范围，不是完整历史范围。

## 10. 未归属来源规则

### 10.1 定义

无法与用户主键关联，且来源字段不是可识别用户 ID 的推文来源，标记为：

```text
unassigned_source
```

典型来源：

```text
Arab_API
```

### 10.2 处理

- 原始层保留；
- 推文样本质量报告单独统计；
- 可以进入全局推文列表；
- 可以参与全局互动总量时，必须单独标注是否包含；
- 默认不进入用户互动排行；
- 默认不进入 B05；
- 默认不进入 B07 主题用户集合；
- 不创建虚构用户；
- 不创建虚构关系；
- 不进入用户资料覆盖率分母，除非报告明确说明口径。

## 11. 质量报告结构

S01 质量报告必须提供三层：

```text
结论摘要
→ 详细报告
→ 原始行号/证据
```

### 11.1 数据集级字段

```text
dataset_id
dataset_version
dataset_status
generated_at
source_type
file_count
user_record_count
relationship_record_count
tweet_record_count
quality_issue_count
capability_impact_count
```

### 11.2 文件级字段

```text
file_name
file_type
source_path_or_logical_name
format
encoding
header_status
row_count
read_ok_count
read_warning_count
read_error_count
required_field_missing
file_status
```

### 11.3 用户质量字段

```text
raw_user_count
normalized_user_count
unique_user_id_count
duplicate_user_id_count
missing_user_id_count
missing_username_count
missing_display_name_count
missing_bio_count
invalid_counter_count
profile_missing_count
```

### 11.4 关系质量字段

```text
raw_relationship_count
candidate_relationship_count
normalized_relationship_count
unique_relationship_count
duplicate_relationship_count
self_loop_count
missing_source_count
missing_target_count
external_source_node_count
external_target_node_count
invalid_relationship_count
```

### 11.5 推文质量字段

```text
raw_tweet_count
unique_tweet_id_count
duplicate_tweet_count
conflict_duplicate_count
missing_tweet_id_count
missing_author_count
linked_author_count
unlinked_author_count
unassigned_source_count
empty_text_count
time_parse_failed_count
```

### 11.6 互动质量字段

```text
like_missing_count
like_invalid_count
reply_missing_count
reply_invalid_count
repost_missing_count
repost_invalid_count
bookmark_missing_count
bookmark_invalid_count
bookmark_all_zero
```

### 11.7 文本质量字段

```text
valid_text_count
empty_text_count
language_detected_count
language_unknown_count
mixed_language_count
text_analysis_available
```

## 12. 数据集健康状态

### 12.1 `healthy`

条件示例：

- 关键文件存在；
- 必填字段完整；
- 关联率达到质量阈值；
- 无阻断性错误；
- 仅有少量信息或可解释警告。

### 12.2 `available_with_warnings`

条件示例：

- 主要分析能力可用；
- 存在重复、外部节点、未归属来源或可解释缺失；
- 质量问题不阻断核心分析。

### 12.3 `partial`

条件示例：

- 关键文件或字段部分缺失；
- 部分业务能力可以降级开放；
- 用户详情、互动或文本能力受影响；
- S02 必须给出具体受限原因。

### 12.4 `unavailable`

条件示例：

- 文件无法读取；
- 缺少全部关键字段；
- 无有效记录；
- 无法构建对应分析输入；
- 所有相关能力关闭。

## 13. 质量问题到能力降级映射

| 质量问题 | 影响能力 | 推荐状态 |
| --- | --- | --- |
| relationships 文件缺失 | B02/B03/B04 网络结构 | unavailable |
| users 文件缺失 | 用户资料和原始账户字段 | limited/unavailable |
| tweets 文件缺失 | B06/B07 | unavailable |
| source_user_id 缺失 | 关系边 | error，排除该边 |
| target_user_id 缺失 | 关系边 | error，排除该边 |
| tweet_id 缺失 | 推文去重 | error，排除该记录 |
| 作者无法关联 | 用户排行和用户详情 | limited，隔离来源 |
| 时间无法解析 | 时间分布 | limited |
| 互动字段为空 | 对应互动指标 | limited |
| 书签全为 0 | 书签分析 | disabled |
| 文本为空 | 关键词/TF-IDF | limited |
| 外部节点较多 | 资料排行 | limited，结构保留 |
| 重复推文较多 | 推文样本统计 | available_with_warnings 或 partial |

质量报告只负责说明影响，不自行关闭页面；S02 负责统一生成能力状态。

## 14. 可复现数量规则

固定数据集版本和处理版本时，至少应复现：

```text
原始用户记录数
规范化用户数
原始关系记录数
唯一关系数
原始推文数
唯一 tweet_id 数
重复推文数
外部节点数
未归属来源推文数
有效时间记录数
有效互动记录数
有效文本记录数
```

### 14.1 数量口径

页面必须区分：

- 原始记录数；
- 规范化记录数；
- 分析有效记录数；
- 去重后样本数；
- 外部节点数；
- 未归属来源数。

禁止只显示一个“总数据量”覆盖所有口径。

### 14.2 预期真实数据基线

基于已盘点数据，当前基线包括：

```text
用户：626
关注关系原始规模：12,963
推文原始样本：237,840
```

最终分析层数字必须由实际规范化和去重结果生成；不得在代码或页面中硬编码为结论。

## 15. 质量报告边界

S01 负责：

- 识别；
- 汇总；
- 分级；
- 解释；
- 映射能力影响。

S01 不负责：

- 修改原始字段；
- 自动补齐用户资料；
- 自动删除重复记录；
- 自动把空值变成 0；
- 自动关联未归属来源；
- 自动修复非法时间；
- 自行定义网络或互动指标。

## 16. T03 验收清单

- [x] 用户 ID 字符串规则；
- [x] 用户重复规则；
- [x] 关系方向；
- [x] 关系去重；
- [x] 自环规则；
- [x] 外部节点规则；
- [x] 推文作者关联；
- [x] tweet_id 去重；
- [x] 冲突推文处理；
- [x] 未归属来源处理；
- [x] 空值与零值；
- [x] 非法值；
- [x] 时间解析失败；
- [x] 互动字段质量；
- [x] 书签全零；
- [x] 原始/规范化/分析三层；
- [x] 质量报告字段；
- [x] 健康状态；
- [x] 能力降级映射；
- [x] 数量可复现规则；
- [x] 不静默删除原始记录；
- [x] 未执行 T10/T11；
- [x] 未创建数据库、接口或页面。

## 17. T03 后续依赖

T03 完成后具备依赖条件的任务包括：

```text
T04 网络指标与 PageRank 参数
T05 文本处理规则
T11 F02 文件读取与格式适配
T12 F03 数据规范化与关联
T13 S01 数据质量与覆盖报告
T17 P1 数据基础闭环阶段门
T24 F05 推文互动统计
```

按当前工作流，下一次只允许执行：

```text
T04 网络指标与 PageRank 参数
```

在用户验收 T03 前：

- 不执行 T04；
- 不执行 T05；
- 不执行 T10；
- 不执行 T11；
- 不执行 T12；
- 不执行任何代码实现；
- 不提交 Git。
