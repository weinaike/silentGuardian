# 核心技术可行性验证 Demo (MicTest) - AI Agent 生成指令

> **[To AI Agent] System Prompt & Task Objective:**
> 你是一个高级 Android 开发专家。请帮我从零创建一个极简的 Android 测试项目，用于验证系统级 API `AudioManager.setMicrophoneMute(true)` 在目标设备上的真实表现，以确认该 API 是否被手机厂商底层魔改或屏蔽。
> 本项目不需要复杂的架构或精美的 UI，重在“快速验证底层机制”。请严格按照以下要求生成代码。

### 1. 工程基础配置
* **Application ID:** `com.example.mictest`
* **开发语言:** 纯 Kotlin
* **SDK 版本:** `minSdkVersion 24`, `targetSdkVersion 34`
* **UI 框架:** 原生 XML + `findViewById` 即可（无需 ViewBinding 或 Compose，越精简越好，避免引入额外依赖）。

### 2. AndroidManifest.xml 核心要求
必须在 `<manifest>` 标签下声明以下权限，否则调用系统音频 API 会失效：
* `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />`

### 3. UI 布局设计 (activity_main.xml)
请使用 `LinearLayout` 进行垂直居中排版，包含以下 4 个元素，并保持适当的间距 (`margin`)：
* **TextView (`tvStatus`):** 用于显示当前麦克风状态。默认文本“当前状态：未知”，字号 20sp，居中加粗。
* **Button (`btnStandardMute`):** 文本为“1. 标准一键静音 (Standard Mute)”。
* **Button (`btnHackMute`):** 文本为“2. 强制通讯模式静音 (Hack Mute)”。用于突破部分厂商的底层拦截。
* **Button (`btnUnmute`):** 文本为“3. 恢复麦克风 (Unmute)”。

### 4. 核心管控逻辑 (MainActivity.kt)
请在 MainActivity 中实现以下逻辑闭环：

1.  **初始化:** 在 `onCreate` 中获取系统 `AudioManager` 实例。
2.  **状态刷新机制:** 编写一个私有方法 `updateStatusView()`。该方法需读取 `audioManager.isMicrophoneMute` 的真实值，如果为 true，让 `tvStatus` 显示红色文本“麦克风已物理静音”；如果为 false，显示绿色文本“麦克风收音正常”。每次点击按钮后必须调用此方法。
3.  **标准静音逻辑 (`btnStandardMute`):**
    * 直接调用 `audioManager.isMicrophoneMute = true`。
    * 使用 `Toast` 提示下发结果。
4.  **强制通讯模式静音逻辑 (`btnHackMute`):**
    * 先将系统音频模式修改为通讯状态：`audioManager.mode = AudioManager.MODE_IN_COMMUNICATION`。
    * 再调用 `audioManager.isMicrophoneMute = true`。
    * 使用 `Toast` 提示下发结果。
5.  **恢复逻辑 (`btnUnmute`):**
    * 将系统音频模式恢复正常：`audioManager.mode = AudioManager.MODE_NORMAL`。
    * 调用 `audioManager.isMicrophoneMute = false`。
    * 使用 `Toast` 提示恢复完成。

> **[To AI Agent] 执行要求：**
> 请理解以上技术痛点和测试需求，不要省略核心逻辑。请一次性输出 `AndroidManifest.xml`、`activity_main.xml` 和 `MainActivity.kt` 这三个文件的完整可用代码。
