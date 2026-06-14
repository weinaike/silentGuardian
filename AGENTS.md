# SilentGuardian (AI语音陪伴防沉迷系统) - AI Agent 开发规范与指南

这份文档旨在为参与开发此项目的 AI Agent 提供核心的开发规范、架构原则和注意事项。请在生成任何代码或修改架构时，严格阅读并遵守以下准则。

## 1. 核心开发规范

- **开发语言**: 统一采用 **Kotlin**（包含 Coroutines），严禁混用 Java。
- **目标 SDK 版本**: `minSdkVersion` = 24 (Android 7.0), `targetSdkVersion` = 34 (Android 14)。
- **包名约定**: `com.yestek.silentguardian`。
- **UI 架构**: 采用 **Material Components + ViewBinding + ViewModel** 模式。未经主控用户同意，不得私自引入 Compose 或其他 UI 框架。
- **国际化与多语言 (I18n)**: 任何新增的 UI 文案（字符串），必须通过 `strings.xml` 引用，**严禁在代码或布局文件中硬编码中文字符串**。每次添加新的中文字符串时，**必须强制同步**在 `values-en/strings.xml` 中添加对应的英文翻译，确保中英文双语环境无缝切换。

## 2. 依赖项及第三方库规范

保持极简主义，拒绝过度设计和臃肿。主要采用以下核心库（仅在此范围内选择）：
- **基础工具库**: `AndroidUtilCode (Blankj)` - 用于快速获取包名、已安装应用列表等。
- **权限管理框架**: `XXPermissions (getActivity)` - 处理极其繁琐的各系统版本权限兼容。
- **本地存储**: `MMKV (Tencent)` - 替代 SharedPreferences，在 Application 初始化，用于状态的极速存取。

## 3. 架构设计与强制代码约束

### 3.1 链式权限申请顺序（避免弹窗重叠）
1. `POST_NOTIFICATIONS` (针对 Android 13+ 的通知权限)。
2. `PACKAGE_USAGE_STATS` (引导用户去系统设置开启“使用情况访问权限”)。
3. 开启服务总控开关时，引导开启无限制电池优化 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。

### 3.2 服务保活与后台策略 (MonitorService)
- **前台服务声明**: Android 14+ 必须配置 `android:foregroundServiceType="specialUse"`，必须通过 `startForeground()` 绑定常驻不可滑动的系统通知。
- **自启与复苏**: `onStartCommand()` 必须返回 `START_STICKY`。注册 `ACTION_BOOT_COMPLETED` 广播以实现开机自启。
- **API 兼容要求**: 
  - 启动服务必须用 `ContextCompat.startForegroundService()` (兼容低版本)。
  - `NotificationChannel` 仅在 API >= 26 时进行判断创建。

### 3.3 核心功能 API 规范
- **前台应用识别**: 必须且仅能使用 `UsageStatsManager.queryEvents()`，通过倒序遍历寻找最近的 `Event.MOVE_TO_FOREGROUND` 事件来获取前台包名，禁绝使用其他模糊时间段统计 API。

## 4. 关键边界条件与兜底防护（Failsafe - 极其重要）

这是本系统的重中之重，必须确保对原手机其他功能的“0误伤”。

- **通话防误伤（绝对高优）**: 在每次轮询最开始，检查 `TelephonyManager.callState`。一旦不处于 `IDLE` 状态，必须立刻解除麦克风静音，并跳过当次计次。
- **资源泄漏防护（生命周期兜底）**: 对系统网络进行控制必须配对操作。在 `MonitorService` 的 `onDestroy()` 阶段，**必须强制关闭 VpnService 连接**，恢复网络。
- **崩溃自愈**: 在 App 初始化阶段配置 `Thread.setDefaultUncaughtExceptionHandler`。发生不可抗力崩溃时，首要任务是终止 VpnService，恢复系统网络。
- **跨日数据清零**: 不得依赖系统跨日广播（不准）。必须在每次计时前，对比 `Today` 的日期字符串与 MMKV 中存储的最后记录日期，若跨日则强制将使用时间清零。
- **省电机制**: 监听屏幕亮暗广播 (`ACTION_SCREEN_ON` / `OFF`)，息屏期间挂起轮询协程停止计次。

## 5. 分阶段开发策略 (Phase 1 & Phase 2)

请遵循预设的开发路线：
1. **先做 API 验证**: 在空工程先验证使用 `VpnService` 构建虚拟黑洞阻断特定 App 网络的有效性。
2. **打好地基**: 初始化配置和依赖，跑通 XXPermissions 权限链条。
3. **完成核心逻辑 (Phase 1 - MVP)**: 开发死循环轮询守护进程 `MonitorService`，加上异常兜底恢复逻辑。
4. **绑定 UI 层**: 利用 AndroidUtilCode 获取 App 列表，由 MMKV 驱动进度条展现。
5. **Phase 2 (进阶)**: 加入防卸载（设备管理器）、防杀后台报警以及针对单一 App 颗粒度管控。

> **终极提示**: 遇到不确定的业务逻辑或权限兼容盲区，请向人类提问确认，切勿自行脑补并编写大量不可控的冗余代码！

## 6. 发布与配置更新 (Deployment & Configuration)

