// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.dhizuku

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.buge.appmanager.shizuku.ShizukuResult
import com.buge.appmanager.util.AppGlobals
import com.buge.appmanager.util.PreferencesManager
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DhizukuManager {
    private const val TAG = "DhizukuManager"
    private const val PACKAGE_UNINSTALLED_ACTION = "com.buge.appmanager.action.PACKAGE_UNINSTALLED"

    private val connectionListeners = mutableListOf<() -> Unit>()
    private var initialized = false

    @Volatile
    private var cachedDpm: DevicePolicyManager? = null

    private val currentUserId: Int
        get() = Process.myUid() / 100000

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

    @SuppressLint("PrivateApi")
    fun clearAppUserData(packageName: String): ShizukuResult {
        return try {
            if (!isPermissionGranted()) {
                return ShizukuResult(false, "", "Dhizuku permission not granted")
            }

            val context = AppGlobals.applicationContext
            val ownerComponent = getOwnerComponent()
                ?: return ShizukuResult(false, "", "Owner component is null")
            val ownerContext = context.createPackageContext(
                ownerComponent.packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )

            val activityManager = ownerContext.getSystemService(Context.ACTIVITY_SERVICE)
            val field = activityManager.javaClass.getDeclaredField("mRemote")
            field.isAccessible = true
            val oldInterface = field.get(activityManager)

            val oldBinder = oldInterface.javaClass.getMethod("asBinder")
                .invoke(oldInterface) as IBinder
            val newBinder = Dhizuku.binderWrapper(oldBinder)

            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val newInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, newBinder)

            field.set(activityManager, newInterface)

            val observerInterface =
                Class.forName("android.content.pm.IPackageDataObserver")

            val clearMethod = activityManager.javaClass.getMethod(
                "clearApplicationUserData",
                String::class.java,
                observerInterface,
                Int::class.javaPrimitiveType
            )

            val latch = CountDownLatch(1)
            var success = false

            val observer = java.lang.reflect.Proxy.newProxyInstance(
                observerInterface.classLoader,
                arrayOf(observerInterface)
            ) { _, method, args ->
                if (method.name == "onRemoveCompleted") {
                    success = args?.getOrNull(1) as? Boolean ?: false
                    latch.countDown()
                }
                null
            }

            clearMethod.invoke(
                activityManager,
                packageName,
                observer,
                currentUserId
            )
            latch.await(10, TimeUnit.SECONDS)

            if (success) ShizukuResult(true, "", "")
            else ShizukuResult(false, "", "clearApplicationUserData failed")
        } catch (e: Exception) {
            Log.e(TAG, "clearAppUserData failed: ${e.message}", e)
            ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
        }
    }

    @SuppressLint("PrivateApi")
    fun uninstallApp(packageName: String): ShizukuResult {
        return try {
            if (!isPermissionGranted()) {
                return ShizukuResult(false, "", "Dhizuku permission not granted")
            }

            val context = AppGlobals.applicationContext
            val ownerComponent = getOwnerComponent()
                ?: return ShizukuResult(false, "", "Owner component is null")
            val ownerContext = context.createPackageContext(
                ownerComponent.packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val packageManager = ownerContext.packageManager

            val installer = packageManager.packageInstaller
            val field = installer.javaClass.getDeclaredField("mInstaller")
            field.isAccessible = true
            val oldInterface = field.get(installer)

            val oldBinder = oldInterface.javaClass.getMethod("asBinder")
                .invoke(oldInterface) as IBinder
            val newBinder = Dhizuku.binderWrapper(oldBinder)

            val stubClass = Class.forName("android.content.pm.IPackageInstaller\$Stub")
            val newInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, newBinder)

            field.set(installer, newInterface)

            val latch = CountDownLatch(1)
            var errorMsg: String? = null
            var pendingIntent: PendingIntent? = null

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirm = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (confirm != null) {
                            try {
                                context.startActivity(confirm)
                            } catch (e: Exception) {
                                errorMsg = e.message
                                latch.countDown()
                            }
                        } else {
                            errorMsg = "Pending user action intent missing"
                            latch.countDown()
                        }
                    } else {
                        if (status != PackageInstaller.STATUS_SUCCESS) {
                            errorMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                ?: "Unknown uninstall error"
                        }
                        latch.countDown()
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PACKAGE_UNINSTALLED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val broadcastIntent = Intent(PACKAGE_UNINSTALLED_ACTION)
                .setPackage(context.packageName)

            val flags = if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_MUTABLE
            }
            pendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent, flags)

            val uninstallMethod = installer.javaClass.getMethod(
                "uninstall",
                String::class.java,
                android.content.IntentSender::class.java,
                Int::class.javaPrimitiveType
            )
            uninstallMethod.invoke(
                installer,
                packageName,
                pendingIntent.intentSender,
                currentUserId
            )

            latch.await(30, TimeUnit.SECONDS)

            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
            }

            if (errorMsg == null) {
                ShizukuResult(true, "", "")
            } else {
                ShizukuResult(false, "", errorMsg!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "uninstallApp failed: ${e.message}", e)
            ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
        }
    }

    @SuppressLint("PrivateApi")
    fun forceStopApp(packageName: String): ShizukuResult {
        return try {
            if (!isPermissionGranted()) {
                return ShizukuResult(false, "", "Dhizuku permission not granted")
            }

            val context = AppGlobals.applicationContext
            val ownerComponent = getOwnerComponent()
                ?: return ShizukuResult(false, "", "Owner component is null")
            val ownerContext = context.createPackageContext(
                ownerComponent.packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )

            val activityManager = ownerContext.getSystemService(Context.ACTIVITY_SERVICE)
            val field = activityManager.javaClass.getDeclaredField("mRemote")
            field.isAccessible = true
            val oldInterface = field.get(activityManager)

            val oldBinder = oldInterface.javaClass.getMethod("asBinder")
                .invoke(oldInterface) as IBinder
            val newBinder = Dhizuku.binderWrapper(oldBinder)

            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val newInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, newBinder)

            field.set(activityManager, newInterface)

            val forceStopMethod = activityManager.javaClass.getMethod(
                "forceStopPackage",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            forceStopMethod.invoke(
                activityManager,
                packageName,
                currentUserId
            )
            ShizukuResult(true, "", "")
        } catch (e: Exception) {
            Log.e(TAG, "forceStopApp failed: ${e.message}", e)
            ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
        }
    }

    @SuppressLint("PrivateApi")
    fun setApplicationEnabled(
        packageName: String, enabled: Boolean
    ): ShizukuResult {
        return try {
            if (!isPermissionGranted()) {
                return ShizukuResult(false, "", "Dhizuku permission not granted")
            }

            val context = AppGlobals.applicationContext
            val ownerComponent = getOwnerComponent()
                ?: return ShizukuResult(false, "", "Owner component is null")
            val ownerContext = context.createPackageContext(
                ownerComponent.packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val packageManager = ownerContext.packageManager

            val field = packageManager.javaClass.getDeclaredField("mPM")
            field.isAccessible = true
            val oldInterface = field.get(packageManager)

            val oldBinder = oldInterface.javaClass.getMethod("asBinder")
                .invoke(oldInterface) as IBinder
            val newBinder = Dhizuku.binderWrapper(oldBinder)

            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val newInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, newBinder)

            field.set(packageManager, newInterface)

            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }

            val setEnabledMethod = packageManager.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            setEnabledMethod.invoke(
                packageManager,
                packageName,
                newState,
                0,
                currentUserId,
                null
            )
            ShizukuResult(true, "", "")
        } catch (e: Exception) {
            Log.e(TAG, "setApplicationEnabled failed: ${e.message}", e)
            ShizukuResult(false, "", "Exception: ${e.message ?: "Unknown error"}")
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