package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
        if (selectionMode != enabled) {
            selectionMode = enabled
            notifyItemRangeChanged(0, itemCount, "selection_mode")
        }
    }

    fun isInSelectionMode(): Boolean {
        return selectionMode
    }

    fun getSelectedItems(): List<AppPermissionItem> {
        return currentList.filter { it.isSelected }
    }

    fun selectAll() {
        currentList.forEachIndexed { index, item ->
            if (!item.isSelected) {
                item.isSelected = true
                notifyItemChanged(index, "selection")
            }
        }
        onSelectionChanged(currentList.size)
    }

    fun clearSelection() {
        currentList.forEachIndexed { index, item ->
            if (item.isSelected) {
                item.isSelected = false
                notifyItemChanged(index, "selection")
            }
        }
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        
        // 方案二关键：每次绑定时都重新设置长按监听器，确保不被覆盖
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                val item = getItem(holder.adapterPosition)
                item.isSelected = true
                notifyItemRangeChanged(0, itemCount, "selection_mode")
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                holder.animateCardSelection(true)
            }
            true
        }
        
        // 重新设置点击监听器
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnClickListener {
            holder.animateClick()
            if (selectionMode) {
                val item = getItem(holder.adapterPosition)
                val newSelected = !item.isSelected
                item.isSelected = newSelected
                holder.checkbox.isChecked = newSelected
                holder.animateCheckbox(newSelected)
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                notifyItemChanged(holder.adapterPosition, "selection")
            }
        }
        
        // 重新设置 checkbox 监听器
        holder.checkbox.setOnClickListener(null)
        holder.checkbox.setOnClickListener {
            holder.animateCheckboxClick()
            val item = getItem(holder.adapterPosition)
            val newSelected = holder.checkbox.isChecked
            item.isSelected = newSelected
            holder.animateCheckbox(newSelected)
            val count = currentList.count { it.isSelected }
            onSelectionChanged(count)
            notifyItemChanged(holder.adapterPosition, "selection")
        }
        
        // 重新设置权限状态 Chip 监听器
        holder.permStatusChip.setOnClickListener(null)
        holder.permStatusChip.setOnClickListener {
            val item = getItem(holder.adapterPosition)
            val isGranted = item.permissionMap[item.primaryPermission] ?: false
            onPermissionToggle(item.app, item.primaryPermission, isGranted)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        if (payloads.contains("selection_mode")) {
            holder.updateSelectionMode(selectionMode, item.isSelected)
        } else if (payloads.contains("selection")) {
            holder.updateSelection(item.isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        val permStatusChip: Chip = itemView.findViewById(R.id.perm_status_chip)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(item: AppPermissionItem) {
            appIcon.setImageDrawable(item.app.icon)
            appName.text = item.app.appName
            packageName.text = item.app.packageName

            if (item.app.isSystemApp) {
                systemAppBadge.visibility = View.VISIBLE
            } else {
                systemAppBadge.visibility = View.GONE
            }

            checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkbox.isChecked = item.isSelected

            if (selectionMode && checkbox.visibility == View.VISIBLE) {
                checkbox.alpha = 0f
                checkbox.scaleX = 0.5f
                checkbox.scaleY = 0.5f
                checkbox.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }

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
        }

        fun updateSelectionMode(mode: Boolean, isSelected: Boolean) {
            val targetVisibility = if (mode) View.VISIBLE else View.GONE
            if (checkbox.visibility != targetVisibility) {
                if (mode) {
                    checkbox.visibility = View.VISIBLE
                    checkbox.alpha = 0f
                    checkbox.scaleX = 0.5f
                    checkbox.scaleY = 0.5f
                    checkbox.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                } else {
                    checkbox.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .setDuration(150)
                        .withEndAction {
                            checkbox.visibility = View.GONE
                        }
                        .start()
                }
            }
            checkbox.isChecked = isSelected
        }

        fun updateSelection(isSelected: Boolean) {
            checkbox.isChecked = isSelected
            animateCheckbox(isSelected)
        }

        fun animateCardSelection(selected: Boolean) {
            if (selected) {
                cardView.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        cardView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }

        fun animateClick() {
            cardView.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(80)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    cardView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }
                .start()
        }

        fun animateCheckbox(checked: Boolean) {
            if (checked) {
                checkbox.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(100)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        checkbox.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
        }

        fun animateCheckboxClick() {
            checkbox.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(80)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    checkbox.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }
                .start()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppPermissionItem>() {
        override fun areItemsTheSame(
            oldItem: AppPermissionItem,
            newItem: AppPermissionItem
        ): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }

        override fun areContentsTheSame(
            oldItem: AppPermissionItem,
            newItem: AppPermissionItem
        ): Boolean {
            return oldItem.permissionMap.size == newItem.permissionMap.size &&
                   oldItem.permissionMap.all { (key, value) -> newItem.permissionMap[key] == value } &&
                   oldItem.isSelected == newItem.isSelected
        }

        override fun getChangePayload(
            oldItem: AppPermissionItem,
            newItem: AppPermissionItem
        ): Any? {
            if (oldItem.isSelected != newItem.isSelected) {
                return "selection"
            }
            return super.getChangePayload(oldItem, newItem)
        }
    }
}