// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityInstallAppsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ApkItem(
    val uri: Uri,
    val fileName: String,
    val filePath: String,
    val packageName: String,
    val versionName: String,
    val appLabel: String
)

class InstallAppsActivity : BaseActivity() {

    private lateinit var binding: ActivityInstallAppsBinding
    private lateinit var adapter: ApkListAdapter
    private val apkList = mutableListOf<ApkItem>()
    private var isInstalling = false

    private val pickApkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            for (uri in uris) {
                val item = loadApkInfo(uri)
                if (item != null) {
                    apkList.add(item)
                } else {
                    SnackbarHelper.showSnackbar(binding.root, getString(R.string.install_invalid_apk))
                }
            }
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupOptions()
        setupRecyclerView()
        setupFab()
        setupInstallButton()
        updateEmptyState()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_install_apps)
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

    private fun setupOptions() {
        // Fuck: Installer name
        val installerName = PreferencesManager.getInstallInstallerName(this)
        binding.installerNameValue.text = if (installerName.isEmpty()) {
            getString(R.string.installer_name_hint)
        } else {
            installerName
        }
        binding.installerNameItem.setOnClickListener {
            showInstallerNameDialog()
        }

        // Fuck: Allow downgrade
        binding.allowDowngradeSwitch.isChecked = PreferencesManager.getInstallAllowDowngrade(this)
        binding.allowDowngradeSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setInstallAllowDowngrade(this, isChecked)
            LogManager.info(this, "Install allow downgrade changed to $isChecked")
        }

        // Fuck: Allow test packages
        binding.allowTestSwitch.isChecked = PreferencesManager.getInstallAllowTest(this)
        binding.allowTestSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setInstallAllowTest(this, isChecked)
            LogManager.info(this, "Install allow test changed to $isChecked")
        }

        // Fuck: Allow system apps
        binding.allowSystemSwitch.isChecked = PreferencesManager.getInstallAllowSystem(this)
        binding.allowSystemSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setInstallAllowSystem(this, isChecked)
            LogManager.info(this, "Install allow system changed to $isChecked")
        }

        // Fuck: All users
        binding.allUsersSwitch.isChecked = PreferencesManager.getInstallAllUsers(this)
        binding.allUsersSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setInstallAllUsers(this, isChecked)
            LogManager.info(this, "Install all users changed to $isChecked")
        }
    }

    private fun showInstallerNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_installer_name, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.installer_name_input)
        val current = PreferencesManager.getInstallInstallerName(this)
        input.setText(current)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.install_installer_name)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                PreferencesManager.setInstallInstallerName(this, name)
                binding.installerNameValue.text = if (name.isEmpty()) {
                    getString(R.string.installer_name_hint)
                } else {
                    name
                }
                LogManager.info(this, "Install installer name changed", name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = ApkListAdapter(
            onRemoveClick = { position ->
                if (position in apkList.indices) {
                    apkList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, apkList.size)
                    updateEmptyState()
                }
            }
        )
        binding.apkRecycler.layoutManager = LinearLayoutManager(this)
        binding.apkRecycler.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            pickApkLauncher.launch(arrayOf(
                "application/vnd.android.package-archive",
                "*/*"
            ))
        }
    }

    private fun setupInstallButton() {
        binding.btnInstall.setOnClickListener {
            if (apkList.isEmpty()) return@setOnClickListener
            if (isInstalling) return@setOnClickListener
            installAllApks()
        }
    }

    private fun updateEmptyState() {
        val isEmpty = apkList.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.apkRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnInstall.isEnabled = !isEmpty && !isInstalling
        binding.btnInstall.alpha = if (isEmpty) 0.5f else 1f
    }

    private suspend fun loadApkInfo(uri: Uri): ApkItem? = withContext(Dispatchers.IO) {
        try {
            // Fuck: Copy APK to cache first
            val fileName = getFileNameFromUri(uri) ?: "unknown.apk"
            val cacheDir = File(cacheDir, "install_cache").apply { mkdirs() }
            val tempFile = File(cacheDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext null
            }

            // Fuck: Parse APK info
            val pm = packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.PackageInfoFlags.of(0)
            } else {
                0
            }
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
            }

            if (packageInfo == null) {
                tempFile.delete()
                return@withContext null
            }

            packageInfo.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = tempFile.absolutePath
                appInfo.publicSourceDir = tempFile.absolutePath
            }

            val appLabel = try {
                if (packageInfo.applicationInfo != null) {
                    pm.getApplicationLabel(packageInfo.applicationInfo).toString()
                } else {
                    packageInfo.packageName
                }
            } catch (e: Exception) {
                packageInfo.packageName
            }

            ApkItem(
                uri = uri,
                fileName = fileName,
                filePath = tempFile.absolutePath,
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName ?: "Unknown",
                appLabel = appLabel
            )
        } catch (e: Exception) {
            LogManager.error(this@InstallAppsActivity, "Failed to load APK info", e.message)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            var name: String? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
            name
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun installAllApks() {
        if (!checkPrivilege()) return

        isInstalling = true
        updateEmptyState()

        val installerName = PreferencesManager.getInstallInstallerName(this)
        val allowDowngrade = PreferencesManager.getInstallAllowDowngrade(this)
        val allowTest = PreferencesManager.getInstallAllowTest(this)
        val allowSystem = PreferencesManager.getInstallAllowSystem(this)
        val allUsers = PreferencesManager.getInstallAllUsers(this)

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            for (apk in apkList.toList()) {
                val result = installApk(
                    apk = apk,
                    installerName = installerName,
                    allowDowngrade = allowDowngrade,
                    allowTest = allowTest,
                    allowSystem = allowSystem,
                    allUsers = allUsers
                )
                if (result) {
                    successCount++
                    LogManager.success(this@InstallAppsActivity, "APK installed", "${apk.appLabel} (${apk.packageName})")
                } else {
                    failCount++
                    LogManager.error(this@InstallAppsActivity, "APK install failed", "${apk.appLabel} (${apk.packageName})")
                }
            }

            isInstalling = false
            updateEmptyState()

            if (failCount == 0) {
                SnackbarHelper.showSnackbar(
                    binding.root,
                    getString(R.string.install_complete, successCount, failCount)
                )
                apkList.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
            } else {
                SnackbarHelper.showSnackbar(
                    binding.root,
                    getString(R.string.install_complete, successCount, failCount)
                )
            }
        }
    }

    private suspend fun installApk(
        apk: ApkItem,
        installerName: String,
        allowDowngrade: Boolean,
        allowTest: Boolean,
        allowSystem: Boolean,
        allUsers: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempPath = "/data/local/tmp/install_temp.apk"

            // Fuck: Copy to /data/local/tmp
            val copyCmd = "cat \"${apk.filePath}\" > $tempPath"
            val copyResult = ShizukuManager.executeCommand(copyCmd)
            if (!copyResult.success) {
                LogManager.error(this@InstallAppsActivity, "Copy to tmp failed", copyResult.error)
                return@withContext false
            }

            // Fuck: Build install command
            val flags = StringBuilder()
            flags.append("-r")  // Replace existing
            if (allowDowngrade) flags.append(" -d")
            if (allowTest) flags.append(" -t")
            if (allowSystem) flags.append(" -g")  // Grant all permissions for system apps

            val userFlag = if (allUsers) {
                "--user all"
            } else {
                "--user 0"
            }

            val safeInstallerName = if (installerName.isNotEmpty() &&
                installerName.matches(Regex("^[a-zA-Z0-9._\\- ]*$"))) {
                installerName
            } else {
                ""
            }

            val installCmd = if (safeInstallerName.isNotEmpty()) {
                "pm install $flags $userFlag -i \"$safeInstallerName\" $tempPath"
            } else {
                "pm install $flags $userFlag $tempPath"
            }

            LogManager.info(this@InstallAppsActivity, "Executing install", installCmd)

            val installResult = ShizukuManager.executeCommand(installCmd)

            // Fuck: Cleanup
            ShizukuManager.executeCommand("rm -f $tempPath")

            installResult.success
        } catch (e: Exception) {
            LogManager.error(this@InstallAppsActivity, "Install exception", e.message)
            false
        }
    }

    private fun checkPrivilege(): Boolean {
        if (!ShizukuManager.isAuthorized()) {
            SnackbarHelper.showSnackbar(
                binding.root,
                getString(R.string.error_no_privilege),
                getString(R.string.request_auth),
                { ShizukuManager.requestAuthorization() }
            )
            return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fuck: Cleanup cache
        try {
            val cacheDir = File(cacheDir, "install_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    inner class ApkListAdapter(
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ApkListAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_apk_install, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(apkList[position])
            holder.btnRemove.setOnClickListener {
                onRemoveClick(holder.adapterPosition)
            }
        }

        override fun getItemCount(): Int = apkList.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: ImageView = itemView.findViewById(R.id.apk_icon)
            private val appName: TextView = itemView.findViewById(R.id.apk_name)
            private val packageName: TextView = itemView.findViewById(R.id.apk_package)
            private val versionName: TextView = itemView.findViewById(R.id.apk_version)
            private val filePath: TextView = itemView.findViewById(R.id.apk_path)
            val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)

            fun bind(item: ApkItem) {
                appName.text = item.appLabel
                packageName.text = "${getString(R.string.install_apk_package)}: ${item.packageName}"
                versionName.text = "${getString(R.string.install_apk_version)}: ${item.versionName}"
                filePath.text = item.filePath

                // Fuck: Try to load icon from APK
                try {
                    val pm = packageManager
                    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageArchiveInfo(item.filePath, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(item.filePath, 0)
                    }
                    packageInfo?.applicationInfo?.let { appInfo ->
                        appInfo.sourceDir = item.filePath
                        appInfo.publicSourceDir = item.filePath
                        appIcon.setImageDrawable(appInfo.loadIcon(pm))
                    } ?: run {
                        appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                } catch (e: Exception) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
        }
    }
}