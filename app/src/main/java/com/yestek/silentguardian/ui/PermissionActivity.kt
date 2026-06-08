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

class PermissionActivity : Activity() {

    private lateinit var llPermissions: LinearLayout
    private lateinit var btnEnter: Button
    private lateinit var switchAntiUninstall: Switch

    private data class PermItem(
        val key: String,
        val icon: String,
        val title: String,
        val desc: String,
        var isGranted: Boolean = false
    )

    private val permissionsList = listOf(
        PermItem("notification", "🔔", "通知栏常驻权限", "保持守护服务在后台持续运行"),
        PermItem("usage", "📊", "应用使用情况访问", "用于判断当前是否在使用管控应用"),
        PermItem("battery", "🔋", "忽略电池优化", "防止系统为省电而关闭守护服务"),
        PermItem("vpn", "🔐", "VPN 连接授权", "用于在网络层阻断管控应用的网络访问")
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
            btnEnter.text = if (fromDashboard) "返回首页" else "进入应用"
            btnEnter.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btnEnter.setTextColor(Color.WHITE)
        } else {
            btnEnter.isEnabled = false
            btnEnter.text = "请先完成全部授权"
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

            tvTitle.text = "${item.icon} ${item.title}"
            tvDesc.text = item.desc

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
                XXPermissions.with(this)
                    .permission(Permission.POST_NOTIFICATIONS)
                    .request { _, _ -> checkPermissions() }
            }
            "usage" -> {
                XXPermissions.with(this)
                    .permission(Permission.PACKAGE_USAGE_STATS)
                    .request { _, _ -> checkPermissions() }
            }
            "battery" -> {
                XXPermissions.with(this)
                    .permission(Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .request { _, _ -> checkPermissions() }
            }
            "vpn" -> {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    checkPermissions()
                }
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
            android.widget.Toast.makeText(this, "设备防卸载保护已解除", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后可以防止应用被意外卸载或强行停止。")
            }
            startActivityForResult(intent, 1001)
        }
    }
}
