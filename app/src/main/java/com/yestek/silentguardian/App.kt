package com.yestek.silentguardian

import android.app.Application
import com.tencent.mmkv.MMKV

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 极速本地存储框架 MMKV 初始化 (仅在用户已同意隐私政策后初始化)
        val sp = getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        if (sp.getBoolean("is_privacy_accepted", false)) {
            MMKV.initialize(this)
            
            val config = com.microsoft.clarity.ClarityConfig("x54dzfq4fm")
            com.microsoft.clarity.Clarity.initialize(applicationContext, config)
        }
        
        // 全局异常捕获，兜底恢复网络
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            // 发生异常时强制拉起服务去销毁 VPN（如果有的话），防止网络假死
            try {
                val intent = android.content.Intent(this, BlackholeVpnService::class.java).apply {
                    action = "STOP"
                }
                startService(intent)
            } catch (e: Exception) {
                // Ignore
            }
            // 默认继续抛出导致闪退，避免僵尸进程
            kotlin.system.exitProcess(1)
        }
    }
}
