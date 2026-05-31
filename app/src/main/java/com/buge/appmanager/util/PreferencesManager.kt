package com.buge.appmanager.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object PreferencesManager {

    private const val PREFS_NAME = "app_preferences"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val SHOW_SYSTEM_APPS_KEY = "show_system_apps"
    private const val SHOW_UNDECLARED_ACTIVITIES_KEY = "show_undeclared_activities"
    private const val SHOW_DISABLED_APPS_KEY = "show_disabled_apps"
    private const val DEFAULT_PAGE_KEY = "default_page"
    private const val ALLOW_SYSTEM_OPS_KEY = "allow_system_ops"
    private const val LOGGING_ENABLED_KEY = "logging_enabled"
    private const val FAVORITE_APPS_KEY = "favorite_apps"
    private const val AUTO_UPDATE_KEY = "auto_update"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPreferences(context).edit().putInt(THEME_MODE_KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getThemeMode(context: Context): Int {
        return getPreferences(context).getInt(
            THEME_MODE_KEY,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    fun setShowSystemApps(context: Context, show: Boolean) {
        getPreferences(context).edit().putBoolean(SHOW_SYSTEM_APPS_KEY, show).apply()
    }

    fun getShowSystemApps(context: Context): Boolean {
        return getPreferences(context).getBoolean(SHOW_SYSTEM_APPS_KEY, false)
    }

    fun setShowUndeclaredActivities(context: Context, show: Boolean) {
        getPreferences(context).edit().putBoolean(SHOW_UNDECLARED_ACTIVITIES_KEY, show).apply()
    }

    fun getShowUndeclaredActivities(context: Context): Boolean {
        return getPreferences(context).getBoolean(SHOW_UNDECLARED_ACTIVITIES_KEY, true)
    }

    fun setShowDisabledApps(context: Context, show: Boolean) {
        getPreferences(context).edit().putBoolean(SHOW_DISABLED_APPS_KEY, show).apply()
    }

    fun getShowDisabledApps(context: Context): Boolean {
        return getPreferences(context).getBoolean(SHOW_DISABLED_APPS_KEY, true)
    }

    fun setDefaultPage(context: Context, page: String) {
        getPreferences(context).edit().putString(DEFAULT_PAGE_KEY, page).apply()
    }

    fun getDefaultPage(context: Context): String {
        return getPreferences(context).getString(DEFAULT_PAGE_KEY, "apps") ?: "apps"
    }

    fun setAllowSystemOps(context: Context, allow: Boolean) {
        getPreferences(context).edit().putBoolean(ALLOW_SYSTEM_OPS_KEY, allow).apply()
    }

    fun getAllowSystemOps(context: Context): Boolean {
        return getPreferences(context).getBoolean(ALLOW_SYSTEM_OPS_KEY, true)
    }

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(LOGGING_ENABLED_KEY, enabled).apply()
    }

    fun getLoggingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(LOGGING_ENABLED_KEY, false)
    }

    fun addFavoriteApp(context: Context, packageName: String) {
        val favorites = getFavoriteApps(context).toMutableSet()
        favorites.add(packageName)
        saveFavoriteApps(context, favorites)
    }

    fun removeFavoriteApp(context: Context, packageName: String) {
        val favorites = getFavoriteApps(context).toMutableSet()
        favorites.remove(packageName)
        saveFavoriteApps(context, favorites)
    }

    fun isFavoriteApp(context: Context, packageName: String): Boolean {
        return getFavoriteApps(context).contains(packageName)
    }

    fun getFavoriteApps(context: Context): Set<String> {
        val json = getPreferences(context).getString(FAVORITE_APPS_KEY, "[]")
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveFavoriteApps(context: Context, favorites: Set<String>) {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(favorites)
        getPreferences(context).edit().putString(FAVORITE_APPS_KEY, json).apply()
    }

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(AUTO_UPDATE_KEY, enabled).apply()
    }

    fun getAutoUpdate(context: Context): Boolean {
        return getPreferences(context).getBoolean(AUTO_UPDATE_KEY, true)
    }
}