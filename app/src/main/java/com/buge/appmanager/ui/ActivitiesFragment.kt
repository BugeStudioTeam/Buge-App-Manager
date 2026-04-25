package com.buge.appmanager.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.ActivityDetailActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.ActivitiesAppAdapter
import com.buge.appmanager.databinding.FragmentActivitiesBinding
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.viewmodel.ActivitiesViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivitiesFragment : Fragment() {

    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ActivitiesViewModel by viewModels()
    private lateinit var appsAdapter: ActivitiesAppAdapter
    private var searchJob: Job? = null
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        viewModel.loadApps()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        viewModel.refresh()
    }

    private fun setupRecyclerView() {
        appsAdapter = ActivitiesAppAdapter { app ->
            val intent = Intent(requireContext(), ActivityDetailActivity::class.java).apply {
                putExtra(ActivityDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(ActivityDetailActivity.EXTRA_APP_NAME, app.appName)
                putExtra(ActivityDetailActivity.EXTRA_IS_SYSTEM, app.isSystemApp)
            }
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appsAdapter
    }

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        searchEditText?.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                filterApps(query)
            }
        }
    }

    private fun filterApps(query: String) {
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
            allApps = apps
            appsAdapter.submitList(apps)
            val isEmpty = apps.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}