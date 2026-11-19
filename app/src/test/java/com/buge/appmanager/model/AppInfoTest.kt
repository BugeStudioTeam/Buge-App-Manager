// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInfoTest {

    private fun sample(): AppInfo = AppInfo(
        packageName = "com.example.app",
        appName = "Example",
        versionName = "1.2.3",
        versionCode = 123L,
        icon = null,
        isSystemApp = false,
        isEnabled = true,
        installTime = 1_700_000_000_000L,
        updateTime = 1_700_000_100_000L,
        targetSdkVersion = 34,
        minSdkVersion = 26,
        apkPath = "/data/app/com.example.app/base.apk"
    )

    @Test
    fun optionalFieldsDefaultToEmpty() {
        val app = sample()

        assertThat(app.installerPackageName).isNull()
        assertThat(app.permissions).isEmpty()
    }

    @Test
    fun copyKeepsUnrelatedFields() {
        val app = sample()
        val updated = app.copy(
            isEnabled = false,
            permissions = listOf(
                PermissionInfo(
                    name = "android.permission.CAMERA",
                    isGranted = true,
                    isRuntime = true,
                    isDangerous = true
                )
            )
        )

        assertThat(updated.packageName).isEqualTo(app.packageName)
        assertThat(updated.versionCode).isEqualTo(app.versionCode)
        assertThat(updated.isEnabled).isFalse()
        assertThat(updated.permissions).hasSize(1)
    }

    @Test
    fun equalityUsesAllFields() {
        assertThat(sample()).isEqualTo(sample())
        assertThat(sample()).isNotEqualTo(sample().copy(versionCode = 124L))
    }

    @Test
    fun filtersExposeExpectedValues() {
        assertThat(AppFilter.entries).containsExactly(
            AppFilter.ALL,
            AppFilter.USER,
            AppFilter.SYSTEM,
            AppFilter.FAVORITE,
            AppFilter.DISABLED
        )
    }

    @Test
    fun sortOrdersExposeExpectedValues() {
        assertThat(AppSortOrder.entries).containsExactly(
            AppSortOrder.NAME,
            AppSortOrder.SIZE,
            AppSortOrder.INSTALL_DATE
        )
    }

    @Test
    fun storageInfoExposesTotalSize() {
        val storage = AppStorageInfo(appSize = 10L, dataSize = 5L, totalSize = 15L)

        assertThat(storage.totalSize).isEqualTo(15L)
    }

    @Test
    fun permissionCategoryKeepsItsPermissions() {
        val category = PermissionCategory(
            id = "camera",
            displayName = "Camera",
            permissions = listOf("android.permission.CAMERA"),
            iconResId = 1
        )

        assertThat(category.permissions).containsExactly("android.permission.CAMERA")
    }
}
