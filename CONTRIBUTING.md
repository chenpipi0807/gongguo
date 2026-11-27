# 参与贡献

## 最需要的：机型兼容性

这个项目靠「无障碍 + 结构判断」，不同 ROM 的差异很大，所以最缺的是**各机型的实测数据**。

### 怎么测

1. 安装 APK，按 README 配好目标应用
2. 正常使用半天
3. 打开「工果」首页，看三个数：
   - **扫描**：应该持续增长（说明无障碍事件正常）
   - **出手**：有广告时应该增加
   - **最近动作**：显示最后干了什么
4. 有问题就导出日志（首页「导出日志」→ 下载目录里的 txt）

### 怎么提

Issue 或 PR 都行，附上：

```
机型：
系统版本：
结果：可用 / 部分可用 / 不可用
现象：
日志（可选）：
```

PR 直接改 README 里的「机型兼容性」表格即可，一行一个机型。

## 代码贡献

- 规则：`app/src/main/java/com/nx/adcleaner/Rules.java`（纯函数，改完跑 `tools/RulesTest.java`）
- 判定逻辑：`AdSkipService.java`
- UI：`MainActivity.java`（纯代码 UI，不依赖 AndroidX）

提交前请确认：

```bash
./gradlew assembleDebug
javac -encoding UTF-8 -d tools/out tools/Rules.java tools/RulesTest.java
java -cp tools/out RulesTest
```

## 注意

- 不要在代码、注释、文档里写具体 App 名称或包名（避免把项目变成「针对某 App 的规避工具」）
- 不要提交 APK、日志、截图（`.gitignore` 已覆盖）
