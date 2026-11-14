// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui

import android.content.Context
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.R
import com.buge.appmanager.adapter.ActivityDetailAdapter
import com.buge.appmanager.databinding.FragmentActivityDetailBinding
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.viewmodel.ActivityDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ActivityDetailFragment : Fragment() {

    interface OnDetailCloseListener {
        fun onDetailClose()
    }

    companion object {
        private const val ARG_PACKAGE_NAME = "arg_package_name"
        private const val ARG_APP_NAME = "arg_app_name"
        private const val ARG_IS_SYSTEM = "arg_is_system"

        fun newInstance(packageName: String, appName: String, isSystem: Boolean): ActivityDetailFragment {
            val fragment = ActivityDetailFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_PACKAGE_NAME, packageName)
                putString(ARG_APP_NAME, appName)
                putBoolean(ARG_IS_SYSTEM, isSystem)
            }
            return fragment
        }
    }

    private var _binding: FragmentActivityDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ActivityDetailViewModel by viewModels()
    private lateinit var activitiesAdapter: ActivityDetailAdapter
    private var searchJob: Job? = null
    private var allActivities: List<ActivityDetail> = emptyList()
    private var packageName: String = ""
    private var searchQuery: String = ""
    private var closeListener: OnDetailCloseListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        closeListener = parentFragment as? OnDetailCloseListener
    }

    override fun onDetach() {
        super.onDetach()
        closeListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: run {
            closeListener?.onDetailClose()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        viewModel.loadActivities(packageName)

        LogManager.info(requireContext(), "ActivityDetailFragment opened", "Package: $packageName")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    private fun setupToolbar() {
        binding.detailToolbar.setNavigationOnClickListener {
            closeListener?.onDetailClose()
        }
    }

    private fun setupRecyclerView() {
        activitiesAdapter = ActivityDetailAdapter(
            onActivityClick = { activity ->
                handleActivityClick(activity)
            },
            onShortcutCreate = { activity, view ->
                showShortcutDialog(activity)
            },
            canLaunchUnexported = {
                ShizukuManager.isAuthorized()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = activitiesAdapter
    }

    private fun setupSearch() {
        val searchEditText = binding.searchEditText
        val btnClear = binding.btnClearSearch

        searchEditText?.addTextChangedListener { text ->
            searchQuery = text?.toString()?.trim() ?: ""
            btnClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            filterActivities(searchQuery)
        }

        btnClear?.setOnClickListener {
            searchEditText?.setText("")
            searchQuery = ""
            searchEditText?.requestFocus()
            filterActivities("")
        }

        searchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                searchEditText.clearFocus()
                true
            } else {
                false
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
                SnackbarHelper.showSnackbar(binding.root, "Launching ${activity.name}")
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Failed to launch: ${e.message}")
            }
        } else if (ShizukuManager.isRootMode() && ShizukuManager.isRootAvailable()) {
            launchUnexportedViaRoot(activity)
        } else if (!ShizukuManager.isRootMode() &&
            ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()
        ) {
            launchUnexportedViaShizuku(activity)
        } else {
            SnackbarHelper.showSnackbar(binding.root, "This activity is not exported and cannot be launched")
        }
    }

    private fun launchUnexportedViaRoot(activity: ActivityDetail) {
        val component = "$packageName/${activity.className}"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ShizukuManager.executeCommand("am start -n '$component'")
            if (result.success) {
                SnackbarHelper.showSnackbar(binding.root, "Launching ${activity.name}")
            } else {
                val detail = result.error.ifBlank { "unknown error" }
                SnackbarHelper.showSnackbar(binding.root, "Failed to launch: $detail")
            }
        }
    }

    private fun launchUnexportedViaShizuku(activity: ActivityDetail) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ShizukuManager.launchUnexportedViaShizuku(packageName, activity.className)
            if (result.success) {
                SnackbarHelper.showSnackbar(binding.root, "Launching ${activity.name}")
            } else {
                val detail = result.error.ifBlank { "unknown error" }
                SnackbarHelper.showSnackbar(binding.root, "Failed to launch: $detail")
            }
        }
    }

    private fun showShortcutDialog(activity: ActivityDetail) {
        if (!activity.isExported) {
            SnackbarHelper.showSnackbar(binding.root, "This activity is not exported, cannot create shortcut")
            return
        }

        MaterialAlertDialogBuilder(requireContext())
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
            val appName = arguments?.getString(ARG_APP_NAME) ?: packageName
            val appIcon = getAppIcon()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                createShortcutModern(activity, appName, appIcon)
            } else {
                createShortcutLegacy(activity, appName, appIcon)
            }
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    private fun getAppIcon(): Drawable? {
        return try {
            val appInfo = requireContext().packageManager.getApplicationInfo(packageName, 0)
            requireContext().packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
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
                    Intent.ShortcutIconResource.fromContext(requireContext(), android.R.drawable.ic_menu_edit)
                )
            }
        }

        try {
            startActivity(shortcutIntent)
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    private fun createShortcutModern(activity: ActivityDetail, appName: String, appIcon: Drawable?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val shortcutManager = requireContext().getSystemService(ShortcutManager::class.java)

        val intent = Intent().apply {
            setClassName(packageName, activity.className)
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcutBuilder = ShortcutInfo.Builder(requireContext(), "activity_${System.currentTimeMillis()}")
            .setShortLabel(activity.name)
            .setLongLabel("$appName - ${activity.name}")
            .setIntent(intent)

        val iconBitmap = drawableToBitmap(appIcon)
        if (iconBitmap != null) {
            try {
                shortcutBuilder.setIcon(Icon.createWithBitmap(iconBitmap))
            } catch (e: Exception) {
                shortcutBuilder.setIcon(Icon.createWithResource(requireContext(), android.R.drawable.ic_menu_edit))
            }
        } else {
            shortcutBuilder.setIcon(Icon.createWithResource(requireContext(), android.R.drawable.ic_menu_edit))
        }

        try {
            shortcutManager.requestPinShortcut(shortcutBuilder.build(), null)
            SnackbarHelper.showSnackbar(binding.root, "Shortcut created")
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Failed to create shortcut: ${e.message}")
        }
    }

    private fun observeViewModel() {
        viewModel.activities.observe(viewLifecycleOwner) { activities ->
            allActivities = activities
            filterActivities(searchQuery)
            val isEmpty = activities.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                SnackbarHelper.showSnackbar(binding.root, "Failed to load activities: $error")
            }
        }
    }
}