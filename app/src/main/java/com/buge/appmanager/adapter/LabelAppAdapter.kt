package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox

data class LabelAppItem(
    val app: AppInfo,
    var isSelected: Boolean = false
)

class LabelAppAdapter(
    private val onItemClick: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<LabelAppAdapter.ViewHolder>() {

    private var items: List<LabelAppItem> = emptyList()

    fun submitList(newItems: List<LabelAppItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateSelection(packageName: String, isSelected: Boolean) {
        val position = items.indexOfFirst { it.app.packageName == packageName }
        if (position >= 0) {
            items[position].isSelected = isSelected
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        applyItemBackground(holder, position)

        holder.itemView.setOnClickListener(null)
        holder.checkbox.setOnCheckedChangeListener(null)

        holder.itemView.setOnClickListener {
            val newSelected = !item.isSelected
            item.isSelected = newSelected
            holder.checkbox.isChecked = newSelected
            onItemClick(item.app, newSelected)
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (item.isSelected != isChecked) {
                item.isSelected = isChecked
                onItemClick(item.app, isChecked)
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
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)

        fun bind(item: LabelAppItem) {
            val icon = item.app.icon
            if (icon != null) {
                appIcon.setImageDrawable(icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            appName.text = item.app.appName
            packageName.text = item.app.packageName
            systemAppBadge.visibility = if (item.app.isSystemApp) View.VISIBLE else View.GONE
            checkbox.isChecked = item.isSelected
        }
    }
}