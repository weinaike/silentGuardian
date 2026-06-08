package com.yestek.silentguardian.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.widget.Toast
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.fragment.app.Fragment
import com.yestek.silentguardian.R
import com.yestek.silentguardian.manager.DataManager
import com.yestek.silentguardian.receiver.AdminReceiver
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class PermissionFragment : Fragment() {

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_permission, container, false)
        
        llPermissions = view.findViewById(R.id.llPermissions)
        btnEnter = view.findViewById(R.id.btnEnter)
        switchAntiUninstall = view.findViewById(R.id.switchAntiUninstall)

        // 隐藏进入应用按钮，因为我们已经在应用主页了
        btnEnter.visibility = View.GONE

        view.findViewById<LinearLayout>(R.id.llAntiUninstallEntry)?.setOnClickListener {
            toggleDeviceAdmin()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateDeviceAdminState()
    }

    private fun checkPermissions() {
        permissionsList[0].isGranted = XXPermissions.isGranted(requireContext(), Permission.POST_NOTIFICATIONS)
        permissionsList[1].isGranted = XXPermissions.isGranted(requireContext(), Permission.PACKAGE_USAGE_STATS)
        permissionsList[2].isGranted = XXPermissions.isGranted(requireContext(), Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        permissionsList[3].isGranted = VpnService.prepare(requireContext()) == null

        renderList()
    }

    private fun renderList() {
        llPermissions.removeAllViews()
        permissionsList.forEachIndexed { index, item ->
            val view = layoutInflater.inflate(R.layout.item_permission, llPermissions, false)
            
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
                XXPermissions.with(requireActivity())
                    .permission(Permission.POST_NOTIFICATIONS)
                    .request { _, _ -> checkPermissions() }
            }
            "usage" -> {
                XXPermissions.with(requireActivity())
                    .permission(Permission.PACKAGE_USAGE_STATS)
                    .request { _, _ -> checkPermissions() }
            }
            "battery" -> {
                XXPermissions.with(requireActivity())
                    .permission(Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .request { _, _ -> checkPermissions() }
            }
            "vpn" -> {
                val intent = VpnService.prepare(requireContext())
                if (intent != null) {
                    requireActivity().startActivityForResult(intent, 0)
                } else {
                    checkPermissions()
                }
            }
        }
    }

    private fun updateDeviceAdminState() {
        val dpm = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(requireContext(), AdminReceiver::class.java)
        switchAntiUninstall.isChecked = dpm.isAdminActive(componentName)
        checkPermissions()
    }

    private fun toggleDeviceAdmin() {
        val dpm = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(requireContext(), AdminReceiver::class.java)

        if (dpm.isAdminActive(componentName)) {
            dpm.removeActiveAdmin(componentName)
            switchAntiUninstall.isChecked = false
            Toast.makeText(requireContext(), "设备防卸载保护已解除", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后可以防止应用被意外卸载或强行停止。")
            }
            requireActivity().startActivityForResult(intent, 1001)
        }
    }
}
