package com.yestek.silentguardian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.yestek.silentguardian.MainActivity
import com.yestek.silentguardian.R
import com.yestek.silentguardian.utils.AdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("SetJavaScriptEnabled")
class SplashActivity : AppCompatActivity() {

    private var countdownJob: Job? = null
    private var isStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sp = getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val isPrivacyAccepted = sp.getBoolean("is_privacy_accepted", false)

        if (isPrivacyAccepted) {
            checkAndShowAd()
        } else {
            showPrivacyDialog()
        }
    }

    private fun checkAndShowAd() {
        lifecycleScope.launch {
            val adConfig = withTimeoutOrNull(2000L) {
                AdManager.fetchSplashAdConfig()
            }
            
            if (adConfig != null && adConfig.enabled && adConfig.adUrl.isNotEmpty()) {
                showAdAndStartCountdown(adConfig.adUrl, adConfig.durationSeconds)
            } else {
                initAppAndStart()
            }
        }
    }

    private fun showAdAndStartCountdown(url: String, duration: Int) {
        setContentView(R.layout.activity_splash)
        
        val webView = findViewById<WebView>(R.id.webViewAd)
        val tvSkipAd = findViewById<TextView>(R.id.tvSkipAd)
        
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url ?: "")
                return true
            }
        }
        webView.webChromeClient = WebChromeClient()
        
        webView.loadUrl(url)
        
        tvSkipAd.visibility = View.VISIBLE
        tvSkipAd.setOnClickListener {
            countdownJob?.cancel()
            initAppAndStart()
        }
        
        countdownJob = lifecycleScope.launch {
            for (i in duration downTo 1) {
                tvSkipAd.text = "跳过 $i"
                delay(1000)
            }
            initAppAndStart()
        }
    }

    private fun showPrivacyDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("用户协议与隐私政策")
            .setMessage(com.yestek.silentguardian.utils.PrivacyPolicyConstants.POLICY_TEXT)
            .setCancelable(false)
            .setPositiveButton("同意") { _, _ ->
                getSharedPreferences("app_config", Context.MODE_PRIVATE).edit().putBoolean("is_privacy_accepted", true).apply()
                checkAndShowAd()
            }
            .setNegativeButton("不同意并退出") { _, _ ->
                finish()
            }
            .show()
    }

    private fun initAppAndStart() {
        if (isStarted) return
        isStarted = true
        
        // 在用户同意后，才初始化第三方 SDK 和数据管理器
        MMKV.initialize(applicationContext)

        if (!com.yestek.silentguardian.manager.DataManager.hasAutoInitializedApps) {
            val pm = packageManager
            val autoApps = mutableSetOf<String>()
            try {
                pm.getPackageInfo("com.larus.nova", 0)
                autoApps.add("com.larus.nova")
            } catch (e: Exception) {}
            try {
                pm.getPackageInfo("com.tencent.hunyuan.app.chat", 0)
                autoApps.add("com.tencent.hunyuan.app.chat")
            } catch (e: Exception) {}
            
            if (autoApps.isNotEmpty()) {
                val currentApps = com.yestek.silentguardian.manager.DataManager.managedApps.toMutableSet()
                currentApps.addAll(autoApps)
                com.yestek.silentguardian.manager.DataManager.managedApps = currentApps
            }
            com.yestek.silentguardian.manager.DataManager.hasAutoInitializedApps = true
        }
        
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
