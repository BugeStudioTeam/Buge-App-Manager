// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.ActivityDetailActivity
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.ActivitiesAppAdapter
import com.buge.appmanager.databinding.FragmentActivitiesBinding
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SplitPaneController
import com.buge.appmanager.util.SpringAnimationHelper
import com.buge.appmanager.viewmodel.ActivitiesViewModel
import kotlinx.coroutines.Job

class ActivitiesFragment : Fragment(), ActivityDetailFragment.OnDetailCloseListener {

    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ActivitiesViewModel by viewModels()
    private lateinit var appsAdapter: ActivitiesAppAdapter
    private var searchJob: Job? = null
    private var allApps: List<AppInfo> = emptyList()
    private var fontApplied = false
    private var searchQuery: String = ""

    private var splitController: SplitPaneController? = null
    private var isTabletMode = false

    private val detailContainerId = 1002

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isTabletMode = isLargeScreen()

        LogManager.info(requireContext(), "ActivitiesFragment created", "View created")

        setupSplitPane()
        setupBackPressedCallback()
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        observeViewModel()
        viewModel.loadApps()

        runSpringEnterAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (activity is BaseActivity && !fontApplied) {
            FontOverrideHelper.applyToActivity(activity as BaseActivity)
            fontApplied = true
        }
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    override fun onDetailClose() {
        closeDetailPane()
    }

    fun refresh() {
        viewModel.refresh()
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

    private fun openDetailInPane(app: AppInfo) {
        val controller = splitController ?: return
        if (binding.detailPane.id != detailContainerId) {
            binding.detailPane.id = detailContainerId
        }
        val fragment = ActivityDetailFragment.newInstance(app.packageName, app.appName, app.isSystemApp)
        controller.open(childFragmentManager, detailContainerId, fragment, "activity_detail_${app.packageName}")
    }

    private fun closeDetailPane(): Boolean {
        val controller = splitController ?: return false
        return controller.closeIfOpen(childFragmentManager, detailContainerId)
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTabletMode && closeDetailPane()) {
                    return
                }
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                    searchQuery = ""
                    filterApps("")
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun runSpringEnterAnimation() {
        if (!isAdded || view == null) return
        binding.recyclerView.alpha = 0f
        binding.recyclerView.translationY = 30f
        binding.recyclerView.post {
            if (isAdded && view != null) {
                SpringAnimationHelper.animateAlpha(binding.recyclerView, 1f)
                SpringAnimationHelper.animateTranslationY(binding.recyclerView, 0f)
            }
        }
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        appsAdapter = ActivitiesAppAdapter { app ->
            SpringAnimationHelper.animateClick(binding.recyclerView)
            openAppActivities(app)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appsAdapter
    }

    private fun openAppActivities(app: AppInfo) {
        if (isTabletMode) {
            openDetailInPane(app)
        } else {
            val intent = Intent(requireContext(), ActivityDetailActivity::class.java).apply {
                putExtra(ActivityDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(ActivityDetailActivity.EXTRA_APP_NAME, app.appName)
                putExtra(ActivityDetailActivity.EXTRA_IS_SYSTEM, app.isSystemApp)
            }
            startActivity(intent)
        }
    }

    private fun setupSearch() {
        if (!isAdded || view == null) return
        val searchEditText = binding.searchEditText
        val btnClear = binding.btnClearSearch

        searchEditText?.addTextChangedListener { text ->
            searchQuery = text?.toString()?.trim() ?: ""
            btnClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            filterApps(searchQuery)
        }

        btnClear?.setOnClickListener {
            searchEditText?.setText("")
            searchQuery = ""
            searchEditText?.requestFocus()
            filterApps("")
        }

        searchEditText?.setOnEditorActionListener { _, actionId, _ ->
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

    private fun setupSwipeRefresh() {
        if (!isAdded || view == null) return
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun filterApps(query: String) {
        if (!isAdded || view == null) return
        if (query.isEmpty()) {
            appsAdapter.submitList(allApps)
        } else {
            val filtered = allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
            appsAdapter.submitList(filtered)
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            if (!isAdded || view == null) return@observe
            allApps = apps
            filterApps(searchQuery)
            val isEmpty = apps.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.swipeRefresh.isRefreshing = false
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
            if (isLoading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                LogManager.error(requireContext(), "Activities loading error", error)
            }
        }
    }
}