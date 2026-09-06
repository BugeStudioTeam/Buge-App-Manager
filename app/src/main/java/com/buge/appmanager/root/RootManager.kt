// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.root

import android.util.Log
import com.buge.appmanager.shizuku.ShizukuResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Executes privileged shell commands through a root `su` binary.
 *
 * The su command itself is configurable so custom root solutions
 * (Magisk, KernelSU, APatch, or a vendor su path) can be used.
 */
object RootManager {
    private const val TAG = "RootManager"
    private const val COMMAND_TIMEOUT_SECONDS = 30L

    private val COMMON_SU_DIRS = listOf(
        "/system/bin",
        "/system/xbin",
        "/sbin",
        "/su/bin",
        "/data/adb/magisk",
        "/data/adb/ksu/bin",
        "/data/adb/ap/bin",
        "/data/local/bin",
        "/vendor/bin"
    )

    /**
     * Checks whether the su binary can be located. This is a fast,
     * non-blocking check that never triggers a root prompt; the actual
     * root grant happens lazily when a command is executed.
     */
    fun isSuBinaryAvailable(suPath: String): Boolean {
        if (suPath.isBlank()) return false
        return try {
            if (suPath.contains('/')) {
                File(suPath).let { it.exists() && it.canExecute() }
            } else {
                val pathDirs = (System.getenv("PATH") ?: "")
                    .split(':')
                    .filter { it.isNotEmpty() }
                val candidates = if (pathDirs.isEmpty()) COMMON_SU_DIRS else pathDirs + COMMON_SU_DIRS
                candidates.any { dir ->
                    val file = File(dir, suPath)
                    file.exists() && file.canExecute()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking su binary: ${e.message}")
            false
        }
    }

    /**
     * Runs [command] through the root shell. The command string is passed
     * verbatim as the `-c` argument, so shell features such as pipes remain
     * available inside the root shell without local shell interpolation.
     */
    suspend fun executeCommand(command: String, suPath: String): ShizukuResult =
        withContext(Dispatchers.IO) {
            if (!isSuBinaryAvailable(suPath)) {
                return@withContext ShizukuResult(
                    false,
                    "",
                    "Root not available (\"$suPath\" not found)"
                )
            }
            try {
                val process = ProcessBuilder(suPath, "-c", command).start()

                val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }

                if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext ShizukuResult(false, output.trim(), "Command timed out")
                }

                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    ShizukuResult(true, output.trim(), "")
                } else {
                    ShizukuResult(false, output.trim(), error.trim().ifEmpty { "Exit code: $exitCode" })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed: ${e.message}", e)
                ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
            }
        }
}
