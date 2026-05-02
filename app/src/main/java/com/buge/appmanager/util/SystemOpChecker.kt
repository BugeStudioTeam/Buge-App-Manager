package com.buge.appmanager.util

import android.content.Context
import android.content.Intent
import com.buge.appmanager.MainActivity
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
}