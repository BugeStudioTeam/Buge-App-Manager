package com.buge.appmanager.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivityRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    companion object {
        private const val TAG = "ActivityRepository"
    }

    suspend fun getInstalledAppsWithActivities(showSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            
            val packageNames = resolveInfos.map { it.activityInfo.packageName }.toSet()
            
            val allApps = mutableListOf<AppInfo>()
            
            for (packageName in packageNames) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (!showSystemApps && isSystem) {
                        continue
                    }
                    
                    allApps.add(
                        AppInfo(
                            packageName = packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            versionName = "",
                            versionCode = 0,
                            icon = try { pm.getApplicationIcon(packageName) } catch (e: Exception) { null },
                            isSystemApp = isSystem,
                            isEnabled = appInfo.enabled,
                            installTime = 0,
                            updateTime = 0,
                            targetSdkVersion = appInfo.targetSdkVersion,
                            minSdkVersion = 0,
                            apkPath = appInfo.sourceDir ?: ""
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting app info for $packageName: ${e.message}")
                }
            }
            
            allApps.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting apps with activities: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAppActivities(packageName: String): List<ActivityDetail> = withContext(Dispatchers.IO) {
        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities ?: return@withContext emptyList()
            
            val result = mutableListOf<ActivityDetail>()
            
            for (activityInfo in activities) {
                result.add(
                    ActivityDetail(
                        name = try { activityInfo.loadLabel(pm).toString() } catch (e: Exception) { activityInfo.name.substringAfterLast(".") },
                        className = activityInfo.name,
                        isExported = activityInfo.exported,
                        intentFilterCount = 0,
                        permission = activityInfo.permission ?: "None",
                        launchMode = getLaunchModeString(activityInfo.launchMode),
                        parentActivityName = activityInfo.parentActivityName ?: "None"
                    )
                )
            }
            
            result.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting activities for $packageName: ${e.message}")
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
}