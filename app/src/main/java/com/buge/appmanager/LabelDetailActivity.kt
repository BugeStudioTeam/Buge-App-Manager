package com.buge.appmanager

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.LabelAppAdapter
import com.buge.appmanager.adapter.LabelAppItem
import com.buge.appmanager.databinding.ActivityLabelDetailBinding
import com.buge.appmanager.util.CustomLabelManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.viewmodel.LabelDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LabelDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_LABEL_ID = "label_id"
        const val EXTRA_LABEL_NAME = "label_name"
    }

    private lateinit var binding: ActivityLabelDetailBinding
    private val viewModel: LabelDetailViewModel by viewModels()
    private lateinit var adapter: LabelAppAdapter
    private var labelId: String = ""
    private var labelName: String = ""
    private var searchJob: Job? = null
    private var currentViewMode: ViewMode = ViewMode.UNSELECTED
    private var isExitingSelectionMode = false

    private enum class ViewMode {
        UNSELECTED, SELECTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        labelId = intent.getStringExtra(EXTRA_LABEL_ID) ?: run {
            finish()
            return
        }
        labelName = intent.getStringExtra(EXTRA_LABEL_NAME) ?: ""

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupViewChips()
        setupBatchActions()
        observeViewModel()
        setupBackPressedCallback()

        viewModel.init(labelId)

        LogManager.info(this, "LabelDetailActivity opened", "Label: $labelName, Id: $labelId")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.select_apps_for_label, labelName)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (adapter.isSelectionMode()) {
                    exitSelectionMode()
                } else {
                    finish()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode()) {
                    exitSelectionMode()
                    return
                }
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun setupViewChips() {
        binding.viewChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when {
                checkedIds.contains(R.id.chip_selected) -> ViewMode.SELECTED
                else -> ViewMode.UNSELECTED
            }
            currentViewMode = mode
            if (adapter.isSelectionMode()) {
                exitSelectionMode()
            }
            applyFilter()
        }
    }

    private fun setupRecyclerView() {
        adapter = LabelAppAdapter(
            onItemClick = { app, isSelected ->
                toggleAppSelection(app.packageName, isSelected)
            },
            onSelectionChanged = { count ->
                // Only update UI if not already exiting
                if (!isExitingSelectionMode) {
                    updateSelectionUI(count)
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                viewModel.setSearch(query)
            }
        }
    }

    private fun setupBatchActions() {
        binding.btnBatchAdd.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            batchAddApps(selected)
        }

        binding.btnBatchRemove.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            batchRemoveApps(selected)
        }
    }

    private fun batchAddApps(apps: List<com.buge.appmanager.model.AppInfo>) {
        lifecycleScope.launch {
            try {
                val label = viewModel.label.value ?: return@launch
                val currentPackages = label.appPackages.toMutableSet()
                var addedCount = 0
                for (app in apps) {
                    if (currentPackages.add(app.packageName)) {
                        addedCount++
                    }
                }
                if (addedCount == 0) {
                    SnackbarHelper.showSnackbar(binding.root, "Apps already in label")
                    return@launch
                }
                val updatedLabel = label.copy(appPackages = currentPackages.toList())
                CustomLabelManager.updateLabel(this@LabelDetailActivity, updatedLabel)
                viewModel.refresh()
                exitSelectionMode()
                SnackbarHelper.showSnackbar(binding.root, "Added $addedCount app(s)")
                LogManager.info(this@LabelDetailActivity, "Batch add apps", "Count: $addedCount")
            } catch (e: Exception) {
                LogManager.error(this@LabelDetailActivity, "Batch add error", e.message)
                SnackbarHelper.showSnackbar(binding.root, "Failed: ${e.message}")
            }
        }
    }

    private fun batchRemoveApps(apps: List<com.buge.appmanager.model.AppInfo>) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Apps")
            .setMessage("Remove ${apps.size} app(s) from this label?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val label = viewModel.label.value ?: return@launch
                        val currentPackages = label.appPackages.toMutableSet()
                        var removedCount = 0
                        for (app in apps) {
                            if (currentPackages.remove(app.packageName)) {
                                removedCount++
                            }
                        }
                        val updatedLabel = label.copy(appPackages = currentPackages.toList())
                        CustomLabelManager.updateLabel(this@LabelDetailActivity, updatedLabel)
                        viewModel.refresh()
                        exitSelectionMode()
                        SnackbarHelper.showSnackbar(binding.root, "Removed $removedCount app(s)")
                        LogManager.info(this@LabelDetailActivity, "Batch remove apps", "Count: $removedCount")
                    } catch (e: Exception) {
                        LogManager.error(this@LabelDetailActivity, "Batch remove error", e.message)
                        SnackbarHelper.showSnackbar(binding.root, "Failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleAppSelection(packageName: String, isSelected: Boolean) {
        lifecycleScope.launch {
            try {
                val label = viewModel.label.value ?: return@launch
                val newPackages = if (isSelected) {
                    if (label.appPackages.contains(packageName)) return@launch
                    label.appPackages + packageName
                } else {
                    if (!label.appPackages.contains(packageName)) return@launch
                    label.appPackages.filter { it != packageName }
                }
                val updatedLabel = label.copy(appPackages = newPackages)
                CustomLabelManager.updateLabel(this@LabelDetailActivity, updatedLabel)
                viewModel.refresh()
                LogManager.debug(this@LabelDetailActivity, "App toggled", "Package: $packageName, Selected: $isSelected")
            } catch (e: Exception) {
                LogManager.error(this@LabelDetailActivity, "Toggle error", e.message)
            }
        }
    }

    private fun applyFilter() {
        val label = viewModel.label.value
        val searchQuery = viewModel.searchQuery.value ?: ""
        val allApps = viewModel.allApps.value ?: emptyList()

        val filtered = if (searchQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        val selectedPackages = label?.appPackages ?: emptyList()
        val items = when (currentViewMode) {
            ViewMode.UNSELECTED -> filtered
                .filter { !selectedPackages.contains(it.packageName) }
                .map { LabelAppItem(it, false, false) }
            ViewMode.SELECTED -> filtered
                .filter { selectedPackages.contains(it.packageName) }
                .map { LabelAppItem(it, true, false) }
        }

        adapter.submitList(items)
        updateEmptyState(items.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateSelectionUI(count: Int) {
        if (count > 0) {
            binding.selectedCountText.text = "$count selected"
            adapter.setSelectionMode(true)
            showBatchActionBar()
        } else {
            exitSelectionMode()
        }
    }

    private fun showBatchActionBar() {
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

        if (currentViewMode == ViewMode.UNSELECTED) {
            binding.btnBatchAdd.visibility = View.VISIBLE
            binding.btnBatchRemove.visibility = View.GONE
        } else {
            binding.btnBatchAdd.visibility = View.GONE
            binding.btnBatchRemove.visibility = View.VISIBLE
        }
    }

    private fun hideBatchActionBar() {
        binding.batchActionScroll.visibility = View.GONE
        binding.batchActionScroll.animate().cancel()
    }

    private fun exitSelectionMode() {
        // Prevent recursive calls
        if (isExitingSelectionMode) {
            return
        }
        isExitingSelectionMode = true
        try {
            hideBatchActionBar()
            adapter.clearSelection()
            adapter.setSelectionMode(false)
        } finally {
            isExitingSelectionMode = false
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(this) { apps ->
            if (apps == null) return@observe
            applyFilter()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading && viewModel.apps.value.isNullOrEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.label.observe(this) { label ->
            if (label != null) {
                supportActionBar?.subtitle = "${label.appPackages.size} apps"
                applyFilter()
            }
        }

        viewModel.searchQuery.observe(this) {
            applyFilter()
        }

        viewModel.allApps.observe(this) {
            applyFilter()
        }
    }
}