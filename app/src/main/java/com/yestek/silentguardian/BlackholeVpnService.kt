package com.yestek.silentguardian

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class BlackholeVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        val packages = intent?.getStringArrayListExtra("PACKAGE_NAMES")
        startVpn(packages)
        return START_STICKY
    }

    private fun startVpn(targetPackages: List<String>?) {
        if (targetPackages.isNullOrEmpty()) {
            stopVpn()
            return
        }

        stopVpn()

        try {
            val builder = Builder()
                .setSession("SilentGuardian Blackhole")
                .addAddress("10.0.0.2", 32) // 随便指派一个虚拟内网 IP
                .addRoute("0.0.0.0", 0)     // 拦截所有流量到 VPN 接口
                .setMtu(1500)
            
            // 🌟 核心杀招：只把超时的那几个 App 关进断网黑洞！其他 App 正常联网 🌟
            for (pkg in targetPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                } catch (e: Exception) {
                    // ignore invalid package
                }
            }
            
            // 建立连接。由于我们没有任何底层 Socket 去处理这些流量，所以它们就像进了黑洞一样凭空消失了
            vpnInterface = builder.establish()
            Log.d("VpnService", "VPN 建立成功，已将流量导入黑洞！目标Apps: $targetPackages")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
