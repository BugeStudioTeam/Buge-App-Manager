// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import android.content.Context
import java.io.File

object AdbShellScriptGenerator {

    private const val SCRIPT_NAME = "start.sh"
    private const val PACKAGE_NAME = "com.buge.appmanager"
    private const val SERVICE_CLASS = "com.buge.appmanager.adb.AdbShellService"

    fun getScriptFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, SCRIPT_NAME)
    }

    fun generateScript(context: Context): File {
        val file = getScriptFile(context)
        val content = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# SPDX-License-Identifier: GPL-3.0-only")
            appendLine("# Copyright (C) 2026 Buge Studio")
            appendLine()
            appendLine("PKG=$PACKAGE_NAME")
            appendLine("APK=\$(pm path \$PKG | head -n1 | cut -d: -f2)")
            appendLine("if [ -z \"\$APK\" ]; then")
            appendLine("  echo \"start.sh: cannot find APK for \$PKG\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine()
            appendLine("if [ ! -x /system/bin/app_process ]; then")
            appendLine("  echo \"start.sh: app_process not found\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine()
            appendLine("echo \"start.sh: starting ADB shell service\"")
            appendLine("CLASSPATH=\"\$APK\" /system/bin/app_process /system/bin --nice-name=adb_shell_service $SERVICE_CLASS </dev/null >/data/local/tmp/adb_shell_service.log 2>&1 &")
            appendLine("echo \"start.sh: service launch command submitted\"")
        }
        file.writeText(content)
        try {
            file.setExecutable(true, false)
        } catch (e: Exception) {
        }
        return file
    }

    fun getAdbCommand(context: Context): String {
        val file = getScriptFile(context)
        return "adb shell sh ${file.absolutePath}"
    }
}
