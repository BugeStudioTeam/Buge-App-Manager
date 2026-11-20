// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import android.content.Context
import java.io.File

object AdbShellClient {

    fun executeAdbCommand(context: Context): String {
        val scriptFile = File(context.getExternalFilesDir(null), "start.sh")
        if (!scriptFile.exists()) {
            AdbShellScriptGenerator.generateScript(context)
        }

        val command = "sh ${scriptFile.absolutePath}"
        val process = Runtime.getRuntime().exec(command)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()

        return if (error.isNotBlank()) error else output
    }
}
