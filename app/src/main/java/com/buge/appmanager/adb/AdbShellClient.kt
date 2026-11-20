// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import android.content.Context
import com.buge.appmanager.shizuku.ShizukuResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

object AdbShellClient {

    private const val HOST = "127.0.0.1"
    private const val DEFAULT_PORT = 15793
    private const val MAGIC = "BUGE_ADB_SHELL"
    private const val PORT_FILE_NAME = "adb_shell_port"
    private const val TIMEOUT_MS = 5000
    private const val CHECK_TIMEOUT_MS = 1500

    private fun readPort(context: Context?): Int {
        if (context == null) return DEFAULT_PORT
        return try {
            val dir = context.getExternalFilesDir(null) ?: return DEFAULT_PORT
            val file = File(dir, PORT_FILE_NAME)
            if (file.exists()) {
                file.readText().trim().toIntOrNull() ?: DEFAULT_PORT
            } else {
                DEFAULT_PORT
            }
        } catch (e: Exception) {
            DEFAULT_PORT
        }
    }

    fun isServiceRunning(context: Context?): Boolean {
        val port = readPort(context)

        val task = FutureTask {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(HOST, port),
                        CHECK_TIMEOUT_MS
                    )
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        return try {
            Thread(task, "adb-shell-check").start()
            task.get(CHECK_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            task.cancel(true)
            false
        }
    }

    fun execute(context: Context?, command: String): ShizukuResult {
        val port = readPort(context)
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(HOST, port), TIMEOUT_MS)
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
