package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip

data class AppPermissionItem(
    val app: AppInfo,
    val permissionMap: Map<String, Boolean>,
    val primaryPermission: String,
    var isSelected: Boolean = false
)

class AppPermissionAdapter(
    private val onPermissionToggle: (AppInfo, String, Boolean) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<AppPermissionItem, AppPermissionAdapter.ViewHolder>(DiffCallback()) {

    private var selectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<AppPermissionItem> {
        return currentList.filter { it.isSelected }
    }

    fun selectAll() {
        currentList.forEach { it.isSelected = true }
        notifyDataSetChanged()
        onSelectionChanged(currentList.size)
    }

    fun clearSelection() {
        currentList.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val permStatusChip: Chip = itemView.findViewById(R.id.perm_status_chip)

        fun bind(item: AppPermissionItem) {
            appIcon.setImageDrawable(item.app.icon)
            appName.text = item.app.appName
            packageName.text = item.app.packageName

            // Show checkbox in selection mode
            checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkbox.isChecked = item.isSelected

            // Determine primary permission status
            val isGranted = item.permissionMap[item.primaryPermission] ?: false

            if (isGranted) {
                permStatusChip.text = itemView.context.getString(R.string.granted)
                permStatusChip.setChipBackgroundColorResource(R.color.color_granted_container)
                permStatusChip.setTextColor(itemView.context.getColor(R.color.color_granted))
            } else {
                permStatusChip.text = itemView.context.getString(R.string.denied)
                permStatusChip.setChipBackgroundColorResource(R.color.color_denied_container)
                permStatusChip.setTextColor(itemView.context.getColor(R.color.color_denied))
            }

            permStatusChip.setOnClickListener {
                onPermissionToggle(item.app, item.primaryPermission, isGranted)
            }

            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    item.isSelected = true
                    notifyDataSetChanged()
                    onSelectionChanged(1)
                }
                true
            }

            itemView.setOnClickListener {
                if (selectionMode) {
                    item.isSelected = !item.isSelected
                    notifyItemChanged(adapterPosition)
                    onSelectionChanged(currentList.count { it.isSelected })
                }
            }

            checkbox.setOnClickListener {
                item.isSelected = checkbox.isChecked
                onSelectionChanged(currentList.count { it.isSelected })
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppPermissionItem>() {
        override fun areItemsTheSame(oldItem: AppPermissionItem, newItem: AppPermissionItem): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }

        override fun areContentsTheSame(oldItem: AppPermissionItem, newItem: AppPermissionItem): Boolean {
            return oldItem.permissionMap.size == newItem.permissionMap.size && 
                   oldItem.permissionMap.all { (key, value) -> newItem.permissionMap[key] == value } &&
                   oldItem.isSelected == newItem.isSelected
        }
    }
}
