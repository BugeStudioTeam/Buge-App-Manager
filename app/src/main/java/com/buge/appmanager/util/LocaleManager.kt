package com.buge.appmanager.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {

    private const val LANGUAGE_PREF = "language_pref"
    private const val LANGUAGE_KEY = "selected_language"

    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_KEY, languageCode).apply()
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        return prefs.getString(LANGUAGE_KEY, "") ?: ""
    }

    fun applyLanguage(context: Context, languageCode: String) {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }
        
        setLocale(context, locale)
    }

    fun createContextWithLocale(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }
        
        return wrapContextWithLocale(context, locale)
    }

    private fun setLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    private fun wrapContextWithLocale(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        return context.createConfigurationContext(config)
    }

    fun getSupportedLanguages(): Map<String, String> {
        return mapOf(
            "" to "System Default",
            "en" to "English",
            "fr" to "Français",
            "de" to "Deutsch",
            "ru" to "Русский",
            "pt" to "Português",
            "zh" to "中文 (Simplified)",
            "ar" to "العربية",
            "ja" to "日本語",
            "ko" to "한국어",
        )
    }
    
    fun getCurrentLocale(context: Context): Locale {
        val savedLanguage = getLanguage(context)
        return if (savedLanguage.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(savedLanguage)
        }
    }
}