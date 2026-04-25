package com.buge.appmanager.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001

    private val SPECIAL_PERMISSION_OPS = mapOf(
        "android.permission.SYSTEM_ALERT_WINDOW" to "SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "REQUEST_INSTALL_PACKAGES"
    )

    private val APPOP_PERMISSIONS = setOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES"
    )

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission: ${e.message}")
        }
    }

    suspend fun executeCommand(command: String): ShizukuResult = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable()) {
            return@withContext ShizukuResult(false, "", "Shizuku is not running")
        }
        if (!hasShizukuPermission()) {
            return@withContext ShizukuResult(false, "", "Shizuku permission not granted")
        }
        try {
            Log.d(TAG, "Executing command: $command")

            val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null, arrayOf("sh", "-c", command), null, null
            ) as Process

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            Log.d(TAG, "Command exit code: $exitCode, output: $output, error: $error")
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

    suspend fun grantPermission(packageName: String, permission: String): ShizukuResult {
        return when {
            permission == "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                grantManageExternalStorage(packageName)
            }
            permission in APPOP_PERMISSIONS -> {
                val op = SPECIAL_PERMISSION_OPS[permission]
                executeCommand("appops set $packageName $op allow")
            }
            else -> {
                executeCommand("pm grant $packageName $permission")
            }
        }
    }

    suspend fun revokePermission(packageName: String, permission: String): ShizukuResult {
        return when {
            permission == "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                revokeManageExternalStorage(packageName)
            }
            permission in APPOP_PERMISSIONS -> {
                val op = SPECIAL_PERMISSION_OPS[permission]
                executeCommand("appops set $packageName $op deny")
            }
            else -> {
                executeCommand("pm revoke $packageName $permission")
            }
        }
    }

    private suspend fun grantManageExternalStorage(packageName: String): ShizukuResult {
        val result = executeCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        if (result.success) {
            executeCommand("cmd appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        }
        return result
    }

    private suspend fun revokeManageExternalStorage(packageName: String): ShizukuResult {
        val result = executeCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE deny")
        if (result.success) {
            executeCommand("cmd appops set $packageName MANAGE_EXTERNAL_STORAGE deny")
        }
        return result
    }

    suspend fun getManageExternalStorageStatus(packageName: String): Boolean? {
        return try {
            val result = executeCommand("appops get $packageName MANAGE_EXTERNAL_STORAGE")
            if (result.success) {
                when {
                    result.output.contains("allow") -> true
                    result.output.contains("deny") -> false
                    else -> null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MANAGE_EXTERNAL_STORAGE: ${e.message}")
            null
        }
    }

    suspend fun forceStop(packageName: String): ShizukuResult {
        return executeCommand("am force-stop $packageName")
    }

    suspend fun clearData(packageName: String): ShizukuResult {
        return executeCommand("pm clear $packageName")
    }

    suspend fun disableApp(packageName: String): ShizukuResult {
        return executeCommand("pm disable-user --user 0 $packageName")
    }

    suspend fun enableApp(packageName: String): ShizukuResult {
        return executeCommand("pm enable $packageName")
    }

    suspend fun uninstallApp(packageName: String): ShizukuResult {
        return executeCommand("pm uninstall --user 0 $packageName")
    }
}

data class ShizukuResult(
    val success: Boolean,
    val output: String,
    val error: String
)