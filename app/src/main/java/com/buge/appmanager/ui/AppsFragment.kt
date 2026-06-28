package com.buge.appmanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.AppDetailActivity
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.AppsAdapter
import com.buge.appmanager.adapter.AppsItem
import com.buge.appmanager.databinding.FragmentAppsBinding
import com.buge.appmanager.model.AppFilter
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppSortOrder
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.SystemOpChecker
import com.buge.appmanager.viewmodel.AppsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppsAdapter
    private var fontApplied = false
    private var allApps: List<AppInfo> = emptyList()

    private var tempZipFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackPressedCallback()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupToolbar()
        setupBatchActions()
        observeViewModel()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadApps()
        }
    }

    override fun onResume() {
        super.onResume()
        if (activity is BaseActivity && !fontApplied) {
            FontOverrideHelper.applyToActivity(activity as BaseActivity)
            fontApplied = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanupTempFiles()
        _binding = null
    }

    private fun cleanupTempFiles() {
        try {
            tempZipFile?.let {
                if (it.exists()) {
                    it.delete()
                    LogManager.debug(requireContext(), "Cleaned up temp zip file", it.name)
                }
                tempZipFile = null
            }
            val cacheDir = requireContext().externalCacheDir ?: requireContext().cacheDir
            val files = cacheDir.listFiles { file -> file.name.endsWith(".zip") }
            files?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isInSelectionMode()) {
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    hideBatchActionBar()
                } else {
                    val searchText = binding.searchEditText.text.toString()
                    if (searchText.isNotEmpty()) {
                        binding.searchEditText.setText("")
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        adapter = AppsAdapter(
            onAppClick = { app -> openAppDetail(app) },
            onSelectionChanged = { count ->
                updateSelectionUI(count)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        if (!isAdded || view == null) return
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.setSearch(text?.toString() ?: "")
        }
    }

    private fun setupFilters() {
        if (!isAdded || view == null) return
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chip_user) -> AppFilter.USER
                checkedIds.contains(R.id.chip_system) -> AppFilter.SYSTEM
                checkedIds.contains(R.id.chip_favorite) -> AppFilter.FAVORITE
                else -> AppFilter.ALL
            }
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                adapter.setSelectionMode(false)
                hideBatchActionBar()
            }
            viewModel.setFilter(filter)
        }
    }

    private fun setupToolbar() {
        if (!isAdded || view == null) return
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBatchActions() {
        if (!isAdded || view == null) return

        binding.btnBatchUninstall.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            val systemApps = selected.filter { it.isSystemApp }
            if (systemApps.isNotEmpty() && !PreferencesManager.getAllowSystemOps(requireContext())) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.uninstall)
                .setMessage("Uninstall ${selected.size} selected app(s)?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    batchUninstall(selected)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchDisable.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            val systemApps = selected.filter { it.isSystemApp }
            if (systemApps.isNotEmpty() && !PreferencesManager.getAllowSystemOps(requireContext())) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }

            val toDisable = selected.filter { it.isEnabled }

            if (toDisable.isEmpty()) {
                SnackbarHelper.showSnackbar(binding.root, "Selected apps are already disabled")
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.disable_app)
                .setMessage("Disable ${toDisable.size} selected app(s)?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    batchDisable(toDisable)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchEnable.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            val systemApps = selected.filter { it.isSystemApp }
            if (systemApps.isNotEmpty() && !PreferencesManager.getAllowSystemOps(requireContext())) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }

            val toEnable = selected.filter { !it.isEnabled }

            if (toEnable.isEmpty()) {
                SnackbarHelper.showSnackbar(binding.root, "Selected apps are already enabled")
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.enable_app)
                .setMessage("Enable ${toEnable.size} selected app(s)?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    batchEnable(toEnable)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchShare.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            if (selected.size > 20) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Share APKs")
                    .setMessage("You are about to share ${selected.size} APKs. This may take a while and create a large zip file. Continue?")
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        batchShareApks(selected)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                batchShareApks(selected)
            }
        }
    }

    private fun batchShareApks(apps: List<AppInfo>) {
        if (!checkShizuku()) return

        binding.loadingOverlay.visibility = View.VISIBLE
        binding.batchActionScroll.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val zipFile = createApkZip(apps)
                if (zipFile != null) {
                    tempZipFile = zipFile
                    shareZipFile(zipFile, apps.size)
                } else {
                    SnackbarHelper.showSnackbar(binding.root, "Failed to create zip file")
                }
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Share failed: ${e.message}")
                LogManager.error(requireContext(), "Batch share failed", e.message)
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    private suspend fun createApkZip(apps: List<AppInfo>): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = requireContext().externalCacheDir ?: requireContext().cacheDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(cacheDir, "apks_${timestamp}.zip")

            val oldZips = cacheDir.listFiles { file -> file.name.endsWith(".zip") }
            oldZips?.forEach { if (it.exists()) it.delete() }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                var successCount = 0
                var failCount = 0

                for (app in apps) {
                    try {
                        val applicationInfo = requireContext().packageManager.getApplicationInfo(app.packageName, 0)
                        val sourcePath = applicationInfo.sourceDir

                        if (sourcePath.isNullOrEmpty()) {
                            failCount++
                            continue
                        }

                        val sourceFile = File(sourcePath)
                        if (!sourceFile.exists()) {
                            failCount++
                            continue
                        }

                        val fileName = "${app.appName}.apk".replace("/", "_").replace("\\", "_").replace(":", "_").replace("?", "_").replace("*", "_").replace(" ", "_")
                        val entry = ZipEntry(fileName)
                        zos.putNextEntry(entry)

                        BufferedInputStream(FileInputStream(sourceFile)).use { input ->
                            val buffer = ByteArray(8192)
                            var length: Int
                            while (input.read(buffer).also { length = it } != -1) {
                                zos.write(buffer, 0, length)
                            }
                        }
                        zos.closeEntry()
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                        LogManager.warning(requireContext(), "Failed to add APK to zip", "Package: ${app.packageName}, Error: ${e.message}")
                    }
                }

                LogManager.info(requireContext(), "Zip created", "Success: $successCount, Failed: $failCount")
            }

            if (zipFile.exists() && zipFile.length() > 0) {
                return@withContext zipFile
            } else {
                zipFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            LogManager.error(requireContext(), "Failed to create zip", e.message)
            return@withContext null
        }
    }

    private fun shareZipFile(zipFile: File, appCount: Int) {
        try {
            val apkUri = FileProvider.getUriForFile(
                requireContext(),
                "com.buge.appmanager.fileprovider",
                zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "${appCount} APKs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(
                shareIntent,
                "Share ${appCount} APKs"
            )
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(chooserIntent)

            LogManager.success(requireContext(), "APK zip shared", "App count: $appCount, Size: ${formatFileSize(zipFile.length())}")

            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()

        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Share failed: ${e.message}")
            LogManager.error(requireContext(), "Share zip failed", e.message)
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun batchUninstall(apps: List<AppInfo>) {
        if (!checkShizuku()) return

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            for (app in apps) {
                val result = ShizukuManager.uninstallApp(app.packageName)
                if (result.success) {
                    successCount++
                    LogManager.info(requireContext(), "App uninstalled", "Package: ${app.packageName}")
                } else {
                    failCount++
                    LogManager.error(requireContext(), "Failed to uninstall", "Package: ${app.packageName}, Error: ${result.error}")
                }
            }
            val msg = if (failCount == 0) {
                "All ${successCount} apps uninstalled"
            } else {
                "Uninstalled: $successCount, Failed: $failCount"
            }
            SnackbarHelper.showSnackbar(binding.root, msg)
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
            viewModel.loadApps()
        }
    }

    private fun batchDisable(apps: List<AppInfo>) {
        if (!checkShizuku()) return

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            for (app in apps) {
                val result = ShizukuManager.disableApp(app.packageName)
                if (result.success) {
                    successCount++
                    LogManager.info(requireContext(), "App disabled", "Package: ${app.packageName}")
                } else {
                    failCount++
                    LogManager.error(requireContext(), "Failed to disable", "Package: ${app.packageName}, Error: ${result.error}")
                }
            }
            val msg = if (failCount == 0) {
                "All ${successCount} apps disabled"
            } else {
                "Disabled: $successCount, Failed: $failCount"
            }
            SnackbarHelper.showSnackbar(binding.root, msg)
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
            viewModel.loadApps()
        }
    }

    private fun batchEnable(apps: List<AppInfo>) {
        if (!checkShizuku()) return

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            for (app in apps) {
                val result = ShizukuManager.enableApp(app.packageName)
                if (result.success) {
                    successCount++
                    LogManager.info(requireContext(), "App enabled", "Package: ${app.packageName}")
                } else {
                    failCount++
                    LogManager.error(requireContext(), "Failed to enable", "Package: ${app.packageName}, Error: ${result.error}")
                }
            }
            val msg = if (failCount == 0) {
                "All ${successCount} apps enabled"
            } else {
                "Enabled: $successCount, Failed: $failCount"
            }
            SnackbarHelper.showSnackbar(binding.root, msg)
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
            viewModel.loadApps()
        }
    }

    private fun checkShizuku(): Boolean {
        if (!ShizukuManager.isShizukuAvailable() || !ShizukuManager.hasShizukuPermission()) {
            SnackbarHelper.showSnackbar(
                binding.root,
                getString(R.string.error_no_shizuku),
                getString(R.string.shizuku_request_auth),
                { ShizukuManager.requestShizukuPermission() }
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

    private fun showBatchActionBar() {
        if (!isAdded || view == null) return
        if (binding.batchActionScroll.visibility != View.VISIBLE) {
            binding.batchActionScroll.visibility = View.VISIBLE
            binding.batchActionScroll.alpha = 0f
            binding.batchActionScroll.scaleX = 0.8f
            binding.batchActionScroll.scaleY = 0.8f
            binding.batchActionScroll.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    private fun hideBatchActionBar() {
        if (!isAdded || view == null) return
        if (binding.batchActionScroll.visibility == View.VISIBLE) {
            binding.batchActionScroll.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .withEndAction {
                    if (isAdded && view != null) {
                        binding.batchActionScroll.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun updateSelectionUI(count: Int) {
        if (!isAdded || view == null) return
        if (count > 0) {
            binding.selectedCountText.text = getString(R.string.selected_count, count)
            adapter.setSelectionMode(true)
            showBatchActionBar()
        } else {
            adapter.setSelectionMode(false)
            hideBatchActionBar()
        }
    }

    private fun showSortDialog() {
        if (!isAdded || view == null) return
        val options = arrayOf(
            getString(R.string.sort_name),
            getString(R.string.sort_size),
            getString(R.string.sort_install_date)
        )
        val currentIndex = when (viewModel.currentSort) {
            AppSortOrder.NAME -> 0
            AppSortOrder.SIZE -> 1
            AppSortOrder.INSTALL_DATE -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_name)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                if (adapter.isInSelectionMode()) {
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    hideBatchActionBar()
                }
                val sort = when (which) {
                    0 -> AppSortOrder.NAME
                    1 -> AppSortOrder.SIZE
                    2 -> AppSortOrder.INSTALL_DATE
                    else -> AppSortOrder.NAME
                }
                viewModel.setSort(sort)
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            if (!isAdded || view == null) return@observe
            allApps = apps
            val items = apps.map { AppsItem(it) }
            adapter.submitList(items)
            binding.emptyState.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
            binding.loadingOverlay.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded || view == null) return@observe
            if (isLoading && allApps.isEmpty()) {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            } else {
                binding.loadingOverlay.visibility = View.GONE
            }
            if (!isLoading) binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun openAppDetail(app: AppInfo) {
        if (adapter.isInSelectionMode()) {
            adapter.toggleSelection(app.packageName)
            return
        }
        val intent = Intent(requireContext(), AppDetailActivity::class.java).apply {
            putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
        }
        startActivity(intent)
    }
}