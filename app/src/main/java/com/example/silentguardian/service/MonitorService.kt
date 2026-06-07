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
import androidx.core.app.NotificationCompat
import com.example.silentguardian.BlackholeVpnService
import com.example.silentguardian.manager.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isPolling = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Lock the app when screen goes off
                DataManager.isAppUnlocked = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
        
        startForegroundService()
        startPolling()
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

    private fun getActiveAudioUids(): Set<Int> {
        val uids = mutableSetOf<Int>()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val recordingConfigs = audioManager.activeRecordingConfigurations
            recordingConfigs?.forEach { config ->
                try {
                    val uid = config.javaClass.getMethod("getClientUid").invoke(config) as Int
                    uids.add(uid)
                } catch (e: Exception) {}
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackConfigs = audioManager.activePlaybackConfigurations
            playbackConfigs?.forEach { config ->
                try {
                    val uid = config.javaClass.getMethod("getClientUid").invoke(config) as Int
                    uids.add(uid)
                } catch (e: Exception) {}
            }
        }
        return uids
    }

    private val appNetworkUsage = mutableMapOf<Int, Long>()

    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        serviceScope.launch {
            while (isPolling) {
                // 1. 若主开关未开，停止计次并解除一切拦截
                if (!DataManager.isServiceEnabled) {
                    updateVpnState(emptySet())
                    delay(5000)
                    continue
                }

                // 2. 通话防误伤检测
                val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                var isCallActive = false
                try {
                    isCallActive = telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE
                } catch (e: SecurityException) {
                    // Ignore missing READ_PHONE_STATE permission, default to false
                }

                if (isCallActive) {
                    updateVpnState(emptySet())
                    delay(5000)
                    continue
                }
                
                val currentTime = System.currentTimeMillis()
                
                // 3. Pre-check Global Block (Condition A & B)
                var isGlobalBlocked = false
                val totalGlobalSecs = DataManager.getGlobalUsedSecondsToday()
                val dailyLimitSecs = DataManager.dailyTotalLimitMinutes * 60
                val cooldownEnd = DataManager.cooldownEndTime
                
                if (dailyLimitSecs > 0 && totalGlobalSecs >= dailyLimitSecs) isGlobalBlocked = true
                if (currentTime < cooldownEnd) isGlobalBlocked = true
                


                // 5. 并行检测与时间累加
                val activeApps = getActiveApps()
                val managedApps = DataManager.managedApps
                var isAnyManagedAppActive = false

                for (pkgName in managedApps) {
                    val appState = activeApps[pkgName]
                    if (appState != null) {
                        
                        val isValidCall = appState.isCall
                        val isValidActive = appState.isScreen || isValidCall

                        if (isValidActive && !isGlobalBlocked) {
                            isAnyManagedAppActive = true
                            if (appState.isScreen) {
                                DataManager.addAppScreenSeconds(pkgName, 1)
                            }
                            if (isValidCall) {
                                DataManager.addAppCallSeconds(pkgName, 1)
                            }
                            DataManager.addAppUsedSeconds(pkgName, 1)
                        }
                    }
                }
                
                // 6. 连续使用与闲置重置逻辑
                if (isAnyManagedAppActive) {
                    DataManager.lastActiveTimestamp = currentTime
                    DataManager.currentSessionSeconds += 1
                } else {
                    val lastActive = DataManager.lastActiveTimestamp
                    if (lastActive > 0 && (currentTime - lastActive) > 5 * 60 * 1000L) { // 5 mins idle
                        DataManager.currentSessionSeconds = 0
                    }
                }
                
                // 7. 再次检测是否触发阻断（Condition C 连续使用耗尽）
                val newTotalGlobalSecs = DataManager.getGlobalUsedSecondsToday()
                if (dailyLimitSecs > 0 && newTotalGlobalSecs >= dailyLimitSecs) isGlobalBlocked = true
                
                val continuousLimitSecs = DataManager.continuousLimitMinutes * 60
                if (!isGlobalBlocked && continuousLimitSecs > 0 && DataManager.currentSessionSeconds >= continuousLimitSecs) {
                    DataManager.cooldownEndTime = currentTime + (DataManager.cooldownMinutes * 60_000L)
                    DataManager.currentSessionSeconds = 0 // 冷却开始，当前连续时长清零
                    isGlobalBlocked = true
                }
                
                if (isGlobalBlocked) {
                    updateVpnState(managedApps)
                } else {
                    updateVpnState(emptySet())
                }
                
                android.util.Log.d("MonitorService", "Polling... isGlobalBlocked=$isGlobalBlocked, session=${DataManager.currentSessionSeconds}, total=$newTotalGlobalSecs")

                delay(1000) // 1 秒轮询一次
            }
        }
    }

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

    private val activeFgServices = mutableSetOf<String>()
    private var lastActiveApp: String? = null
    private var lastEventTime: Long = 0

    private fun updateActiveApps() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        val events = if (lastEventTime == 0L) {
            // Initial deep query (last 24 hours) to find the current state
            usm.queryEvents(time - 1000 * 3600 * 24, time)
        } else {
            // Incremental query
            usm.queryEvents(lastEventTime, time)
        }
        
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, 1 -> {
                    lastActiveApp = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED, 2 -> {
                    if (event.packageName == lastActiveApp) {
                        lastActiveApp = null
                    }
                }
                19 -> { // FOREGROUND_SERVICE_START
                    activeFgServices.add(event.packageName)
                }
                20 -> { // FOREGROUND_SERVICE_STOP
                    activeFgServices.remove(event.packageName)
                }
            }
        }
        lastEventTime = time
    }

    data class AppState(val isScreen: Boolean, val isCall: Boolean)

    private fun getActiveApps(): Map<String, AppState> {
        updateActiveApps()
        val activeApps = mutableMapOf<String, AppState>()
        
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = pm.isInteractive
        var currentFgApp = if (isScreenOn) lastActiveApp else null

        // Phase 4 Bugfix 1: Gemini Alias
        if (currentFgApp == "com.google.android.googlequicksearchbox") {
            currentFgApp = "com.google.android.apps.bard"
        }

        // Merge foreground services
        activeFgServices.forEach { pkg ->
            activeApps[pkg] = AppState(
                isScreen = (pkg == currentFgApp),
                isCall = true
            )
        }
        
        // Add foreground app if not already added
        if (currentFgApp != null && !activeApps.containsKey(currentFgApp)) {
            activeApps[currentFgApp] = AppState(
                isScreen = true,
                isCall = false
            )
        }
        
        return activeApps
    }

    override fun onDestroy() {
        super.onDestroy()
        isPolling = false
        unregisterReceiver(screenReceiver)
        updateVpnState(emptySet())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
