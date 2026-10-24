// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DhizukuManager {
    private const val TAG = "DhizukuManager"

    private val connectionListeners = mutableListOf<() -> Unit>()
    private var initialized = false

    fun addConnectionListener(listener: () -> Unit) {
        if (!connectionListeners.contains(listener)) {
            connectionListeners.add(listener)
        }
    }

    fun removeConnectionListener(listener: () -> Unit) {
        connectionListeners.remove(listener)
    }

    private fun notifyConnected() {
        connectionListeners.forEach { it.invoke() }
    }

    fun init(context: Context): Boolean {
        if (initialized) return true
        return try {
            val success = Dhizuku.init(context)
            initialized = success
            Log.d(TAG, "Dhizuku initialized, result: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Dhizuku init failed: ${e.message}", e)
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            if (!initialized) {
                if (!init(AppGlobals.applicationContext)) {
                    return false
                }
            }
            Dhizuku.isPermissionGranted()
        } catch (e: Exception) {
            Log.w(TAG, "isPermissionGranted failed: ${e.message}")
            false
        }
    }

    fun isDhizukuAuthorized(): Boolean {
        return isPermissionGranted()
    }

    fun requestDhizukuPermission() {
        val context = AppGlobals.applicationContext
        try {
            init(context)
            if (Dhizuku.isPermissionGranted()) {
                Log.d(TAG, "Dhizuku permission already granted")
                notifyConnected()
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Dhizuku permission granted via API")
                        notifyConnected()
                    } else {
                        Log.w(TAG, "Dhizuku permission denied via API")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "requestDhizukuPermission failed: ${e.message}", e)
        }
    }

    fun setPermissionState(packageName: String, permission: String, grant: Boolean): Boolean {
        return try {
            val binder: IBinder = try {
                val method = Dhizuku::class.java.getMethod("getBinderWrapper")
                method.invoke(null) as IBinder
            } catch (e: Exception) {
                null
            } ?: run {
                Log.w(TAG, "Dhizuku binder wrapper is null")
                return false
            }

            val iDpmClass = Class.forName("android.app.admin.IDevicePolicyManager")
            val stubClass = Class.forName("android.app.admin.IDevicePolicyManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val dpmBinder = asInterfaceMethod.invoke(null, binder)

            val dpmClass = DevicePolicyManager::class.java
            val contextClass = Context::class.java
            val constructor = dpmClass.getDeclaredConstructor(contextClass, iDpmClass)
            constructor.isAccessible = true
            val dpm = constructor.newInstance(AppGlobals.applicationContext, dpmBinder)

            val ownerComponent = Dhizuku.getOwnerComponent() ?: run {
                Log.w(TAG, "Owner component is null")
                return false
            }

            val state = if (grant) {
                1 // PERMISSION_GRANT_STATE_GRANTED
            } else {
                0 // PERMISSION_GRANT_STATE_DEFAULT
            }

            val setPermissionMethod = dpmClass.getMethod(
                "setPermissionGrantState",
                ComponentName::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )

            val result = setPermissionMethod.invoke(dpm, ownerComponent, packageName, permission, state) as? Boolean ?: false
            Log.d(TAG, "setPermissionGrantState result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "setPermissionState failed: ${e.message}", e)
            false
        }
    }

    suspend fun executeCommand(command: String): DhizukuResult = withContext(Dispatchers.IO) {
        try {
            if (!isPermissionGranted()) {
                return@withContext DhizukuResult(false, "", "Dhizuku permission not granted")
            }

            val process = Dhizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                ?: return@withContext DhizukuResult(false, "", "Failed to create Dhizuku process")

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
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