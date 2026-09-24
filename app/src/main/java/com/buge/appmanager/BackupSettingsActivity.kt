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

class BackupSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupSettingsBinding
    private lateinit var adapter: BackupSettingsAdapter
    private var isBackupEnabled = false

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            LogManager.warning(this, "Failed to persist folder permission", e.message)
        }
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
            onLocationClick = { pickFolderLauncher.launch(null) },
            onAutoBackupToggle = { enabled ->
                PreferencesManager.setAutoBackupOnLaunch(this, enabled)
                LogManager.info(this, "Auto backup on launch changed to $enabled")
                rebuildList()
            },
            onContentClick = { showContentDialog() }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        rebuildList()
    }

    private fun rebuildList() {
        adapter.submitList(buildItems())
    }

    private fun buildItems(): List<BackupSettingItem> {
        val autoBackupOnLaunch = PreferencesManager.getAutoBackupOnLaunch(this)

        val locationUri = PreferencesManager.getBackupLocation(this)
        val locationText = if (locationUri.isEmpty()) {
            getString(R.string.backup_location_default)
        } else {
            Uri.parse(locationUri).lastPathSegment ?: getString(R.string.backup_location_default)
        }

        val contentMode = PreferencesManager.getBackupContentMode(this)
        val contentText = when (contentMode) {
            PreferencesManager.BACKUP_CONTENT_APP_LIST -> getString(R.string.backup_content_app_list)
            PreferencesManager.BACKUP_CONTENT_BOTH -> getString(R.string.backup_content_both)
            else -> getString(R.string.backup_content_settings)
        }

        return listOf(
            // Fuck: Manual Backup section
            BackupSettingItem(
                title = getString(R.string.backup_section),
                subtitle = "",
                isHeader = true
            ),
            BackupSettingItem(
                title = getString(R.string.backup_app_settings),
                subtitle = getString(R.string.backup_app_settings_summary),
                isHeader = false,
                type = BackupItemType.BACKUP
            ),

            // Fuck: Auto Backup section
            BackupSettingItem(
                title = getString(R.string.backup_auto_section),
                subtitle = "",
                isHeader = true
            ),
            BackupSettingItem(
                title = getString(R.string.backup_auto_on_launch),
                subtitle = getString(R.string.backup_auto_on_launch_summary),
                isHeader = false,
                type = BackupItemType.AUTO_BACKUP_SWITCH,
                isChecked = autoBackupOnLaunch
            ),
            BackupSettingItem(
                title = getString(R.string.backup_content),
                subtitle = contentText,
                isHeader = false,
                type = BackupItemType.CONTENT
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
        // Fuck: Deprecated, kept for backward compat but not used
    }

    private fun showContentDialog() {
        val options = arrayOf(
            getString(R.string.backup_content_settings),
            getString(R.string.backup_content_app_list),
            getString(R.string.backup_content_both)
        )
        val currentMode = PreferencesManager.getBackupContentMode(this)
        val currentIndex = when (currentMode) {
            PreferencesManager.BACKUP_CONTENT_APP_LIST -> 1
            PreferencesManager.BACKUP_CONTENT_BOTH -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_content)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val mode = when (which) {
                    0 -> PreferencesManager.BACKUP_CONTENT_SETTINGS
                    1 -> PreferencesManager.BACKUP_CONTENT_APP_LIST
                    2 -> PreferencesManager.BACKUP_CONTENT_BOTH
                    else -> PreferencesManager.BACKUP_CONTENT_SETTINGS
                }
                PreferencesManager.setBackupContentMode(this, mode)
                LogManager.info(this, "Backup content changed to $mode")
                dialog.dismiss()
                rebuildList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performManualBackup() {
        val locationUri = PreferencesManager.getBackupLocation(this)
        if (locationUri.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.backup_select_location_first))
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