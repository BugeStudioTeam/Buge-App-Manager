// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.databinding.ActivityBackupSettingsBinding
import com.buge.appmanager.util.BackupManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class BackupSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupSettingsBinding
    private lateinit var adapter: BackupSettingsAdapter
    private var isBackupEnabled = false

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        PreferencesManager.setBackupLocation(this, uri.toString())
        LogManager.info(this, "Backup location changed", uri.toString())
        rebuildList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isBackupEnabled = PreferencesManager.getBackupEnabled(this)
        binding.backupSwitch.isChecked = isBackupEnabled

        setupToolbar()
        setupSwitch()
        setupRecyclerView()
        updateVisibility()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.backup_auto_settings)
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

    private fun setupSwitch() {
        binding.backupSwitch.setOnCheckedChangeListener { _, isChecked ->
            isBackupEnabled = isChecked
            PreferencesManager.setBackupEnabled(this, isChecked)
            updateVisibility()
            LogManager.info(this, "Backup enabled changed to $isChecked")
        }
    }

    private fun setupRecyclerView() {
        adapter = BackupSettingsAdapter(
            onBackupClick = { performManualBackup() },
            onIntervalClick = { showIntervalDialog() },
            onLocationClick = { pickFolderLauncher.launch(null) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        rebuildList()
    }

    private fun rebuildList() {
        adapter.submitList(buildItems())
    }

    private fun buildItems(): List<BackupSettingItem> {
        val interval = PreferencesManager.getBackupIntervalHours(this)
        val intervalText = if (interval <= 0) {
            getString(R.string.backup_auto_disabled)
        } else {
            getString(R.string.backup_auto_hours, interval)
        }

        val locationUri = PreferencesManager.getBackupLocation(this)
        val locationText = if (locationUri.isEmpty()) {
            getString(R.string.backup_location_default)
        } else {
            Uri.parse(locationUri).lastPathSegment ?: getString(R.string.backup_location_default)
        }

        return listOf(
            BackupSettingItem(
                title = getString(R.string.backup_section),
                subtitle = "",
                isHeader = true
            ),
            BackupSettingItem(
                title = getString(R.string.backup_app_settings),
                subtitle = "Create a manual backup now",
                isHeader = false,
                type = BackupItemType.BACKUP
            ),
            BackupSettingItem(
                title = getString(R.string.backup_auto_section),
                subtitle = "",
                isHeader = true
            ),
            BackupSettingItem(
                title = getString(R.string.backup_auto_title),
                subtitle = intervalText,
                isHeader = false,
                type = BackupItemType.INTERVAL
            ),
            BackupSettingItem(
                title = getString(R.string.backup_location),
                subtitle = locationText,
                isHeader = false,
                type = BackupItemType.LOCATION
            )
        )
    }

    private fun updateVisibility() {
        binding.recyclerView.visibility = if (isBackupEnabled) View.VISIBLE else View.GONE
    }

    private fun showIntervalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_interval, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.interval_input)
        val current = PreferencesManager.getBackupIntervalHours(this)
        input.setText(current.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_auto_title)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val hours = input.text?.toString()?.trim()?.toIntOrNull() ?: 0
                PreferencesManager.setBackupIntervalHours(this, hours.coerceAtLeast(0))
                LogManager.info(this, "Backup interval changed to $hours hours")
                rebuildList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performManualBackup() {
        val locationUri = PreferencesManager.getBackupLocation(this)
        if (locationUri.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, "Please select a backup location first")
            return
        }
        try {
            val success = BackupManager.performAutoBackup(this)
            if (success) {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_success))
            } else {
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_failed))
            }
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.backup_failed)}: ${e.message}")
        }
    }
}

data class BackupSettingItem(
    val title: String,
    val subtitle: String,
    val isHeader: Boolean,
    val type: BackupItemType = BackupItemType.NONE
)

enum class BackupItemType {
    NONE, BACKUP, INTERVAL, LOCATION
}
