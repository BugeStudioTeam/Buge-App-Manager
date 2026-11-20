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
            appendLine("  echo \"start: cannot find APK for \$PKG\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine()
            appendLine("APK_DIR=\$(dirname \"\$APK\")")
            appendLine("LIB_DIR=\"\$APK_DIR/lib\"")
            appendLine("ARCH=\$(getprop ro.product.cpu.abi)")
            appendLine("LIBST=\"\$LIB_DIR/\$ARCH/libstart.so\"")
            appendLine()
            appendLine("if [ ! -f \"\$LIBST\" ]; then")
            appendLine("  echo \"start: libstart.so not found at \$LIBST\"")
            appendLine("  echo \"start: verify that the installed APK contains the native library for \$ARCH\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine()
            appendLine("echo \"start: starting with apk=\$APK arch=\$ARCH\"")
            appendLine("exec \"\$LIBST\" --apk=\"\$APK\" --class=$SERVICE_CLASS")
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
