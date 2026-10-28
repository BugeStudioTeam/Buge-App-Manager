// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.WelcomeActivity
import com.buge.appmanager.util.ThemeManager
import com.google.android.material.materialswitch.MaterialSwitch

class WelcomeAppearanceFragment : Fragment() {

    private var dynamicColor: Boolean = false
    private var hideNavLabels: Boolean = false
    private var selectedColorTheme: ThemeManager.ColorTheme = ThemeManager.ColorTheme.DEFAULT

    private lateinit var colorThemeContainer: View
    private lateinit var dynamicColorSwitch: MaterialSwitch
    private lateinit var hideNavSwitch: MaterialSwitch
    private lateinit var checkDefault: ImageView
    private lateinit var checkRed: ImageView
    private lateinit var checkGreen: ImageView
    private lateinit var checkYellow: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_appearance, container, false)

        val dynamicColorContainer = view.findViewById<View>(R.id.dynamic_color_container)
        dynamicColorSwitch = view.findViewById(R.id.dynamic_color_switch)
        hideNavSwitch = view.findViewById(R.id.hide_nav_switch)
        colorThemeContainer = view.findViewById(R.id.color_theme_container)
        checkDefault = view.findViewById(R.id.color_check_default)
        checkRed = view.findViewById(R.id.color_check_red)
        checkGreen = view.findViewById(R.id.color_check_green)
        checkYellow = view.findViewById(R.id.color_check_yellow)

        val activity = activity as? WelcomeActivity
        val supported = activity?.isDynamicColorSupported() == true

        if (supported) {
            dynamicColorContainer.visibility = View.VISIBLE
            dynamicColorSwitch.isEnabled = true
            dynamicColorSwitch.alpha = 1f
        } else {
            dynamicColorContainer.visibility = View.GONE
        }

        dynamicColorSwitch.isChecked = false
        hideNavSwitch.isChecked = false
        activity?.onDynamicColorChanged(false)
        activity?.onHideNavLabelsChanged(false)
        activity?.onColorThemeChanged(ThemeManager.ColorTheme.DEFAULT)

        updateColorThemeUI(false)
        updateColorSelection()

        dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            dynamicColor = isChecked
            activity?.onDynamicColorChanged(isChecked)
            updateColorThemeUI(isChecked)
        }

        hideNavSwitch.setOnCheckedChangeListener { _, isChecked ->
            hideNavLabels = isChecked
            activity?.onHideNavLabelsChanged(isChecked)
        }

        view.findViewById<View>(R.id.color_default).setOnClickListener {
            if (!dynamicColor) {
                selectColorTheme(ThemeManager.ColorTheme.DEFAULT)
            }
        }
        view.findViewById<View>(R.id.color_red).setOnClickListener {
            if (!dynamicColor) {
                selectColorTheme(ThemeManager.ColorTheme.RED)
            }
        }
        view.findViewById<View>(R.id.color_green).setOnClickListener {
            if (!dynamicColor) {
                selectColorTheme(ThemeManager.ColorTheme.GREEN)
            }
        }
        view.findViewById<View>(R.id.color_yellow).setOnClickListener {
            if (!dynamicColor) {
                selectColorTheme(ThemeManager.ColorTheme.YELLOW)
            }
        }

        return view
    }

    private fun selectColorTheme(theme: ThemeManager.ColorTheme) {
        selectedColorTheme = theme
        updateColorSelection()
        (activity as? WelcomeActivity)?.onColorThemeChanged(theme)
    }

    private fun updateColorSelection() {
        checkDefault.visibility = if (selectedColorTheme == ThemeManager.ColorTheme.DEFAULT) View.VISIBLE else View.GONE
        checkRed.visibility = if (selectedColorTheme == ThemeManager.ColorTheme.RED) View.VISIBLE else View.GONE
        checkGreen.visibility = if (selectedColorTheme == ThemeManager.ColorTheme.GREEN) View.VISIBLE else View.GONE
        checkYellow.visibility = if (selectedColorTheme == ThemeManager.ColorTheme.YELLOW) View.VISIBLE else View.GONE
    }

    private fun updateColorThemeUI(dynamicEnabled: Boolean) {
        if (dynamicEnabled) {
            colorThemeContainer.alpha = 0.4f
            colorThemeContainer.isEnabled = false
            checkDefault.visibility = View.GONE
            checkRed.visibility = View.GONE
            checkGreen.visibility = View.GONE
            checkYellow.visibility = View.GONE
        } else {
            colorThemeContainer.alpha = 1f
            colorThemeContainer.isEnabled = true
            updateColorSelection()
        }
    }
}