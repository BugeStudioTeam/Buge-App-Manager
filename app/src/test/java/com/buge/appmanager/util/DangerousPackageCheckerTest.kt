// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DangerousPackageCheckerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        DangerousPackageChecker.invalidateCache()
    }

    @Test
    fun listedPackagesAreFlagged() {
        val listed = readAssetPackages()

        assertThat(listed).isNotEmpty()
        listed.forEach { packageName ->
            assertThat(DangerousPackageChecker.isDangerous(context, packageName)).isTrue()
        }
    }

    @Test
    fun unknownPackageIsNotFlagged() {
        assertThat(
            DangerousPackageChecker.isDangerous(context, "com.example.definitely.not.listed")
        ).isFalse()
    }

    @Test
    fun cacheIsReusedAndCanBeCleared() {
        val first = DangerousPackageChecker.getDangerousPackages(context)
        val second = DangerousPackageChecker.getDangerousPackages(context)

        assertThat(second).isSameInstanceAs(first)

        DangerousPackageChecker.invalidateCache()
        val reloaded = DangerousPackageChecker.getDangerousPackages(context)

        assertThat(reloaded).isEqualTo(first)
        assertThat(reloaded).isNotSameInstanceAs(first)
    }

    @Test
    fun assetHasNoDuplicatesAfterTrimming() {
        val listed = readAssetPackages()

        assertThat(listed).containsNoDuplicates()
    }

    private fun readAssetPackages(): List<String> =
        context.assets.open("dangerous_packages.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
}
