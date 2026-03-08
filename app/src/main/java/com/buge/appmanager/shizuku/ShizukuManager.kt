package com.buge.appmanager.shizuku

import android.content.Context
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

    /**
     * Check if Shizuku service is running and available
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    /**
     * Check if we have Shizuku permission
     */
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

    /**
     * Request Shizuku permission
     */
    fun requestShizukuPermission() {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission: ${e.message}")
        }
    }

    /**
     * Execute a shell command via Shizuku
     */
    suspend fun executeCommand(command: String): ShizukuResult = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable()) {
            return@withContext ShizukuResult(false, "", "Shizuku is not running")
        }
        if (!hasShizukuPermission()) {
            return@withContext ShizukuResult(false, "", "Shizuku permission not granted")
        }

        try {
            Log.d(TAG, "Executing command: $command")
            
            // If Shizuku.newProcess is reported as private, we use reflection to access it
            // or find the public alternative. In some versions it's Shizuku.newProcess(...)
            // but let's try reflection as a foolproof way for this specific environment.
            val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", 
                Array<String>::class.java, 
                Array<String>::class.java, 
                String::class.java
            )
            newProcessMethod.isAccessible = true
            
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            
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
        return executeCommand("pm grant $packageName $permission")
    }

    suspend fun revokePermission(packageName: String, permission: String): ShizukuResult {
        return executeCommand("pm revoke $packageName $permission")
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
