package com.airdrophunter.app

import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Stores an app-specific language choice and applies it before Activity resources are created.
 * When no choice has been saved, the app follows the device language (Chinese or English).
 */
object AppLocale {
    private const val PREFS = "airdrop_hunter_preferences"
    private const val KEY_LANGUAGE = "app_language"

    fun selectedLanguage(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        if (!saved.isNullOrBlank()) return normalize(saved)
        val deviceLanguage = context.resources.configuration.locales[0]?.language.orEmpty()
        return if (deviceLanguage.equals("zh", ignoreCase = true)) "zh-CN" else "en"
    }

    fun setLanguage(context: Context, languageTag: String) {
        val normalized = normalize(languageTag)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
        // Android 13+ applies the app-specific locale at package level too,
        // so launcher labels follow the in-app language selection.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(normalized)
        }
        syncLauncherAlias(context, normalized)
    }

    fun syncLauncherAlias(context: Context, languageTag: String = selectedLanguage(context)) {
        val english = languageTag.startsWith("en", ignoreCase = true)
        val englishAlias = android.content.ComponentName(context, "com.airdrophunter.app.LauncherEnglish")
        val chineseAlias = android.content.ComponentName(context, "com.airdrophunter.app.LauncherChinese")
        val pm = context.packageManager
        pm.setComponentEnabledSetting(englishAlias,
            if (english) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(chineseAlias,
            if (english) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
    }

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(selectedLanguage(base))
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun normalize(languageTag: String): String =
        if (languageTag.startsWith("zh", ignoreCase = true)) "zh-CN" else "en"
}
