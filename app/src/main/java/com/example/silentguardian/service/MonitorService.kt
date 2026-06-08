package com.example.silentguardian.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.silentguardian.BlackholeVpnService
import com.example.silentguardian.manager.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        private const val POLL_INTERVAL_MS = 5000L // 5 秒轮询
        private const val VOICE_SESSION_TIMEOUT_MS = 2 * 60 * 1000L // 2 分钟会话超时
        private const val IDLE_RESET_MS = 5 * 60 * 1000L // 5 分钟闲置重置连续会话
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isPolling = false

    // 系统服务缓存，避免在轮询热路径中重复获取
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var telephonyManager: TelephonyManager

    // 亮屏计时的结算时间戳通过 DataManager.lastSettledTimestamp 持久化
    // 以支持服务被杀后重启续算

    // --- 通话计时：语音会话表（包名 → 会话信息）---
    private data class VoiceSession(
        val packageName: String,
        val sessionStartTime: Long,
        var lastPlaybackTime: Long,
        var lastSettledTime: Long // 上次结算到的时间点
    )

    private val voiceSessions = mutableMapOf<String, VoiceSession>()
    private val activeFgServices = mutableSetOf<String>()

    // --- 包名别名映射 ---
    private val packageAliases = mapOf(
        "com.google.android.googlequicksearchbox" to "com.google.android.apps.bard"
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Lock the app when screen goes off
                    DataManager.isAppUnlocked = false
                    Log.d(TAG, "Screen OFF: app locked, voice sessions preserved")
                }
                Intent.ACTION_SCREEN_ON -> {
                    // 亮屏时清除所有语音会话（切换到亮屏计时模式）
                    settleAndClearVoiceSessions()
                    Log.d(TAG, "Screen ON: voice sessions settled and cleared")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化缓存的系统服务
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        // 如果没有持久化的结算时间戳（首次启动或升级后），初始化为当前时间，避免把升级前的全天时长重复计算
        if (DataManager.lastSettledTimestamp < getTodayStartMillis()) {
            DataManager.lastSettledTimestamp = System.currentTimeMillis()
        }

        rebuildActiveFgServicesFromHistory()
        startForegroundService()
        startPolling()
    }

    /**
     * 服务启动时调用，通过回溯最近 1 小时的事件重建 activeFgServices 初始状态。
     * 解决：服务重启后，已在运行的前台服务（如元宝通话）无法被增量事件流捕获的问题。
     */
    private fun rebuildActiveFgServicesFromHistory() {
        val usm = usageStatsManager
        val managedApps = DataManager.managedApps
        if (managedApps.isEmpty()) return

        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3600_000L, now)

        // 记录每个受管 App 最近一次前台服务事件（19 或 20），只保留最后一条
        val latestFgEvent = mutableMapOf<String, Int>()
        while (events.hasNextEvent()) {
            val e = UsageEvents.Event()
            events.getNextEvent(e)
            val pkg = packageAliases.getOrDefault(e.packageName, e.packageName)
            if (pkg in managedApps && (e.eventType == 19 || e.eventType == 20)) {
                latestFgEvent[pkg] = e.eventType
            }
        }

        // 最后一次事件是 START(19) → 说明前台服务目前仍在运行
        for ((pkg, eventType) in latestFgEvent) {
            if (eventType == 19) {
                activeFgServices.add(pkg)
                Log.d(TAG, "Restored active fg service on start: $pkg")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "守护服务运行中", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Silent Guardian")
            .setContentText("正在后台默默守护你的健康时间...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()

        startForeground(1, notification)
    }

    // ========================================================================
    // 主轮询循环
    // ========================================================================

    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        serviceScope.launch {
            while (isPolling) {
                // 1. 主开关检查
                if (!DataManager.isServiceEnabled) {
                    updateVpnState(emptySet())
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // 2. 通话防误伤检测（电话通话，非 VoIP）
                var isPhoneCallActive = false
                try {
                    isPhoneCallActive = telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE
                } catch (e: SecurityException) {
                    // Ignore missing READ_PHONE_STATE permission
                }
                if (isPhoneCallActive) {
                    updateVpnState(emptySet())
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val currentTime = System.currentTimeMillis()

                // 3. 跨日检测：如果跨日了，重置结算时间戳
                val todayStart = getTodayStartMillis()
                if (DataManager.lastSettledTimestamp < todayStart) {
                    DataManager.lastSettledTimestamp = todayStart
                }

                // 4. Pre-check 全局阻断（条件 A & B）
                var isGlobalBlocked = false
                val totalGlobalSecs = DataManager.getGlobalUsedSecondsToday()
                val dailyLimitSecs = DataManager.dailyTotalLimitMinutes * 60
                val cooldownEnd = DataManager.cooldownEndTime

                if (dailyLimitSecs > 0 && totalGlobalSecs >= dailyLimitSecs) isGlobalBlocked = true
                if (currentTime < cooldownEnd) isGlobalBlocked = true

                // 5. 时间结算（核心）
                var isAnyManagedAppActive = false

                if (!isGlobalBlocked) {
                    // === 事件对齐处理 ===
                    // 无论亮屏息屏，始终增量处理 UsageEvents，确保不漏掉前台服务启停事件
                    val isAnyScreenAppActive = settleScreenTime(currentTime)

                    // === 通话时长处理 ===
                    isAnyManagedAppActive = isAnyScreenAppActive || settleCallTime(currentTime)
                } else {
                    // 全局阻断期间跳过计时，但必须推进结算时间戳
                    // 否则解封后 settleScreenTime 会追溯整个阻断期的事件，造成时间虚增
                    DataManager.lastSettledTimestamp = currentTime
                }

                // 6. 连续使用与闲置重置
                if (isAnyManagedAppActive) {
                    DataManager.lastActiveTimestamp = currentTime
                } else {
                    val lastActive = DataManager.lastActiveTimestamp
                    if (lastActive > 0 && (currentTime - lastActive) > IDLE_RESET_MS) {
                        DataManager.currentSessionSeconds = 0
                    }
                }

                // 7. 后置阻断判定
                val newTotalGlobalSecs = DataManager.getGlobalUsedSecondsToday()
                if (dailyLimitSecs > 0 && newTotalGlobalSecs >= dailyLimitSecs) isGlobalBlocked = true

                val continuousLimitSecs = DataManager.continuousLimitMinutes * 60
                if (!isGlobalBlocked && continuousLimitSecs > 0 && DataManager.currentSessionSeconds >= continuousLimitSecs) {
                    DataManager.cooldownEndTime = currentTime + (DataManager.cooldownMinutes * 60_000L)
                    DataManager.currentSessionSeconds = 0
                    isGlobalBlocked = true
                }

                // 8. VPN 状态更新
                if (isGlobalBlocked) {
                    updateVpnState(DataManager.managedApps)
                } else {
                    updateVpnState(emptySet())
                }

                Log.d(TAG, "Poll: blocked=$isGlobalBlocked, session=${DataManager.currentSessionSeconds}, total=$newTotalGlobalSecs, voiceSessions=${voiceSessions.keys}")

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ========================================================================
    // 亮屏时间结算：queryEvents 事件对差值法
    // ========================================================================

    /**
     * 从 lastSettledTimestamp 到 currentTime 的事件中，计算每个受管 App 的前台时长。
     * 返回是否有受管 App 当前处于活跃状态。
     */
    private fun settleScreenTime(currentTime: Long): Boolean {
        val usm = usageStatsManager
        val managedApps = DataManager.managedApps
        if (managedApps.isEmpty()) return false

        // 查询从上次结算到现在的事件
        val queryStart = DataManager.lastSettledTimestamp
        val events = usm.queryEvents(queryStart, currentTime)

        // 收集事件，按包名分组
        data class TimedEvent(val packageName: String, val eventType: Int, val timestamp: Long)

        val eventList = mutableListOf<TimedEvent>()
        while (events.hasNextEvent()) {
            val e = UsageEvents.Event()
            events.getNextEvent(e)
            val canonicalPkg = packageAliases.getOrDefault(e.packageName, e.packageName)
            if (canonicalPkg in managedApps) {
                val type = e.eventType
                // ACTIVITY_RESUMED = 1
                // ACTIVITY_PAUSED = 2, END_OF_DAY = 3, ACTIVITY_STOPPED = 23, ACTIVITY_DESTROYED = 24
                // FOREGROUND_SERVICE_START = 19, FOREGROUND_SERVICE_STOP = 20
                if (type == 1 || type == 2 || type == 3 || type == 23 || type == 24 || type == 19 || type == 20) {
                    eventList.add(TimedEvent(canonicalPkg, type, e.timeStamp))
                }
            }
        }

        // 用状态机为每个 App 计算前台时长
        // 追踪当前哪个 App 在前台（同一时刻只有一个）
        var currentFgApp: String? = null
        var currentFgStartTime = 0L
        val appDurations = mutableMapOf<String, Long>() // 包名 → 本轮新增毫秒数

        // 先确定 queryStart 时刻的初始前台 App（通过查询更早的事件）
        val initialFg = findForegroundAppAt(queryStart)
        if (initialFg != null && initialFg in managedApps) {
            currentFgApp = initialFg
            currentFgStartTime = queryStart
        }

        for (event in eventList) {
            when {
                event.eventType == 1 -> {
                    // 某个 App 进入前台，先结算之前的前台 App
                    val prevFg = currentFgApp
                    if (prevFg != null && prevFg in managedApps) {
                        val duration = event.timestamp - currentFgStartTime
                        if (duration > 0) {
                            appDurations[prevFg] = (appDurations[prevFg] ?: 0L) + duration
                        }
                    }
                    currentFgApp = event.packageName
                    currentFgStartTime = event.timestamp
                }
                event.eventType == 2 || event.eventType == 3 || event.eventType == 23 || event.eventType == 24 -> {
                    // 某个 App 离开前台
                    val prevFg = currentFgApp
                    if (prevFg == event.packageName) {
                        val duration = event.timestamp - currentFgStartTime
                        if (duration > 0) {
                            appDurations[prevFg] = (appDurations[prevFg] ?: 0L) + duration
                        }
                        currentFgApp = null
                    }
                }
                event.eventType == 19 -> {
                    activeFgServices.add(event.packageName)
                }
                event.eventType == 20 -> {
                    activeFgServices.remove(event.packageName)
                }
            }
        }

        // 如果当前仍有受管 App 在前台，算到 currentTime
        var isAnyActive = false
        val finalFg = currentFgApp
        if (finalFg != null && finalFg in managedApps) {
            val duration = currentTime - currentFgStartTime
            if (duration > 0) {
                appDurations[finalFg] = (appDurations[finalFg] ?: 0L) + duration
            }
            isAnyActive = true
        }

        // 写入 DataManager
        var totalNewSeconds = 0
        for ((pkg, durationMs) in appDurations) {
            val secs = (durationMs / 1000).toInt()
            if (secs > 0) {
                DataManager.addAppScreenSeconds(pkg, secs)
                DataManager.addAppUsedSeconds(pkg, secs)
                totalNewSeconds += secs
            }
        }

        // 更新连续会话（用实际新增的秒数，而非硬编码 +1）
        if (isAnyActive && totalNewSeconds > 0) {
            DataManager.currentSessionSeconds += totalNewSeconds
        }

        // 更新结算时间戳
        DataManager.lastSettledTimestamp = currentTime

        return isAnyActive
    }

    /**
     * 查找指定时刻哪个受管 App 在前台。
     * 通过查询之前一段时间的事件，倒序找最近的 ACTIVITY_RESUMED。
     */
    private fun findForegroundAppAt(timestamp: Long): String? {
        val usm = usageStatsManager
        // 往前查 1 小时的事件来确定初始状态
        val lookbackStart = timestamp - 3600_000L
        val events = usm.queryEvents(lookbackStart, timestamp)

        val eventList = mutableListOf<UsageEvents.Event>()
        while (events.hasNextEvent()) {
            val e = UsageEvents.Event()
            events.getNextEvent(e)
            eventList.add(e)
        }

        // 倒序找最近的 RESUMED（确定当时的前台 App）
        for (i in eventList.indices.reversed()) {
            val e = eventList[i]
            if (e.eventType == 1) {
                return packageAliases.getOrDefault(e.packageName, e.packageName)
            }
            // 如果先遇到离开前台的事件，说明那一刻没有 App 在前台（或在桌面）
            if (e.eventType == 2 || e.eventType == 3 || e.eventType == 23 || e.eventType == 24) {
                return null
            }
        }
        return null
    }

    // ========================================================================
    // 通话时间结算：语音会话表
    // ========================================================================

    /**
     * 结算所有活跃的语音会话。清除超时的会话。
     * 返回是否有受管 App 当前在语音会话中。
     */
    private fun settleCallTime(currentTime: Long): Boolean {
        val managedApps = DataManager.managedApps
        val expiredSessions = mutableListOf<String>()
        var isAnyActive = false

        // 根据最新状态，刷新所有仍活跃的前台服务对应的会话
        for (pkg in activeFgServices) {
            val session = voiceSessions[pkg]
            if (session != null) {
                session.lastPlaybackTime = currentTime
            } else {
                voiceSessions[pkg] = VoiceSession(
                    packageName = pkg,
                    sessionStartTime = currentTime,
                    lastPlaybackTime = currentTime,
                    lastSettledTime = currentTime
                )
            }
        }

        for ((pkg, session) in voiceSessions) {
            if (pkg !in managedApps) {
                expiredSessions.add(pkg)
                continue
            }

            // 检查是否超时
            if (currentTime - session.lastPlaybackTime > VOICE_SESSION_TIMEOUT_MS) {
                // 会话超时：结算到 lastPlaybackTime，然后移除
                val duration = session.lastPlaybackTime - session.lastSettledTime
                if (duration > 0) {
                    val secs = (duration / 1000).toInt()
                    DataManager.addAppCallSeconds(pkg, secs)
                    DataManager.addAppUsedSeconds(pkg, secs)
                    DataManager.currentSessionSeconds += secs
                }
                expiredSessions.add(pkg)
                Log.d(TAG, "Voice session expired: $pkg")
            } else {
                // 会话仍活跃：结算到当前时刻
                val secs = ((currentTime - session.lastSettledTime) / 1000).toInt()
                if (secs > 0) {
                    DataManager.addAppCallSeconds(pkg, secs)
                    DataManager.addAppUsedSeconds(pkg, secs)
                    DataManager.currentSessionSeconds += secs
                    session.lastSettledTime = currentTime
                }
                isAnyActive = true
            }
        }

        expiredSessions.forEach { voiceSessions.remove(it) }
        return isAnyActive
    }



    /**
     * 结算并清除所有语音会话（亮屏时调用）。
     */
    private fun settleAndClearVoiceSessions() {
        val currentTime = System.currentTimeMillis()
        for ((pkg, session) in voiceSessions) {
            val duration = currentTime - session.lastSettledTime
            if (duration > 0) {
                val secs = (duration / 1000).toInt()
                if (secs > 0) {
                    DataManager.addAppCallSeconds(pkg, secs)
                    DataManager.addAppUsedSeconds(pkg, secs)
                    DataManager.currentSessionSeconds += secs
                }
            }
        }
        voiceSessions.clear()
    }

    // ========================================================================
    // VPN 状态管理
    // ========================================================================

    private var currentVpnTargets = setOf<String>()

    private fun updateVpnState(targets: Set<String>) {
        if (currentVpnTargets == targets) return
        currentVpnTargets = targets

        if (targets.isEmpty()) {
            val intent = Intent(this, BlackholeVpnService::class.java).apply {
                action = "STOP"
            }
            startService(intent)
        } else {
            val intent = Intent(this, BlackholeVpnService::class.java).apply {
                putStringArrayListExtra("PACKAGE_NAMES", ArrayList(targets))
            }
            startService(intent)
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ========================================================================
    // 生命周期
    // ========================================================================

    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程（立即中断，无需等待 delay 自然超时）
        serviceJob.cancel()
        isPolling = false
        unregisterReceiver(screenReceiver)

        // 结算残余的语音会话
        settleAndClearVoiceSessions()

        // 强制关闭 VPN，恢复网络
        updateVpnState(emptySet())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
