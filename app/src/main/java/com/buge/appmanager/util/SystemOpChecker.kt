// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import com.buge.appmanager.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SystemOpChecker {

    fun checkAndShowDialog(
        context: Context,
        appName: String,
        isSystemApp: Boolean,
        onAllowed: () -> Unit
    ): Boolean {
        if (!isSystemApp) {
            onAllowed()
            return true
        }

        if (PreferencesManager.getAllowSystemOps(context)) {
            onAllowed()
            return true
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.system_op_blocked_title)
            .setMessage(R.string.system_op_blocked_message)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .show()

        LogManager.warning(context, "System app operation blocked", "App: $appName")
        return false
    }

    fun canOperate(context: Context, isSystemApp: Boolean): Boolean {
        if (!isSystemApp) return true
        return PreferencesManager.getAllowSystemOps(context)
    }

    fun isDangerousPackage(context: Context, packageName: String): Boolean {
        if (!PreferencesManager.getDangerousPackagesWarning(context)) return false
        return DangerousPackageChecker.isDangerous(context, packageName)
    }

    fun checkDangerousPackage(
        context: Context,
        packageName: String,
        appName: String,
        operation: String,
        onConfirmed: () -> Unit
    ): Boolean {
        if (!isDangerousPackage(context, packageName)) {
            return false
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dangerous_package_warning_title)
            .setMessage(context.getString(R.string.dangerous_package_warning_message, appName, packageName, operation))
            .setPositiveButton(R.string.dangerous_package_warning_continue) { _, _ ->
                onConfirmed()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

        LogManager.warning(
            context,
            "Dangerous package operation blocked",
            "Package: $packageName, App: $appName, Operation: $operation"
        )
        return true
    }

    fun checkDangerousPackages(
        context: Context,
        packages: List<String>,
        operation: String,
        onConfirmed: () -> Unit
    ): Boolean {
        if (!PreferencesManager.getDangerousPackagesWarning(context)) return false
        val dangerous = packages.filter { DangerousPackageChecker.isDangerous(context, it) }
        if (dangerous.isEmpty()) return false

        val preview = if (dangerous.size > 5) {
            dangerous.take(5).joinToString("\n") + "\n…"
        } else {
            dangerous.joinToString("\n")
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dangerous_package_warning_title)
            .setMessage(
                context.getString(
                    R.string.dangerous_package_warning_message_batch,
                    dangerous.size,
                    operation,
                    preview
                )
            )
            .setPositiveButton(R.string.dangerous_package_warning_continue) { _, _ ->
                onConfirmed()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

        LogManager.warning(
            context,
            "Batch dangerous package operation blocked",
            "Count: ${dangerous.size}, Operation: $operation, Packages: ${dangerous.joinToString(",")}"
        )
        return true
    }
}