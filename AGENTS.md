# SilentGuardian (AI语音陪伴防沉迷系统) - AI Agent 开发规范与指南

这份文档旨在为参与开发此项目的 AI Agent 提供核心的开发规范、架构原则和注意事项。请在生成任何代码或修改架构时，严格阅读并遵守以下准则。

## 1. 核心开发规范

- **开发语言**: 统一采用 **Kotlin**（包含 Coroutines），严禁混用 Java。
- **目标 SDK 版本**: `minSdkVersion` = 24 (Android 7.0), `targetSdkVersion` = 34 (Android 14)。
- **包名约定**: `com.example.silentguardian`。
- **UI 架构**: 采用 **Material Components + ViewBinding + ViewModel** 模式。未经主控用户同意，不得私自引入 Compose 或其他 UI 框架。

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
- **配置维护 (Submodule)**: 本地通过引入子模块 `gitee_release` 统一管理和更新发布配置。该配置项目位于 `https://gitee.com/weinaike/silentGuardian`，重点维护其中的 `update_config.json` 文件以触发端侧更新。
- **Gitee Token 凭据**: 更新配置仓库时需要鉴权。请使用 Git 的 Credential Helper 进行本地持久化保存，或者在部署前配置环境变量。严禁将私人 Token 明文提交到任何文档或代码中！
