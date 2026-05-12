package com.buge.appmanager

import android.os.Bundle

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings are embedded in SettingsFragment via MainActivity
        finish()
    }
}