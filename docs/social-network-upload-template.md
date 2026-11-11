# 社交网络上传模板

本模板用于社交网络分析会话。建议按需上传 `users.csv`、`relationships.csv`、`tweets.csv`；三个角色可以分别提供多个文件，也可以只提供可用子集。所有 CSV 使用 UTF-8 编码并保留表头。

## `users.csv`

```csv
user_id,username,display_name,bio,followers_count,following_count,tweet_count,created_at,location
1002259603537702912,example_user,Example User,,1475,20,300,2020-01-01,Beijing
```

必填字段：

- `user_id`

可选字段：

- `username`
- `display_name`
- `bio`
- `followers_count`
- `following_count`
- `tweet_count`
- `created_at`
- `location`

## `relationships.csv`

```csv
source_user_id,target_user_id
1002259603537702912,188736341
```

每行表示一条有向关注关系：

```text
source_user_id → target_user_id
```

即 source 用户关注 target 用户。两个 ID 都按字符串处理，不使用用户名或显示名称代替。

## `tweets.csv`

```csv
tweet_id,user_id,created_at,text,like_count,reply_count,repost_count,bookmark_count
1997991551738183929,1002259603537702912,2025-12-08,"Example text",3,0,0,0
```

必填字段：

- `tweet_id`
- `user_id`
- `created_at`
- `text`

可选互动字段：

- `like_count`
- `reply_count`
- `repost_count`
- `bookmark_count`

## JSONL 形式

JSONL 每行一个对象，例如：

```json
{"user_id":"1002259603537702912","username":"example_user","display_name":"Example User"}
```

不支持整文件 JSON 数组。

## 校验规则

- CSV 必须有表头；
- 支持 UTF-8 和 UTF-8 BOM；
- 不支持 GBK、GB18030、UTF-16、Excel、XML 和无表头 CSV；
- ID 不得转换为整数或 JavaScript `Number`；
- 空 ID、非法日期、负数互动值会进入质量报告；
- 重复关系在分析层去重，重复推文在原始层保留并报告冲突；
- 上传数据只属于当前分析会话，不作为永久数据集保存。
