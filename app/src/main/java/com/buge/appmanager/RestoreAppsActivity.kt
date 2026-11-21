// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityRestoreAppsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

data class UninstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

class RestoreAppsActivity : BaseActivity() {

    private lateinit var binding: ActivityRestoreAppsBinding
    private lateinit var adapter: RestoreAppsAdapter
    private var uninstalledApps: List<UninstalledAppInfo> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestoreAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressedCallback()
        setupToolbar()
        setupSearch()
        setupRecyclerView()
        loadUninstalledApps()
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                    searchQuery = ""
                    filterApps("")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_restore_apps)
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

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        val btnClear = binding.btnClearSearch

        searchEditText.addTextChangedListener { text ->
            searchQuery = text?.toString()?.trim() ?: ""
            btnClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            filterApps(searchQuery)
        }

        btnClear.setOnClickListener {
            searchEditText.setText("")
            searchQuery = ""
            searchEditText.requestFocus()
            filterApps("")
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                searchEditText.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RestoreAppsAdapter { app ->
            showRestoreConfirmDialog(app)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadUninstalledApps() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val apps = getUninstalledApps()
                uninstalledApps = apps
                filterApps(searchQuery)
            } catch (e: Exception) {
                LogManager.error(this@RestoreAppsActivity, "Failed to load uninstalled apps", e.message)
                SnackbarHelper.showSnackbar(binding.root, "Failed to load: ${e.message}")
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) {
            uninstalledApps
        } else {
            uninstalledApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filtered)

        val isEmpty = filtered.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

        binding.emptyStateText.text = if (uninstalledApps.isEmpty()) {
            getString(R.string.no_uninstalled_apps)
        } else {
            getString(R.string.no_apps_found)
        }
    }

    private suspend fun getUninstalledApps(): List<UninstalledAppInfo> {
        val result = ShizukuManager.executeCommand("pm list packages -u")
        if (!result.success) {
            LogManager.warning(this, "Failed to get all packages", result.error)
            return emptyList()
        }

        val allPackages = result.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotEmpty() }
            .toSet()

        val installedResult = ShizukuManager.executeCommand("pm list packages")
        if (!installedResult.success) {
            LogManager.warning(this, "Failed to get installed packages", installedResult.error)
            return emptyList()
        }

        val installedPackages = installedResult.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotEmpty() }
            .toSet()

        val systemResult = ShizukuManager.executeCommand("pm list packages -s")
        if (!systemResult.success) {
            LogManager.warning(this, "Failed to get system packages", systemResult.error)
            return emptyList()
        }

        val systemPackages = systemResult.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotEmpty() }
            .toSet()

        val uninstalledPackageNames = allPackages.filter { pkg ->
            pkg !in installedPackages
        }

        if (uninstalledPackageNames.isEmpty()) {
            return emptyList()
        }

        val apps = mutableListOf<UninstalledAppInfo>()

        for (pkg in uninstalledPackageNames) {
            var appName = pkg.substringAfterLast(".")
            val isSystemApp = pkg in systemPackages

            try {
                val labelResult = ShizukuManager.executeCommand("dumpsys package $pkg | grep -E 'applicationInfo.*labelRes' | head -1")
                if (labelResult.success && labelResult.output.isNotEmpty()) {
                    val match = Regex("labelRes=0x[0-9a-f]+ '([^']+)'").find(labelResult.output)
                    appName = match?.groupValues?.get(1) ?: appName
                }
            } catch (e: Exception) {
            }

            apps.add(UninstalledAppInfo(
                packageName = pkg,
                appName = appName,
                isSystemApp = isSystemApp
            ))
        }

        return apps.sortedBy { it.appName.lowercase() }
    }

    private fun showRestoreConfirmDialog(app: UninstalledAppInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore)
            .setMessage(getString(R.string.restore_confirm, app.appName))
            .setPositiveButton(R.string.restore) { _, _ ->
                restoreApp(app)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreApp(app: UninstalledAppInfo) {
        if (!checkShizuku()) return

        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = ShizukuManager.executeCommand("pm install-existing --user 0 ${app.packageName}")
                if (result.success) {
                    SnackbarHelper.showSnackbar(binding.root, getString(R.string.restore_success))
                    LogManager.success(this@RestoreAppsActivity, "App restored", "Package: ${app.packageName}")
                    loadUninstalledApps()
                } else {
                    val errorMsg = result.error.ifEmpty { "Unknown error" }
                    SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.restore_failed)}: $errorMsg")
                    LogManager.error(this@RestoreAppsActivity, "Restore failed", "Package: ${app.packageName}, Error: $errorMsg")
                }
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.restore_failed)}: ${e.message}")
                LogManager.error(this@RestoreAppsActivity, "Restore failed", e.message)
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
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

    inner class RestoreAppsAdapter(
        private val onRestoreClick: (UninstalledAppInfo) -> Unit
    ) : RecyclerView.Adapter<RestoreAppsAdapter.ViewHolder>() {

        private var items: List<UninstalledAppInfo> = emptyList()

        fun submitList(newItems: List<UninstalledAppInfo>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_restore_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])

            val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
            val size = items.size

            val background = when {
                size == 1 -> R.drawable.bg_setting_item_single
                position == 0 -> R.drawable.bg_setting_item_top
                position == size - 1 -> R.drawable.bg_setting_item_bottom
                else -> R.drawable.bg_setting_item_middle
            }
            container.setBackgroundResource(background)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
            private val appName: TextView = itemView.findViewById(R.id.app_name)
            private val packageName: TextView = itemView.findViewById(R.id.package_name)
            private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
            private val btnRestore: MaterialButton = itemView.findViewById(R.id.btn_restore)

            fun bind(app: UninstalledAppInfo) {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                appName.text = app.appName
                packageName.text = app.packageName

                if (app.isSystemApp) {
                    systemAppBadge.visibility = View.VISIBLE
                } else {
                    systemAppBadge.visibility = View.GONE
                }

                btnRestore.setOnClickListener {
                    onRestoreClick(app)
                }
            }
        }
    }
}
