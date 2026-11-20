// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File

class AdbShellService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scriptFile = File(getExternalFilesDir(null), "start.sh")
        if (!scriptFile.exists()) {
            AdbShellScriptGenerator.generateScript(this)
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", scriptFile.absolutePath))
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stopSelf()
        return START_NOT_STICKY
    }
}
