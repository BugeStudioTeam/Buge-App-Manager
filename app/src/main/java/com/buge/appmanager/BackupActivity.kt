// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.buge.appmanager.databinding.ActivityBackupBinding
import com.buge.appmanager.util.BackupManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupBinding

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        performBackupSettings(uri)
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        performImportSettings(uri)
    }

    private val createAppListLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        performBackupAppList(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_backup)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupClickListeners() {
        binding.backupAppSettingsItem.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            createBackupLauncher.launch("buge_settings_$timestamp.json")
        }

        binding.backupAppListItem.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            createAppListLauncher.launch("buge_app_list_$timestamp.txt")
        }

        binding.importAppSettingsItem.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }

        binding.backupSettingsItem.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }
    }

    private fun performBackupSettings(uri: Uri) {
        try {
            val success = BackupManager.backupSettings(this, uri)
            if (success) {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_success))
                LogManager.success(this, "Settings backed up", "URI: $uri")
            } else {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_failed))
            }
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.backup_failed)}: ${e.message}")
            LogManager.error(this, "Backup failed", e.message)
        }
    }

    private fun performImportSettings(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_app_settings)
            .setMessage("Importing will overwrite current settings. Continue?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                try {
                    val success = BackupManager.importSettings(this, uri)
                    if (success) {
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.import_success))
                        LogManager.success(this, "Settings imported", "URI: $uri")
                    } else {
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.import_failed))
                    }
                } catch (e: Exception) {
                    SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.import_failed)}: ${e.message}")
                    LogManager.error(this, "Import failed", e.message)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performBackupAppList(uri: Uri) {
        try {
            val success = BackupManager.backupAppList(this, uri)
            if (success) {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_success))
                LogManager.success(this, "App list backed up", "URI: $uri")
            } else {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_failed))
            }
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.backup_failed)}: ${e.message}")
            LogManager.error(this, "App list backup failed", e.message)
        }
    }
}
