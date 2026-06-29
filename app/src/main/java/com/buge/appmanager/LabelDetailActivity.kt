package com.buge.appmanager

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.LabelAppAdapter
import com.buge.appmanager.adapter.LabelAppItem
import com.buge.appmanager.databinding.ActivityLabelDetailBinding
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.viewmodel.LabelDetailViewModel
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
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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

    private fun setupRecyclerView() {
        adapter = LabelAppAdapter { app, isChecked ->
            viewModel.toggleAppSelection(app.packageName, isChecked)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                val query = text?.toString()?.trim() ?: ""
                LogManager.debug(this@LabelDetailActivity, "Search", "Query: $query")
                viewModel.setSearch(query)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(this) { apps ->
            if (apps == null) return@observe
            val label = viewModel.label.value
            val items = apps.map { app ->
                LabelAppItem(app, label?.appPackages?.contains(app.packageName) ?: false)
            }
            adapter.submitList(items)

            val isEmpty = apps.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

            LogManager.debug(this@LabelDetailActivity, "Apps updated", "Count: ${apps.size}, Empty: $isEmpty")
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
                LogManager.debug(this@LabelDetailActivity, "Label updated", "Name: ${label.name}, Apps: ${label.appPackages.size}")
            }
        }
    }
}