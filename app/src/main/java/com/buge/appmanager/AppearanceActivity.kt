// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.buge.appmanager.databinding.ActivityAppearanceBinding
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.ThemeManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppearanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAppearanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyColorTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupAppearanceGroup()
        setupThemeGroup()
        setupDynamicColor()
        setupColorThemeGroup()
        setupHideNavLabelsSwitch()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.appearance_settings)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupAppearanceGroup() {
        val defaultPage = PreferencesManager.getDefaultPage(this)
        val defaultPageText = when (defaultPage) {
            "apps" -> getString(R.string.default_page_apps)
            "permissions" -> getString(R.string.default_page_permissions)
            "activities" -> getString(R.string.default_page_activities)
            "settings" -> getString(R.string.default_page_settings)
            else -> getString(R.string.default_page_apps)
        }
        binding.defaultPageValue.text = defaultPageText
        binding.defaultPageItem.setOnClickListener {
            showDefaultPageDialog()
        }

        val currentLanguage = LocaleManager.getLanguage(this)
        val languages = LocaleManager.getSupportedLanguages()
        val languageText = languages[currentLanguage] ?: languages[""] ?: "System Default"
        binding.languageValue.text = languageText
        binding.languageItem.setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun setupThemeGroup() {
        val currentTheme = PreferencesManager.getThemeMode(this)
        val themeText = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.pref_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.pref_theme_dark)
            else -> getString(R.string.pref_theme_auto)
        }
        binding.themeValue.text = themeText
        binding.themeItem.setOnClickListener {
            showThemeDialog()
        }
    }

    private fun setupDynamicColor() {
        val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val switch = binding.dynamicColorSwitch
        val statusText = binding.dynamicColorStatus
        val isDynamicColorEnabled = PreferencesManager.getDynamicColor(this)

        // Fuck: Set icon for dynamic color using ic_colorize
        // The icon is already set in the layout, but we can optionally customize it here

        switch.isChecked = isDynamicColorEnabled

        if (isAndroid12Plus) {
            switch.isEnabled = true
            statusText.text = getString(R.string.dynamic_color_summary)
            switch.alpha = 1f

            switch.setOnCheckedChangeListener { _, isChecked ->
                PreferencesManager.setDynamicColor(this, isChecked)
                updateColorThemeUI(isChecked)
                showDynamicColorRestartDialog(isChecked)
            }
        } else {
            switch.isEnabled = false
            statusText.text = getString(R.string.dynamic_color_unavailable)
            switch.alpha = 0.4f
            switch.setOnCheckedChangeListener(null)
        }

        updateColorThemeUI(isDynamicColorEnabled && isAndroid12Plus)
    }

    private fun showDynamicColorRestartDialog(enabled: Boolean) {
        val message = if (enabled) {
            "Dynamic color has been enabled. Restart the app to apply the changes?"
        } else {
            "Dynamic color has been disabled. Restart the app to apply the changes?"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restart_required)
            .setMessage(message)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && enabled) {
                    DynamicColors.applyToActivitiesIfAvailable(application)
                }
                restartApp()
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun updateColorThemeUI(dynamicColorEnabled: Boolean) {
        val containers = listOf(
            binding.colorDefault,
            binding.colorRed,
            binding.colorGreen,
            binding.colorYellow
        )

        for (container in containers) {
            if (dynamicColorEnabled) {
                container.isEnabled = false
                container.alpha = 0.4f
                container.isClickable = false
                container.isFocusable = false
            } else {
                container.isEnabled = true
                container.alpha = 1f
                container.isClickable = true
                container.isFocusable = true
            }
        }

        if (dynamicColorEnabled) {
            val checkDefault = binding.colorDefault.findViewById<ImageView>(R.id.color_check_default)
            val checkRed = binding.colorRed.findViewById<ImageView>(R.id.color_check_red)
            val checkGreen = binding.colorGreen.findViewById<ImageView>(R.id.color_check_green)
            val checkYellow = binding.colorYellow.findViewById<ImageView>(R.id.color_check_yellow)

            checkDefault?.visibility = View.GONE
            checkRed?.visibility = View.GONE
            checkGreen?.visibility = View.GONE
            checkYellow?.visibility = View.GONE
        } else {
            val currentColorTheme = ThemeManager.getCurrentColorTheme(this)
            updateColorThemeSelection(currentColorTheme)
        }
    }

    private fun setupColorThemeGroup() {
        val currentColorTheme = ThemeManager.getCurrentColorTheme(this)
        updateColorThemeSelection(currentColorTheme)

        binding.colorDefault.setOnClickListener {
            selectColorTheme(ThemeManager.ColorTheme.DEFAULT)
        }
        binding.colorRed.setOnClickListener {
            selectColorTheme(ThemeManager.ColorTheme.RED)
        }
        binding.colorGreen.setOnClickListener {
            selectColorTheme(ThemeManager.ColorTheme.GREEN)
        }
        binding.colorYellow.setOnClickListener {
            selectColorTheme(ThemeManager.ColorTheme.YELLOW)
        }

        val isDynamicEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                PreferencesManager.getDynamicColor(this)
        updateColorThemeUI(isDynamicEnabled)
    }

    private fun setupHideNavLabelsSwitch() {
        val isEnabled = PreferencesManager.getHideNavLabels(this)
        binding.hideNavLabelsSwitch.isChecked = isEnabled
        binding.hideNavLabelsSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setHideNavLabels(this, isChecked)
            applyHideNavLabelsToMainActivity()
        }
    }

    private fun applyHideNavLabelsToMainActivity() {
        val intent = Intent("com.buge.appmanager.ACTION_NAV_LABELS_CHANGED")
        sendBroadcast(intent)
    }

    private fun selectColorTheme(theme: ThemeManager.ColorTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            PreferencesManager.getDynamicColor(this)) {
            PreferencesManager.setDynamicColor(this, false)
            binding.dynamicColorSwitch.isChecked = false
            updateColorThemeUI(false)
        }

        ThemeManager.setColorTheme(this, theme)
        updateColorThemeSelection(theme)
        showRestartDialog()
    }

    private fun updateColorThemeSelection(selectedTheme: ThemeManager.ColorTheme) {
        val checkDefault = binding.colorDefault.findViewById<ImageView>(R.id.color_check_default)
        val checkRed = binding.colorRed.findViewById<ImageView>(R.id.color_check_red)
        val checkGreen = binding.colorGreen.findViewById<ImageView>(R.id.color_check_green)
        val checkYellow = binding.colorYellow.findViewById<ImageView>(R.id.color_check_yellow)

        checkDefault?.visibility = if (selectedTheme == ThemeManager.ColorTheme.DEFAULT) View.VISIBLE else View.GONE
        checkRed?.visibility = if (selectedTheme == ThemeManager.ColorTheme.RED) View.VISIBLE else View.GONE
        checkGreen?.visibility = if (selectedTheme == ThemeManager.ColorTheme.GREEN) View.VISIBLE else View.GONE
        checkYellow?.visibility = if (selectedTheme == ThemeManager.ColorTheme.YELLOW) View.VISIBLE else View.GONE
    }

    private fun showDefaultPageDialog() {
        val options = arrayOf(
            getString(R.string.default_page_apps),
            getString(R.string.default_page_permissions),
            getString(R.string.default_page_activities),
            getString(R.string.default_page_settings)
        )
        val defaultPage = PreferencesManager.getDefaultPage(this)
        val currentIndex = when (defaultPage) {
            "apps" -> 0
            "permissions" -> 1
            "activities" -> 2
            "settings" -> 3
            else -> 0
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.default_page_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val page = when (which) {
                    0 -> "apps"
                    1 -> "permissions"
                    2 -> "activities"
                    3 -> "settings"
                    else -> "apps"
                }
                PreferencesManager.setDefaultPage(this, page)
                binding.defaultPageValue.text = options[which]
                dialog.dismiss()
                showRestartDialog()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = LocaleManager.getSupportedLanguages()
        val options = languages.values.toTypedArray()
        val codes = languages.keys.toList()
        val currentCode = LocaleManager.getLanguage(this)
        val currentIndex = codes.indexOf(currentCode).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_language)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selectedCode = codes[which]
                LocaleManager.setLanguage(this, selectedCode)
                val languageText = languages[selectedCode] ?: languages[""] ?: "System Default"
                binding.languageValue.text = languageText
                dialog.dismiss()
                showRestartDialog()
            }
            .show()
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.pref_theme_light),
            getString(R.string.pref_theme_dark),
            getString(R.string.pref_theme_auto)
        )
        val currentMode = PreferencesManager.getThemeMode(this)
        val currentIndex = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_theme)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                PreferencesManager.setThemeMode(this, mode)
                AppCompatDelegate.setDefaultNightMode(mode)
                binding.themeValue.text = options[which]
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun showRestartDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restart_required)
            .setMessage(R.string.theme_change_restart_message)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                restartApp()
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}