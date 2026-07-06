package com.buge.appmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityOptionalPermissionsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

data class OptionalPermissionItem(
    val name: String,
    val permission: String,
    val description: String,
    var isGranted: Boolean = false
)

class OptionalPermissionsActivity : BaseActivity() {

    private lateinit var binding: ActivityOptionalPermissionsBinding
    private lateinit var adapter: OptionalPermissionAdapter
    private var permissionItems: List<OptionalPermissionItem> = emptyList()

    companion object {
        private val PERMISSIONS = listOf(
            OptionalPermissionItem(
                name = "Read External Storage",
                permission = "android.permission.READ_EXTERNAL_STORAGE",
                description = "Allows app to read files from external storage for APK export and sharing"
            ),
            OptionalPermissionItem(
                name = "Write External Storage",
                permission = "android.permission.WRITE_EXTERNAL_STORAGE",
                description = "Allows app to write files to external storage for APK export and sharing"
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionalPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadPermissionStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_optional_permissions)
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

    private fun setupRecyclerView() {
        adapter = OptionalPermissionAdapter(
            onGrantClick = { perm ->
                grantPermission(perm)
            },
            onRevokeClick = { perm ->
                revokePermission(perm)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadPermissionStatus() {
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val updatedItems = PERMISSIONS.map { perm ->
                    val isGranted = checkPermissionStatus(perm.permission)
                    perm.copy(isGranted = isGranted)
                }
                permissionItems = updatedItems
                adapter.submitList(updatedItems)
                binding.loadingOverlay.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.loadingOverlay.visibility = View.GONE
                SnackbarHelper.showSnackbar(binding.root, "Failed to load permission status: ${e.message}")
                LogManager.error(this@OptionalPermissionsActivity, "Load permission status failed", e.message)
            }
        }
    }

    private suspend fun checkPermissionStatus(permission: String): Boolean {
        return try {
            // Use dumpsys package to check if permission is granted
            val result = ShizukuManager.executeCommand("dumpsys package ${packageName} | grep \"$permission\"")
            // Check if permission is granted
            result.output.contains("granted=true") || result.output.contains("$permission: granted=true")
        } catch (e: Exception) {
            false
        }
    }

    private fun grantPermission(perm: OptionalPermissionItem) {
        if (!checkShizuku()) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Grant Permission")
            .setMessage("Grant ${perm.name}?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    binding.loadingOverlay.visibility = View.VISIBLE
                    try {
                        val result = ShizukuManager.executeCommand("pm grant ${packageName} ${perm.permission}")
                        if (result.success) {
                            val updatedItems = permissionItems.map { item ->
                                if (item.permission == perm.permission) {
                                    item.copy(isGranted = true)
                                } else {
                                    item
                                }
                            }
                            permissionItems = updatedItems
                            adapter.submitList(updatedItems)
                            SnackbarHelper.showSnackbar(binding.root, "${perm.name} granted")
                            LogManager.success(this@OptionalPermissionsActivity, "Permission granted", perm.permission)
                        } else {
                            SnackbarHelper.showSnackbar(binding.root, "Failed to grant: ${result.error}")
                            LogManager.error(this@OptionalPermissionsActivity, "Grant failed", result.error)
                        }
                    } catch (e: Exception) {
                        SnackbarHelper.showSnackbar(binding.root, "Error: ${e.message}")
                        LogManager.error(this@OptionalPermissionsActivity, "Grant error", e.message)
                    } finally {
                        binding.loadingOverlay.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun revokePermission(perm: OptionalPermissionItem) {
        if (!checkShizuku()) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Revoke Permission")
            .setMessage("Revoke ${perm.name}?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    binding.loadingOverlay.visibility = View.VISIBLE
                    try {
                        val result = ShizukuManager.executeCommand("pm revoke ${packageName} ${perm.permission}")
                        if (result.success) {
                            val updatedItems = permissionItems.map { item ->
                                if (item.permission == perm.permission) {
                                    item.copy(isGranted = false)
                                } else {
                                    item
                                }
                            }
                            permissionItems = updatedItems
                            adapter.submitList(updatedItems)
                            SnackbarHelper.showSnackbar(binding.root, "${perm.name} revoked")
                            LogManager.success(this@OptionalPermissionsActivity, "Permission revoked", perm.permission)
                        } else {
                            SnackbarHelper.showSnackbar(binding.root, "Failed to revoke: ${result.error}")
                            LogManager.error(this@OptionalPermissionsActivity, "Revoke failed", result.error)
                        }
                    } catch (e: Exception) {
                        SnackbarHelper.showSnackbar(binding.root, "Error: ${e.message}")
                        LogManager.error(this@OptionalPermissionsActivity, "Revoke error", e.message)
                    } finally {
                        binding.loadingOverlay.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkShizuku(): Boolean {
        if (!ShizukuManager.isShizukuAvailable() || !ShizukuManager.hasShizukuPermission()) {
            SnackbarHelper.showSnackbar(
                binding.root,
                getString(R.string.error_no_shizuku),
                getString(R.string.shizuku_request_auth),
                { ShizukuManager.requestShizukuPermission() }
            )
            return false
        }
        return true
    }

    inner class OptionalPermissionAdapter(
        private val onGrantClick: (OptionalPermissionItem) -> Unit,
        private val onRevokeClick: (OptionalPermissionItem) -> Unit
    ) : RecyclerView.Adapter<OptionalPermissionAdapter.ViewHolder>() {

        private var items: List<OptionalPermissionItem> = emptyList()

        fun submitList(newItems: List<OptionalPermissionItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_optional_permission, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            applyItemBackground(holder, position)

            holder.btnGrant.setOnClickListener {
                if (item.isGranted) {
                    onRevokeClick(item)
                } else {
                    onGrantClick(item)
                }
            }
        }

        private fun applyItemBackground(holder: ViewHolder, position: Int) {
            val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
            val size = items.size
            val background = when {
                size == 1 -> R.drawable.bg_setting_item_single
                position == 0 -> R.drawable.bg_setting_item_top
                position == size - 1 -> R.drawable.bg_setting_item_bottom
                else -> R.drawable.bg_setting_item_middle
            }
            container.setBackgroundResource(background)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon: ImageView = itemView.findViewById(R.id.perm_icon)
            private val permName: TextView = itemView.findViewById(R.id.perm_name)
            private val permDesc: TextView = itemView.findViewById(R.id.perm_desc)
            private val statusText: TextView = itemView.findViewById(R.id.status_text)
            val btnGrant: MaterialButton = itemView.findViewById(R.id.btn_grant)

            fun bind(item: OptionalPermissionItem) {
                permName.text = item.name
                permDesc.text = item.description

                if (item.isGranted) {
                    statusText.text = getString(R.string.optional_perm_granted)
                    statusText.setTextColor(getColor(R.color.color_granted))
                    btnGrant.text = "Revoke"
                    btnGrant.isEnabled = true
                    btnGrant.alpha = 1f
                } else {
                    statusText.text = getString(R.string.optional_perm_denied)
                    statusText.setTextColor(getColor(R.color.color_denied))
                    btnGrant.text = getString(R.string.optional_perm_grant)
                    btnGrant.isEnabled = true
                    btnGrant.alpha = 1f
                }

                // Check Shizuku status
                val shizukuOk = ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()
                if (!shizukuOk) {
                    btnGrant.isEnabled = false
                    btnGrant.alpha = 0.4f
                }
            }
        }
    }
}