package com.buge.appmanager.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivityRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    companion object {
        private const val TAG = "ActivityRepository"
    }

    suspend fun getInstalledAppsWithActivities(showSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            LogManager.info(context, "ActivityRepository: Getting apps with activities", "ShowSystemApps: $showSystemApps")
            
            val allApps = mutableListOf<AppInfo>()
            
            val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            
            LogManager.debug(context, "ActivityRepository: Total packages", "${packages.size}")
            
            for (pkgInfo in packages) {
                try {
                    val packageName = pkgInfo.packageName
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (!showSystemApps && isSystem) {
                        continue
                    }
                    
                    // Check if app has any activities - lightweight check
                    val hasActivities = try {
                        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                        }
                        val activities = packageInfo?.activities
                        activities != null && activities.isNotEmpty()
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (!hasActivities) {
                        continue
                    }
                    
                    allApps.add(
                        AppInfo(
                            packageName = packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            versionName = pkgInfo.versionName ?: "",
                            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                pkgInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                pkgInfo.versionCode.toLong()
                            },
                            icon = try { pm.getApplicationIcon(packageName) } catch (e: Exception) { null },
                            isSystemApp = isSystem,
                            isEnabled = appInfo.enabled,
                            installTime = pkgInfo.firstInstallTime,
                            updateTime = pkgInfo.lastUpdateTime,
                            targetSdkVersion = appInfo.targetSdkVersion,
                            minSdkVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                appInfo.minSdkVersion
                            } else 0,
                            apkPath = appInfo.sourceDir ?: ""
                        )
                    )
                } catch (e: Exception) {
                    LogManager.warning(context, "ActivityRepository: Error getting app info", "${pkgInfo.packageName}: ${e.message}")
                }
            }
            
            val sorted = allApps.sortedBy { it.appName.lowercase() }
            LogManager.info(context, "ActivityRepository: Apps with activities loaded", "Count: ${sorted.size}")
            sorted
        } catch (e: Exception) {
            LogManager.error(context, "ActivityRepository: Error getting apps with activities", e.message)
            emptyList()
        }
    }

    suspend fun getAppActivities(packageName: String, showUndeclared: Boolean): List<ActivityDetail> = withContext(Dispatchers.IO) {
        try {
            LogManager.info(context, "ActivityRepository: Getting activities for app", "Package: $packageName, ShowUndeclared: $showUndeclared")
            
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            val activities = packageInfo?.activities ?: return@withContext emptyList()
            
            LogManager.debug(context, "ActivityRepository: Total activities in manifest", "${activities.size}")
            
            val result = mutableListOf<ActivityDetail>()
            
            // Pre-load intent filter counts in batch to avoid per-activity queries
            val intentFilterCounts = mutableMapOf<String, Int>()
            
            for (activityInfo in activities) {
                val className = activityInfo.name
                // Count intent filters by checking if the activity responds to any intent
                var intentFilterCount = 0
                try {
                    // Use a more efficient approach - just check if there are any intent filters
                    // without enumerating all of them individually
                    val intent = Intent().apply { 
                        setClassName(packageName, className)
                        action = Intent.ACTION_MAIN
                    }
                    val resolveInfoList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, 0)
                    }
                    // If activity can be resolved, it has at least one intent filter
                    intentFilterCount = if (resolveInfoList.isNotEmpty()) 1 else 0
                } catch (e: Exception) {
                    intentFilterCount = 0
                }
                intentFilterCounts[className] = intentFilterCount
            }
            
            var exportedCount = 0
            var undeclaredCount = 0
            
            for (activityInfo in activities) {
                val className = activityInfo.name
                val intentFilterCount = intentFilterCounts[className] ?: 0
                
                val activity = ActivityDetail(
                    name = try { 
                        val label = activityInfo.loadLabel(pm)
                        if (label.isNullOrEmpty()) activityInfo.name.substringAfterLast(".") else label.toString()
                    } catch (e: Exception) { 
                        activityInfo.name.substringAfterLast(".") 
                    },
                    className = className,
                    isExported = activityInfo.exported,
                    intentFilterCount = intentFilterCount,
                    permission = activityInfo.permission ?: "None",
                    launchMode = getLaunchModeString(activityInfo.launchMode),
                    parentActivityName = activityInfo.parentActivityName ?: "None"
                )
                
                if (showUndeclared) {
                    result.add(activity)
                    undeclaredCount++
                } else {
                    if (activity.isExported) {
                        result.add(activity)
                        exportedCount++
                    }
                }
            }
            
            val sorted = result.sortedBy { it.name.lowercase() }
            LogManager.info(context, "ActivityRepository: Activities loaded", "Package: $packageName, Exported: $exportedCount, Undeclared: $undeclaredCount, Total: ${sorted.size}")
            sorted
        } catch (e: Exception) {
            LogManager.error(context, "ActivityRepository: Error getting activities", "Package: $packageName, Error: ${e.message}")
            emptyList()
        }
    }

    private fun getLaunchModeString(launchMode: Int): String {
        return when (launchMode) {
            android.content.pm.ActivityInfo.LAUNCH_MULTIPLE -> "standard"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP -> "singleTop"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK -> "singleTask"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE -> "singleInstance"
            else -> "unknown"
        }
    }

    fun launchActivity(packageName: String, className: String): Boolean {
        return try {
            val componentName = ComponentName(packageName, className)
            val intent = Intent().apply {
                setComponent(componentName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            LogManager.info(context, "Activity launched", "Package: $packageName, Class: $className")
            true
        } catch (e: Exception) {
            LogManager.error(context, "Error launching activity", "Package: $packageName, Class: $className, Error: ${e.message}")
            false
        }
    }
}