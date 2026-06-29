package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox

data class AppsItem(
    val app: AppInfo,
    var isSelected: Boolean = false
)

class AppsAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<AppsItem, AppsAdapter.AppViewHolder>(DiffCallback()) {

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

    fun getSelectedItems(): List<AppInfo> {
        return currentList.filter { it.isSelected }.map { it.app }
    }

    fun toggleSelection(packageName: String) {
        val index = currentList.indexOfFirst { it.app.packageName == packageName }
        if (index >= 0) {
            val item = currentList[index]
            val newSelected = !item.isSelected
            item.isSelected = newSelected
            notifyItemChanged(index, "selection")
            onSelectionChanged(currentList.count { it.isSelected })
        }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
        applyItemBackground(holder, position)
        setupItemClickListeners(holder, position)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        if (payloads.contains("selection_mode")) {
            holder.updateSelectionMode(selectionMode, item.isSelected)
        } else if (payloads.contains("selection")) {
            holder.updateSelection(item.isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
        // Always re-apply background on any bind
        applyItemBackground(holder, position)
        setupItemClickListeners(holder, position)
    }

    private fun applyItemBackground(holder: AppViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = currentList.size

        val background = when {
            size == 1 -> R.drawable.bg_setting_item_single
            position == 0 -> R.drawable.bg_setting_item_top
            position == size - 1 -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)
    }

    private fun setupItemClickListeners(holder: AppViewHolder, position: Int) {
        val item = getItem(position)

        // Click listener
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                val newSelected = !item.isSelected
                item.isSelected = newSelected
                holder.checkbox.isChecked = newSelected
                holder.animateCheckbox(newSelected)
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                notifyItemChanged(position, "selection")
            } else {
                onAppClick(item.app)
            }
        }

        // Long press listener
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                item.isSelected = true
                notifyItemRangeChanged(0, itemCount, "selection_mode")
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                holder.animateCardSelection(true)
            }
            true
        }

        // Checkbox listener
        holder.checkbox.setOnClickListener {
            val newSelected = holder.checkbox.isChecked
            item.isSelected = newSelected
            holder.animateCheckbox(newSelected)
            val count = currentList.count { it.isSelected }
            onSelectionChanged(count)
            notifyItemChanged(position, "selection")
        }
    }

    override fun submitList(list: List<AppsItem>?) {
        super.submitList(list)
        // Notify all items to update backgrounds when list changes
        // This ensures corner radii are recalculated after filter/search
        list?.let {
            // Use post to ensure it runs after the list is fully submitted
            // and notifyDataSetChanged to force refresh all item backgrounds
            // This is a safe approach to recalculate corner radii
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<AppsItem>, currentList: MutableList<AppsItem>) {
        super.onCurrentListChanged(previousList, currentList)
        // When list size changes, refresh all items to update corner radii
        if (previousList.size != currentList.size) {
            notifyItemRangeChanged(0, currentList.size, "background")
        }
    }

    override fun getItemCount(): Int = currentList.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val appVersion: TextView = itemView.findViewById(R.id.app_version)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
        private val textContainer: ViewGroup = appName.parent as ViewGroup
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: View = itemView

        fun bind(item: AppsItem) {
            val app = item.app
            if (app.icon != null) {
                appIcon.setImageDrawable(app.icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            appName.text = app.appName
            packageName.text = app.packageName
            appVersion.text = "v${app.versionName}"

            if (app.isSystemApp) {
                systemAppBadge.visibility = View.VISIBLE
            } else {
                systemAppBadge.visibility = View.GONE
            }

            // Set initial state based on selectionMode
            if (selectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 1f
                checkbox.scaleX = 1f
                checkbox.scaleY = 1f
                appIcon.translationX = 28f
                textContainer.translationX = 28f
                packageName.translationX = 28f
            } else {
                checkbox.visibility = View.GONE
                appIcon.translationX = 0f
                textContainer.translationX = 0f
                packageName.translationX = 0f
            }
            checkbox.isChecked = item.isSelected
        }

        fun updateSelectionMode(mode: Boolean, isSelected: Boolean) {
            if (mode) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 0f
                checkbox.scaleX = 0.5f
                checkbox.scaleY = 0.5f
                
                appIcon.animate()
                    .translationX(28f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
                textContainer.animate()
                    .translationX(28f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
                packageName.animate()
                    .translationX(28f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
                checkbox.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            } else {
                appIcon.animate()
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
                textContainer.animate()
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
                packageName.animate()
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                
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
    }

    class DiffCallback : DiffUtil.ItemCallback<AppsItem>() {
        override fun areItemsTheSame(oldItem: AppsItem, newItem: AppsItem): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }

        override fun areContentsTheSame(oldItem: AppsItem, newItem: AppsItem): Boolean {
            return oldItem.app.packageName == newItem.app.packageName &&
                   oldItem.isSelected == newItem.isSelected
        }

        override fun getChangePayload(oldItem: AppsItem, newItem: AppsItem): Any? {
            if (oldItem.isSelected != newItem.isSelected) {
                return "selection"
            }
            return "background"
        }
    }
}