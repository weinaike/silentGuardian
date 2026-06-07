package com.example.silentguardian.manager

import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataManager {
    private val kv = MMKV.defaultMMKV()

    var isServiceEnabled: Boolean
        get() = kv.decodeBool("isServiceEnabled", false)
        set(value) { kv.encode("isServiceEnabled", value) }

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
        set(value) { kv.encode("cooldownMinutes", value) }

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
        return kv.decodeInt("globalUsedSecondsToday", 0)
    }

    private var lastRecordDate: String
        get() = kv.decodeString("lastRecordDate", "") ?: ""
        set(value) { kv.encode("lastRecordDate", value) }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }

    fun getAppUsedSecondsToday(pkgName: String): Int {
        checkAndResetCrossDay()
        return kv.decodeInt("used_$pkgName", 0)
    }

    fun addAppUsedSeconds(pkgName: String, seconds: Int) {
        checkAndResetCrossDay()
        val current = getAppUsedSecondsToday(pkgName)
        kv.encode("used_$pkgName", current + seconds)
        
        // Also update global tracking independently
        val currentGlobal = kv.decodeInt("globalUsedSecondsToday", 0)
        kv.encode("globalUsedSecondsToday", currentGlobal + seconds)
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

    fun getAppUsedMinutesToday(pkgName: String): Int {
        return getAppUsedSecondsToday(pkgName) / 60
    }

    // --- Private / Inner Methods ---

    private fun checkAndResetCrossDay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        val lastRecordDate = kv.decodeString("last_record_date", "")
        
        if (lastRecordDate != todayStr) {
            // It's a new day! Reset all individual app used times
            managedApps.forEach { pkg ->
                kv.removeValueForKey("used_$pkg")
                kv.removeValueForKey("screen_$pkg")
                kv.removeValueForKey("call_$pkg")
            }
            kv.removeValueForKey("globalUsedSecondsToday")
            kv.removeValueForKey("currentSessionSeconds")
            kv.removeValueForKey("cooldownEndTime")
            kv.encode("last_record_date", todayStr)
        }
    }
}
