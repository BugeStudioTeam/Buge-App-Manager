// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.buge.appmanager.R
import com.google.android.material.color.DynamicColors

object ThemeManager {

    enum class ColorTheme(val value: String) {
        DYNAMIC("dynamic"),
        DEFAULT("default"),
        RED("red"),
        GREEN("green"),
        YELLOW("yellow")
    }

    private const val PREF_COLOR_THEME = "color_theme"

    fun getCurrentColorTheme(context: Context): ColorTheme {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        // 默认启用莫奈取色（dynamic），无需用户手动开关
        val themeValue = prefs.getString(PREF_COLOR_THEME, ColorTheme.DYNAMIC.value)
        return when (themeValue) {
            ColorTheme.RED.value -> ColorTheme.RED
            ColorTheme.GREEN.value -> ColorTheme.GREEN
            ColorTheme.YELLOW.value -> ColorTheme.YELLOW
            // Android < 12 不支持莫奈取色，回退到 DEFAULT 蓝色主题
            ColorTheme.DYNAMIC.value -> if (DynamicColors.isDynamicColorAvailable()) ColorTheme.DYNAMIC else ColorTheme.DEFAULT
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
           // DYNAMIC 使用不带任何颜色属性的主题，DynamicColors overlay 能覆盖全部 colorScheme
           // （包括 colorSurface/colorBackground），背景随壁纸变化。
           ColorTheme.DYNAMIC -> R.style.Theme_BugeAppManager_Dynamic
           ColorTheme.DEFAULT -> R.style.Theme_BugeAppManager
        }
        context.setTheme(themeResId)
    }

    /** 莫奈取色是否可用（Android 12+） */
    fun isDynamicColorAvailable(): Boolean = DynamicColors.isDynamicColorAvailable()
}
