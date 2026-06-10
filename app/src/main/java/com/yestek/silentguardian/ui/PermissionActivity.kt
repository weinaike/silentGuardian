package com.yestek.silentguardian.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.yestek.silentguardian.R
import com.yestek.silentguardian.receiver.AdminReceiver
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class PermissionActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var llPermissions: LinearLayout
    private lateinit var btnEnter: Button
    private lateinit var switchAntiUninstall: Switch

    private data class PermItem(
        val key: String,
        val icon: String,
        val titleResId: Int,
        val descResId: Int,
        var isGranted: Boolean = false
    )

    private val permissionsList = listOf(
        PermItem("notification", "🔔", R.string.perm_notification_title, R.string.perm_notification_desc),
        PermItem("usage", "📊", R.string.perm_usage_title, R.string.perm_usage_desc),
        PermItem("battery", "🔋", R.string.perm_battery_title, R.string.perm_battery_desc),
        PermItem("vpn", "🔐", R.string.perm_vpn_title, R.string.perm_vpn_desc)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        com.yestek.silentguardian.utils.UpdateManager.checkUpdate(this)

        llPermissions = findViewById(R.id.llPermissions)
        btnEnter = findViewById(R.id.btnEnter)
        switchAntiUninstall = findViewById(R.id.switchAntiUninstall)

        findViewById<LinearLayout>(R.id.llAntiUninstallEntry)?.setOnClickListener {
            toggleDeviceAdmin()
        }

        btnEnter.setOnClickListener {
            val fromDashboard = intent.getBooleanExtra("from_dashboard", false)
            if (fromDashboard) {
                finish()
            } else {
                startActivity(Intent(this, com.yestek.silentguardian.MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (com.yestek.silentguardian.manager.DataManager.appPinCode.isNotEmpty() && !com.yestek.silentguardian.manager.DataManager.isAppUnlocked) {
            val intent = Intent(this, com.yestek.silentguardian.ui.PinLockActivity::class.java)
            startActivity(intent)
            return
        }

        checkPermissions()
        updateDeviceAdminState()

        val fromDashboard = intent.getBooleanExtra("from_dashboard", false)
        val allGranted = permissionsList.all { it.isGranted }
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        val isDeviceAdmin = dpm.isAdminActive(componentName)

        if (allGranted && isDeviceAdmin && !fromDashboard) {
            com.yestek.silentguardian.manager.DataManager.isServiceEnabled = true
            startActivity(Intent(this, com.yestek.silentguardian.MainActivity::class.java))
            finish()
        }
    }

    private fun checkPermissions() {
        permissionsList[0].isGranted = XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)
        permissionsList[1].isGranted = XXPermissions.isGranted(this, Permission.PACKAGE_USAGE_STATS)
        permissionsList[2].isGranted = XXPermissions.isGranted(this, Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        permissionsList[3].isGranted = VpnService.prepare(this) == null

        renderList()

        val fromDashboard = intent.getBooleanExtra("from_dashboard", false)
        val allGranted = permissionsList.all { it.isGranted }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        val isDeviceAdmin = dpm.isAdminActive(componentName)

        if (allGranted && isDeviceAdmin) {
            btnEnter.isEnabled = true
            btnEnter.text = getString(if (fromDashboard) R.string.perm_back_home else R.string.perm_enter_app)
            btnEnter.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btnEnter.setTextColor(Color.WHITE)
        } else {
            btnEnter.isEnabled = false
            btnEnter.text = getString(R.string.perm_complete_all)
            btnEnter.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C2C9BD"))
            btnEnter.setTextColor(Color.parseColor("#9AA0A6"))
        }
    }

    private fun renderList() {
        llPermissions.removeAllViews()
        permissionsList.forEachIndexed { index, item ->
            val view = LayoutInflater.from(this).inflate(R.layout.item_permission, llPermissions, false)

            val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
            val tvDesc = view.findViewById<TextView>(R.id.tvDesc)
            val ivStatus = view.findViewById<ImageView>(R.id.ivStatus)
            val btnGrant = view.findViewById<Button>(R.id.btnGrant)

            tvTitle.text = "${item.icon} ${getString(item.titleResId)}"
            tvDesc.text = getString(item.descResId)

            if (item.isGranted) {
                ivStatus.setImageResource(android.R.drawable.presence_online)
                ivStatus.setColorFilter(Color.parseColor("#4CAF50"))
                view.setBackgroundResource(R.drawable.bg_card_surface)
                ivStatus.visibility = View.VISIBLE
                btnGrant.visibility = View.GONE
            } else {
                view.setBackgroundResource(R.drawable.bg_card_surface)
                ivStatus.visibility = View.GONE
                btnGrant.visibility = View.VISIBLE

                btnGrant.setOnClickListener { requestPermission(item.key) }
            }

            llPermissions.addView(view)
        }
    }

    private fun requestPermission(key: String) {
        when (key) {
            "notification" -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_guide_title)
                    .setMessage(android.text.Html.fromHtml(getString(R.string.perm_guide_notification_msg), android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton(R.string.btn_go_settings) { _, _ ->
                        XXPermissions.with(this)
                            .permission(Permission.POST_NOTIFICATIONS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            "usage" -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_guide_title)
                    .setMessage(android.text.Html.fromHtml(getString(R.string.perm_guide_usage_msg), android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton(R.string.btn_go_settings) { _, _ ->
                        XXPermissions.with(this)
                            .permission(Permission.PACKAGE_USAGE_STATS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            "battery" -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_guide_title)
                    .setMessage(android.text.Html.fromHtml(getString(R.string.perm_guide_battery_msg), android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton(R.string.btn_go_settings) { _, _ ->
                        XXPermissions.with(this)
                            .permission(Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            "vpn" -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_guide_title)
                    .setMessage(android.text.Html.fromHtml(getString(R.string.perm_guide_vpn_msg), android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton(R.string.btn_go_authorize) { _, _ ->
                        val intent = VpnService.prepare(this)
                        if (intent != null) {
                            startActivityForResult(intent, 0)
                        } else {
                            checkPermissions()
                        }
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0) {
            checkPermissions()
        } else if (requestCode == 1001) {
            updateDeviceAdminState()
        }
    }

    private fun updateDeviceAdminState() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        switchAntiUninstall.isChecked = dpm.isAdminActive(componentName)
        checkPermissions()
    }

    private fun toggleDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)

        if (dpm.isAdminActive(componentName)) {
            dpm.removeActiveAdmin(componentName)
            switchAntiUninstall.isChecked = false
            android.widget.Toast.makeText(this, R.string.toast_device_admin_disabled, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_explanation))
            }
            startActivityForResult(intent, 1001)
        }
    }
}
