// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DhizukuManager {
    private const val TAG = "DhizukuManager"

    private const val DHIZUKU_PACKAGE = "com.iamr0s.Dhizuku"
    private const val DHIZUKU_SERVICE = "com.iamr0s.Dhizuku.server.DhizukuService"
    private const val DHIZUKU_PERMISSION = "com.iamr0s.Dhizuku.permission.DHIZUKU_PERMISSION"

    private const val ACTION_REQUEST_AUTHORIZATION = "com.iamr0s.Dhizuku.action.REQUEST_AUTHORIZATION"

    private const val EXTRA_REQUEST_CODE = "request_code"
    private const val EXTRA_REQUEST_PERMISSION = "request_permission"

    private const val REQUEST_CODE_AUTHORIZATION = 2001

    private var boundService: Any? = null
    private var bound = false
    private var boundContext: Context? = null

    private var serviceConnection: ServiceConnection? = null

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
            context.checkSelfPermission(DHIZUKU_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku permission: ${e.message}")
            false
        }
    }

    fun isDhizukuAuthorized(): Boolean {
        return try {
            if (!hasDhizukuPermission()) return false
            val service = boundService
            if (service == null) return false
            invokeStaticInt("getVersion").let { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Dhizuku authorization: ${e.message}")
            false
        }
    }

    fun requestDhizukuPermission() {
        try {
            val context = AppGlobals.applicationContext
            val intent = Intent(ACTION_REQUEST_AUTHORIZATION).apply {
                setPackage(DHIZUKU_PACKAGE)
                putExtra(EXTRA_REQUEST_CODE, REQUEST_CODE_AUTHORIZATION)
                putExtra(EXTRA_REQUEST_PERMISSION, DHIZUKU_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Dhizuku authorization requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Dhizuku permission: ${e.message}", e)
        }
    }

    suspend fun bindService(): Boolean = withContext(Dispatchers.Main) {
        if (bound && boundService != null) return@withContext true

        val context = AppGlobals.applicationContext
        boundContext = context

        return@withContext suspendCancellableCoroutine { continuation ->
            val intent = Intent().apply {
                setComponent(ComponentName(DHIZUKU_PACKAGE, DHIZUKU_SERVICE))
                putExtra(EXTRA_REQUEST_PERMISSION, DHIZUKU_PERMISSION)
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
                putExtra(EXTRA_REQUEST_PERMISSION, DHIZUKU_PERMISSION)
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