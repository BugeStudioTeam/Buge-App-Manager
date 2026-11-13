// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.buge.appmanager.R
import com.buge.appmanager.shizuku.ShizukuManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/BugeStudioTeam/Buge-App-Manager/releases"
    private const val FDROID_API_URL = "https://f-droid.org/api/v1/packages/com.buge.appmanager"
    private const val FDROID_PAGE_URL = "https://f-droid.org/packages/com.buge.appmanager/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val publishedAt: String,
        val htmlUrl: String,
        val apkDownloadUrl: String?
    )

    suspend fun checkForUpdates(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        val source = PreferencesManager.getAppSource(context)
        if (source == "F-Droid") {
            checkFdroidUpdates(context)
        } else {
            checkGithubUpdates(context)
        }
    }

    private suspend fun checkFdroidUpdates(context: Context): ReleaseInfo? {
        return try {
            val currentVersionCode = getCurrentVersionCode(context)
            Log.d(TAG, "F-Droid check, current versionCode: $currentVersionCode")

            val request = Request.Builder()
                .url(FDROID_API_URL)
                .header("User-Agent", "BugeAppManager")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "F-Droid API request failed: ${response.code}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            val suggestedVersionCode = json.optLong("suggestedVersionCode", 0L)
            Log.d(TAG, "F-Droid suggestedVersionCode: $suggestedVersionCode")

            if (suggestedVersionCode <= currentVersionCode) {
                Log.d(TAG, "F-Droid: no newer version (suggested=$suggestedVersionCode, current=$currentVersionCode)")
                return null
            }

            var suggestedVersionName = "unknown"
            val packagesArray = json.optJSONArray("packages")
            if (packagesArray != null) {
                for (i in 0 until packagesArray.length()) {
                    val pkg = packagesArray.getJSONObject(i)
                    if (pkg.optLong("versionCode", 0L) == suggestedVersionCode) {
                        suggestedVersionName = pkg.optString("versionName", "unknown")
                        break
                    }
                }
            }

            ReleaseInfo(
                tagName = suggestedVersionName,
                name = "Buge App Manager $suggestedVersionName",
                body = "A new version is available on F-Droid.",
                publishedAt = "",
                htmlUrl = FDROID_PAGE_URL,
                apkDownloadUrl = FDROID_PAGE_URL
            )
        } catch (e: Exception) {
            Log.e(TAG, "F-Droid update check error: ${e.message}", e)
            null
        }
    }

    private suspend fun checkGithubUpdates(context: Context): ReleaseInfo? {
        return try {
            val currentVersion = getCurrentVersion(context)
            Log.d(TAG, "GitHub check, current version: $currentVersion")

            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "BugeAppManager")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub API request failed: ${response.code}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            val releasesArray = JSONArray(responseBody)
            Log.d(TAG, "Total releases: ${releasesArray.length()}")

            for (i in 0 until releasesArray.length()) {
                val release = releasesArray.getJSONObject(i)
                val isPrerelease = release.optBoolean("prerelease", false)
                val tagName = release.getString("tag_name")

                val versionNumber = extractVersionNumber(tagName)
                Log.d(TAG, "Checking release $i: tag=$tagName, version=$versionNumber, prerelease=$isPrerelease")

                if (!isPrerelease && versionNumber != null) {
                    if (isNewerVersion(currentVersion, versionNumber)) {
                        Log.d(TAG, "Newer version found: $tagName")

                        var apkUrl: String? = null
                        val assets = release.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            for (j in 0 until assets.length()) {
                                val asset = assets.getJSONObject(j)
                                val assetName = asset.getString("name")
                                if (assetName.endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url")
                                    Log.d(TAG, "Found APK asset: $assetName")
                                    break
                                }
                            }
                        }

                        return@withContext ReleaseInfo(
                            tagName = tagName,
                            name = release.getString("name"),
                            body = release.getString("body"),
                            publishedAt = release.getString("published_at"),
                            htmlUrl = release.getString("html_url"),
                            apkDownloadUrl = apkUrl ?: release.getString("html_url")
                        )
                    } else {
                        Log.d(TAG, "Version $versionNumber is not newer than $currentVersion")
                        return@withContext null
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "GitHub update check error: ${e.message}", e)
            null
        }
    }

    private fun extractVersionNumber(tagName: String): String? {
        val patterns = listOf(
            Regex("""AppManager(\d+\.\d+\.\d+)"""),
            Regex("""[vV]?(\d+\.\d+\.\d+)""")
        )
        for (pattern in patterns) {
            val matchResult = pattern.find(tagName)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        return null
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = packageInfo.versionName ?: "0"
            Log.d(TAG, "Package versionName: $version")
            version
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get package info", e)
            "0"
        }
    }

    private fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version code", e)
            0L
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currentParts.size, latestParts.size)

            for (i in 0 until maxLength) {
                val currentPart = if (i < currentParts.size) currentParts[i] else 0
                val latestPart = if (i < latestParts.size) latestParts[i] else 0

                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison error: current=$current, latest=$latest", e)
            false
        }
    }

    fun showUpdateDialog(context: Context, releaseInfo: ReleaseInfo, onDownload: () -> Unit) {
        val updateMethod = PreferencesManager.getUpdateMethod(context)
        val installerName = PreferencesManager.getInstallerName(context)

        Handler(Looper.getMainLooper()).post {
            val releaseNotes = if (releaseInfo.body.length > 500) {
                releaseInfo.body.take(500) + "..."
            } else {
                releaseInfo.body
            }

            val source = PreferencesManager.getAppSource(context)
            val methodText = if (source == "F-Droid") {
                "Open F-Droid page"
            } else if (updateMethod == "silent") {
                "Silent install (Shizuku)"
            } else {
                "Open in browser"
            }

            val message = buildString {
                appendLine("Version: ${releaseInfo.tagName}")
                if (releaseInfo.publishedAt.isNotEmpty()) {
                    appendLine("Published: ${releaseInfo.publishedAt.substringBefore("T")}")
                }
                appendLine("Method: $methodText")
                if (installerName.isNotEmpty() && source != "F-Droid") {
                    appendLine("Installer: $installerName")
                }
                appendLine()
                appendLine(releaseNotes)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_available)
                .setMessage(message)
                .setPositiveButton(R.string.download) { _, _ ->
                    performUpdateWithConfig(context, releaseInfo)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun performUpdateWithConfig(context: Context, releaseInfo: ReleaseInfo) {
        val apkUrl = releaseInfo.apkDownloadUrl
        if (apkUrl.isNullOrEmpty()) {
            showErrorDialog(context, "No APK download URL available")
            return
        }

        val source = PreferencesManager.getAppSource(context)
        if (source == "F-Droid") {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_PAGE_URL))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogManager.info(context, "Update opened on F-Droid", "URL: $FDROID_PAGE_URL")
            } catch (e: Exception) {
                LogManager.error(context, "Failed to open F-Droid page", e.message)
                showErrorDialog(context, "Cannot open browser: ${e.message}")
            }
            return
        }

        val updateMethod = PreferencesManager.getUpdateMethod(context)
        val installerName = PreferencesManager.getInstallerName(context)

        when (updateMethod) {
            "silent" -> {
                if (!ShizukuManager.isAuthorized()) {
                    showErrorDialog(context, "No privilege backend is available or authorized. Please check your authorization method in Settings.")
                    return
                }
                UpdateHelper.startSilentUpdate(context, apkUrl, installerName)
            }
            else -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LogManager.info(context, "Update opened in browser", "URL: $apkUrl")
                } catch (e: Exception) {
                    LogManager.error(context, "Failed to open browser", e.message)
                    showErrorDialog(context, "Cannot open browser: ${e.message}")
                }
            }
        }
    }

    fun showNoUpdateDialog(context: Context) {
        Handler(Looper.getMainLooper()).post {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.no_update_title)
                .setMessage(R.string.no_update_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    fun showErrorDialog(context: Context, error: String? = null) {
        Handler(Looper.getMainLooper()).post {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_check_failed)
                .setMessage(error ?: context.getString(R.string.update_check_failed_message))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
