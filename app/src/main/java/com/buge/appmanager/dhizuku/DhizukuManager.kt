// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DhizukuManager {
    private const val TAG = "DhizukuManager"

    private val connectionListeners = mutableListOf<() -> Unit>()
    private var initialized = false

    @Volatile
    private var cachedDpm: DevicePolicyManager? = null

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
                invalidateDpm()
                notifyConnected()
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Dhizuku permission granted via API")
                        invalidateDpm()
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

    fun invalidateDpm() {
        cachedDpm = null
    }

    @SuppressLint("PrivateApi")
    fun getWrappedDevicePolicyManager(): DevicePolicyManager? {
        cachedDpm?.let { return it }
        return try {
            if (!isPermissionGranted()) {
                return null
            }

            val context = AppGlobals.applicationContext
            val ownerComponent = Dhizuku.getOwnerComponent() ?: run {
                Log.w(TAG, "Owner component is null")
                return null
            }

            val ownerContext = context.createPackageContext(
                ownerComponent.packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val manager = ownerContext.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager

            val field = manager.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            val oldInterface = field.get(manager)

            if (oldInterface is DhizukuBinderWrapper) {
                cachedDpm = manager
                return manager
            }

            val asBinderMethod = oldInterface.javaClass.getMethod("asBinder")
            val oldBinder = asBinderMethod.invoke(oldInterface) as IBinder

            val newBinder = Dhizuku.binderWrapper(oldBinder)
            val newInterface = IDevicePolicyManager.Stub.asInterface(newBinder)
            field.set(manager, newInterface)

            cachedDpm = manager
            Log.d(TAG, "Wrapped DevicePolicyManager acquired")
            manager
        } catch (e: Exception) {
            Log.e(TAG, "getWrappedDevicePolicyManager failed: ${e.message}", e)
            null
        }
    }

    fun getOwnerComponent(): ComponentName? {
        return try {
            Dhizuku.getOwnerComponent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get owner component: ${e.message}", e)
            null
        }
    }

    fun setPermissionState(
        packageName: String, permission: String, grant: Boolean
    ): Boolean {
        return try {
            val dpm = getWrappedDevicePolicyManager() ?: run {
                Log.w(TAG, "Wrapped DPM is null")
                return false
            }
            val admin = getOwnerComponent() ?: run {
                Log.w(TAG, "Owner component is null")
                return false
            }

            val state = if (grant) {
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            } else {
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            }

            val result = dpm.setPermissionGrantState(admin, packageName, permission, state)
            Log.d(TAG, "setPermissionGrantState($packageName, $permission, $state) = $result")
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