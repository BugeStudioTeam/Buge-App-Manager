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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.ActivityDetailActivity
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.ActivitiesAppAdapter
import com.buge.appmanager.databinding.FragmentActivitiesBinding
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SpringAnimationHelper
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
    private var fontApplied = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        LogManager.info(requireContext(), "ActivitiesFragment created", "View created")

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
        LogManager.info(requireContext(), "ActivitiesFragment resumed", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        LogManager.info(requireContext(), "ActivitiesFragment paused", "onPause called")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
        LogManager.info(requireContext(), "ActivitiesFragment destroyed", "View destroyed")
    }

    fun refresh() {
        LogManager.info(requireContext(), "ActivitiesFragment refresh", "Refreshing apps")
        viewModel.refresh()
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchText = binding.searchEditText.text.toString()
                if (searchText.isNotEmpty()) {
                    binding.searchEditText.setText("")
                    LogManager.info(requireContext(), "Search cleared via back", "Search text was: $searchText")
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        LogManager.debug(requireContext(), "Back pressed callback setup", "ActivitiesFragment")
    }

    private fun runSpringEnterAnimation() {
        if (!isAdded || view == null) return
        binding.recyclerView.alpha = 0f
        binding.recyclerView.translationY = 30f
        binding.recyclerView.post {
            if (isAdded && view != null) {
                SpringAnimationHelper.animateAlpha(binding.recyclerView, 1f)
                SpringAnimationHelper.animateTranslationY(binding.recyclerView, 0f)
                LogManager.debug(requireContext(), "Spring enter animation applied", "ActivitiesFragment")
            }
        }
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        appsAdapter = ActivitiesAppAdapter { app ->
            SpringAnimationHelper.animateClick(binding.recyclerView)
            LogManager.info(requireContext(), "App clicked in Activities", "Package: ${app.packageName}, App: ${app.appName}")
            val intent = Intent(requireContext(), ActivityDetailActivity::class.java).apply {
                putExtra(ActivityDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(ActivityDetailActivity.EXTRA_APP_NAME, app.appName)
                putExtra(ActivityDetailActivity.EXTRA_IS_SYSTEM, app.isSystemApp)
            }
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appsAdapter
        LogManager.debug(requireContext(), "RecyclerView setup complete", "ActivitiesFragment")
    }

    private fun setupSearch() {
        if (!isAdded || view == null) return
        val searchEditText = binding.searchEditText
        searchEditText?.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    LogManager.info(requireContext(), "Searching activities", "Query: $query")
                }
                filterApps(query)
            }
        }
        LogManager.debug(requireContext(), "Search setup complete", "ActivitiesFragment")
    }

    private fun setupSwipeRefresh() {
        if (!isAdded || view == null) return
        binding.swipeRefresh.setOnRefreshListener {
            LogManager.info(requireContext(), "Swipe refresh triggered", "ActivitiesFragment")
            viewModel.refresh()
        }
        LogManager.debug(requireContext(), "Swipe refresh setup complete", "ActivitiesFragment")
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
            LogManager.debug(requireContext(), "Filter applied", "Query: $query, Results: ${filtered.size}")
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            if (!isAdded || view == null) return@observe
            allApps = apps
            appsAdapter.submitList(apps)
            val isEmpty = apps.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.swipeRefresh.isRefreshing = false
            binding.loadingOverlay.visibility = View.GONE
            
            LogManager.info(requireContext(), "Activities loaded", "Count: ${apps.size}, Empty: $isEmpty")
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded || view == null) return@observe
            if (isLoading && allApps.isEmpty()) {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
                LogManager.debug(requireContext(), "Loading activities", "Loading overlay shown")
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