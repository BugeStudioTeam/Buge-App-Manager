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

    suspend fun getAllWriteSettingsStatus(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Boolean>()
        val packagesResult = executeCommand("pm list packages | cut -d':' -f2")
        if (packagesResult.success) {
            val packages = packagesResult.output.lines().filter { it.isNotEmpty() }
            for (pkg in packages) {
                val status = getWriteSettingsStatus(pkg)
                if (status != null) {
                    result[pkg] = status
                }
            }
        }
        result
    }

    suspend fun grantPermission(packageName: String, permission: String): ShizukuResult {
        return when (permission) {
            "android.permission.WRITE_SETTINGS" -> {
                executeCommand("appops set $packageName WRITE_SETTINGS allow")
            }
            "android.permission.SYSTEM_ALERT_WINDOW" -> {
                executeCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            }
            "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                executeCommand("appops set $packageName REQUEST_INSTALL_PACKAGES allow")
            }
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                grantManageExternalStorage(packageName)
            }
            else -> {
                executeCommand("pm grant $packageName $permission")
            }
        }
    }

    suspend fun revokePermission(packageName: String, permission: String): ShizukuResult {
        return when (permission) {
            "android.permission.WRITE_SETTINGS" -> {
                executeCommand("appops set $packageName WRITE_SETTINGS deny")
            }
            "android.permission.SYSTEM_ALERT_WINDOW" -> {
                executeCommand("appops set $packageName SYSTEM_ALERT_WINDOW deny")
            }
            "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                executeCommand("appops set $packageName REQUEST_INSTALL_PACKAGES deny")
            }
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                revokeManageExternalStorage(packageName)
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

    suspend fun getWriteSettingsStatus(packageName: String): Boolean? {
        return try {
            val result = executeCommand("appops get $packageName WRITE_SETTINGS")
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
            Log.e(TAG, "Error checking WRITE_SETTINGS: ${e.message}")
            null
        }
    }

    suspend fun getOverlayStatus(packageName: String): Boolean? {
        return try {
            val result = executeCommand("appops get $packageName SYSTEM_ALERT_WINDOW")
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
            Log.e(TAG, "Error checking SYSTEM_ALERT_WINDOW: ${e.message}")
            null
        }
    }

    suspend fun getInstallUnknownAppsStatus(packageName: String): Boolean? {
        return try {
            val result = executeCommand("appops get $packageName REQUEST_INSTALL_PACKAGES")
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
            Log.e(TAG, "Error checking REQUEST_INSTALL_PACKAGES: ${e.message}")
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