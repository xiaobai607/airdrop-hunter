package com.airdrophunter.app

import android.content.Context
import android.content.res.Configuration
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalize(languageTag))
            .apply()
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
