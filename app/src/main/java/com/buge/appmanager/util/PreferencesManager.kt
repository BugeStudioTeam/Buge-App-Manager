package com.buge.appmanager.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object PreferencesManager {

    private const val PREFS_NAME = "app_preferences"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val SHOW_SYSTEM_APPS_KEY = "show_system_apps"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Set theme mode (light/dark/system)
     */
    fun setThemeMode(context: Context, mode: Int) {
        getPreferences(context).edit().putInt(THEME_MODE_KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * Get current theme mode
     */
    fun getThemeMode(context: Context): Int {
        return getPreferences(context).getInt(
            THEME_MODE_KEY,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    /**
     * Set whether to show system apps
     */
    fun setShowSystemApps(context: Context, show: Boolean) {
        getPreferences(context).edit().putBoolean(SHOW_SYSTEM_APPS_KEY, show).apply()
    }

    /**
     * Get whether to show system apps
     */
    fun getShowSystemApps(context: Context): Boolean {
        return getPreferences(context).getBoolean(SHOW_SYSTEM_APPS_KEY, false)
    }
}
