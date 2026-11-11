# 本地部署提示词

把本文件和项目源码一起交给协作助手时，可使用下方任务说明。真实数据属于本机数据资产，是否随项目交付由数据持有人决定。

## 部署任务

请在 Windows 环境检查并运行本项目，实际执行命令并报告结果。不要只给出命令，也不要修改源码、覆盖用户改动、删除数据或执行 Git 提交/推送。

### 项目与环境

- 项目是 Java 17、Spring Boot、Maven 项目，前端使用 HTML/CSS/JavaScript。
- 从包含 `pom.xml`、`mvn-settings.xml` 和 `start.bat` 的项目根目录操作。
- 执行 `java -version`、`mvn -version` 确认运行环境。
- 默认端口由 `src/main/resources/application.yml` 配置为 `8080`；可通过环境变量 `SERVER_PORT` 临时覆盖。

### 数据说明

- 预测模块使用本机 `data/smtpd.db`，封面资源位于 `data/covers/` 等本地资产中。
- 内置社交网络分析使用 `all_csv/merged_dataset/` 中的用户、关系和推文 CSV。
- `data/`、`all_csv/`、`csv/` 不一定存在于源码克隆中。缺少真实数据时可以检查服务启动，但不得创建空数据库、使用 mock 数据冒充真实数据，或声称相应内置数据链路已验证。
- `test-data/upload-demo/` 中的文件是合成上传样例，只能用于检查上传功能，不能作为真实研究数据。

### 构建与测试

在项目根目录运行：

```powershell
mvn -s mvn-settings.xml test
```

运行前端契约测试：

```powershell
Get-ChildItem scripts -Filter '*.test.js' | ForEach-Object {
  node $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "Frontend test failed: $($_.Name)" }
}
```

如依赖下载失败，请先判断网络、镜像、权限或本地仓库原因，再报告具体问题；不要删除本地数据或反复盲目重试。

### 启动与检查

优先使用：

```powershell
.\start.bat
```

默认打开 `http://localhost:8080/`。若端口冲突且无法安全处理占用进程，可在当前 PowerShell 会话中切换端口：

```powershell
$env:SERVER_PORT = "18198"
mvn -s mvn-settings.xml spring-boot:run
```

以启动日志显示的实际端口为准。检查首页、`#/predict` 和 `#/social-network`，并区分“页面服务可访问”与“真实数据分析链路已验证”。浏览器核验使用桌面版 Microsoft Edge。

### 安全边界

- 不删除或覆盖 `data/`、`all_csv/`、`csv/` 或任何已有用户文件。
- 不创建空数据库或 mock 数据来掩盖真实数据缺失。
- 不擅自修改数据库结构、依赖或端口配置文件。
- 不执行 Git commit、push、reset 或其他会覆盖用户改动的操作。
- 不以整机可用内存低于 2 GiB 为由拒绝测试或分析。

### 最终报告

请列出项目目录、Java/Maven 版本、启动方式、实际访问端口、后端和前端测试结果、真实数据目录是否存在，以及仍未验证的功能。测试命令通过、服务启动或页面返回 HTTP 200，均不能单独表述为完整业务验收通过。
