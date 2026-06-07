package com.example.silentguardian.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.silentguardian.R

class OtherFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_other, container, false)
        
        view.findViewById<View>(R.id.cvFeedback)?.setOnClickListener {
            val deviceModel = android.os.Build.MODEL
            val osVersion = android.os.Build.VERSION.RELEASE
            val appVersionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) {
                "Unknown"
            }
            
            val subject = "[AI语音锁] 用户反馈"
            val body = "问题描述：\n\n\n-----------------\n设备：$deviceModel\n系统：Android $osVersion\n版本：v$appVersionName"
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:weinaike@foxmail.com")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "未找到邮件客户端，请先安装邮件App", Toast.LENGTH_SHORT).show()
            }
        }
        
        view.findViewById<View>(R.id.cvModifyPin)?.setOnClickListener {
            val intent = Intent(requireContext(), com.example.silentguardian.ui.PinLockActivity::class.java).apply {
                putExtra("MODIFY_MODE", true)
            }
            startActivity(intent)
        }
        
        view.findViewById<View>(R.id.cvSettings)?.setOnClickListener {
            showSettingsDialog()
        }

        view.findViewById<View>(R.id.cvManageApps)?.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.silentguardian.ui.AppSelectActivity::class.java))
        }
        
        return view
    }


    private fun showSettingsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_dialog_settings, null)
        dialog.setContentView(view)

        val tvDailyLimit = view.findViewById<android.widget.TextView>(R.id.tvDailyLimit)
        val sbDailyLimit = view.findViewById<android.widget.SeekBar>(R.id.sbDailyLimit)
        val tvContinuousLimit = view.findViewById<android.widget.TextView>(R.id.tvContinuousLimit)
        val sbContinuousLimit = view.findViewById<android.widget.SeekBar>(R.id.sbContinuousLimit)
        val tvCooldown = view.findViewById<android.widget.TextView>(R.id.tvCooldown)
        val sbCooldown = view.findViewById<android.widget.SeekBar>(R.id.sbCooldown)

        sbDailyLimit.max = 47 // (480 - 10) / 10
        sbDailyLimit.progress = ((com.example.silentguardian.manager.DataManager.dailyTotalLimitMinutes - 10) / 10).coerceIn(0, 47)
        tvDailyLimit.text = "${com.example.silentguardian.manager.DataManager.dailyTotalLimitMinutes} 分钟"

        sbDailyLimit.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 10 + progress * 10
                tvDailyLimit.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progress = seekBar?.progress ?: 0
                com.example.silentguardian.manager.DataManager.dailyTotalLimitMinutes = 10 + progress * 10
            }
        })
        
        sbContinuousLimit.max = 35 // (180 - 5) / 5
        sbContinuousLimit.progress = ((com.example.silentguardian.manager.DataManager.continuousLimitMinutes - 5) / 5).coerceIn(0, 35)
        tvContinuousLimit.text = "${com.example.silentguardian.manager.DataManager.continuousLimitMinutes} 分钟"

        sbContinuousLimit.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 5 + progress * 5
                tvContinuousLimit.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progress = seekBar?.progress ?: 0
                com.example.silentguardian.manager.DataManager.continuousLimitMinutes = 5 + progress * 5
            }
        })
        
        sbCooldown.max = 59 // (60 - 1) / 1
        sbCooldown.progress = ((com.example.silentguardian.manager.DataManager.cooldownMinutes - 1) / 1).coerceIn(0, 59)
        tvCooldown.text = "${com.example.silentguardian.manager.DataManager.cooldownMinutes} 分钟"

        sbCooldown.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 1 + progress
                tvCooldown.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progress = seekBar?.progress ?: 0
                com.example.silentguardian.manager.DataManager.cooldownMinutes = 1 + progress
            }
        })

        view.findViewById<android.widget.Button>(R.id.btnSaveSettings).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "健康防沉迷规则已保存", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
