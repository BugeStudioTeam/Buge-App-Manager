// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.ActivityDetailAdapter
import com.buge.appmanager.databinding.ActivityActivityDetailBinding
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.SpringAnimationHelper
import com.buge.appmanager.viewmodel.ActivityDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivityDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_IS_SYSTEM = "extra_is_system"
        private const val SHORTCUT_REQUEST_CODE = 1001
    }

    private lateinit var binding: ActivityActivityDetailBinding
    private val viewModel: ActivityDetailViewModel by viewModels()
    private lateinit var activitiesAdapter: ActivityDetailAdapter
    private var searchJob: Job? = null
    private var allActivities: List<ActivityDetail> = emptyList()
    private var packageName: String = ""
    private var fontApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        LogManager.info(this, "ActivityDetailActivity created", "Package: $packageName")

        setupBackPressedCallback()
        setupToolbar()
        setupAppInfo()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        viewModel.loadActivities(packageName)

        LogManager.info(this, "Activity details opened", "Package: $packageName")
    }

    override fun onResume() {
        super.onResume()
        if (!fontApplied) {
            fontApplied = true
        }
        LogManager.debug(this, "ActivityDetailActivity resumed", "Package: $packageName")
    }

    override fun onPause() {
        super.onPause()
        LogManager.debug(this, "ActivityDetailActivity paused", "Package: $packageName")
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
        LogManager.info(this, "ActivityDetailActivity destroyed", "Package: $packageName")
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                    LogManager.debug(this@ActivityDetailActivity, "Search cleared via back", "Package: $packageName")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        LogManager.debug(this, "Back pressed callback setup", "ActivityDetailActivity")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_detail)
        LogManager.debug(this, "Toolbar setup complete", "ActivityDetailActivity")
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

    private fun setupAppInfo() {
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val isSystem = intent.getBooleanExtra(EXTRA_IS_SYSTEM, false)

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val icon = packageManager.getApplicationIcon(appInfo)
            binding.appIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            LogManager.warning(this, "Failed to load app icon", "Package: $packageName, Error: ${e.message}")
        }

        binding.appName.text = appName
        binding.packageName.text = packageName
        binding.systemBadge.visibility = if (isSystem) android.view.View.VISIBLE else android.view.View.GONE

        LogManager.debug(this, "App info setup complete", "Package: $packageName, App: $appName, System: $isSystem")
    }

    private fun setupRecyclerView() {
        activitiesAdapter = ActivityDetailAdapter(
            onActivityClick = { activity ->
                handleActivityClick(activity)
            },
            onShortcutCreate = { activity, view ->
                showShortcutDialog(activity)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = activitiesAdapter
        LogManager.debug(this, "RecyclerView setup complete", "Package: $packageName")
    }

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        searchEditText?.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    LogManager.info(this@ActivityDetailActivity, "Searching activities", "Package: $packageName, Query: $query")
                }
                filterActivities(query)
            }
        }
        LogManager.debug(this, "Search setup complete", "Package: $packageName")
    }

    private fun filterActivities(query: String) {
        if (query.isEmpty()) {
            activitiesAdapter.submitList(allActivities)
        } else {
            val filtered = allActivities.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.className.contains(query, ignoreCase = true)
            }
            activitiesAdapter.submitList(filtered)
            LogManager.debug(this, "Filter applied", "Package: $packageName, Query: $query, Results: ${filtered.size}")
        }
    }

    private fun handleActivityClick(activity: ActivityDetail) {
        if (activity.isExported) {
            try {
                val intent = Intent().apply {
                    setClassName(packageName, activity.className)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent)
                SnackbarHelper.showSnackbar(binding.root, "Launching ${activity.name}")
                LogManager.info(this, "Activity launched", "Package: $packageName, Activity: ${activity.className}")
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Failed to launch: ${e.message}")
                LogManager.error(this, "Failed to launch activity", "Package: $packageName, Activity: ${activity.className}, Error: ${e.message}")
            }
        } else {
            SnackbarHelper.showSnackbar(binding.root, "This activity is not exported and cannot be launched")
            LogManager.warning(this, "Cannot launch unexported activity", "Package: $packageName, Activity: ${activity.className}")
        }
    }

    private fun showShortcutDialog(activity: ActivityDetail) {
        if (!activity.isExported) {
            SnackbarHelper.showSnackbar(binding.root, "This activity is not exported, cannot create shortcut")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Create Shortcut")
            .setMessage("Create shortcut for ${activity.name} on home screen?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                createShortcut(activity)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createShortcut(activity: ActivityDetail) {
        try {
            val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
            val appIcon = getAppIcon()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                createShortcutModern(activity, appName, appIcon)
            } else {
                createShortcutLegacy(activity, appName, appIcon)
            }
        } catch (e: Exception) {
            LogManager.error(this, "Shortcut creation failed", e.message)
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    private fun getAppIcon(): Drawable? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            LogManager.warning(this, "Failed to get app icon", e.message)
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null

        return try {
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 48
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }
        } catch (e: Exception) {
            LogManager.warning(this, "Failed to convert drawable to bitmap", e.message)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun createShortcutLegacy(activity: ActivityDetail, appName: String, appIcon: Drawable?) {
        val intent = Intent().apply {
            setClassName(packageName, activity.className)
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, activity.name)

            val iconBitmap = drawableToBitmap(appIcon)
            if (iconBitmap != null) {
                putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
            } else {
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this@ActivityDetailActivity, android.R.drawable.ic_menu_edit)
                )
            }
        }

        try {
            startActivityForResult(shortcutIntent, SHORTCUT_REQUEST_CODE)
        } catch (e: Exception) {
            LogManager.error(this, "Failed to start shortcut activity", e.message)
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    private fun createShortcutModern(activity: ActivityDetail, appName: String, appIcon: Drawable?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val shortcutManager = getSystemService(ShortcutManager::class.java)

        val intent = Intent().apply {
            setClassName(packageName, activity.className)
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcutBuilder = ShortcutInfo.Builder(this, "activity_${System.currentTimeMillis()}")
            .setShortLabel(activity.name)
            .setLongLabel("$appName - ${activity.name}")
            .setIntent(intent)

        val iconBitmap = drawableToBitmap(appIcon)
        if (iconBitmap != null) {
            try {
                shortcutBuilder.setIcon(Icon.createWithBitmap(iconBitmap))
            } catch (e: Exception) {
                LogManager.warning(this, "Failed to set icon bitmap, using fallback", e.message)
                shortcutBuilder.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_edit))
            }
        } else {
            shortcutBuilder.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_edit))
        }

        try {
            shortcutManager.requestPinShortcut(shortcutBuilder.build(), null)
            SnackbarHelper.showSnackbar(binding.root, "Shortcut created")
            LogManager.info(this, "Shortcut created", "Activity: ${activity.className}")
        } catch (e: Exception) {
            LogManager.error(this, "Failed to pin shortcut", e.message)
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SHORTCUT_REQUEST_CODE && resultCode == RESULT_OK) {
            SnackbarHelper.showSnackbar(binding.root, "Shortcut created")
            LogManager.info(this, "Shortcut created (legacy)", "Activity: $packageName")
        }
    }

    private fun observeViewModel() {
        viewModel.activities.observe(this) { activities ->
            allActivities = activities
            activitiesAdapter.submitList(activities)
            val isEmpty = activities.isEmpty()
            binding.emptyState.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            binding.recyclerView.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE

            LogManager.info(this, "Activities loaded for app", "Package: $packageName, Count: ${activities.size}, Empty: $isEmpty")
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
            if (isLoading) {
                LogManager.debug(this, "Loading activities", "Package: $packageName")
            }
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                LogManager.error(this, "Activity loading error", "Package: $packageName, Error: $error")
                SnackbarHelper.showSnackbar(binding.root, "Failed to load activities: $error")
            }
        }
    }
}