// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DhizukuManager {
    private const val TAG = "DhizukuManager"

    private const val DHIZUKU_PACKAGE = "com.iamr0s.Dhizuku"
    private const val DHIZUKU_SERVICE = "com.iamr0s.Dhizuku.server.DhizukuService"
    private const val DHIZUKU_PERMISSION = "com.iamr0s.Dhizuku.permission.DHIZUKU_PERMISSION"

    private var boundService: Any? = null
    private var bound = false
    private var boundContext: Context? = null
    private var serviceConnection: ServiceConnection? = null
    private var initialized = false

    private val connectionListeners = mutableListOf<() -> Unit>()

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

    fun isDhizukuRunning(): Boolean {
        return try {
            bound && boundService != null
        } catch (e: Exception) {
            false
        }
    }

    fun hasDhizukuPermission(): Boolean {
        return try {
            val context = AppGlobals.applicationContext
            context.checkSelfPermission(DHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku permission: ${e.message}")
            false
        }
    }

    fun isDhizukuAuthorized(): Boolean {
        return try {
            isPermissionGranted()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku authorization: ${e.message}")
            false
        }
    }

    fun requestDhizukuPermission() {
        val context = AppGlobals.applicationContext
        try {
            init(context)
            if (Dhizuku.isPermissionGranted()) {
                Log.d(TAG, "Dhizuku permission already granted")
                bindServiceAsync(context)
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Dhizuku permission granted via API")
                        bindServiceAsync(context)
                    } else {
                        Log.w(TAG, "Dhizuku permission denied via API")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "requestDhizukuPermission failed: ${e.message}", e)
        }
    }

    suspend fun bindService(): Boolean = withContext(Dispatchers.Main) {
        if (bound && boundService != null) return@withContext true

        val context = AppGlobals.applicationContext
        boundContext = context

        return@withContext suspendCancellableCoroutine { continuation ->
            val intent = Intent().apply {
                setComponent(ComponentName(DHIZUKU_PACKAGE, DHIZUKU_SERVICE))
                putExtra("request_permission", DHIZUKU_PERMISSION)
            }

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    try {
                        val stubClass = Class.forName("com.iamr0s.Dhizuku.server.IDhizukuService\$Stub")
                        val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
                        val serviceInstance = asInterfaceMethod.invoke(null, service)
                        boundService = serviceInstance
                        bound = true
                        Log.d(TAG, "Dhizuku service connected")
                        if (continuation.isActive) continuation.resume(true)
                        notifyConnected()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind Dhizuku service: ${e.message}", e)
                        bound = false
                        boundService = null
                        if (continuation.isActive) continuation.resume(false)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.d(TAG, "Dhizuku service disconnected")
                    bound = false
                    boundService = null
                }
            }

            serviceConnection = connection

            val result = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "bindService exception: ${e.message}", e)
                false
            }

            if (!result) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    fun unbindService() {
        try {
            val context = boundContext
            val connection = serviceConnection
            if (context != null && connection != null && bound) {
                context.unbindService(connection)
            }
        } catch (e: Exception) {
            Log.w(TAG, "unbindService error: ${e.message}")
        } finally {
            bound = false
            boundService = null
            serviceConnection = null
        }
    }

    private fun invokeStaticInt(methodName: String): Int {
        val service = boundService ?: return 0
        return try {
            val method = service.javaClass.getMethod(methodName)
            (method.invoke(service) as? Int) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun executeCommand(command: String): DhizukuResult = withContext(Dispatchers.IO) {
        try {
            if (boundService == null) {
                val bindSuccess = bindService()
                if (!bindSuccess) {
                    return@withContext DhizukuResult(false, "", "Failed to bind Dhizuku service")
                }
            }
            val service = boundService
            if (service == null) {
                return@withContext DhizukuResult(false, "", "Dhizuku service is not bound")
            }

            val execMethod = service.javaClass.getMethod(
                "exec",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = execMethod.invoke(
                service,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

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

    fun ensureBound(context: Context) {
        try {
            if (!initialized) {
                init(context)
            }
            if (bound) return
            bindServiceAsync(context)
        } catch (e: Exception) {
            Log.w(TAG, "ensureBound error: ${e.message}")
        }
    }

    private fun bindServiceAsync(context: Context) {
        try {
            val intent = Intent().apply {
                setComponent(ComponentName(DHIZUKU_PACKAGE, DHIZUKU_SERVICE))
                putExtra("request_permission", DHIZUKU_PERMISSION)
            }

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    try {
                        val stubClass = Class.forName("com.iamr0s.Dhizuku.server.IDhizukuService\$Stub")
                        val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
                        val serviceInstance = asInterfaceMethod.invoke(null, service)
                        boundService = serviceInstance
                        bound = true
                        Log.d(TAG, "Dhizuku service connected (async)")
                        notifyConnected()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind Dhizuku service (async): ${e.message}", e)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    bound = false
                    boundService = null
                }
            }

            serviceConnection = connection
            boundContext = context
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.w(TAG, "bindServiceAsync error: ${e.message}")
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