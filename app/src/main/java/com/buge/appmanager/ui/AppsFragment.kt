// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
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
import com.buge.appmanager.util.CustomLabelManager
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.SplitPaneController
import com.buge.appmanager.viewmodel.AppsViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppsAdapter
    private var fontApplied = false
    private var allApps: List<AppInfo> = emptyList()
    private var selectedLabelId: String? = null
    private var currentFilter: AppFilter = AppFilter.ALL
    private var isUpdatingChips = false
    private var searchQuery: String = ""

    private var savedScrollPosition: Int = 0
    private var savedScrollOffset: Int = 0
    private var isRestoringScroll = false

    private var splitController: SplitPaneController? = null
    private var isTabletMode = false

    private val detailContainerId = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isTabletMode = isLargeScreen()

        setupSplitPane()
        setupRecyclerView()
        setupBackPressedCallback()
        setupSearch()
        setupFilters()
        setupBatchActions()
        setupLabelChips()
        setupToolbarMenu()
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
        updateDisabledChipVisibility()
        setupLabelChips()
        viewModel.loadApps()
        restoreScrollPosition()
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isLargeScreen(): Boolean {
        val displayMetrics = resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        return widthDp >= 600
    }

    private fun setupSplitPane() {
        if (!isTabletMode) {
            binding.detailPane.visibility = View.GONE
            binding.splitDivider.visibility = View.GONE
            return
        }
        splitController = SplitPaneController(
            listPane = binding.listPane,
            divider = binding.splitDivider,
            detailPane = binding.detailPane
        ) {
            binding.recyclerView.scrollToPosition(0)
        }
    }

    private fun openDetailInPane(packageName: String) {
        val controller = splitController ?: return
        if (binding.detailPane.id != detailContainerId) {
            binding.detailPane.id = detailContainerId
        }
        val fragment = AppDetailFragment.newInstance(packageName)
        controller.open(childFragmentManager, detailContainerId, fragment, "detail_$packageName")
    }

    private fun closeDetailPane(): Boolean {
        val controller = splitController ?: return false
        return controller.closeIfOpen(childFragmentManager, detailContainerId)
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

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTabletMode && closeDetailPane()) {
                    return
                }
                if (adapter.isInSelectionMode()) {
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    hideBatchActionBar()
                    return
                }

                if (selectedLabelId != null) {
                    isUpdatingChips = true
                    for (i in 0 until binding.labelChipGroup.childCount) {
                        val chip = binding.labelChipGroup.getChildAt(i) as? Chip
                        chip?.isChecked = false
                    }
                    selectedLabelId = null
                    isUpdatingChips = false

                    val allChip = binding.filterChipGroup.findViewById<Chip>(R.id.chip_all)
                    allChip?.isChecked = true
                    currentFilter = AppFilter.ALL
                    applyFilter(currentFilter)
                    return
                }

                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                    searchQuery = ""
                    applyFilter(currentFilter)
                    return
                }

                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupToolbarMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        val btnClear = binding.btnClearSearch

        searchEditText.addTextChangedListener { text ->
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                adapter.setSelectionMode(false)
                hideBatchActionBar()
            }

            searchQuery = text?.toString()?.trim() ?: ""
            btnClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            applyFilter(currentFilter)
        }

        btnClear.setOnClickListener {
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                adapter.setSelectionMode(false)
                hideBatchActionBar()
            }

            searchEditText.setText("")
            searchQuery = ""
            searchEditText.requestFocus()
            applyFilter(currentFilter)
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                searchEditText.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupFilters() {
        if (!isAdded || view == null) return
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isUpdatingChips) return@setOnCheckedStateChangeListener

            if (checkedIds.isNotEmpty()) {
                isUpdatingChips = true
                for (i in 0 until binding.labelChipGroup.childCount) {
                    val chip = binding.labelChipGroup.getChildAt(i) as? Chip
                    chip?.isChecked = false
                }
                selectedLabelId = null
                isUpdatingChips = false
            }

            val filter = when {
                checkedIds.contains(R.id.chip_user) -> AppFilter.USER
                checkedIds.contains(R.id.chip_system) -> AppFilter.SYSTEM
                checkedIds.contains(R.id.chip_disabled) -> AppFilter.DISABLED
                checkedIds.contains(R.id.chip_favorite) -> AppFilter.FAVORITE
                else -> AppFilter.ALL
            }
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                adapter.setSelectionMode(false)
                hideBatchActionBar()
            }
            binding.searchEditText.setText("")
            searchQuery = ""
            applyFilter(filter)
        }

        updateDisabledChipVisibility()
    }

    private fun updateDisabledChipVisibility() {
        if (!isAdded || view == null) return
        val chip = binding.filterChipGroup.findViewById<Chip>(R.id.chip_disabled) ?: return
        val showDisabled = PreferencesManager.getShowDisabledApps(requireContext())
        chip.visibility = if (showDisabled) View.VISIBLE else View.GONE

        if (!showDisabled && chip.isChecked) {
            isUpdatingChips = true
            chip.isChecked = false
            val allChip = binding.filterChipGroup.findViewById<Chip>(R.id.chip_all)
            allChip?.isChecked = true
            currentFilter = AppFilter.ALL
            isUpdatingChips = false
            applyFilter(currentFilter)
        }
    }

    private fun setupLabelChips() {
        val labels = CustomLabelManager.getLabels(requireContext())
        val chipGroup = binding.labelChipGroup
        chipGroup.removeAllViews()

        chipGroup.isSelectionRequired = false
        chipGroup.isSingleSelection = false

        if (labels.isNotEmpty()) {
            chipGroup.visibility = View.VISIBLE
            binding.labelDivider.visibility = View.VISIBLE

            for (label in labels) {
                val chip = layoutInflater.inflate(R.layout.chip_label, chipGroup, false) as Chip
                chip.text = label.name
                chip.id = View.generateViewId()
                val isSelected = selectedLabelId == label.id
                chip.isChecked = isSelected
                chip.isClickable = true
                chip.isFocusable = true

                val typeface = FontOverrideHelper.getTypefaceByStyle(android.graphics.Typeface.NORMAL)
                if (typeface != null) {
                    chip.typeface = typeface
                }

                chip.setOnClickListener {
                    if (isUpdatingChips) return@setOnClickListener

                    val currentId = selectedLabelId

                    if (currentId == label.id) {
                        chip.isChecked = true
                        return@setOnClickListener
                    }

                    isUpdatingChips = true

                    for (i in 0 until chipGroup.childCount) {
                        val c = chipGroup.getChildAt(i) as? Chip
                        c?.isChecked = false
                    }

                    chip.isChecked = true
                    selectedLabelId = label.id

                    binding.filterChipGroup.clearCheck()

                    isUpdatingChips = false

                    binding.searchEditText.setText("")
                    searchQuery = ""
                    applyLabelFilter(label.id)
                }

                chipGroup.addView(chip)
            }
        } else {
            chipGroup.visibility = View.GONE
            binding.labelDivider.visibility = View.GONE
        }
    }

    private fun applyLabelFilter(labelId: String) {
        val label = CustomLabelManager.getLabelById(requireContext(), labelId)
        if (label != null) {
            val filteredApps = allApps.filter { label.appPackages.contains(it.packageName) }
            val items = filteredApps.map { AppsItem(it) }
            adapter.submitList(items)
            if (!isRestoringScroll) {
                binding.recyclerView.scrollToPosition(0)
            }
            val isEmpty = filteredApps.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun applyFilter(filter: AppFilter) {
        currentFilter = filter

        val filtered = when (filter) {
            AppFilter.ALL -> allApps
            AppFilter.USER -> allApps.filter { !it.isSystemApp }
            AppFilter.SYSTEM -> allApps.filter { it.isSystemApp }
            AppFilter.DISABLED -> allApps.filter { !it.isEnabled }
            AppFilter.FAVORITE -> allApps.filter { PreferencesManager.isFavoriteApp(requireContext(), it.packageName) }
        }

        val finalList = if (searchQuery.isNotEmpty()) {
            filtered.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filtered
        }

        val items = finalList.map { AppsItem(it) }
        adapter.submitList(items)
        if (!isRestoringScroll) {
            binding.recyclerView.scrollToPosition(0)
        }
        val isEmpty = finalList.isEmpty()
        binding.emptyState.visibility = if (isEmpty && allApps.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty && allApps.isNotEmpty()) View.GONE else View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun setupBatchActions() {
        if (!isAdded || view == null) return

        binding.btnSelectAll.setOnClickListener {
            val itemCount = adapter.itemCount
            if (itemCount > 0) {
                adapter.selectAll()
            }
        }

        binding.btnClearSelection.setOnClickListener {
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
        }

        binding.btnBatchUninstall.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
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
            val selected = adapter.getSelectedItems().filter { it.isEnabled }
            if (selected.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.disable_app)
                .setMessage("Disable ${selected.size} selected app(s)?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    batchDisable(selected)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchEnable.setOnClickListener {
            val selected = adapter.getSelectedItems().filter { !it.isEnabled }
            if (selected.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.enable_app)
                .setMessage("Enable ${selected.size} selected app(s)?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    batchEnable(selected)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchShare.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
        }
    }

    private fun batchUninstall(apps: List<AppInfo>) {
        if (!checkShizuku()) return
        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            for (app in apps) {
                val result = ShizukuManager.uninstallApp(app.packageName)
                if (result.success) successCount++ else failCount++
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
                if (result.success) successCount++ else failCount++
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
                if (result.success) successCount++ else failCount++
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
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(0)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            if (!isAdded || view == null) return@observe
            allApps = apps
            if (selectedLabelId != null) {
                applyLabelFilter(selectedLabelId!!)
            } else {
                applyFilter(currentFilter)
            }
            if (!isRestoringScroll) {
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(0)
                }
            } else {
                restoreScrollPosition()
            }
            binding.loadingOverlay.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
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

    private fun saveScrollPosition() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.let {
            savedScrollPosition = it.findFirstVisibleItemPosition()
            val firstView = it.findViewByPosition(savedScrollPosition)
            savedScrollOffset = firstView?.top ?: 0
        }
    }

    private fun restoreScrollPosition() {
        if (savedScrollPosition > 0 && !isRestoringScroll) {
            isRestoringScroll = true
            binding.recyclerView.post {
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset)
                isRestoringScroll = false
            }
        }
    }

    private fun openAppDetail(app: AppInfo) {
        if (adapter.isInSelectionMode()) {
            adapter.toggleSelection(app.packageName)
            return
        }
        if (isTabletMode) {
            openDetailInPane(app.packageName)
        } else {
            saveScrollPosition()
            val intent = Intent(requireContext(), AppDetailActivity::class.java).apply {
                putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
            }
            startActivity(intent)
        }
    }
}