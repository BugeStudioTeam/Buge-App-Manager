package com.buge.appmanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.R
import com.buge.appmanager.adapter.AppPermissionAdapter
import com.buge.appmanager.adapter.AppPermissionItem
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.databinding.FragmentPermissionsBinding
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.viewmodel.PermissionsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionsViewModel by viewModels()
    private lateinit var adapter: AppPermissionAdapter

    private var currentPermissions: List<String> = AppRepository.PERMISSION_MICROPHONE
    private var currentPermissionLabel: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentPermissionLabel = getString(R.string.perm_microphone)
        setupRecyclerView()
        setupPermissionChips()
        setupBatchActions()
        observeViewModel()

        viewModel.loadAppsForPermissions(currentPermissions)
    }

    private fun setupRecyclerView() {
        adapter = AppPermissionAdapter(
            onPermissionToggle = { app, permission, isGranted ->
                handlePermissionToggle(app, permission, isGranted)
            },
            onSelectionChanged = { count ->
                updateSelectionUI(count)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupPermissionChips() {
        binding.permissionChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val (permissions, label) = when {
                checkedIds.contains(R.id.chip_mic) -> Pair(AppRepository.PERMISSION_MICROPHONE, getString(R.string.perm_microphone))
                checkedIds.contains(R.id.chip_camera) -> Pair(AppRepository.PERMISSION_CAMERA, getString(R.string.perm_camera))
                checkedIds.contains(R.id.chip_location) -> Pair(AppRepository.PERMISSION_LOCATION, getString(R.string.perm_location))
                checkedIds.contains(R.id.chip_contacts) -> Pair(AppRepository.PERMISSION_CONTACTS, getString(R.string.perm_contacts))
                checkedIds.contains(R.id.chip_storage) -> Pair(AppRepository.PERMISSION_STORAGE, getString(R.string.perm_storage))
                checkedIds.contains(R.id.chip_phone) -> Pair(AppRepository.PERMISSION_PHONE, getString(R.string.perm_phone))
                checkedIds.contains(R.id.chip_sms) -> Pair(AppRepository.PERMISSION_SMS, getString(R.string.perm_sms))
                checkedIds.contains(R.id.chip_calendar) -> Pair(AppRepository.PERMISSION_CALENDAR, getString(R.string.perm_calendar))
                checkedIds.contains(R.id.chip_sensors) -> Pair(AppRepository.PERMISSION_SENSORS, getString(R.string.perm_body_sensors))
                checkedIds.contains(R.id.chip_notifications) -> Pair(AppRepository.PERMISSION_NOTIFICATIONS, getString(R.string.perm_notifications))
                checkedIds.contains(R.id.chip_overlay) -> Pair(AppRepository.PERMISSION_OVERLAY, getString(R.string.perm_overlay))
                checkedIds.contains(R.id.chip_install_unknown) -> Pair(AppRepository.PERMISSION_INSTALL_UNKNOWN_APPS, getString(R.string.perm_install_unknown))
                else -> Pair(AppRepository.PERMISSION_MICROPHONE, getString(R.string.perm_microphone))
            }
            currentPermissions = permissions
            currentPermissionLabel = label
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            binding.batchActionBar.visibility = View.GONE
            viewModel.loadAppsForPermissions(permissions)
        }
    }

    private fun setupBatchActions() {
        binding.btnBatchRevoke.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.batch_revoke)
                .setMessage(getString(R.string.confirm_revoke, selected.size))
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.batchRevokePermission(
                        selected.map { Pair(it.app, it.permissionMap) },
                        currentPermissions.first()
                    )
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    binding.batchActionBar.visibility = View.GONE
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnBatchGrant.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.batch_grant)
                .setMessage(getString(R.string.confirm_grant, selected.size))
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.batchGrantPermission(
                        selected.map { Pair(it.app, it.permissionMap) },
                        currentPermissions.first()
                    )
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    binding.batchActionBar.visibility = View.GONE
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun handlePermissionToggle(app: AppInfo, permission: String, isGranted: Boolean) {
        val actionLabel = if (isGranted) getString(R.string.revoke_permission) else getString(R.string.grant_permission)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(actionLabel)
            .setMessage("${app.appName}\n${permission}")
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (isGranted) {
                    viewModel.revokePermission(app.packageName, permission)
                } else {
                    viewModel.grantPermission(app.packageName, permission)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateSelectionUI(count: Int) {
        if (count > 0) {
            binding.batchActionBar.visibility = View.VISIBLE
            binding.selectedCountText.text = getString(R.string.selected_count, count)
            adapter.setSelectionMode(true)
        } else {
            binding.batchActionBar.visibility = View.GONE
            adapter.setSelectionMode(false)
        }
    }

    private fun observeViewModel() {
        viewModel.appsWithPermission.observe(viewLifecycleOwner) { apps ->
            val primaryPerm = currentPermissions.firstOrNull() ?: return@observe
            val items = apps.map { (app, permMap) ->
                AppPermissionItem(
                    app = app,
                    permissionMap = permMap,
                    primaryPermission = primaryPerm
                )
            }
            adapter.submitList(items)
            binding.toolbar.subtitle = getString(R.string.apps_count, items.size)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val msg = if (result.success) {
                getString(R.string.operation_success)
            } else {
                val errorMsg = result.error.ifEmpty { "Operation failed" }
                getString(R.string.operation_failed, errorMsg)
            }
            val duration = if (result.success) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
            Snackbar.make(binding.root, msg, duration).show()
            viewModel.clearOperationResult()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
