package com.yestek.silentguardian.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SplashAdConfig(
    val enabled: Boolean,
    val adUrl: String,
    val durationSeconds: Int
)

object AdManager {
    private const val TAG = "AdManager"
    
    private const val AD_CONFIG_URL = "https://www.yes-tek.com/assets/apk/ad_config.json"

    suspend fun fetchSplashAdConfig(): SplashAdConfig? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(AD_CONFIG_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1500 // 短超时，避免影响启动速度
                connection.readTimeout = 1500

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val adEnabled = json.optBoolean("adEnabled", false)
                    if (!adEnabled) return@withContext null
                    
                    val splashAd = json.optJSONObject("splashAd")
                    if (splashAd != null) {
                        return@withContext SplashAdConfig(
                            enabled = splashAd.optBoolean("enabled", false),
                            adUrl = splashAd.optString("adUrl", ""),
                            durationSeconds = splashAd.optInt("durationSeconds", 3)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch ad config", e)
            } finally {
                connection?.disconnect()
            }
            null
        }
    }
}
