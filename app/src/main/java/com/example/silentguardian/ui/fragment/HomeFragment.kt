package com.example.silentguardian.ui.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.silentguardian.R
import com.example.silentguardian.manager.DataManager
import com.example.silentguardian.service.MonitorService
import com.example.silentguardian.utils.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var switchService: Switch
    private lateinit var tvServiceStatus: TextView
    private lateinit var cardService: LinearLayout
    private lateinit var tvUsedMinutes: TextView
    private lateinit var tvLimitMinutes: TextView
    private lateinit var pbUsage: ProgressBar
    private lateinit var llManagedApps: LinearLayout
    private lateinit var llVpnNotice: LinearLayout
    
    private lateinit var tvSessionMinutes: TextView
    private lateinit var tvSessionLimit: TextView
    private lateinit var pbSessionUsage: ProgressBar
    private lateinit var tvCooldownStatus: TextView
    private lateinit var llSessionCard: LinearLayout
    private lateinit var tvSessionTitle: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        switchService = view.findViewById(R.id.switchService)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        cardService = view.findViewById(R.id.cardService)
        tvUsedMinutes = view.findViewById(R.id.tvUsedMinutes)
        tvLimitMinutes = view.findViewById(R.id.tvLimitMinutes)
        pbUsage = view.findViewById(R.id.pbUsage)
        llManagedApps = view.findViewById(R.id.llManagedApps)
        llVpnNotice = view.findViewById(R.id.llVpnNotice)
        
        tvSessionMinutes = view.findViewById(R.id.tvSessionMinutes)
        tvSessionLimit = view.findViewById(R.id.tvSessionLimit)
        pbSessionUsage = view.findViewById(R.id.pbSessionUsage)
        tvCooldownStatus = view.findViewById(R.id.tvCooldownStatus)
        llSessionCard = view.findViewById(R.id.llSessionCard)
        tvSessionTitle = view.findViewById(R.id.tvSessionTitle)
        
        switchService.setOnCheckedChangeListener { _, isChecked ->
            DataManager.isServiceEnabled = isChecked
            updateServiceCardUI(isChecked)
            
            if (isChecked) {
                requireContext().startService(Intent(requireContext(), MonitorService::class.java))
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        updateServiceCardUI(DataManager.isServiceEnabled)
        switchService.isChecked = DataManager.isServiceEnabled
        refreshManagedApps()
        startUIRefreshLoop()
    }

    private fun updateServiceCardUI(isEnabled: Boolean) {
        val tvServiceTitle = view?.findViewById<TextView>(R.id.tvServiceTitle)
        if (isEnabled) {
            tvServiceStatus.text = "运行中"
            tvServiceStatus.setTextColor(android.graphics.Color.parseColor("#D9FFFFFF"))
            tvServiceTitle?.setTextColor(android.graphics.Color.WHITE)
            cardService.setBackgroundResource(R.drawable.bg_service_card_on)
            llVpnNotice.visibility = View.VISIBLE
        } else {
            tvServiceStatus.text = "已暂停"
            tvServiceStatus.setTextColor(android.graphics.Color.parseColor("#9AA0A6"))
            tvServiceTitle?.setTextColor(android.graphics.Color.parseColor("#1B1C18"))
            cardService.setBackgroundResource(R.drawable.bg_card_surface)
            llVpnNotice.visibility = View.GONE
        }
    }

    private fun refreshManagedApps() {
        llManagedApps.removeAllViews()
        val apps = DataManager.managedApps
        if (apps.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply { text = "暂无受守护应用" }
            llManagedApps.addView(emptyTv)
            return
        }
        
        val pm = requireContext().packageManager
        apps.forEach { pkgName ->
            var appName = pkgName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val info = pm.getApplicationInfo(pkgName, 0)
                appName = pm.getApplicationLabel(info).toString()
                icon = pm.getApplicationIcon(info)
            } catch (e: Exception) {
                // Ignore and use pkgName
            }
            
            val view = layoutInflater.inflate(R.layout.item_managed_app, llManagedApps, false)
            view.tag = pkgName
            view.findViewById<TextView>(R.id.tvAppName).text = appName
            if (icon != null) {
                view.findViewById<android.widget.ImageView>(R.id.ivAppIcon).setImageDrawable(icon)
            }

            // --- Set actual data ---
            val usedSecs = DataManager.getAppUsedSecondsToday(pkgName)
            val screenSecs = DataManager.getAppScreenSecondsToday(pkgName)
            val callSecs = DataManager.getAppCallSecondsToday(pkgName)
            val totalLimitMinutes = DataManager.dailyTotalLimitMinutes
            
            val usedStr = String.format("%02d:%02d", usedSecs / 60, usedSecs % 60)
            val limitStr = String.format("%02d:00", totalLimitMinutes)
            val screenStr = String.format("%02d:%02d", screenSecs / 60, screenSecs % 60)
            val callStr = String.format("%02d:%02d", callSecs / 60, callSecs % 60)
            
            view.findViewById<TextView>(R.id.tvAppUsage).text = "$usedStr / $limitStr"
            view.findViewById<TextView>(R.id.tvAppUsageDetail).text = "(亮屏: $screenStr | 通话: $callStr)"

            llManagedApps.addView(view)
        }
    }

    private var isRefreshing = false

    private fun startUIRefreshLoop() {
        if (isRefreshing) return
        isRefreshing = true
        
        lifecycleScope.launch {
            while (isRefreshing) {
                val totalUsedSecs = DataManager.getGlobalUsedSecondsToday()
                val totalLimitSecs = DataManager.dailyTotalLimitMinutes * 60
                
                val totalMin = totalUsedSecs / 60
                val totalSec = totalUsedSecs % 60
                tvUsedMinutes.text = String.format("%02d:%02d", totalMin, totalSec)
                tvLimitMinutes.text = " / ${DataManager.dailyTotalLimitMinutes}:00"
                pbUsage.max = totalLimitSecs
                pbUsage.progress = totalUsedSecs

                updateSessionUI()
                
                // Update managed apps data dynamically without removeAllViews
                for (i in 0 until llManagedApps.childCount) {
                    val view = llManagedApps.getChildAt(i)
                    val pkgName = view.tag as? String ?: continue
                    
                    val usedSecs = DataManager.getAppUsedSecondsToday(pkgName)
                    val screenSecs = DataManager.getAppScreenSecondsToday(pkgName)
                    val callSecs = DataManager.getAppCallSecondsToday(pkgName)
                    val totalLimitMinutes = DataManager.dailyTotalLimitMinutes
                    
                    val usedStr = String.format("%02d:%02d", usedSecs / 60, usedSecs % 60)
                    val limitStr = String.format("%02d:00", totalLimitMinutes)
                    val screenStr = String.format("%02d:%02d", screenSecs / 60, screenSecs % 60)
                    val callStr = String.format("%02d:%02d", callSecs / 60, callSecs % 60)
                    
                    view.findViewById<TextView>(R.id.tvAppUsage).text = "$usedStr / $limitStr"
                    view.findViewById<TextView>(R.id.tvAppUsageDetail).text = "(亮屏: $screenStr | 通话: $callStr)"
                }
                
                delay(1000)
            }
        }
    }
    
    private fun updateSessionUI() {
        val totalUsedSecs = DataManager.getGlobalUsedSecondsToday()
        val totalLimitSecs = DataManager.dailyTotalLimitMinutes * 60
        val isTotalExhausted = totalLimitSecs > 0 && totalUsedSecs >= totalLimitSecs
        
        if (isTotalExhausted) {
            llSessionCard.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
            tvSessionTitle.text = "今日额度已用尽"
            tvSessionTitle.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            tvSessionMinutes.text = String.format("%02d:00", DataManager.dailyTotalLimitMinutes)
            tvSessionLimit.text = " / ${DataManager.dailyTotalLimitMinutes}:00"
            pbSessionUsage.progress = 100
            tvCooldownStatus.visibility = View.VISIBLE
            tvCooldownStatus.text = "明天再来吧！"
            return
        }
        
        val currentSessionSecs = DataManager.currentSessionSeconds
        val sessionLimitSecs = DataManager.continuousLimitMinutes * 60
        
        if (currentSessionSecs > 0) {
            llSessionCard.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
            tvSessionTitle.text = "单次使用中"
            tvSessionTitle.setTextColor(android.graphics.Color.parseColor("#2196F3"))
            val curMin = currentSessionSecs / 60
            val curSec = currentSessionSecs % 60
            tvSessionMinutes.text = String.format("%02d:%02d", curMin, curSec)
            tvSessionLimit.text = " / ${DataManager.continuousLimitMinutes}:00"
            pbSessionUsage.max = sessionLimitSecs
            pbSessionUsage.progress = currentSessionSecs
            tvCooldownStatus.visibility = View.GONE
        } else {
            val cooldownEndTime = DataManager.cooldownEndTime
            if (cooldownEndTime > System.currentTimeMillis()) {
                val remainingSecs = ((cooldownEndTime - System.currentTimeMillis()) / 1000).toInt()
                val cooldownLimitSecs = DataManager.cooldownMinutes * 60
                
                llSessionCard.setBackgroundColor(android.graphics.Color.parseColor("#F3E5F5"))
                tvSessionTitle.text = "强制休息中"
                tvSessionTitle.setTextColor(android.graphics.Color.parseColor("#9C27B0"))
                val remMin = remainingSecs / 60
                val remSec = remainingSecs % 60
                tvSessionMinutes.text = String.format("%02d:%02d", remMin, remSec)
                tvSessionLimit.text = " / ${DataManager.cooldownMinutes}:00 (剩余)"
                pbSessionUsage.max = cooldownLimitSecs
                pbSessionUsage.progress = remainingSecs
                tvCooldownStatus.visibility = View.VISIBLE
                tvCooldownStatus.text = "请放下手机休息一下眼睛"
                return
            }
            
            llSessionCard.setBackgroundColor(android.graphics.Color.WHITE)
            tvSessionTitle.text = "可使用状态"
            tvSessionTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            tvSessionMinutes.text = "00:00"
            tvSessionLimit.text = " / ${DataManager.continuousLimitMinutes}:00"
            pbSessionUsage.progress = 0
            tvCooldownStatus.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        isRefreshing = false
    }
}
