// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.buge.appmanager.databinding.ActivityWelcomeBinding
import com.buge.appmanager.ui.welcome.WelcomeAppearanceFragment
import com.buge.appmanager.ui.welcome.WelcomeAuthFragment
import com.buge.appmanager.ui.welcome.WelcomeIntroFragment
import com.buge.appmanager.ui.welcome.WelcomeReadyFragment
import com.buge.appmanager.ui.welcome.WelcomeSourceFragment
import com.buge.appmanager.ui.welcome.WelcomeUpdateFragment
import com.buge.appmanager.ui.welcome.WelcomeUpdateOptionsFragment
import com.buge.appmanager.ui.welcome.WelcomeUsageFragment
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.ThemeManager

class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var pagerAdapter: WelcomePagerAdapter

    private var selectedSource: String = "GitHub"
    private var autoUpdate: Boolean = true
    private var selectedTheme: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    private var selectedLanguage: String = ""
    private var selectedDefaultPage: String = "apps"
    private var dynamicColor: Boolean = false
    private var hideNavLabels: Boolean = false
    private var selectedColorTheme: ThemeManager.ColorTheme = ThemeManager.ColorTheme.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
        setupButtons()
    }

    private fun setupPager() {
        pagerAdapter = WelcomePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtons(position)
            }
        })

        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.currentItem = current - 1
            }
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pagerAdapter.itemCount - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishWelcome()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishWelcome()
        }

        updateButtons(0)
    }

    private fun setupButtons() {
    }

    private fun updateButtons(position: Int) {
        val lastIndex = pagerAdapter.itemCount - 1
        binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text = if (position == lastIndex) {
            getString(R.string.welcome_finish)
        } else {
            getString(R.string.welcome_next)
        }
        binding.btnSkip.visibility = if (position == lastIndex) View.GONE else View.VISIBLE
        binding.pageIndicator.text = "${position + 1} / ${pagerAdapter.itemCount}"
    }

    fun onSourceSelected(source: String) {
        selectedSource = source
        autoUpdate = source != "F-Droid"
    }

    fun onAutoUpdateChanged(enabled: Boolean) {
        autoUpdate = enabled
    }

    fun onThemeChanged(mode: Int) {
        selectedTheme = mode
    }

    fun onLanguageChanged(code: String) {
        selectedLanguage = code
    }

    fun onDefaultPageChanged(page: String) {
        selectedDefaultPage = page
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        dynamicColor = enabled
        if (enabled) {
            selectedColorTheme = ThemeManager.ColorTheme.DEFAULT
        }
    }

    fun onHideNavLabelsChanged(enabled: Boolean) {
        hideNavLabels = enabled
    }

    fun onColorThemeChanged(theme: ThemeManager.ColorTheme) {
        selectedColorTheme = theme
    }

    fun getSelectedSource(): String = selectedSource
    fun getSelectedAutoUpdate(): Boolean = autoUpdate
    fun getSelectedTheme(): Int = selectedTheme
    fun getSelectedLanguage(): String = selectedLanguage
    fun getSelectedDefaultPage(): String = selectedDefaultPage
    fun getSelectedDynamicColor(): Boolean = dynamicColor
    fun getSelectedHideNavLabels(): Boolean = hideNavLabels
    fun getSelectedColorTheme(): ThemeManager.ColorTheme = selectedColorTheme

    fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun applyThemePreview(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun finishWelcome() {
        PreferencesManager.setWelcomeCompleted(this, true)
        PreferencesManager.setAppSource(this, selectedSource)
        PreferencesManager.setAutoUpdate(this, autoUpdate)
        PreferencesManager.setThemeMode(this, selectedTheme)
        LocaleManager.setLanguage(this, selectedLanguage)
        PreferencesManager.setDefaultPage(this, selectedDefaultPage)
        PreferencesManager.setDynamicColor(this, dynamicColor)
        PreferencesManager.setHideNavLabels(this, hideNavLabels)
        ThemeManager.setColorTheme(this, selectedColorTheme)

        LogManager.info(this, "Welcome completed",
            "source=$selectedSource, autoUpdate=$autoUpdate, theme=$selectedTheme, lang=$selectedLanguage, page=$selectedDefaultPage, dyn=$dynamicColor, hideNav=$hideNavLabels, color=$selectedColorTheme")

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    inner class WelcomePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 8

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WelcomeIntroFragment()
                1 -> WelcomeSourceFragment()
                2 -> WelcomeUpdateFragment()
                3 -> WelcomeUpdateOptionsFragment()
                4 -> WelcomeUsageFragment()
                5 -> WelcomeAppearanceFragment()
                6 -> WelcomeAuthFragment()
                else -> WelcomeReadyFragment()
            }
        }
    }
}