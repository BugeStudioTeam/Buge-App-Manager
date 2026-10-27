// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.WelcomeActivity
import com.buge.appmanager.util.LocaleManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WelcomeUsageFragment : Fragment() {

    private lateinit var themeValue: TextView
    private lateinit var languageValue: TextView
    private lateinit var defaultPageValue: TextView

    private var selectedTheme: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    private var selectedLanguage: String = ""
    private var selectedDefaultPage: String = "apps"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_usage, container, false)

        themeValue = view.findViewById(R.id.theme_value)
        languageValue = view.findViewById(R.id.language_value)
        defaultPageValue = view.findViewById(R.id.default_page_value)

        updateThemeText()
        updateLanguageText()
        updateDefaultPageText()

        (activity as? WelcomeActivity)?.onThemeChanged(selectedTheme)
        (activity as? WelcomeActivity)?.onLanguageChanged(selectedLanguage)
        (activity as? WelcomeActivity)?.onDefaultPageChanged(selectedDefaultPage)

        view.findViewById<View>(R.id.theme_item).setOnClickListener {
            showThemeDialog()
        }
        view.findViewById<View>(R.id.language_item).setOnClickListener {
            showLanguageDialog()
        }
        view.findViewById<View>(R.id.default_page_item).setOnClickListener {
            showDefaultPageDialog()
        }

        return view
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.pref_theme_light),
            getString(R.string.pref_theme_dark),
            getString(R.string.pref_theme_auto)
        )
        val currentIndex = when (selectedTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_theme)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                selectedTheme = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                updateThemeText()
                (activity as? WelcomeActivity)?.onThemeChanged(selectedTheme)
                dialog.dismiss()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = LocaleManager.getSupportedLanguages()
        val options = languages.values.toTypedArray()
        val codes = languages.keys.toList()
        val currentIndex = codes.indexOf(selectedLanguage).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_language)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                selectedLanguage = codes[which]
                updateLanguageText()
                (activity as? WelcomeActivity)?.onLanguageChanged(selectedLanguage)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDefaultPageDialog() {
        val options = arrayOf(
            getString(R.string.default_page_apps),
            getString(R.string.default_page_permissions),
            getString(R.string.default_page_activities),
            getString(R.string.default_page_settings)
        )
        val currentIndex = when (selectedDefaultPage) {
            "apps" -> 0
            "permissions" -> 1
            "activities" -> 2
            "settings" -> 3
            else -> 0
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_page_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                selectedDefaultPage = when (which) {
                    0 -> "apps"
                    1 -> "permissions"
                    2 -> "activities"
                    3 -> "settings"
                    else -> "apps"
                }
                updateDefaultPageText()
                (activity as? WelcomeActivity)?.onDefaultPageChanged(selectedDefaultPage)
                dialog.dismiss()
            }
            .show()
    }

    private fun updateThemeText() {
        themeValue.text = when (selectedTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.pref_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.pref_theme_dark)
            else -> getString(R.string.pref_theme_auto)
        }
    }

    private fun updateLanguageText() {
        val languages = LocaleManager.getSupportedLanguages()
        languageValue.text = languages[selectedLanguage] ?: languages[""] ?: "System Default"
    }

    private fun updateDefaultPageText() {
        defaultPageValue.text = when (selectedDefaultPage) {
            "apps" -> getString(R.string.default_page_apps)
            "permissions" -> getString(R.string.default_page_permissions)
            "activities" -> getString(R.string.default_page_activities)
            "settings" -> getString(R.string.default_page_settings)
            else -> getString(R.string.default_page_apps)
        }
    }
}