为了统一下发更新配置与分发包，遵循以下维护规范：
- **APK 发布位置**: 定期将构建好的 apk 包上传至阿里云服务器 `admin@47.237.161.121` 的 `/home/admin/gost/brand/apk` 目录下。
- **配置维护**: 版本更新配置统一由项目根目录的 `update_config.json` 维护。每次发布时，`deploy.sh` 会读取此文件并将最新版本信息 scp 上传至服务器同一目录，APK 和配置 JSON 集中在一处管理。
- **端侧拉取地址**: App 通过 `https://www.yes-tek.com/assets/apk/update_config.json` 拉取更新配置。

### 6.1 发版标准流程（每次发布必须严格按此步骤执行）

> **核心原则**：一切通过 `deploy.sh` 完成，禁止手动 scp 或手动修改 `update_config.json`。

**第一步：更新版本号**

修改 `app/build.gradle.kts` 中的两个字段：
```kotlin
versionCode = <上一次 + 1>       // 整数，每次递增
versionName = "<major.minor.patch>"  // 语义化版本，如 0.1.5
```

**第二步：Commit 版本号变更**

```bash
git add app/build.gradle.kts
git commit -m "chore: 发版 v<versionName>，升级版本号至 code=<versionCode>"
```

**第三步：执行一键部署脚本**

```bash
bash deploy.sh
```

脚本将自动完成以下所有操作：
1. 读取 `build.gradle.kts` 中的版本号
2. 执行 `gradle assembleRelease` 编译签名包
3. `scp` 上传至阿里云 `/home/admin/gost/brand/apk/SilentGuardian_v<版本号>.apk`
4. 读取根目录 `update_config.json` 中的字段，生成含最新版本号与下载链接的 JSON，scp 上传覆盖服务器上的 `update_config.json`

**第三步半（可选）：同步发不到 GitHub Release**

`deploy.sh` 支持 `--github` 参数（或环境变量 `SG_RELEASE_GITHUB=1`），会在 GitHub 上为 `v<versionName>` 创建 Release 并挂载签名 APK 附件，Release 说明自动复用 `update_config.json` 的双语 changelog。tag 由脚本在当前 HEAD 自动创建，**无需再手动 `git tag`**。幂等：重复执行不会因 release/asset 已存在而报错。

```bash
bash deploy.sh --github
```

> Token 默认走 git 凭证（与 `git push` 同源，零配置）；也可用 `SG_GITHUB_TOKEN` 环境变量覆盖。依赖 `jq`。

### 6.2 update_config.json 字段说明

| 字段 | 说明 |
|------|------|
| `latestVersionCode` | 与 `build.gradle.kts` 中 `versionCode` 保持一致 |
| `latestVersionName` | 与 `build.gradle.kts` 中 `versionName` 保持一致 |
| `forceUpdate` | `true` 表示强制更新，用户无法跳过；一般设为 `false` |
| `updateTitle` | 更新弹窗标题，建议写 `发现新版本 v<版本号>` |
| `updateMessage` | 更新日志，支持 `\n` 换行，列出本版本改动要点 |
| `downloadUrl` | APK 下载直链，格式固定为 `https://www.yes-tek.com/assets/apk/SilentGuardian.apk` |

## 7. AI 协作与长效维护规范 (AI Collaboration & Maintenance)

为了确保多个 AI Agent 在长周期的交接中不产生上下文断层和代码腐化，必须严格遵守以下规范：

### 7.1 强制自测与交付闭环
- **编译自证**：任何 UI 调整、逻辑修改或库更新完成后，Agent 必须主动通过工具执行 `gradle assembleDebug` (或对应的构建命令) 验证编译。只有在确认 `BUILD SUCCESSFUL` 之后，方可向主控用户汇报“修改已完成”。绝不允许交付带有语法错误或漏导包的半成品。

### 7.2 本地存储 (MMKV) 键值集中管理
- **消除魔法字符串**：所有的 MMKV Key 必须统一在 `DataManager` 的 `companion object` 中声明为全局常量（`const val`）。严禁在业务 Fragment 或 Service 中直接硬编码字符串（Magic Strings）作为存取 Key。

### 7.3 UI 与资源硬编码禁令
- **颜色与尺寸**：严禁在 `layout.xml` 中直接硬编码十六进制颜色值（如 `#FF0000`），必须引用 `@color/` 或主题属性 `?attr/`；尺寸边距应规范使用常用 dp 间距，以便后续深色模式适配和 UI 统一。

### 7.4 跨 AI 上下文的注释原则
- **Why over What**：代码注释不仅仅要说明“做了什么”，更要清晰说明“为什么这么做”（业务背景和取舍）。
- **兜底标记**：遇到绕过系统限制的特殊处理（如处理 Doze 机制的跳变），必须在注释中显式打上 `[Hack]` 或 `[Failsafe]` 标签，警告后续接手的 AI 切勿将这些“看似冗余”的代码轻易精简或删除。

### 7.5 Git 提交规范
- 强制采用常规提交 (Conventional Commits) 规范，保持 AI 生成的提交历史清晰可溯：
  - `feat:` (新功能)
  - `fix:` (修复 bug)
  - `refactor:` (代码重构，不改变功能)
  - `chore:` (构建过程、脚本改动或发版)


