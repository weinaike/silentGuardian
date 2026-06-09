package com.yestek.silentguardian.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 语言管理工具类，基于 AppCompat Per-app Language API。
 * 默认跟随系统语言，用户可手动切换，偏好自动持久化。
 */
object LanguageHelper {

    /** 跟随系统 */
    const val LANG_SYSTEM = "system"

    /** 简体中文 */
    const val LANG_ZH = "zh"

    /** 英文 */
    const val LANG_EN = "en"

    /**
     * 支持的语言列表：(语言代码, 显示名称)
     */
    fun getSupportedLanguages(): List<Pair<String, String>> = listOf(
        LANG_SYSTEM to "跟随系统 / System",
        LANG_ZH to "简体中文",
        LANG_EN to "English",
    )

    /**
     * 获取当前 App 设置的语言代码。
     * @return 语言代码如 "zh"、"en"，或 null 表示跟随系统
     */
    fun getAppLanguage(): String? {
        val localeList = AppCompatDelegate.getApplicationLocales()
        if (localeList.isEmpty) return null
        val lang = localeList.get(0)?.language
        return when {
            lang == null || lang.isEmpty() -> null
            else -> lang
        }
    }

    /**
     * 设置 App 语言。传入 null 或 LANG_SYSTEM 恢复跟随系统。
     */
    fun setAppLanguage(languageCode: String?) {
        val localeList = when (languageCode) {
            null, LANG_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.create(Locale(languageCode))
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
