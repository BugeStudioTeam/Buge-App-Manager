// 文件路径: app/src/main/java/com/buge/appmanager/data/AppRepository.kt

package com.buge.appmanager.data

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.buge.appmanager.model.AppFilter
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppSortOrder
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    companion object {
        private const val TAG = "AppRepository"

        val PERMISSION_MICROPHONE = listOf(
            "android.permission.RECORD_AUDIO"
        )
        val PERMISSION_CAMERA = listOf(
            "android.permission.CAMERA"
        )
        val PERMISSION_LOCATION = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
        )
        val PERMISSION_BACKGROUND_LOCATION = listOf(
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        )
        val PERMISSION_CONTACTS = listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS"
        )
        val PERMISSION_STORAGE = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )
        val PERMISSION_PHONE = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS"
        )
        val PERMISSION_SMS = listOf(
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS"
        )
        val PERMISSION_CALENDAR = listOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        )
        val PERMISSION_SENSORS = listOf(
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND"
        )
        val PERMISSION_ACTIVITY = listOf(
            "android.permission.ACTIVITY_RECOGNITION"
        )
        val PERMISSION_NEARBY = listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.UWB_RANGING"
        )
        val PERMISSION_NOTIFICATIONS = listOf(
            "android.permission.POST_NOTIFICATIONS"
        )
        val PERMISSION_MEDIA_IMAGES = listOf(
            "android.permission.READ_MEDIA_IMAGES"
        )
        val PERMISSION_MEDIA_VIDEO = listOf(
            "android.permission.READ_MEDIA_VIDEO"
        )
        val PERMISSION_MEDIA_AUDIO = listOf(
            "android.permission.READ_MEDIA_AUDIO"
        )
        val PERMISSION_OVERLAY = listOf(
            "android.permission.SYSTEM_ALERT_WINDOW"
        )
        val PERMISSION_INSTALL_UNKNOWN_APPS = listOf(
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )
        val PERMISSION_MANAGE_STORAGE = listOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )
        val ALL_DANGEROUS_PERMISSIONS = (
            PERMISSION_MICROPHONE + PERMISSION_CAMERA + PERMISSION_LOCATION +
            PERMISSION_BACKGROUND_LOCATION + PERMISSION_CONTACTS + PERMISSION_STORAGE +
            PERMISSION_PHONE + PERMISSION_SMS + PERMISSION_CALENDAR + PERMISSION_SENSORS +
            PERMISSION_ACTIVITY + PERMISSION_NEARBY + PERMISSION_NOTIFICATIONS +
            PERMISSION_MEDIA_IMAGES + PERMISSION_MEDIA_VIDEO + PERMISSION_MEDIA_AUDIO +
            PERMISSION_OVERLAY + PERMISSION_INSTALL_UNKNOWN_APPS + PERMISSION_MANAGE_STORAGE
        ).toSet()

        val SPECIAL_PERMISSIONS = setOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )

        val APPOP_ONLY_PERMISSIONS = setOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )
    }

    private fun getSpecialPermissionStatus(packageName: String, permission: String): Boolean? {
        return try {
            when (permission) {
                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val appOps = context.getSystemService(AppOpsManager::class.java)
                        val uid = pm.getApplicationInfo(packageName, 0).uid
                        val mode = appOps.checkOpNoThrow(
                            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            uid,
                            packageName
                        )
                        mode == AppOpsManager.MODE_ALLOWED
                    } else {
                        true
                    }
                }
                "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appOps = context.getSystemService(AppOpsManager::class.java)
                        val uid = pm.getApplicationInfo(packageName, 0).uid
                        val mode = appOps.checkOpNoThrow(
                            "android:request_install_packages",
                            uid,
                            packageName
                        )
                        mode == AppOpsManager.MODE_ALLOWED
                    } else {
                        true
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSpecialPermissionStatus error for $packageName / $permission: ${e.message}")
            null
        }
    }

    private suspend fun getManageExternalStorageStatus(packageName: String): Boolean? {
        return try {
            ShizukuManager.getManageExternalStorageStatus(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting MANAGE_EXTERNAL_STORAGE status: ${e.message}")
            null
        }
    }

    suspend fun getInstalledApps(
        filter: AppFilter = AppFilter.ALL,
        sortOrder: AppSortOrder = AppSortOrder.NAME,
        searchQuery: String = "",
        showSystemApps: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val flags = PackageManager.GET_META_DATA
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
            var apps = packages.mapNotNull { pkg ->
                try {
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isEnabled = appInfo.enabled
                    AppInfo(
                        packageName = pkg.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        versionName = pkg.versionName ?: "N/A",
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkg.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkg.versionCode.toLong()
                        },
                        icon = try { pm.getApplicationIcon(pkg.packageName) } catch (e: Exception) { null },
                        isSystemApp = isSystem,
                        isEnabled = isEnabled,
                        installTime = pkg.firstInstallTime,
                        updateTime = pkg.lastUpdateTime,
                        targetSdkVersion = appInfo.targetSdkVersion,
                        minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            appInfo.minSdkVersion
                        } else 0,
                        apkPath = appInfo.sourceDir ?: ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing package ${pkg.packageName}: ${e.message}")
                    null
                }
            }
            apps = when (filter) {
                AppFilter.ALL -> if (showSystemApps) apps else apps.filter { !it.isSystemApp }
                AppFilter.USER -> apps.filter { !it.isSystemApp }
                AppFilter.SYSTEM -> apps.filter { it.isSystemApp }
            }
            if (searchQuery.isNotEmpty()) {
                apps = apps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
            apps = when (sortOrder) {
                AppSortOrder.NAME -> apps.sortedBy { it.appName.lowercase() }
                AppSortOrder.SIZE -> apps.sortedByDescending { it.versionCode }
                AppSortOrder.INSTALL_DATE -> apps.sortedByDescending { it.installTime }
            }
            apps
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAppPermissions(packageName: String): List<PermissionInfo> = withContext(Dispatchers.IO) {
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            val requestedPermissions = pkgInfo.requestedPermissions ?: return@withContext emptyList()
            val permissionFlags = pkgInfo.requestedPermissionsFlags ?: return@withContext emptyList()

            val permissions = mutableListOf<PermissionInfo>()

            for (index in requestedPermissions.indices) {
                val permName = requestedPermissions[index]

                val isGranted = when {
                    permName == "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                        getManageExternalStorageStatus(packageName) ?: false
                    }
                    permName in SPECIAL_PERMISSIONS -> {
                        getSpecialPermissionStatus(packageName, permName) ?: false
                    }
                    else -> {
                        (permissionFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    }
                }

                val isDangerous = try {
                    val permInfo = @Suppress("DEPRECATION") pm.getPermissionInfo(permName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        permInfo.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }

                val isSpecial = permName in SPECIAL_PERMISSIONS
                val isAppOpOnly = permName in APPOP_ONLY_PERMISSIONS

                permissions.add(
                    PermissionInfo(
                        name = permName,
                        isGranted = isGranted,
                        isRuntime = isDangerous || isSpecial || isAppOpOnly,
                        isDangerous = isDangerous || isSpecial || isAppOpOnly
                    )
                )
            }

            permissions.sortedWith(compareByDescending<PermissionInfo> { it.isDangerous }.thenBy { it.name })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting permissions for $packageName: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAppsWithPermission(permission: String): List<Pair<AppInfo, Boolean>> =
        withContext(Dispatchers.IO) {
            try {
                val allApps = getInstalledApps()
                val result = mutableListOf<Pair<AppInfo, Boolean>>()
                for (app in allApps) {
                    try {
                        val isGranted = when (permission) {
                            "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                                getManageExternalStorageStatus(app.packageName) ?: false
                            }
                            else -> {
                                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getPackageInfo(
                                        app.packageName,
                                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                                }
                                val permissions = pkgInfo.requestedPermissions ?: continue
                                val permFlags = pkgInfo.requestedPermissionsFlags ?: continue
                                val permIndex = permissions.indexOf(permission)
                                if (permIndex >= 0) {
                                    getSpecialPermissionStatus(app.packageName, permission)
                                        ?: ((permFlags[permIndex] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                                } else {
                                    continue
                                }
                            }
                        }
                        result.add(Pair(app, isGranted))
                    } catch (e: Exception) {
                    }
                }
                result.sortedBy { it.first.appName.lowercase() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting apps with permission $permission: ${e.message}")
                emptyList()
            }
        }

    suspend fun getAppsWithPermissionCategory(
        permissions: List<String>,
        showSystemApps: Boolean = false
    ): List<Pair<AppInfo, Map<String, Boolean>>> = withContext(Dispatchers.IO) {
        try {
            val allApps = getInstalledApps(showSystemApps = showSystemApps)
            val result = mutableListOf<Pair<AppInfo, Map<String, Boolean>>>()
            for (app in allApps) {
                try {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            app.packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    }
                    val requestedPerms = pkgInfo.requestedPermissions ?: continue
                    val permFlags = pkgInfo.requestedPermissionsFlags ?: continue
                    val appPermMap = mutableMapOf<String, Boolean>()
                    for (targetPerm in permissions) {
                        val idx = requestedPerms.indexOf(targetPerm)
                        if (idx >= 0) {
                            val isGranted = when (targetPerm) {
                                "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                                    getManageExternalStorageStatus(app.packageName) ?: false
                                }
                                else -> {
                                    getSpecialPermissionStatus(app.packageName, targetPerm)
                                        ?: ((permFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                                }
                            }
                            appPermMap[targetPerm] = isGranted
                        }
                    }
                    if (appPermMap.isNotEmpty()) {
                        result.add(Pair(app, appPermMap))
                    }
                } catch (e: Exception) {
                }
            }
            result.sortedBy { it.first.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }
}