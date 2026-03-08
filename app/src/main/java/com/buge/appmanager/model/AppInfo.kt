package com.buge.appmanager.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val apkPath: String,
    val permissions: List<PermissionInfo> = emptyList()
)

data class PermissionInfo(
    val name: String,
    val isGranted: Boolean,
    val isRuntime: Boolean,
    val isDangerous: Boolean
)

data class PermissionCategory(
    val id: String,
    val displayName: String,
    val permissions: List<String>,
    val iconResId: Int
)

enum class AppFilter {
    ALL, USER, SYSTEM
}

enum class AppSortOrder {
    NAME, SIZE, INSTALL_DATE
}
