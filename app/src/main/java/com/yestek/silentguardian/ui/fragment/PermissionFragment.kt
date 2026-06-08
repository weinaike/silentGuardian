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
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("授权引导")
                    .setMessage("接下来将跳转至「通知」设置。\n\n为了保证防沉迷服务在后台持续稳定运行而不被清理，我们需要常驻一条系统通知。请在接下来的界面中打开允许通知的开关。")
                    .setPositiveButton("去设置") { _, _ ->
                        XXPermissions.with(requireActivity())
                            .permission(Permission.POST_NOTIFICATIONS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            "usage" -> {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("授权引导")
                    .setMessage("接下来将跳转至「使用情况访问权限」设置。\n\n为了能够准确判断您当前是否正在使用受管 App，请在接下来的列表中找到「SilentGuardian」，并将其状态修改为【允许访问使用记录】。")
                    .setPositiveButton("去设置") { _, _ ->
                        XXPermissions.with(requireActivity())
                            .permission(Permission.PACKAGE_USAGE_STATS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            "battery" -> {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("授权引导")
                    .setMessage("接下来将跳转至「电池优化」设置。\n\n由于 Android 系统的省电机制会强制关闭后台应用，为了防沉迷服务能稳定生效，请务必将其设置为【无限制】或【不优化】。")
                    .setPositiveButton("去设置") { _, _ ->
                        XXPermissions.with(requireActivity())
                            .permission(Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .request { _, _ -> checkPermissions() }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            "vpn" -> {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("授权引导")
                    .setMessage("接下来将弹出系统级别的「网络连接请求」确认框。\n\n此功能仅用于在设备本地建立虚拟黑洞来阻断受管 App 的网络，绝对不会上传您的任何流量数据。请放心点击【确定】。")
                    .setPositiveButton("去授权") { _, _ ->
                        val intent = VpnService.prepare(requireContext())
                        if (intent != null) {
                            requireActivity().startActivityForResult(intent, 0)
                        } else {
                            checkPermissions()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
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
