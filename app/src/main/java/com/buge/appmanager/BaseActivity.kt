// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.ThemeManager
import com.google.android.material.color.DynamicColors

abstract class BaseActivity : AppCompatActivity() {

    private var fontApplied = false

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val savedLanguage = LocaleManager.getLanguage(newBase)
            val context = LocaleManager.createContextWithLocale(newBase, savedLanguage)
            super.attachBaseContext(context)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fuck: Apply dynamic color if enabled and Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            PreferencesManager.getDynamicColor(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        ThemeManager.applyColorTheme(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (!fontApplied) {
            FontOverrideHelper.applyToActivity(this)
            fontApplied = true
        }
    }
}