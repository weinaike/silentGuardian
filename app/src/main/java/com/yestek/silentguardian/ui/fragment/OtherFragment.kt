package com.yestek.silentguardian.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yestek.silentguardian.R
import com.yestek.silentguardian.manager.DataManager
import com.yestek.silentguardian.ui.AppSelectActivity
import com.yestek.silentguardian.ui.PinLockActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

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
            val intent = Intent(requireContext(), PinLockActivity::class.java).apply {
                putExtra("MODIFY_MODE", true)
            }
            startActivity(intent)
        }
        
        view.findViewById<View>(R.id.cvSettings)?.setOnClickListener {
            showSettingsDialog()
        }

        view.findViewById<View>(R.id.cvManageApps)?.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectActivity::class.java))
        }

        view.findViewById<View>(R.id.cvAbout)?.setOnClickListener {
            showAboutDialog()
        }
        
        return view
    }


    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_dialog_settings, null)
        dialog.setContentView(view)

        val tvDailyLimit = view.findViewById<TextView>(R.id.tvDailyLimit)
        val sbDailyLimit = view.findViewById<SeekBar>(R.id.sbDailyLimit)
        val tvContinuousLimit = view.findViewById<TextView>(R.id.tvContinuousLimit)
        val sbContinuousLimit = view.findViewById<SeekBar>(R.id.sbContinuousLimit)
        val tvCooldown = view.findViewById<TextView>(R.id.tvCooldown)
        val sbCooldown = view.findViewById<SeekBar>(R.id.sbCooldown)

        sbDailyLimit.max = 47 // (480 - 10) / 10
        sbDailyLimit.progress = ((DataManager.dailyTotalLimitMinutes - 10) / 10).coerceIn(0, 47)
        tvDailyLimit.text = "${DataManager.dailyTotalLimitMinutes} 分钟"

        sbDailyLimit.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 10 + progress * 10
                tvDailyLimit.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.dailyTotalLimitMinutes = 10 + progress * 10
            }
        })
        
        sbContinuousLimit.max = 35 // (180 - 5) / 5
        sbContinuousLimit.progress = ((DataManager.continuousLimitMinutes - 5) / 5).coerceIn(0, 35)
        tvContinuousLimit.text = "${DataManager.continuousLimitMinutes} 分钟"

        sbContinuousLimit.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 5 + progress * 5
                tvContinuousLimit.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.continuousLimitMinutes = 5 + progress * 5
            }
        })
        
        sbCooldown.max = 59 // (60 - 1) / 1
        sbCooldown.progress = ((DataManager.cooldownMinutes - 1) / 1).coerceIn(0, 59)
        tvCooldown.text = "${DataManager.cooldownMinutes} 分钟"

        sbCooldown.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 1 + progress
                tvCooldown.text = "$mins 分钟"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.cooldownMinutes = 1 + progress
            }
        })

        view.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "健康防沉迷规则已保存", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_dialog_about, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.llViewPrivacy)?.setOnClickListener {
            dialog.dismiss()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("用户协议与隐私政策")
                .setMessage(com.yestek.silentguardian.utils.PrivacyPolicyConstants.POLICY_TEXT)
                .setPositiveButton("已阅", null)
                .show()
        }

        view.findViewById<View>(R.id.llWithdrawPrivacy)?.setOnClickListener {
            dialog.dismiss()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("撤回隐私协议授权")
                .setMessage("撤回隐私协议同意将停止所有数据记录，并清空本地存储，App 将退出且不可用直到您再次同意。\n\n您确定要撤回同意吗？")
                .setPositiveButton("撤回同意并退出") { _, _ ->
                    requireContext().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("is_privacy_accepted", false).apply()
                    DataManager.clearAllUsageData()
                    DataManager.managedApps = emptySet()
                    requireActivity().finishAffinity()
                    kotlin.system.exitProcess(0)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val tvAboutVersion = view.findViewById<TextView>(R.id.tvAboutVersion)
        val appVersionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        tvAboutVersion?.text = "检查更新 (当前版本 v$appVersionName)"

        view.findViewById<View>(R.id.llCheckUpdate)?.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show()
            com.yestek.silentguardian.utils.UpdateManager.checkUpdate(requireActivity())
        }

        dialog.show()
    }
}
