# 首页桌面导航规范

适用原型：[home-preview-ac.html](file:///e:/liuxingdu/social-prediction-platform/docs/home-preview-ac.html)

## 设计边界

- 首页原型和正式首页仅面向桌面端 Microsoft Edge。
- 支持 Edge 最新 3 个正式版本及 Edge 企业版。
- 验收分辨率：`1920×1080`、`2560×1440`、`3840×2160`。
- 永久禁止新增移动端、手机、平板、触摸设备或响应式断点适配需求。
- CSS 不得新增尺寸媒体查询；仅允许 `prefers-reduced-motion` 无障碍规则。

## 主导航

| 元素 | 尺寸/规则 |
|---|---|
| 导航栏高度 | 72px |
| 内容最大宽度 | 1520px |
| 页面左右安全边距 | 32px |
| 左侧品牌区 | 280px |
| 中部功能区 | 4 项，间距 12px，每项最小高度 40px |
| 研究路径 | 14px、600，墨蓝文字，右箭头 12px |

主导航项目：

```text
流行度预测平台
SMTPD · 真实数据研究入口

上传预测 / 热度排行榜 / 数据洞察 / 曲线对比

研究路径 →
```

## 内部章节导航

采用左侧吸顶导航：

```text
01 数据基础
02 检索增强
03 时序预测
04 真实案例
```

- 左栏宽度 164px。
- 内容与导航间距 42px。
- 导航距离顶部主导航 24px。
- 当前章节使用墨蓝文字、左侧高亮线和砖橙圆点。
- 章节锚点使用 `scroll-margin-top` 避开固定导航。

## 案例模块标注

- “真实案例预览”左侧使用 `3px × 18px` 砖橙竖线。
- 标题区最小高度 `56px`，内边距左右 `22px`。
- 封面使用 `16:9` 比例，最小高度 `180px`。
- 趋势图高度 `72px`，指标卡最小高度 `52px`。

## 字体层级

- 标题：`Noto Serif SC`，`500~600`，行高 `1.18`。
- 正文：`Noto Sans SC`、`Microsoft YaHei UI`，基础字号 `16px`，行高 `1.72`。
- 数据与英文标签：Inter/IBM Plex Mono，启用等宽数字。

## 滚动行为

- 使用 `scroll-behavior: smooth`。
- 章节使用 `scroll-margin-top` 补偿固定导航高度。
- 使用 `IntersectionObserver` 根据视口中部自动更新当前章节。
- 内容超出一屏时允许自然延伸，不强制裁剪。

## 评审与验收

- 任何新增需求必须先确认不涉及移动端适配。
- 使用 `pwsh -File scripts/check-desktop-only.ps1` 做提交前扫描。
- 键盘 Tab 可以访问全部导航项。
- `prefers-reduced-motion: reduce` 下仍可正常跳转。
- 运行 Edge 桌面回归：`1920×1080`、`2560×1440`、`3840×2160`。
