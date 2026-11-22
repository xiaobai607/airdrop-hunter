package com.airdrophunter.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Centralized per-app language management.
 *
 * Android 13+ uses the platform per-app language API so the selection is also
 * reflected in Android Settings. Android 8-12 persist the preference locally
 * and apply it through an Activity base-context configuration wrapper.
 *
 * A null language tag means "follow system".
 */
object AppLocale {
    private const val PREFS_NAME = "app_language_preferences"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedLanguageTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    fun selectedLanguageTag(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) null else locales[0]?.toLanguageTag()
        } else {
            storedLanguageTag(context)
        }
    }

    fun setLanguage(context: Context, languageTag: String?) {
        val normalizedTag = languageTag?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = normalizedTag
                ?.let(LocaleList::forLanguageTags)
                ?: LocaleList.getEmptyLocaleList()
        } else {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                if (normalizedTag == null) remove(KEY_LANGUAGE_TAG) else putString(KEY_LANGUAGE_TAG, normalizedTag)
            }.apply()
            context.findActivity()?.recreate()
        }
    }

    private fun storedLanguageTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
            ?.let(::normalizeTag)

    private fun normalizeTag(tag: String): String = when {
        tag.startsWith("zh", ignoreCase = true) -> "zh-Hans"
        tag.startsWith("en", ignoreCase = true) -> "en"
        else -> "en"
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
