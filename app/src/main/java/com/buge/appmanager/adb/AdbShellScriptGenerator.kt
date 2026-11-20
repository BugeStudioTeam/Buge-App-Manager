// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import android.content.Context
import java.io.File

object AdbShellScriptGenerator {

    fun generateScript(context: Context): File {
        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("pkg=\"${context.packageName}\"")
            appendLine("base_dir=\"/data/app/~~*==/\$pkg-*==/lib/\"")
            appendLine("native_dir=\"\"")
            appendLine("for arch in arm64-v8a armeabi-v7a x86_64 x86; do")
            appendLine("    candidate=\"/data/app/\"*\"/\$pkg-\"*\"/lib/\$arch/libstart.so\"")
            appendLine("    found=\$(ls \$candidate 2>/dev/null | head -n 1)")
            appendLine("    if [ -n \"\$found\" ]; then")
            appendLine("        native_dir=\$(dirname \"\$found\")")
            appendLine("        break")
            appendLine("    fi")
            appendLine("done")
            appendLine("if [ -z \"\$native_dir\" ]; then")
            appendLine("    echo \"buge_service: libstart.so not found\"")
            appendLine("    exit 1")
            appendLine("fi")
            appendLine("export LD_LIBRARY_PATH=\"\$native_dir:\$LD_LIBRARY_PATH\"")
            appendLine("exec \"\$native_dir/libstart.so\"")
        }

        val file = File(context.getExternalFilesDir(null), "start.sh")
        file.writeText(scriptContent)
        file.setExecutable(true, false)
        return file
    }
}
