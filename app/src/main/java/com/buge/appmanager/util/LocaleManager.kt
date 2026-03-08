package com.buge.appmanager.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {

    private const val LANGUAGE_PREF = "language_pref"
    private const val LANGUAGE_KEY = "selected_language"

    /**
     * Set app language
     * @param context Application context
     * @param languageCode Language code (e.g., "en", "zh", "ja", "ko", "fr", "de", "ru")
     */
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_KEY, languageCode).apply()
        applyLanguage(context, languageCode)
    }

    /**
     * Get current language code
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        return prefs.getString(LANGUAGE_KEY, "") ?: ""
    }

    /**
     * Apply language to context - properly wraps context with the correct locale
     */
    fun applyLanguage(context: Context, languageCode: String) {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }
        
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        Locale.setDefault(locale)
    }

    /**
     * Create a context wrapper with the correct locale
     */
    fun createContextWithLocale(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            val config = Configuration(context.resources.configuration)
            @Suppress("DEPRECATION")
            config.locale = locale
            context.createConfigurationContext(config)
        }
    }

    /**
     * Get list of supported languages
     */
    fun getSupportedLanguages(): Map<String, String> {
        return mapOf(
            "" to "System Default",
            "en" to "English",
            "zh" to "中文 (Simplified)",
            "ja" to "日本語",
            "ko" to "한국어",
            "fr" to "Français",
            "de" to "Deutsch",
            "ru" to "Русский"
        )
    }
}
