// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import com.buge.appmanager.shizuku.ShizukuResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object AdbShellClient {

    private const val HOST = "127.0.0.1"
    private const val PORT = 15793
    private const val MAGIC = "BUGE_ADB_SHELL"
    private const val TIMEOUT_MS = 5000

    fun isServiceRunning(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun execute(command: String): ShizukuResult {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
            socket.soTimeout = 30000

            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.write(MAGIC)
            writer.write("\n")
            writer.flush()

            writer.write(command)
            writer.write("\n")
            writer.flush()

            val resultBuilder = StringBuilder()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line == "---END---") break
                resultBuilder.append(line).append("\n")
            }

            socket.close()

            val full = resultBuilder.toString().trimEnd()
            val firstLine = full.lineSequence().firstOrNull() ?: ""
            val exitCode = firstLine.removePrefix("EXIT_CODE:").toIntOrNull() ?: -1
            val output = full.lineSequence().drop(1).joinToString("\n")

            if (exitCode == 0) {
                ShizukuResult(true, output.trim(), "")
            } else {
                ShizukuResult(false, output.trim(), "Exit code: $exitCode")
            }
        } catch (e: Exception) {
            ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
        }
    }
}
