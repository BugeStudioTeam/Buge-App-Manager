// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.buge.appmanager.R
import com.buge.appmanager.shizuku.ShizukuManager

class AppGlobals : Application() {

    companion object {
        private lateinit var _applicationContext: Context

        val applicationContext: Context
            get() = _applicationContext

        lateinit var regularTypeface: Typeface
        lateinit var mediumTypeface: Typeface
        lateinit var boldTypeface: Typeface

        fun preloadTypefaces() {
            regularTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_regular
            ) ?: Typeface.DEFAULT

            mediumTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_medium
            ) ?: Typeface.DEFAULT

            boldTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_bold
            ) ?: Typeface.DEFAULT
        }
    }

    override fun onCreate() {
        super.onCreate()
        _applicationContext = applicationContext
        preloadTypefaces()
        ShizukuManager.restoreAssistantBackup()
        LogManager.init(this)

        // Fuck: Auto backup on launch if enabled
        if (PreferencesManager.getBackupEnabled(this) &&
            PreferencesManager.getAutoBackupOnLaunch(this)) {
            Thread {
                try {
                    val success = BackupManager.performAutoBackup(this)
                    if (success) {
                        LogManager.success(this, "Auto backup on launch completed")
                    } else {
                        LogManager.warning(this, "Auto backup on launch failed")
                    }
                } catch (e: Exception) {
                    LogManager.error(this, "Auto backup on launch error", e.message)
                }
            }.start()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        LogManager.shutdown()
    }
}