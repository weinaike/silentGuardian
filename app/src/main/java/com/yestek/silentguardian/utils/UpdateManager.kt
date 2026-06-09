package com.yestek.silentguardian.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val UPDATE_URL = "https://www.yes-tek.com/asset/apk/update_config.json"

    fun checkUpdate(activity: Activity) {
        MainScope().launch {
            try {
                val jsonResult = withContext(Dispatchers.IO) {
                    fetchUpdateConfig()
                }
                
                jsonResult?.let { json ->
                    val latestVersionCode = json.optInt("latestVersionCode", 0)
                    val forceUpdate = json.optBoolean("forceUpdate", false)
                    val updateTitle = json.optString("updateTitle", "发现新版本")
                    val updateMessage = json.optString("updateMessage", "有新版本可用，请更新。")
                    val downloadUrl = json.optString("downloadUrl", "")

                    val currentVersionCode = getAppVersionCode(activity)
                    
                    Log.d(TAG, "Current version: $currentVersionCode, Latest version: $latestVersionCode")

                    // 严格按照大于判断。如果要测试，请在服务端把 latestVersionCode 改为 2 或者以上
                    if (latestVersionCode > currentVersionCode) {
                        val ignoredVersion = com.tencent.mmkv.MMKV.defaultMMKV().decodeInt("ignored_update_version", 0)
                        if (!forceUpdate && latestVersionCode == ignoredVersion) {
                            Log.d(TAG, "Update $latestVersionCode was ignored by user, skipping prompt.")
                            return@let
                        }
                        showUpdateDialog(activity, updateTitle, updateMessage, downloadUrl, forceUpdate, latestVersionCode)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check update failed", e)
            }
        }
    }

    private fun fetchUpdateConfig(): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(UPDATE_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                return JSONObject(response.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching update config", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun showUpdateDialog(
        activity: Activity,
        title: String,
        message: String,
        downloadUrl: String,
        forceUpdate: Boolean,
        latestVersionCode: Int
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(!forceUpdate)
            .setPositiveButton("立即更新") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    activity.startActivity(intent)
                    if (forceUpdate) {
                        // 如果是强制更新，跳转浏览器后直接退出应用，避免绕过
                        activity.finishAffinity()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start browser for update", e)
                }
            }

        if (!forceUpdate) {
            builder.setNegativeButton("稍后") { dialog, _ ->
                com.tencent.mmkv.MMKV.defaultMMKV().encode("ignored_update_version", latestVersionCode)
                dialog.dismiss()
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            1L
        }
    }
}
