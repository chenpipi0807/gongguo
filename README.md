# 工果 · 免 root 的广告自动跳过

> **仅供技术交流与学习使用。**
> 本项目用于研究 Android 无障碍（AccessibilityService）的控件树遍历与手势模拟，
> 请勿用于商业用途、二次分发或规避任何付费服务。使用前请阅读文末[免责声明](#免责声明)。

一个基于 **无障碍服务（AccessibilityService）** 的安卓小工具：识别界面上的「跳过 / 关闭」并点掉；
对**一个文字节点都没有**的全屏视频广告，用「可见文字归零 + 多窗口结构」判断后上滑跳过。

- 不用 root，不装 Xposed / LSPosed
- 不修改任何宿主 App，不注入、不脱壳
- 不联网，不申请网络权限，不读取也不上传任何内容

## 能做什么

| 场景 | 做法 |
|---|---|
| 开屏广告 | 匹配「跳过 / 跳过 3s / SKIP」并点击 |
| 弹窗广告 | 匹配「关闭 / × / close」并点击（只在像按钮的小控件上出手） |
| **无文字广告**（全屏视频 / 直播预览） | 这类广告整屏都是 Surface 画出来的，无障碍读不到文字；用「可见文字数归零 + 在目标应用内 + 持续 2.5 秒」判断，然后上滑退出 |
| 多窗口广告 | 广告是叠在播放器上的独立窗口时，遍历所有窗口找「空的全屏窗口」 |

## 不做什么

- 不拦截网络请求（DNS / VPN 对走 HTTPDNS 的 App 无效，实测过）
- 不破解、不绕过会员、不伪造权益
- 不修改宿主 App 的任何文件

## 安装

1. 下载 Release 里的 APK，或自己编译
2. 打开「工果」→ **去开启无障碍** → 在系统列表里打开「工果 · 广告跳过」
3. 先去你要处理的 App 里划两下，回到「工果」→ **添加最近使用** → 选中它
4. 点 **开启监控**

之后开机自启、打开 App 自动恢复，不需要每次手动点。

> Android 13+ 侧载应用默认禁止开无障碍：`设置 → 应用 → 工果 → ⋮ → 允许受限设置`。
> 国产 ROM 建议给「工果」开自启动 / 后台运行 / 电池不优化。

## 权限说明

| 权限 | 用途 | 是否必需 |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | 读控件树、模拟点击/滑动 | 必需 |
| `FOREGROUND_SERVICE` + 通知 | 常驻「监控中」，提高后台存活率 | 必需 |
| `POST_NOTIFICATIONS` | 常驻通知 | Android 13+ |
| `SYSTEM_ALERT_WINDOW` | 屏幕顶部「● 监控中」小胶囊 | 可选 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 | 可选 |
| `WRITE_SECURE_SETTINGS` | 被系统强停后自动恢复无障碍（需 adb 授权一次） | 可选 |

`WRITE_SECURE_SETTINGS` 需要手动授权一次：

```bash
adb shell pm grant com.nx.adcleaner android.permission.WRITE_SECURE_SETTINGS
```

## 原理

```
无障碍事件 / 800ms 定时扫描
        ↓
遍历当前窗口控件树（≤700 节点）+ 所有窗口
        ↓
① 有「跳过」类文字      → 点击（只点小按钮）
② 有「关闭 / ×」        → 点击（只点小按钮）
③ 可见文字数 = 0 且持续 2.5s → 上滑（用于无文字广告）
        ↓
900ms 冷却 + 同一条 1.5s 去重 + 每分钟最多 3 次
```

几个刻意的设计：

- **只点小控件**（宽 ≤ 35% 屏宽、高 ≤ 18% 屏高），整行设置项、整屏容器一律不碰——这是早期版本会在系统设置里乱点的原因。
- **不往上找「可点的父容器」**：广告的父容器常常是整屏可点区域，一点就跳广告落地页。
- **动作前校验可见性**：`isVisibleToUser` + 同一窗口，避免拿过期坐标乱点。
- **多窗口遍历**：`flagRetrieveInteractiveWindows`，能找到叠在播放器上的广告窗口。

## 诊断

每次出手都会写一行 JSONL 到应用私有目录，首页「导出日志」可导出到「下载」：

```json
{"t":1795318233745,"k":"action","d":"点击跳过|跳过 5|cls=..."}
{"t":1795318230630,"k":"diag","d":"act=... vis=0 nodes=315 overlay=false"}
{"t":1795318233745,"k":"snap","d":"why=struct cls=...\n1 FrameLayout b=..."}
```

`beat` 是 30 秒心跳（当前包名、窗口类名、界面上的文字），用来判断它到底有没有读到界面。

## 编译

需要 JDK 17+ 和 Android SDK（compileSdk 36）：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

规则单测（纯 JVM，不需要设备）：

```bash
javac -encoding UTF-8 -d tools/out tools/Rules.java tools/RulesTest.java
java -cp tools/out RulesTest
```

## 目录结构

```
app/src/main/java/com/nx/adcleaner/
  AdSkipService.java        无障碍服务：扫描 → 判定 → 点击/滑动
  MainActivity.java         设置界面（纯代码 UI，不依赖 AndroidX）
  MonitorService.java       前台服务 + 常驻通知
  MonitorOverlay.java       顶部「监控中」悬浮胶囊
  AccessibilityKeeper.java  被强停后自动恢复无障碍
  BootReceiver.java         开机自启
  Rules.java                文案规则（纯函数，可单测）
  Prefs.java                配置存储（目标应用、开关、关键词）
  LogStore.java             JSONL 诊断日志
  Status.java               运行计数
tools/RulesTest.java        规则单测
```

## 机型兼容性

欢迎大家补充自己机型的实测结果（见下方「参与贡献」）。

| 机型 | 系统 | 结果 | 备注 |
|---|---|---|---|
| 华为 畅享 70X（BRE-AL80） | Android 12 / EMUI 14 | ✅ 可用 | 强停后无障碍会被系统关掉，App 可自愈（需 `WRITE_SECURE_SETTINGS`） |
| 你的机型 | — | 待补充 | 按下面的模板提 PR 或 Issue |

提交格式（一行即可）：

```
| 小米 14 | Android 14 / HyperOS 1.0 | ✅ 可用 | 需关闭「后台弹出界面」限制 |
| 某机型 | Android 13 | ⚠️ 部分可用 | 直播广告能跳过，开屏广告漏 |
| 某机型 | Android 14 | ❌ 不可用 | 无障碍频繁被系统回收 |
```

## 参与贡献

最需要的是**机型兼容性反馈**：

1. 装好之后在目标 App 里正常用半天
2. 打开「工果」→ 导出日志，看首页的「扫描 / 出手」两个数有没有正常增长
3. 提 Issue 或 PR，附上：机型、系统版本、结果（可用/部分可用/不可用）、现象描述

代码层面的 PR 也欢迎：规则、机型适配、UI、文档都行。

## 已知限制

- 广告是 App 自己拉取并渲染的，本工具只能做到「出现即处理」，不能阻止加载。
- 有些广告（尤其是视频贴片）没有可关闭的控件，只能上滑翻过；如果它插在一集播到一半的位置，上滑会吃掉后半段。
- 部分 App 的广告界面完全不可访问（Surface / WebView 未开无障碍），这类情况只能靠窗口结构判断，存在误判可能。

## 免责声明

本项目**仅供技术交流与学习**，用于研究 Android 无障碍 API 的能力边界。

- 请遵守目标应用的用户协议与当地法律法规，不要用于商业用途或二次分发。
- 自动化点击可能触发部分应用的风控，账号异常请自行评估、自行承担。
- 本项目不提供任何破解、去会员、绕过付费的能力，也不修改任何宿主应用。
- 作者不对使用本项目造成的任何直接或间接损失负责；如不同意，请勿使用。
