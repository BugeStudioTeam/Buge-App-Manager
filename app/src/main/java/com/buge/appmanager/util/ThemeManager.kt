package com.buge.appmanager.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.buge.appmanager.R

object ThemeManager {

    enum class ColorTheme(val value: String) {
        DEFAULT("default"),
        RED("red"),
        GREEN("green"),
        YELLOW("yellow")
    }

    private const val PREF_COLOR_THEME = "color_theme"

    fun getCurrentColorTheme(context: Context): ColorTheme {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val themeValue = prefs.getString(PREF_COLOR_THEME, ColorTheme.DEFAULT.value)
        return when (themeValue) {
            ColorTheme.RED.value -> ColorTheme.RED
            ColorTheme.GREEN.value -> ColorTheme.GREEN
            ColorTheme.YELLOW.value -> ColorTheme.YELLOW
            else -> ColorTheme.DEFAULT
        }
    }

    fun setColorTheme(context: Context, theme: ColorTheme) {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_COLOR_THEME, theme.value).apply()
    }

    fun applyColorTheme(context: Context) {
        val colorTheme = getCurrentColorTheme(context)
        val themeResId = when (colorTheme) {
            ColorTheme.RED -> R.style.Theme_BugeAppManager_Red
            ColorTheme.GREEN -> R.style.Theme_BugeAppManager_Green
            ColorTheme.YELLOW -> R.style.Theme_BugeAppManager_Yellow
            ColorTheme.DEFAULT -> R.style.Theme_BugeAppManager
        }
        context.setTheme(themeResId)
    }
}