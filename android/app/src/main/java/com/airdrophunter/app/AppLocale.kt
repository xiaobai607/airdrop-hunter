package com.airdrophunter.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the app-selected locale independently of the device language.
 * New installs use English; users can switch to Simplified Chinese in Settings.
 */
object AppLocale {
    private const val PREFS = "droppilot_preferences"
    private const val KEY_LANGUAGE = "app_language"
    private const val DEFAULT_LANGUAGE = "en"

    fun selectedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setLanguage(context: Context, languageTag: String) {
        val normalized = if (languageTag.startsWith("zh", ignoreCase = true)) "zh-CN" else "en"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
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
}
