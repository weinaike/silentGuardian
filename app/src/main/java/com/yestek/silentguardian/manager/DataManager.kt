package com.yestek.silentguardian.manager

import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataManager {
    private val kv = MMKV.defaultMMKV()

    var isServiceEnabled: Boolean
        get() = kv.decodeBool("isServiceEnabled", false)
        set(value) {
            val oldValue = kv.decodeBool("isServiceEnabled", false)
            if (oldValue != value) {
                // 方案 C: 当守护服务被重新开启时，清空所有当天的统计数据，重头开始
                if (value) {
                    clearAllUsageData()
                }
                kv.encode("isServiceEnabled", value)
            }
        }

    var managedApps: Set<String>
        get() = kv.decodeStringSet("managedApps", emptySet()) ?: emptySet()
        set(value) { kv.encode("managedApps", value) }

    // Phase 6: Security PIN
    var appPinCode: String
        get() = kv.decodeString("appPinCode", "") ?: ""
        set(value) { kv.encode("appPinCode", value) }

    // In-memory lock state. True by default so cold starts are always locked.
    var isAppUnlocked: Boolean = false

    // Phase 4: Global Time Controls
    var dailyTotalLimitMinutes: Int
        get() = kv.decodeInt("dailyTotalLimitMinutes", 120)
        set(value) { kv.encode("dailyTotalLimitMinutes", value) }

    var continuousLimitMinutes: Int
        get() = kv.decodeInt("continuousLimitMinutes", 30)
        set(value) { kv.encode("continuousLimitMinutes", value) }

    var cooldownMinutes: Int
        get() = kv.decodeInt("cooldownMinutes", 20)
        set(value) {
            val old = kv.decodeInt("cooldownMinutes", 20)
            kv.encode("cooldownMinutes", value)
            // 如果当前正在冷却期中，按新时长重新计算截止时间，防止"改短规则仍按旧时长倒计时"的 bug
            val currentEnd = kv.decodeLong("cooldownEndTime", 0L)
            val now = System.currentTimeMillis()
            if (currentEnd > now) {
                // 冷却开始时间 = 旧截止时间 - 旧冷却时长
                val cooldownStart = currentEnd - (old * 60_000L)
                // 用旧开始时间 + 新时长重新计算截止时间
                val newEnd = cooldownStart + (value * 60_000L)
                // 如果新截止时间已经过去，直接清除冷却
                kv.encode("cooldownEndTime", if (newEnd > now) newEnd else 0L)
            }
        }

    // Phase 4: Session State
    var currentSessionSeconds: Int
        get() {
            checkAndResetCrossDay()
            return kv.decodeInt("currentSessionSeconds", 0)
        }
        set(value) { kv.encode("currentSessionSeconds", value) }

    var lastActiveTimestamp: Long
        get() = kv.decodeLong("lastActiveTimestamp", 0L)
        set(value) { kv.encode("lastActiveTimestamp", value) }

    var cooldownEndTime: Long
        get() = kv.decodeLong("cooldownEndTime", 0L)
        set(value) { kv.encode("cooldownEndTime", value) }

    fun getGlobalUsedSecondsToday(): Int {
        checkAndResetCrossDay()
        // 从各 App 的已用秒数求和派生，避免与独立计数器脱钩
        var total = 0
        managedApps.forEach { pkg ->
            total += kv.decodeInt("used_$pkg", 0)
        }
        return total
    }

    // 计时引擎结算时间戳，持久化以支持服务重启后续算
    var lastSettledTimestamp: Long
        get() = kv.decodeLong("lastSettledTimestamp", 0L)
        set(value) { kv.encode("lastSettledTimestamp", value) }

    fun getAppUsedSecondsToday(pkgName: String): Int {
        checkAndResetCrossDay()
        return kv.decodeInt("used_$pkgName", 0)
    }

    fun addAppUsedSeconds(pkgName: String, seconds: Int) {
        checkAndResetCrossDay()
        val current = getAppUsedSecondsToday(pkgName)
        kv.encode("used_$pkgName", current + seconds)
        // 全局秒数由 getGlobalUsedSecondsToday() 从各 App 求和派生，无需独立维护
    }

    fun getAppScreenSecondsToday(pkgName: String): Int {
        checkAndResetCrossDay()
        return kv.decodeInt("screen_$pkgName", 0)
    }

    fun addAppScreenSeconds(pkgName: String, seconds: Int) {
        checkAndResetCrossDay()
        val current = getAppScreenSecondsToday(pkgName)
        kv.encode("screen_$pkgName", current + seconds)
    }

    fun getAppCallSecondsToday(pkgName: String): Int {
        checkAndResetCrossDay()
        return kv.decodeInt("call_$pkgName", 0)
    }

    fun addAppCallSeconds(pkgName: String, seconds: Int) {
        checkAndResetCrossDay()
        val current = getAppCallSecondsToday(pkgName)
        kv.encode("call_$pkgName", current + seconds)
    }

    private fun checkAndResetCrossDay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        val lastRecordDate = kv.decodeString("last_record_date", "")
        
        if (lastRecordDate != todayStr) {
            // It's a new day! Reset all individual app used times
            clearAllUsageData()
            kv.encode("last_record_date", todayStr)
        }
    }

    fun clearAllUsageData() {
        managedApps.forEach { pkg ->
            kv.removeValueForKey("used_$pkg")
            kv.removeValueForKey("screen_$pkg")
            kv.removeValueForKey("call_$pkg")
        }
        kv.removeValueForKey("currentSessionSeconds")
        kv.removeValueForKey("cooldownEndTime")
        // 将结算时间戳设为当前时间，防止重启服务后追溯历史或从 0 计算
        lastSettledTimestamp = System.currentTimeMillis()
    }
}
