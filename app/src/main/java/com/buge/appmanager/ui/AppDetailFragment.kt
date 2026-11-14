// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.R
import com.buge.appmanager.adapter.PermissionDetailAdapter
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.databinding.FragmentAppDetailBinding
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.SystemOpChecker
import com.buge.appmanager.viewmodel.AppDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailFragment : Fragment() {

    companion object {
        private const val ARG_PACKAGE_NAME = "arg_package_name"
        fun newInstance(packageName: String): AppDetailFragment {
            val fragment = AppDetailFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_PACKAGE_NAME, packageName)
            }
            return fragment
        }
    }

    private var _binding: FragmentAppDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppDetailViewModel by viewModels()
    private lateinit var permAdapter: PermissionDetailAdapter
    private var packageName: String = ""
    private var favoriteMenuItem: MenuItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: run {
            parentFragmentManager.popBackStack()
            return
        }

        setupToolbar()
        setupPermissionsRecycler()
        setupActions()
        observeViewModel()

        viewModel.loadApp(packageName)
        LogManager.info(requireContext(), "AppDetailFragment opened", "Package: $packageName")
    }

    override fun onResume() {
        super.onResume()
        if (packageName.isNotEmpty()) {
            viewModel.loadApp(packageName)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.detailToolbar.inflateMenu(R.menu.menu_app_detail)
        favoriteMenuItem = binding.detailToolbar.menu.findItem(R.id.action_favorite)
        updateFavoriteIcon()
        binding.detailToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.detailToolbar.setOnMenuItemClickListener { item ->
            handleMenuItem(item)
        }
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_favorite -> {
                toggleFavorite()
                return true
            }
            R.id.action_grant_all_permissions -> {
                grantAllPermissions()
                return true
            }
            R.id.action_revoke_all_permissions -> {
                revokeAllPermissions()
                return true
            }
            R.id.action_google_play -> {
                openInGooglePlay()
                return true
            }
            R.id.action_f_droid -> {
                openInFDroid()
                return true
            }
        }
        return false
    }

    private fun setupPermissionsRecycler() {
        permAdapter = PermissionDetailAdapter(
            onToggle = { perm ->
                handlePermissionToggle(perm)
            }
        )
        binding.permissionsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.permissionsRecycler.adapter = permAdapter
    }

    private fun setupActions() {
        binding.btnOpen.setOnClickListener {
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                LogManager.info(requireContext(), "App opened", "Package: $packageName")
            } else {
                SnackbarHelper.showSnackbar(binding.root, "Cannot launch this app")
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
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.force_stop)
                .setMessage(getString(R.string.force_stop_confirm, app.appName))
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.forceStop()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnUninstall.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.uninstall)
                .setMessage("Uninstall ${app.appName}?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.uninstallApp()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnClearData.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_data)
                .setMessage("Clear all data for ${app.appName}? This cannot be undone.")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.clearData()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnDisableEnable.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            if (app.isEnabled) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.disable_app)
                    .setMessage(getString(R.string.disable_app_confirm, app.appName))
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        viewModel.disableApp()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.enable_app)
                    .setMessage(getString(R.string.enable_app_confirm, app.appName))
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        viewModel.enableApp()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun handlePermissionToggle(perm: PermissionInfo) {
        if (!checkShizuku()) return

        if (ShizukuManager.isDhizukuMode() && perm.name in AppRepository.DHIZUKU_UNSUPPORTED_PERMISSIONS) {
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.dhizuku_unsupported_permission))
            return
        }

        val app = viewModel.appInfo.value
        if (app != null && !SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
            showSystemOpBlockedDialog()
            return
        }

        val action = if (perm.isGranted) getString(R.string.revoke_permission) else getString(R.string.grant_permission)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(action)
            .setMessage(perm.name)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.togglePermission(perm.name, perm.isGranted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkShizuku(): Boolean {
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

    private fun showSystemOpBlockedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.system_op_blocked_title)
            .setMessage(R.string.system_op_blocked_message)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleFavorite() {
        if (PreferencesManager.isFavoriteApp(requireContext(), packageName)) {
            PreferencesManager.removeFavoriteApp(requireContext(), packageName)
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.removed_from_favorites))
        } else {
            PreferencesManager.addFavoriteApp(requireContext(), packageName)
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.added_to_favorites))
        }
        updateFavoriteIcon()
    }

    private fun updateFavoriteIcon() {
        val isFavorite = PreferencesManager.isFavoriteApp(requireContext(), packageName)
        if (isFavorite) {
            favoriteMenuItem?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_filled)
        } else {
            favoriteMenuItem?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite)
        }
    }

    private fun openInGooglePlay() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (e2: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Cannot open Google Play")
            }
        }
    }

    private fun openInFDroid() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$packageName/")))
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Cannot open F-Droid")
        }
    }

    private fun grantAllPermissions() {
        val permissions = viewModel.permissions.value ?: emptyList()
        val targetPermissions = if (ShizukuManager.isDhizukuMode()) {
            permissions.filter { it.isDangerous && !it.isGranted && it.name !in AppRepository.DHIZUKU_UNSUPPORTED_PERMISSIONS }
        } else {
            permissions.filter { it.isDangerous && !it.isGranted }
        }

        if (targetPermissions.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, "All dangerous permissions are already granted")
            return
        }

        val app = viewModel.appInfo.value
        if (app != null && !SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
            showSystemOpBlockedDialog()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Grant All Permissions")
            .setMessage("Grant ${targetPermissions.size} dangerous permission(s) to ${app?.appName ?: packageName}?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var successCount = 0
                    var failCount = 0
                    for (perm in targetPermissions) {
                        val result = ShizukuManager.grantPermission(packageName, perm.name)
                        if (result.success) successCount++ else failCount++
                    }
                    val msg = if (failCount == 0) {
                        "All ${successCount} permissions granted"
                    } else {
                        "Granted: $successCount, Failed: $failCount"
                    }
                    SnackbarHelper.showSnackbar(binding.root, msg)
                    viewModel.loadApp(packageName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun revokeAllPermissions() {
        val permissions = viewModel.permissions.value ?: emptyList()
        val targetPermissions = if (ShizukuManager.isDhizukuMode()) {
            permissions.filter { it.isDangerous && it.isGranted && it.name !in AppRepository.DHIZUKU_UNSUPPORTED_PERMISSIONS }
        } else {
            permissions.filter { it.isDangerous && it.isGranted }
        }

        if (targetPermissions.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, "No granted dangerous permissions to revoke")
            return
        }

        val app = viewModel.appInfo.value
        if (app != null && !SystemOpChecker.canOperate(requireContext(), app.isSystemApp)) {
            showSystemOpBlockedDialog()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Revoke All Permissions")
            .setMessage("Revoke ${targetPermissions.size} dangerous permission(s) from ${app?.appName ?: packageName}?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var successCount = 0
                    var failCount = 0
                    for (perm in targetPermissions) {
                        val result = ShizukuManager.revokePermission(packageName, perm.name)
                        if (result.success) successCount++ else failCount++
                    }
                    val msg = if (failCount == 0) {
                        "All ${successCount} permissions revoked"
                    } else {
                        "Revoked: $successCount, Failed: $failCount"
                    }
                    SnackbarHelper.showSnackbar(binding.root, msg)
                    viewModel.loadApp(packageName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.appInfo.observe(viewLifecycleOwner) { app ->
            app ?: return@observe
            if (app.icon != null) {
                binding.appIcon.setImageDrawable(app.icon)
            } else {
                try {
                    binding.appIcon.setImageDrawable(requireContext().packageManager.getApplicationIcon(app.packageName))
                } catch (e: Exception) {
                    binding.appIcon.setImageResource(android.R.drawable.ic_dialog_info)
                }
            }
            binding.appName.text = app.appName
            binding.packageName.text = app.packageName
            binding.appVersion.text = "v${app.versionName} (${app.versionCode})"

            binding.btnDisableEnable.text = if (app.isEnabled) {
                getString(R.string.disable_app)
            } else {
                getString(R.string.enable_app)
            }

            binding.infoContainer.removeAllViews()
            val currentLocale = getCurrentLocale()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", currentLocale)
            addInfoRow(getString(R.string.install_date), dateFormat.format(Date(app.installTime)))
            addInfoRow(getString(R.string.update_date), dateFormat.format(Date(app.updateTime)))
            addInfoRow(getString(R.string.target_sdk), "API ${app.targetSdkVersion}")
            addInfoRow(getString(R.string.min_sdk), "API ${app.minSdkVersion}")
            addInfoRow(getString(R.string.package_name), app.packageName)

            val storageInfo = viewModel.storageInfo.value
            if (storageInfo != null) {
                addInfoRow(getString(R.string.storage_app), formatFileSize(storageInfo.totalSize))
            } else {
                addInfoRow(getString(R.string.storage_app), getString(R.string.storage_unknown))
            }

            val installer = viewModel.installerAppName.value
            if (!installer.isNullOrEmpty()) {
                addInfoRow(getString(R.string.installer_app), installer)
            }
        }

        viewModel.permissions.observe(viewLifecycleOwner) { perms ->
            val filtered = if (ShizukuManager.isDhizukuMode()) {
                perms.filter { it.name !in AppRepository.DHIZUKU_UNSUPPORTED_PERMISSIONS }
            } else {
                perms
            }
            if (filtered.isNotEmpty()) {
                permAdapter.submitList(filtered)
                binding.permCount.text = "${filtered.count { it.isGranted }}/${filtered.size}"
            } else {
                permAdapter.submitList(emptyList())
                binding.permCount.text = "0/0"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val msg = if (result.success) {
                getString(R.string.operation_success)
            } else {
                val errorMsg = result.error.ifEmpty { "Operation failed" }
                getString(R.string.operation_failed, errorMsg)
            }
            SnackbarHelper.showSnackbar(binding.root, msg)
            viewModel.clearOperationResult()
        }
    }

    private fun getCurrentLocale(): Locale {
        val savedLanguage = LocaleManager.getLanguage(requireContext())
        return if (savedLanguage.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(savedLanguage)
        }
    }

    private fun addInfoRow(label: String, value: String) {
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_info_row, binding.infoContainer, false)
        row.findViewById<TextView>(R.id.label).text = label
        row.findViewById<TextView>(R.id.value).text = value
        binding.infoContainer.addView(row)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}