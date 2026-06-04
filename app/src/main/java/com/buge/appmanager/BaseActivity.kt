package com.buge.appmanager

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.ThemeManager

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