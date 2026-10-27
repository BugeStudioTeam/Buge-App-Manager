// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.WelcomeActivity
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.ThemeManager

class WelcomeReadyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_ready, container, false)
        val summaryContainer = view.findViewById<LinearLayout>(R.id.summary_container)

        val activity = activity as? WelcomeActivity
        if (activity != null) {
            addRow(summaryContainer, getString(R.string.welcome_summary_source), activity.getSelectedSource())
            addRow(summaryContainer, getString(R.string.welcome_summary_auto_update),
                if (activity.getSelectedAutoUpdate()) getString(R.string.welcome_summary_on) else getString(R.string.welcome_summary_off))
            addRow(summaryContainer, getString(R.string.welcome_summary_theme), themeText(activity.getSelectedTheme()))
            addRow(summaryContainer, getString(R.string.welcome_summary_language), languageText(activity.getSelectedLanguage()))
            addRow(summaryContainer, getString(R.string.welcome_summary_default_page), defaultPageText(activity.getSelectedDefaultPage()))
            if (activity.isDynamicColorSupported()) {
                addRow(summaryContainer, getString(R.string.welcome_summary_dynamic_color),
                    if (activity.getSelectedDynamicColor()) getString(R.string.welcome_summary_on) else getString(R.string.welcome_summary_off))
            }
            addRow(summaryContainer, getString(R.string.welcome_summary_hide_nav_labels),
                if (activity.getSelectedHideNavLabels()) getString(R.string.welcome_summary_on) else getString(R.string.welcome_summary_off))
            addRow(summaryContainer, getString(R.string.welcome_summary_color_theme),
                colorThemeText(activity.getSelectedColorTheme()))
        }

        return view
    }

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_info_row, container, false)
        row.findViewById<TextView>(R.id.label).text = label
        row.findViewById<TextView>(R.id.value).text = value
        container.addView(row)
    }

    private fun themeText(mode: Int): String = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.pref_theme_light)
        AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.pref_theme_dark)
        else -> getString(R.string.pref_theme_auto)
    }

    private fun languageText(code: String): String {
        val languages = LocaleManager.getSupportedLanguages()
        return languages[code] ?: languages[""] ?: "System Default"
    }

    private fun defaultPageText(page: String): String = when (page) {
        "apps" -> getString(R.string.default_page_apps)
        "permissions" -> getString(R.string.default_page_permissions)
        "activities" -> getString(R.string.default_page_activities)
        "settings" -> getString(R.string.default_page_settings)
        else -> getString(R.string.default_page_apps)
    }

    private fun colorThemeText(theme: ThemeManager.ColorTheme): String = when (theme) {
        ThemeManager.ColorTheme.DEFAULT -> getString(R.string.color_default)
        ThemeManager.ColorTheme.RED -> getString(R.string.color_red)
        ThemeManager.ColorTheme.GREEN -> getString(R.string.color_green)
        ThemeManager.ColorTheme.YELLOW -> getString(R.string.color_yellow)
    }
}