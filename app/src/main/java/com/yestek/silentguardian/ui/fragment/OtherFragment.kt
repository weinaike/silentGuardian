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
import com.yestek.silentguardian.utils.LanguageHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

            val subject = getString(R.string.feedback_subject)
            val body = "${getString(R.string.feedback_body_prefix)}${getString(R.string.feedback_device_info, deviceModel, osVersion, appVersionName)}"

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:weinaike@foxmail.com")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.toast_no_email_client, Toast.LENGTH_SHORT).show()
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

        view.findViewById<View>(R.id.cvLanguage)?.setOnClickListener {
            showLanguageDialog()
        }

        return view
    }

    private fun showLanguageDialog() {
        val languages = LanguageHelper.getSupportedLanguages()
        val currentLang = LanguageHelper.getAppLanguage()
        val labels = languages.map { it.second }.toTypedArray()

        // Determine current selection
        val selectedIndex = when (currentLang) {
            null, LanguageHelper.LANG_SYSTEM -> 0
            LanguageHelper.LANG_ZH -> 1
            LanguageHelper.LANG_EN -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.other_language)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selectedCode = languages[which].first
                LanguageHelper.setAppLanguage(selectedCode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
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
        tvDailyLimit.text = getString(R.string.settings_minutes_format, DataManager.dailyTotalLimitMinutes)

        sbDailyLimit.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 10 + progress * 10
                tvDailyLimit.text = getString(R.string.settings_minutes_format, mins)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.dailyTotalLimitMinutes = 10 + progress * 10
            }
        })

        sbContinuousLimit.max = 35 // (180 - 5) / 5
        sbContinuousLimit.progress = ((DataManager.continuousLimitMinutes - 5) / 5).coerceIn(0, 35)
        tvContinuousLimit.text = getString(R.string.settings_minutes_format, DataManager.continuousLimitMinutes)

        sbContinuousLimit.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 5 + progress * 5
                tvContinuousLimit.text = getString(R.string.settings_minutes_format, mins)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.continuousLimitMinutes = 5 + progress * 5
            }
        })

        sbCooldown.max = 59 // (60 - 1) / 1
        sbCooldown.progress = ((DataManager.cooldownMinutes - 1) / 1).coerceIn(0, 59)
        tvCooldown.text = getString(R.string.settings_minutes_format, DataManager.cooldownMinutes)

        sbCooldown.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = 1 + progress
                tvCooldown.text = getString(R.string.settings_minutes_format, mins)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                DataManager.cooldownMinutes = 1 + progress
            }
        })

        view.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_dialog_about, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.llViewPrivacy)?.setOnClickListener {
            dialog.dismiss()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privacy_dialog_title)
                .setMessage(com.yestek.silentguardian.utils.PrivacyPolicyConstants.POLICY_TEXT)
                .setPositiveButton(R.string.btn_read, null)
                .show()
        }

        view.findViewById<View>(R.id.llWithdrawPrivacy)?.setOnClickListener {
            dialog.dismiss()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_withdraw_confirm_title)
                .setMessage(android.text.Html.fromHtml(getString(R.string.about_withdraw_confirm_msg), android.text.Html.FROM_HTML_MODE_COMPACT))
                .setPositiveButton(R.string.btn_withdraw_and_exit) { _, _ ->
                    requireContext().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("is_privacy_accepted", false).apply()
                    DataManager.clearAllUsageData()
                    DataManager.managedApps = emptySet()
                    requireActivity().finishAffinity()
                    kotlin.system.exitProcess(0)
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        val tvAboutVersion = view.findViewById<TextView>(R.id.tvAboutVersion)
        val appVersionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        tvAboutVersion?.text = getString(R.string.about_check_update_with_version, appVersionName)

        view.findViewById<View>(R.id.llCheckUpdate)?.setOnClickListener {
            dialog.dismiss()
            android.widget.Toast.makeText(requireContext(), R.string.toast_checking_update, android.widget.Toast.LENGTH_SHORT).show()
            // 用户主动点击时，显示 Toast 反馈
            com.yestek.silentguardian.utils.UpdateManager.checkUpdate(requireActivity(), true)
        }

        dialog.show()
    }
}
