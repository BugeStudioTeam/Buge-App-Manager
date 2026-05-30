package com.buge.appmanager

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LocaleManager

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
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Apply font only once
        if (!fontApplied && FontOverrideHelper.isEnglishLocale()) {
            FontOverrideHelper.applyToActivity(this)
            fontApplied = true
        }
    }
}