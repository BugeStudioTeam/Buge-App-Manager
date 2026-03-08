package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.PermissionDetailAdapter
import com.buge.appmanager.databinding.ActivityAppDetailBinding
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.viewmodel.AppDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var binding: ActivityAppDetailBinding
    private val viewModel: AppDetailViewModel by viewModels()
    private lateinit var permAdapter: PermissionDetailAdapter
    private var packageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        setupToolbar()
        setupPermissionsRecycler()
        setupActions()
        observeViewModel()

        viewModel.loadApp(packageName)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupPermissionsRecycler() {
        permAdapter = PermissionDetailAdapter { perm ->
            handlePermissionToggle(perm)
        }
        binding.permissionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.permissionsRecycler.adapter = permAdapter
    }

    private fun setupActions() {
        binding.btnOpen.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Snackbar.make(binding.root, "Cannot launch this app", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnAppInfo.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        binding.btnForceStop.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.force_stop)
                .setMessage("Force stop ${binding.appName.text}?")
                .setPositiveButton(R.string.confirm) { _, _ -> viewModel.forceStop() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnUninstall.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.uninstall)
                .setMessage("Uninstall ${binding.appName.text}?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.uninstallApp()
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnClearData.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data)
                .setMessage("Clear all data for ${binding.appName.text}? This cannot be undone.")
                .setPositiveButton(R.string.confirm) { _, _ -> viewModel.clearData() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnDisableEnable.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (app.isEnabled) {
                viewModel.disableApp()
            } else {
                viewModel.enableApp()
            }
        }
    }

    private fun handlePermissionToggle(perm: PermissionInfo) {
        if (!checkShizuku()) return
        val action = if (perm.isGranted) getString(R.string.revoke_permission) else getString(R.string.grant_permission)
        MaterialAlertDialogBuilder(this)
            .setTitle(action)
            .setMessage(perm.name)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.togglePermission(perm.name, perm.isGranted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkShizuku(): Boolean {
        if (!ShizukuManager.isShizukuAvailable() || !ShizukuManager.hasShizukuPermission()) {
            Snackbar.make(binding.root, R.string.error_no_shizuku, Snackbar.LENGTH_LONG)
                .setAction(R.string.shizuku_request_auth) {
                    ShizukuManager.requestShizukuPermission()
                }
                .show()
            return false
        }
        return true
    }

    private fun observeViewModel() {
        viewModel.appInfo.observe(this) { app ->
            app ?: return@observe
            // Fix system app icon display - use default icon if null
            if (app.icon != null) {
                binding.appIcon.setImageDrawable(app.icon)
            } else {
                try {
                    val icon = packageManager.getApplicationIcon(app.packageName)
                    binding.appIcon.setImageDrawable(icon)
                } catch (e: Exception) {
                    binding.appIcon.setImageResource(android.R.drawable.ic_dialog_info)
                }
            }
            binding.appName.text = app.appName
            binding.packageName.text = app.packageName
            binding.appVersion.text = "v${app.versionName} (${app.versionCode})"

            // Update disable/enable button
            binding.btnDisableEnable.text = if (app.isEnabled) {
                getString(R.string.disable_app)
            } else {
                getString(R.string.enable_app)
            }

            // Populate info rows
            binding.infoContainer.removeAllViews()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            addInfoRow(getString(R.string.install_date), dateFormat.format(Date(app.installTime)))
            addInfoRow(getString(R.string.update_date), dateFormat.format(Date(app.updateTime)))
            addInfoRow(getString(R.string.target_sdk), "API ${app.targetSdkVersion}")
            addInfoRow(getString(R.string.min_sdk), "API ${app.minSdkVersion}")
            addInfoRow(getString(R.string.package_name), app.packageName)
        }

        viewModel.permissions.observe(this) { perms ->
            // Fix system app permissions - ensure list is not empty
            if (perms.isNotEmpty()) {
                permAdapter.submitList(perms)
                binding.permCount.text = "${perms.count { it.isGranted }}/${perms.size}"
            } else {
                // Try to reload permissions for system apps
                viewModel.loadApp(packageName)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.operationResult.observe(this) { result ->
            result ?: return@observe
            val msg = if (result.success) {
                getString(R.string.operation_success)
            } else {
                val errorMsg = result.error.ifEmpty { "Operation failed" }
                getString(R.string.operation_failed, errorMsg)
            }
            val duration = if (result.success) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
            Snackbar.make(binding.root, msg, duration).show()
            viewModel.clearOperationResult()
        }
    }

    private fun addInfoRow(label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_info_row, binding.infoContainer, false)
        row.findViewById<TextView>(R.id.label).text = label
        row.findViewById<TextView>(R.id.value).text = value
        binding.infoContainer.addView(row)
    }
}
