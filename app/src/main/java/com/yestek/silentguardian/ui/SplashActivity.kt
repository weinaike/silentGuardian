package com.yestek.silentguardian.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yestek.silentguardian.MainActivity
import com.yestek.silentguardian.manager.DataManager
import com.tencent.mmkv.MMKV

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sp = getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val isPrivacyAccepted = sp.getBoolean("is_privacy_accepted", false)

        if (isPrivacyAccepted) {
            initAppAndStart()
        } else {
            showPrivacyDialog()
        }
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("用户协议与隐私政策")
            .setMessage("欢迎使用 SilentGuardian！\n\n我们非常重视您的隐私保护。在您使用本应用前，请仔细阅读以下内容：\n\n1. 本应用需要获取您的“应用使用时间（Usage Stats）”权限以计算防沉迷时间。\n2. 本应用需要使用“本地 VPN 服务 (VpnService)”来在额度耗尽时阻断网络，此 VPN 仅在设备本地运行，绝对不会收集或上传您的任何流量数据。\n3. 所有数据（包括使用时间、应用列表）均仅存储于您的设备本地，不涉及任何云端上传。\n\n请点击“同意”开始使用。")
            .setCancelable(false)
            .setPositiveButton("同意") { _, _ ->
                getSharedPreferences("app_config", Context.MODE_PRIVATE).edit().putBoolean("is_privacy_accepted", true).apply()
                initAppAndStart()
            }
            .setNegativeButton("不同意并退出") { _, _ ->
                finish()
            }
            .show()
    }

    private fun initAppAndStart() {
        // 在用户同意后，才初始化第三方 SDK 和数据管理器
        MMKV.initialize(applicationContext)
        
        // 判断是否需要引导权限
        val intent = if (!com.yestek.silentguardian.manager.DataManager.isServiceEnabled) {
            // 如果服务没开启，说明没走完权限流程（或者主动关了），跳权限引导
            Intent(this, PermissionActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
