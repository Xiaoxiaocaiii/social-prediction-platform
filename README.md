# 多模态社交媒体流行度预测与可视化分析平台

基于 SMTPD 数据集（28 万条真实 YouTube 视频：封面图 + 文本 + 30 天热度序列）的本地演示平台。

## 环境要求

- JDK 17
- Maven 3.9+
- 数据文件位于 `data/`（不入 git）：`smtpd.db`、`img_yt.zip`、`covers/`

## 启动

双击 `start.bat`，或执行：

```bat
mvn -s mvn-settings.xml spring-boot:run
```

浏览器打开 http://localhost:8080

## 接口

| 接口 | 说明 |
|---|---|
| `GET /api/health` | 健康检查 |
| `GET /api/videos` | 分页浏览 / 分类筛选 / 排序 / 搜索 |
| `GET /api/videos/{id}` | 视频详情 + 30 天曲线 + 峰值/衰减指标 |
| `GET /api/insights` | 分类热度、Day1-Day30 相关性、平均衰减曲线 |
| `POST /api/compare` | 2~3 个视频曲线对比 |
| `POST /api/predict` | 上传图文 + 30 天基线预测（multipart） |
| `GET /covers/{前缀}/{id}.jpg` | 封面（缺失时按需从 zip 提取，再缺失返回占位图） |

## 测试

```bat
mvn -s mvn-settings.xml test
```

## 模型接入

预测服务双实现，由 `app.prediction-mode` 切换：

- `baseline`（默认）：BM25 检索参考案例，取真实曲线平均，纯数据计算。
- `remote`：HTTP 调用 Python 服务 `POST http://127.0.0.1:5001/predict`。

模型团队按契约实现后，改配置即可切换，前端零改动。

## 数据说明

- `data/basic_view_pn.csv`：原始数据（608MB，构建索引用）
- `data/smtpd.db`：SQLite 索引（`scripts/build_index.py` 生成，282,481 行）
- `data/img_yt.zip`：封面压缩包（由分卷还原，`scripts/extract_spanning_zip.py`）
- `data/covers/`：本地封面（轻量方案先提取常用部分，其余按需补提）
