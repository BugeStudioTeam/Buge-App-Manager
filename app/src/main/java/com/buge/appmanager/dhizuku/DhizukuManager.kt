// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import com.iamr0s.dhizuku.api.Dhizuku
import com.iamr0s.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object DhizukuManager {
    private const val TAG = "DhizukuManager"

    fun isDhizukuMode(): Boolean {
        return try {
            PreferencesManager.getAuthMode(AppGlobals.applicationContext) == PreferencesManager.AUTH_MODE_DHIZUKU
        } catch (e: Exception) {
            false
        }
    }

    fun initDhizuku(): Boolean {
        return try {
            Dhizuku.init(AppGlobals.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Dhizuku init failed: ${e.message}")
            false
        }
    }

    fun hasDhizukuPermission(): Boolean {
        return try {
            Dhizuku.isPermissionGranted()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku permission: ${e.message}")
            false
        }
    }

    fun isDhizukuAuthorized(): Boolean {
        return try {
            initDhizuku() && Dhizuku.isPermissionGranted()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku authorization: ${e.message}")
            false
        }
    }

    fun requestDhizukuPermission(onResult: (Boolean) -> Unit) {
        try {
            if (!initDhizuku()) {
                Log.e(TAG, "Dhizuku init failed before permission request")
                onResult.invoke(false)
                return
            }
            if (Dhizuku.isPermissionGranted()) {
                onResult.invoke(true)
                return
            }
            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener {
                override fun onRequestPermission(grantResult: Int) {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    Log.d(TAG, "Dhizuku permission result: $granted")
                    onResult.invoke(granted)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Dhizuku permission: ${e.message}", e)
            onResult.invoke(false)
        }
    }

    suspend fun executeCommand(command: String): DhizukuResult = withContext(Dispatchers.IO) {
        try {
            if (!initDhizuku()) {
                return@withContext DhizukuResult(false, "", "Dhizuku init failed")
            }
            if (!Dhizuku.isPermissionGranted()) {
                return@withContext DhizukuResult(false, "", "Dhizuku permission not granted")
            }

            val process = Dhizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                DhizukuResult(true, output.trim(), "")
            } else {
                DhizukuResult(false, output.trim(), error.trim().ifEmpty { "Exit code: $exitCode" })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dhizuku command execution failed: ${e.message}", e)
            DhizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
        }
    }

    fun ensureBound(context: Context) {
        try {
            Dhizuku.init(context)
        } catch (e: Exception) {
            Log.w(TAG, "ensureBound error: ${e.message}")
        }
    }

    fun getAuthModeLabel(): String {
        return try {
            when (PreferencesManager.getAuthMode(AppGlobals.applicationContext)) {
                PreferencesManager.AUTH_MODE_DHIZUKU -> "Dhizuku"
                PreferencesManager.AUTH_MODE_ROOT -> "Root"
                else -> "Shizuku"
            }
        } catch (e: Exception) {
            "Shizuku"
        }
    }
}

data class DhizukuResult(
    val success: Boolean,
    val output: String,
    val error: String
)