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
import com.buge.appmanager.AppDetailActivity
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.AppsAdapter
import com.buge.appmanager.databinding.FragmentAppsBinding
import com.buge.appmanager.model.AppFilter
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppSortOrder
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.viewmodel.AppsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppsAdapter
    private var fontApplied = false

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
        _binding = null
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        adapter = AppsAdapter { app -> openAppDetail(app) }
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
            adapter.submitList(apps)
            binding.emptyState.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
            binding.loadingOverlay.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded || view == null) return@observe
            if (isLoading) {
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
        val intent = Intent(requireContext(), AppDetailActivity::class.java).apply {
            putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
        }
        startActivity(intent)
    }
}