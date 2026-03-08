package com.buge.appmanager.data

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    companion object {
        private const val TAG = "AppRepository"

        // Permission categories
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

        val ALL_DANGEROUS_PERMISSIONS = (
            PERMISSION_MICROPHONE + PERMISSION_CAMERA + PERMISSION_LOCATION +
            PERMISSION_BACKGROUND_LOCATION + PERMISSION_CONTACTS + PERMISSION_STORAGE +
            PERMISSION_PHONE + PERMISSION_SMS + PERMISSION_CALENDAR + PERMISSION_SENSORS +
            PERMISSION_ACTIVITY + PERMISSION_NEARBY + PERMISSION_NOTIFICATIONS +
            PERMISSION_MEDIA_IMAGES + PERMISSION_MEDIA_VIDEO + PERMISSION_MEDIA_AUDIO +
            PERMISSION_OVERLAY + PERMISSION_INSTALL_UNKNOWN_APPS
        ).toSet()
    }

    /**
     * Get all installed apps with optional filtering and sorting
     */
    suspend fun getInstalledApps(
        filter: AppFilter = AppFilter.ALL,
        sortOrder: AppSortOrder = AppSortOrder.NAME,
        searchQuery: String = "",
        showSystemApps: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_META_DATA
            } else {
                PackageManager.GET_META_DATA
            }

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

            // Apply filter
            apps = when (filter) {
                AppFilter.ALL -> if (showSystemApps) apps else apps.filter { !it.isSystemApp }
                AppFilter.USER -> apps.filter { !it.isSystemApp }
                AppFilter.SYSTEM -> apps.filter { it.isSystemApp }
            }

            // Apply search
            if (searchQuery.isNotEmpty()) {
                apps = apps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

            // Apply sort
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

    /**
     * Get detailed permissions for a specific app
     */
    suspend fun getAppPermissions(packageName: String): List<PermissionInfo> = withContext(Dispatchers.IO) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            } else {
                null
            }

            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, flags!!)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }

            val requestedPermissions = pkgInfo.requestedPermissions ?: return@withContext emptyList()
            val permissionFlags = pkgInfo.requestedPermissionsFlags ?: return@withContext emptyList()

            requestedPermissions.mapIndexed { index, permName ->
                val isGranted = (permissionFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                val isDangerous = try {
                    val permInfo = @Suppress("DEPRECATION") pm.getPermissionInfo(permName, 0)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        permInfo.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }

                PermissionInfo(
                    name = permName,
                    isGranted = isGranted,
                    isRuntime = isDangerous,
                    isDangerous = isDangerous
                )
            }.sortedWith(compareByDescending<PermissionInfo> { it.isDangerous }.thenBy { it.name })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting permissions for $packageName: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get apps that have requested a specific permission
     */
    suspend fun getAppsWithPermission(permission: String): List<Pair<AppInfo, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val allApps = getInstalledApps()
            val result = mutableListOf<Pair<AppInfo, Boolean>>()

            for (app in allApps) {
                try {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    } else {
                        null
                    }

                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(app.packageName, flags!!)
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    }

                    val permissions = pkgInfo.requestedPermissions ?: continue
                    val permFlags = pkgInfo.requestedPermissionsFlags ?: continue

                    val permIndex = permissions.indexOf(permission)
                    if (permIndex >= 0) {
                        val isGranted = (permFlags[permIndex] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        result.add(Pair(app, isGranted))
                    }
                } catch (e: Exception) {
                    // Skip apps with errors
                }
            }

            result.sortedBy { it.first.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting apps with permission $permission: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get apps with any permission from a category
     */
    suspend fun getAppsWithPermissionCategory(permissions: List<String>): List<Pair<AppInfo, Map<String, Boolean>>> = withContext(Dispatchers.IO) {
        try {
            val allApps = getInstalledApps()
            val result = mutableListOf<Pair<AppInfo, Map<String, Boolean>>>()

            for (app in allApps) {
                try {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    } else {
                        null
                    }

                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(app.packageName, flags!!)
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
                            val isGranted = (permFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                            appPermMap[targetPerm] = isGranted
                        }
                    }

                    if (appPermMap.isNotEmpty()) {
                        result.add(Pair(app, appPermMap))
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }

            result.sortedBy { it.first.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }
}
