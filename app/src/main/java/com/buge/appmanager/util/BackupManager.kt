// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import com.buge.appmanager.model.CustomLabel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_VERSION = 1

    data class SettingsBackup(
        val version: Int = BACKUP_VERSION,
        val timestamp: Long = System.currentTimeMillis(),
        val appVersion: String = "",
        val settings: Map<String, Any?>,
        val favorites: List<String>,
        val customLabels: List<CustomLabel>
    )

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    /**
     * Backup app settings to the given URI
     */
    fun backupSettings(context: Context, uri: Uri): Boolean {
        return try {
            val settings = collectSettings(context)
            val favorites = PreferencesManager.getFavoriteApps(context).toList()
            val customLabels = CustomLabelManager.getLabels(context)

            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }

            val backup = SettingsBackup(
                settings = settings,
                favorites = favorites,
                customLabels = customLabels,
                appVersion = appVersion
            )

            val json = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }
            LogManager.success(context, "Settings backup completed", "URI: $uri")
            true
        } catch (e: Exception) {
            LogManager.error(context, "Settings backup failed", e.message)
            false
        }
    }

    /**
     * Import app settings from the given URI
     */
    fun importSettings(context: Context, uri: Uri): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return false

            val backup = gson.fromJson(json, SettingsBackup::class.java) ?: return false

            applySettings(context, backup.settings)
            PreferencesManager.setFavoriteApps(context, backup.favorites.toSet())
            CustomLabelManager.saveLabels(context, backup.customLabels)

            LogManager.success(context, "Settings import completed", "URI: $uri")
            true
        } catch (e: Exception) {
            LogManager.error(context, "Settings import failed", e.message)
            false
        }
    }

    /**
     * Backup list of all installed apps
     */
    fun backupAppList(context: Context, uri: Uri): Boolean {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            val sb = StringBuilder()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            sb.appendLine("Buge App Manager - Installed App List")
            sb.appendLine("Generated: $timestamp")
            sb.appendLine("Total: ${packages.size}")
            sb.appendLine("=".repeat(60))
            sb.appendLine()

            val sorted = packages.sortedBy { it.packageName }
            for (pkg in sorted) {
                val appName = try {
                    pm.getApplicationLabel(pkg.applicationInfo).toString()
                } catch (e: Exception) {
                    pkg.packageName
                }
                sb.appendLine("$appName")
                sb.appendLine("  Package: ${pkg.packageName}")
                sb.appendLine("  Version: ${pkg.versionName ?: "Unknown"}")
                sb.appendLine()
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            LogManager.success(context, "App list backup completed", "Count: ${packages.size}")
            true
        } catch (e: Exception) {
            LogManager.error(context, "App list backup failed", e.message)
            false
        }
    }

    /**
     * Perform automatic backup using the configured location
     */
    fun performAutoBackup(context: Context): Boolean {
        return try {
            val locationUri = PreferencesManager.getBackupLocation(context)
            if (locationUri.isEmpty()) return false

            val locationDir = Uri.parse(locationUri)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "buge_auto_backup_$timestamp.json"

            // Build document URI inside the tree
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                locationDir,
                android.provider.DocumentsContract.getTreeDocumentId(locationDir) ?: return false
            )
            val newDocUri = android.provider.DocumentsContract.createDocument(
                context.contentResolver,
                docUri,
                "application/json",
                fileName
            ) ?: return false

            backupSettings(context, newDocUri)
        } catch (e: Exception) {
            LogManager.error(context, "Auto backup failed", e.message)
            false
        }
    }

    /**
     * Collect all app settings into a map
     */
    private fun collectSettings(context: Context): Map<String, Any?> {
        return mapOf(
            "theme_mode" to PreferencesManager.getThemeMode(context),
            "language" to LocaleManager.getLanguage(context),
            "default_page" to PreferencesManager.getDefaultPage(context),
            "show_system_apps" to PreferencesManager.getShowSystemApps(context),
            "show_disabled_apps" to PreferencesManager.getShowDisabledApps(context),
            "show_undeclared_activities" to PreferencesManager.getShowUndeclaredActivities(context),
            "allow_system_ops" to PreferencesManager.getAllowSystemOps(context),
            "auto_update" to PreferencesManager.getAutoUpdate(context),
            "logging_enabled" to PreferencesManager.getLoggingEnabled(context),
            "hide_nav_labels" to PreferencesManager.getHideNavLabels(context),
            "installer_name" to PreferencesManager.getInstallerName(context),
            "update_method" to PreferencesManager.getUpdateMethod(context),
            "update_source" to PreferencesManager.getUpdateSource(context),
            "shizuku_provider" to PreferencesManager.getShizukuProvider(context),
            "auth_mode" to PreferencesManager.getAuthMode(context),
            "root_su_path" to PreferencesManager.getRootSuPath(context),
            "dynamic_color" to PreferencesManager.getDynamicColor(context),
            "backup_enabled" to PreferencesManager.getBackupEnabled(context),
            "backup_interval_hours" to PreferencesManager.getBackupIntervalHours(context),
            "backup_location" to PreferencesManager.getBackupLocation(context)
        )
    }

    /**
     * Apply settings from a map
     */
    private fun applySettings(context: Context, settings: Map<String, Any?>) {
        fun getInt(key: String, default: Int): Int {
            return (settings[key] as? Number)?.toInt() ?: default
        }

        fun getBool(key: String, default: Boolean): Boolean {
            return settings[key] as? Boolean ?: default
        }

        fun getString(key: String, default: String): String {
            return settings[key] as? String ?: default
        }

        // Fuck: Apply theme mode
        PreferencesManager.setThemeMode(context, getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        LocaleManager.setLanguage(context, getString("language", ""))
        PreferencesManager.setDefaultPage(context, getString("default_page", "apps"))
        PreferencesManager.setShowSystemApps(context, getBool("show_system_apps", false))
        PreferencesManager.setShowDisabledApps(context, getBool("show_disabled_apps", true))
        PreferencesManager.setShowUndeclaredActivities(context, getBool("show_undeclared_activities", true))
        PreferencesManager.setAllowSystemOps(context, getBool("allow_system_ops", true))
        PreferencesManager.setAutoUpdate(context, getBool("auto_update", true))
        PreferencesManager.setLoggingEnabled(context, getBool("logging_enabled", false))
        PreferencesManager.setHideNavLabels(context, getBool("hide_nav_labels", false))
        PreferencesManager.setInstallerName(context, getString("installer_name", ""))
        PreferencesManager.setUpdateMethod(context, getString("update_method", "browser"))
        PreferencesManager.setUpdateSource(context, getString("update_source", "GitHub"))
        PreferencesManager.setShizukuProvider(context, getString("shizuku_provider", "moe.shizuku.privileged.api"))
        PreferencesManager.setAuthMode(context, getString("auth_mode", PreferencesManager.AUTH_MODE_SHIZUKU))
        PreferencesManager.setRootSuPath(context, getString("root_su_path", PreferencesManager.DEFAULT_SU_PATH))
        PreferencesManager.setDynamicColor(context, getBool("dynamic_color", false))
        PreferencesManager.setBackupEnabled(context, getBool("backup_enabled", false))
        PreferencesManager.setBackupIntervalHours(context, getInt("backup_interval_hours", 0))
        PreferencesManager.setBackupLocation(context, getString("backup_location", ""))
    }
}
