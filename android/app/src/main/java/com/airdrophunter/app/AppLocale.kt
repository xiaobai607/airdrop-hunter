package com.airdrophunter.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App language policy shared by every Activity.
 * "system" clears the app override; English and Simplified Chinese are explicit choices.
 */
object AppLocale {
    private const val PREFS = "airdrop_hunter_preferences"
    private const val KEY_LANGUAGE = "app_language"

    /** Returns the user's selected option: system, en, or zh-CN. */
    fun selectedLanguageChoice(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.toLanguageTags().orEmpty()
            return if (appLocales.isBlank()) "system" else normalizeChoice(appLocales.substringBefore(','))
        }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return normalizeChoice(saved)
    }

    /** Returns the effective language used to wrap pre-Android 13 Activities. */
    fun selectedLanguage(context: Context): String {
        val choice = selectedLanguageChoice(context)
        if (choice != "system") return choice
        val deviceLanguage = context.resources.configuration.locales[0]?.language.orEmpty()
        return if (deviceLanguage.equals("zh", ignoreCase = true)) "zh-CN" else "en"
    }

    fun setLanguage(context: Context, languageTag: String) {
        val choice = normalizeChoice(languageTag)
        // Android 13+ keeps the platform per-app language as the single source of truth.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (choice == "system") {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(choice)
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LANGUAGE).apply()
        } else {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (choice == "system") preferences.edit().remove(KEY_LANGUAGE).apply()
            else preferences.edit().putString(KEY_LANGUAGE, choice).apply()
        }
        syncLauncherAlias(context, selectedLanguage(context))
    }

    fun syncLauncherAlias(context: Context, languageTag: String = selectedLanguage(context)) {
        val english = languageTag.startsWith("en", ignoreCase = true)
        val englishAlias = android.content.ComponentName(context, "com.airdrophunter.app.LauncherEnglish")
        val chineseAlias = android.content.ComponentName(context, "com.airdrophunter.app.LauncherChinese")
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            englishAlias,
            if (english) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            chineseAlias,
            if (english) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
    }

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(selectedLanguage(base))
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun normalizeChoice(languageTag: String?): String = when {
        languageTag.isNullOrBlank() -> "system"
        languageTag.equals("system", ignoreCase = true) -> "system"
        languageTag.startsWith("zh", ignoreCase = true) -> "zh-CN"
        else -> "en"
    }
}
