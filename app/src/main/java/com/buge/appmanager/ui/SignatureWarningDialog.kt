// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import com.buge.appmanager.R
import com.buge.appmanager.util.SignatureValidator
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SignatureWarningDialog {

    private var dialog: AlertDialog? = null

    fun show(context: Context, onExit: () -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_signature_warning, null)

        // Show hash values
        val tvExpectedHash = view.findViewById<TextView>(R.id.tv_expected_hash)
        val tvCurrentHash = view.findViewById<TextView>(R.id.tv_current_hash)

        tvExpectedHash?.text = "Expected: ${SignatureValidator.EXPECTED_SHA256}"
        tvCurrentHash?.text = "Current: ${SignatureValidator.getSignatureFingerprint(context) ?: "Unknown"}"

        val builder = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setCancelable(false)

        dialog = builder.show()

        // Fuck: Get dialog reference for dismiss
        val currentDialog = dialog

        // Button listeners
        val btnContinue = view.findViewById<AppCompatButton>(R.id.btn_continue)
        val btnExit = view.findViewById<AppCompatButton>(R.id.btn_exit)

        // Fuck: Continue anyway - dismiss dialog
        btnContinue?.setOnClickListener {
            currentDialog?.dismiss()
            dialog = null
        }

        // Fuck: Exit - dismiss and call onExit
        btnExit?.setOnClickListener {
            currentDialog?.dismiss()
            dialog = null
            onExit.invoke()
        }

        // Platform links - ImageView click listeners
        view.findViewById<ImageView>(R.id.github_item)?.setOnClickListener {
            openUrl(context, context.getString(R.string.signature_warning_github_url))
        }
        view.findViewById<ImageView>(R.id.fdroid_item)?.setOnClickListener {
            openUrl(context, context.getString(R.string.signature_warning_fdroid_url))
        }

        dialog?.setOnDismissListener {
            dialog = null
        }
    }

    private fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun dismiss() {
        try {
            dialog?.dismiss()
        } catch (e: Exception) {
            // ignore
        }
        dialog = null
    }
}