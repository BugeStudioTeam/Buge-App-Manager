package com.buge.appmanager

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.ActivityDetailAdapter
import com.buge.appmanager.databinding.ActivityActivityDetailBinding
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SpringAnimationHelper
import com.buge.appmanager.viewmodel.ActivityDetailViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivityDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_IS_SYSTEM = "extra_is_system"
    }

    private lateinit var binding: ActivityActivityDetailBinding
    private val viewModel: ActivityDetailViewModel by viewModels()
    private lateinit var activitiesAdapter: ActivityDetailAdapter
    private var searchJob: Job? = null
    private var allActivities: List<ActivityDetail> = emptyList()
    private var packageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        setupBackPressedCallback()
        setupToolbar()
        setupAppInfo()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        viewModel.loadActivities(packageName)

        LogManager.info(this, "Activity details opened", "Package: $packageName")
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_detail)
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
        }

        binding.appName.text = appName
        binding.packageName.text = packageName
        binding.systemBadge.visibility = if (isSystem) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupRecyclerView() {
        activitiesAdapter = ActivityDetailAdapter { activity ->
            handleActivityClick(activity)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = activitiesAdapter
    }

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        searchEditText?.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                filterActivities(query)
            }
        }
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
                Snackbar.make(binding.root, "Launching ${activity.name}", Snackbar.LENGTH_SHORT).show()
                LogManager.info(this, "Activity launched", "Package: $packageName, Activity: ${activity.className}")
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to launch: ${e.message}", Snackbar.LENGTH_LONG).show()
                LogManager.error(this, "Failed to launch activity", "Package: $packageName, Activity: ${activity.className}, Error: ${e.message}")
            }
        } else {
            Snackbar.make(binding.root, "This activity is not exported and cannot be launched", Snackbar.LENGTH_SHORT).show()
            LogManager.warning(this, "Cannot launch unexported activity", "Package: $packageName, Activity: ${activity.className}")
        }
    }

    private fun observeViewModel() {
        viewModel.activities.observe(this) { activities ->
            allActivities = activities
            activitiesAdapter.submitList(activities)
            val isEmpty = activities.isEmpty()
            binding.emptyState.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            binding.recyclerView.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
}