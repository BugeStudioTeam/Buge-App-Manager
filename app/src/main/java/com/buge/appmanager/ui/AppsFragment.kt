package com.buge.appmanager.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.AppDetailActivity
import com.buge.appmanager.adapter.AppsAdapter
import com.buge.appmanager.databinding.FragmentAppsBinding
import com.buge.appmanager.model.AppFilter
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppSortOrder
import com.buge.appmanager.viewmodel.AppsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupToolbar()
        observeViewModel()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadApps()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppsAdapter { app -> openAppDetail(app) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.setSearch(text?.toString() ?: "")
        }
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(com.buge.appmanager.R.id.chip_user) -> AppFilter.USER
                checkedIds.contains(com.buge.appmanager.R.id.chip_system) -> AppFilter.SYSTEM
                else -> AppFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.buge.appmanager.R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(com.buge.appmanager.R.string.sort_name),
            getString(com.buge.appmanager.R.string.sort_size),
            getString(com.buge.appmanager.R.string.sort_install_date)
        )
        val currentIndex = when (viewModel.currentSort) {
            AppSortOrder.NAME -> 0
            AppSortOrder.SIZE -> 1
            AppSortOrder.INSTALL_DATE -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(com.buge.appmanager.R.string.sort_name)
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
            adapter.submitList(apps)
            binding.emptyState.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE

            // Update count in toolbar subtitle
            binding.toolbar.subtitle = getString(com.buge.appmanager.R.string.apps_count, apps.size)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (!isLoading) binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun openAppDetail(app: AppInfo) {
        val intent = Intent(requireContext(), AppDetailActivity::class.java).apply {
            putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
